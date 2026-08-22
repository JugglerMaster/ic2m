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

            for (int i = 0; i < 4; i++) {
                cable.distributePower();
                cable.update();
                target.update();
            }

            check("LV cable transferred energy", target.energy > 0f);
            check("LV cable stayed within capacity", cable.energy <= cable.maxEnergy);
            check("source remained isolated from cable output", source.energy == source.maxEnergy);

            Ic2CableBlock hvBlock = new Ic2CableBlock("eu-test-hv-cable");
            hvBlock.highVoltage = true;
            hvBlock.nodeRange = 16;
            hvBlock.transferRate = 10f;
            hvBlock.loss = 0f;
            hvBlock.cableCapacity = 100f;
            hvBlock.init();
            Ic2TransformerBlock transformerBlock = new Ic2TransformerBlock("eu-test-transformer");
            transformerBlock.init();

            BatteryBlock.BatteryBuild hvSource = place(batteryBlock, 4, 2);
            Ic2TransformerBlock.Ic2TransformerBuild stepUp = place(transformerBlock, 6, 2);
            Ic2CableBlock.Ic2CableBuild hvCable = place(hvBlock, 8, 2);
            Ic2TransformerBlock.Ic2TransformerBuild stepDown = place(transformerBlock, 24, 2);
            BatteryBlock.BatteryBuild hvTarget = place(batteryBlock, 26, 2);
            stepDown.mode = Ic2TransformerBlock.MODE_STEP_DOWN;
            hvCable.onConfigureBuildTapped(stepUp);
            hvCable.onConfigureBuildTapped(stepDown);
            hvSource.energy = 20f;
            for (int i = 0; i < 8; i++) {
                hvSource.update();
                stepUp.update();
                hvCable.update();
                stepDown.update();
                hvTarget.update();
            }
            check("HV transformer path transferred energy", hvTarget.energy > 0f);
            check("HV transfer used both manual links", hvCable.links.size == 2);

            // HV must not bypass transformers in either direction.
            Ic2CableBlock isolatedHvBlock = new Ic2CableBlock("eu-test-isolated-hv");
            isolatedHvBlock.highVoltage = true;
            isolatedHvBlock.nodeRange = 2;
            isolatedHvBlock.init();
            Ic2CableBlock.Ic2CableBuild isolatedCable = place(isolatedHvBlock, 4, 6);
            BatteryBlock.BatteryBuild isolatedMachine = place(batteryBlock, 6, 6);
            isolatedCable.energy = 20f;
            isolatedCable.update();
            check("HV cable cannot directly power LV machine", isolatedMachine.energy == 0f);

            Ic2TransformerBlock.Ic2TransformerBuild isolatedStepUp = place(transformerBlock, 10, 6);
            Ic2CableBlock.Ic2CableBuild incomingHv = place(isolatedHvBlock, 12, 6);
            incomingHv.energy = 20f;
            incomingHv.update();
            check("step-up transformer rejects HV input", isolatedStepUp.energy == 0f);

            Ic2TransformerBlock.Ic2TransformerBuild isolatedStepDown = place(transformerBlock, 16, 6);
            isolatedStepDown.mode = Ic2TransformerBlock.MODE_STEP_DOWN;
            Ic2CableBlock.Ic2CableBuild outgoingHv = place(isolatedHvBlock, 18, 6);
            isolatedStepDown.energy = 20f;
            isolatedStepDown.update();
            check("step-down transformer cannot feed HV cable", outgoingHv.energy == 0f);

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

            byte[] cableState = save(hvCable);
            Ic2CableBlock.Ic2CableBuild restoredCable = place(hvBlock, 8, 0);
            restoredCable.read(new arc.util.io.Reads(new DataInputStream(new ByteArrayInputStream(cableState))), (byte)1);
            check("HV manual links survive save/load", restoredCable.links.size == hvCable.links.size);
            byte[] transformerState = save(stepDown);
            Ic2TransformerBlock.Ic2TransformerBuild restoredTransformer = place(transformerBlock, 24, 0);
            restoredTransformer.read(new arc.util.io.Reads(new DataInputStream(new ByteArrayInputStream(transformerState))), (byte)2);
            check("transformer mode survives save/load", restoredTransformer.mode == Ic2TransformerBlock.MODE_STEP_DOWN);

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
            hvSource.drawSelect();
            stepUp.drawSelect();
            hvCable.drawSelect();
            stepDown.drawSelect();
            hvTarget.drawSelect();
            solar.drawSelect();
            feederMacerator.drawSelect();
            restoredCable.drawSelect();
            restoredTransformer.drawSelect();
            batteryBlock.drawPlace(4, 4, 0, true);
            cableBlock.drawPlace(6, 4, 0, true);
            hvBlock.drawPlace(8, 2, 0, true);
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
