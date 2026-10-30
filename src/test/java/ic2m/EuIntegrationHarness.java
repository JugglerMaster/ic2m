package ic2m;

import arc.ApplicationListener;
import arc.Core;
import arc.backend.headless.HeadlessApplication;
import arc.graphics.Camera;
import arc.graphics.Color;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.core.World;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.world.Tile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** Headless world smoke test for real cable buildings and tile placement. */
public final class EuIntegrationHarness implements ApplicationListener {
    private static final Team TEAM = Team.sharded;
    private Throwable failure;

    public static void main(String[] args) {
        new HeadlessApplication(new EuIntegrationHarness());
    }

    @Override
    public void init() {
        try {
            Vars.headless = true;
            // Avoid Vars.init(): it expects packaged campaign maps/assets. The harness only
            // needs the content registry, world tiles, and the headless application loop.
            Vars.content = new ContentLoader();
            Vars.state = new GameState();
            Vars.state.rules = new Rules();
            Vars.state.set(GameState.State.playing);
            Groups.init();
            Vars.world = new World();
            Vars.world.tiles = Vars.world.resize(32, 8);
            Vars.world.tiles.fill();
            Items.load();
            Vars.content.handleContent(new mindustry.type.Item("test-copper-dust", Color.white));

            Ic2CableBlock cableBlock = new Ic2CableBlock("eu-test-cable");
            cableBlock.nodeRange = 2;
            cableBlock.transferRate = 10f;
            cableBlock.loss = 0f;
            cableBlock.cableCapacity = 100f;
            cableBlock.init();

            BatteryBlock batteryBlock = new BatteryBlock("eu-test-battery");
            batteryBlock.init();

            BatteryBlock.BatteryBuild source = place(batteryBlock, 4, 4);
            Ic2CableBlock.Ic2CableBuild cable = place(cableBlock, 6, 4);
            BatteryBlock.BatteryBuild target = place(batteryBlock, 8, 4);
            source.energy = 20f;
            // Inject at the cable so this test isolates the real cable/world path from
            // generator scheduling and tests the cable's own transfer behavior.
            cable.energy = source.provideEnergy(20f);
            source.energy = source.maxEnergy;
            // The harness places tiles directly (no proximities), so link the cable to its
            // target manually to give it a transfer path.
            cable.onConfigureBuildTapped(target);

            for (int i = 0; i < 4; i++) {
                cable.distributePower();
                cable.update();
                target.update();
            }

            check("LV cable transferred energy", target.energy > 0f);
            check("LV cable stayed within capacity", cable.energy <= cable.maxEnergy);
            check("source remained isolated from cable output", source.energy == source.maxEnergy);

            // --- Transformer: two bridge pairs (LV<->MV, MV<->HV) with up/down direction ---
            Ic2CableBlock mvBlock = new Ic2CableBlock("eu-test-mv-cable");
            mvBlock.powerTier = 1;
            mvBlock.nodeRange = 2;
            mvBlock.transferRate = 10f;
            mvBlock.loss = 0f;
            mvBlock.cableCapacity = 100f;
            mvBlock.init();
            Ic2CableBlock hv2Block = new Ic2CableBlock("eu-test-hv2-cable");
            hv2Block.powerTier = 2;
            hv2Block.highVoltage = true;
            hv2Block.nodeRange = 2;
            hv2Block.transferRate = 10f;
            hv2Block.loss = 0f;
            hv2Block.cableCapacity = 100f;
            hv2Block.init();
            Ic2TransformerBlock transformerBlock = new Ic2TransformerBlock("eu-test-transformer");
            transformerBlock.init();

            // Probes for the tier-gating checks (independent of the transfer runs below).
            Ic2CableBlock.Ic2CableBuild mvProbe = place(mvBlock, 4, 0);
            Ic2CableBlock.Ic2CableBuild hvProbe = place(hv2Block, 5, 0);
            BatteryBlock.BatteryBuild lvProbe = place(batteryBlock, 6, 0);

            // LV -> MV step-up (pair0): transformer rotation 0 faces +x into an MV cable (tier1).
            Ic2TransformerBlock.Ic2TransformerBuild stepUpLM = place(transformerBlock, 1, 2);
            Ic2CableBlock.Ic2CableBuild mvCable = place(mvBlock, 2, 2);
            stepUpLM.energy = 20f;
            stepUpLM.update();
            check("step-up LV->MV feeds the faced MV cable", mvCable.energy > 0f);
            check("step-up LV->MV accepts LV input", stepUpLM.acceptsFrom(lvProbe));
            check("step-up LV->MV rejects MV input", !stepUpLM.acceptsFrom(mvProbe));
            check("step-up LV->MV rejects HV input", !stepUpLM.acceptsFrom(hvProbe));

            // MV -> LV step-down (pair0): transformer faces an LV machine (tier0).
            Ic2TransformerBlock.Ic2TransformerBuild stepDownML = place(transformerBlock, 1, 4);
            stepDownML.mode = Ic2TransformerBlock.MODE_STEP_DOWN;
            BatteryBlock.BatteryBuild lvTarget = place(batteryBlock, 2, 4);
            stepDownML.energy = 20f;
            stepDownML.update();
            check("step-down MV->LV feeds the faced LV machine", lvTarget.energy > 0f);
            check("step-down MV->LV accepts MV input", stepDownML.acceptsFrom(mvProbe));
            check("step-down MV->LV rejects LV input", !stepDownML.acceptsFrom(lvTarget));
            check("step-down MV->LV rejects HV input", !stepDownML.acceptsFrom(hvProbe));

            // MV -> HV step-up (pair1): transformer faces an HV cable (tier2).
            Ic2TransformerBlock.Ic2TransformerBuild stepUpMH = place(transformerBlock, 1, 6);
            stepUpMH.pair = Ic2TransformerBlock.PAIR_MV_HV;
            Ic2CableBlock.Ic2CableBuild hvCable2 = place(hv2Block, 2, 6);
            stepUpMH.energy = 20f;
            stepUpMH.update();
            check("step-up MV->HV feeds the faced HV cable", hvCable2.energy > 0f);
            check("step-up MV->HV accepts MV input", stepUpMH.acceptsFrom(mvProbe));
            check("step-up MV->HV rejects HV input", !stepUpMH.acceptsFrom(hvProbe));

            check("insulated LV cable has no loss", configuredCable(false, 2, 15f, 0f).loss == 0f);
            check("reinforced HV cable has extended range", configuredCable(true, 20, 25f, .02f).nodeRange == 20);
            check("superconductor cable is lossless", configuredCable(true, 24, 40f, 0f).loss == 0f);

            SolarPanel solarBlock = new SolarPanel("eu-test-solar");
            solarBlock.init();
            Vars.state.rules.solarMultiplier = 1f;
            SolarPanel.SolarPanelBuild solar = place(solarBlock, 28, 4);
            solar.energy = 0f;
            solar.update();
            check("solar panel generates EU", solar.energy > 0f);

            MaceratorBlock feederMaceratorBlock = new MaceratorBlock("eu-test-feeder-macerator");
            feederMaceratorBlock.init();
            MaceratorBlock.MaceratorBuild feederMacerator = place(feederMaceratorBlock, 28, 6);
            check("machine accepts conveyor-style ore input", feederMacerator.acceptItem(null, Items.copper));
            feederMacerator.handleItem(null, Items.copper);
            feederMacerator.energy = 2000f;
            for (int i = 0; i < 220; i++) feederMacerator.update();
            check("conveyor-style input reaches macerator output", feederMacerator.pendingOutputAmount == 2);

            byte[] cableState = save(mvCable);
            Ic2CableBlock.Ic2CableBuild restoredCable = place(mvBlock, 8, 0);
            restoredCable.read(new arc.util.io.Reads(new DataInputStream(new ByteArrayInputStream(cableState))), (byte)1);
            check("cable links survive save/load", restoredCable.links.size == mvCable.links.size);
            byte[] transformerState = save(stepUpLM);
            Ic2TransformerBlock.Ic2TransformerBuild restoredTransformer = place(transformerBlock, 24, 0);
            restoredTransformer.read(new arc.util.io.Reads(new DataInputStream(new ByteArrayInputStream(transformerState))), (byte)4);
            check("transformer pair survives save/load", restoredTransformer.pair == Ic2TransformerBlock.PAIR_LV_MV);
            check("transformer mode survives save/load", restoredTransformer.mode == Ic2TransformerBlock.MODE_STEP_UP);

            // The headless harness has no renderer/GL, so give drawing code a batch to
            // operate on. This lets the selection/placement draw paths actually run and
            // surface regressions in our rendering code.
            if (arc.Core.batch == null) arc.Core.batch = new HeadlessBatch();
            if (arc.Core.camera == null) arc.Core.camera = new arc.graphics.Camera();
            if (arc.Core.atlas == null) arc.Core.atlas = new HeadlessAtlas();

            // Exercise the client-only draw paths so regressions in selection/placement
            // rendering are caught even in this headless harness.
            source.drawSelect();
            cable.drawSelect();
            target.drawSelect();
            stepUpLM.drawSelect();
            mvCable.drawSelect();
            stepDownML.drawSelect();
            stepUpMH.drawSelect();
            hvCable2.drawSelect();
            solar.drawSelect();
            feederMacerator.drawSelect();
            restoredCable.drawSelect();
            restoredTransformer.drawSelect();
            batteryBlock.drawPlace(4, 4, 0, true);
            cableBlock.drawPlace(6, 4, 0, true);
            mvBlock.drawPlace(8, 2, 0, true);
            transformerBlock.drawPlace(6, 2, 0, true);
            solarBlock.drawPlace(28, 4, 0, true);
            feederMaceratorBlock.drawPlace(28, 6, 0, true);

            System.out.println("EU integration harness passed.");
        } catch (Throwable error) {
            failure = error;
            error.printStackTrace();
            throw error;
        } finally {
            if (failure != null) System.exit(1);
            else System.exit(0);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Building> T place(mindustry.world.Block block, int x, int y) {
        Tile tile = Vars.world.tile(x, y);
        tile.setBlock(block, TEAM, 0);
        return (T)tile.build;
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
    }

    private static byte[] save(Building building) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            building.write(new arc.util.io.Writes(new DataOutputStream(bytes)));
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static Ic2CableBlock configuredCable(boolean hv, int range, float rate, float loss) {
        Ic2CableBlock cable = new Ic2CableBlock("eu-test-variant-" + range + "-" + (int)rate);
        cable.highVoltage = hv;
        cable.nodeRange = range;
        cable.transferRate = rate;
        cable.loss = loss;
        return cable;
    }

    /** Minimal no-op Batch so the headless harness can execute drawing code without a GL context. */
    private static class HeadlessBatch extends arc.graphics.g2d.Batch {
        @Override
        protected void draw(arc.graphics.Texture texture, float[] vertices, int offset, int count) {
        }

        @Override
        protected void draw(arc.graphics.g2d.TextureRegion region, float x, float y, float w, float h, float u, float v, float u2) {
        }

        @Override
        protected void flush() {
        }
    }

    /** Minimal TextureAtlas stub so draw code that fetches the white pixel doesn't NPE headlessly. */
    private static class HeadlessAtlas extends arc.graphics.g2d.TextureAtlas {
        private final arc.graphics.g2d.TextureAtlas.AtlasRegion dummy = new arc.graphics.g2d.TextureAtlas.AtlasRegion();

        @Override
        public arc.graphics.g2d.TextureAtlas.AtlasRegion white() {
            return dummy;
        }

        @Override
        public arc.graphics.g2d.TextureAtlas.AtlasRegion find(String name) {
            return dummy;
        }
    }
}
