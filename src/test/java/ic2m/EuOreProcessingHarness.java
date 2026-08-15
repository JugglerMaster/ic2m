package ic2m;

import arc.ApplicationListener;
import arc.backend.headless.HeadlessApplication;
import arc.graphics.Color;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.core.World;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.Item;
import mindustry.world.Tile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** Headless regression test for ore -> dust -> ingot processing. */
public final class EuOreProcessingHarness implements ApplicationListener {
    private static final Team TEAM = Team.sharded;
    private Throwable failure;

    public static void main(String[] args) {
        new HeadlessApplication(new EuOreProcessingHarness());
    }

    @Override
    public void init() {
        try {
            Vars.headless = true;
            Vars.content = new ContentLoader();
            Vars.state = new GameState();
            Vars.state.rules = new Rules();
            Vars.state.set(GameState.State.playing);
            Groups.init();
            Vars.world = new World();
            Vars.world.tiles = Vars.world.resize(32, 8);
            Vars.world.tiles.fill();
            Items.load();
            Liquids.load();

            Item copperDust = registerItem("test-copper-dust");
            Item leadDust = registerItem("test-lead-dust");
            Item graphiteDust = registerItem("test-graphite-dust");
            Item coalDust = registerItem("test-coal-dust");
            Item titaniumDust = registerItem("test-titanium-dust");
            Item thoriumDust = registerItem("test-thorium-dust");
            Item copperIngot = registerItem("test-copper-ingot");
            Item leadIngot = registerItem("test-lead-ingot");
            Item graphiteIngot = registerItem("test-graphite-ingot");
            Item thoriumIngot = registerItem("test-thorium-ingot");
            Item titaniumIngot = registerItem("test-titanium-ingot");
            Item coalIngot = registerItem("test-coal-ingot");
            Item titaniumCarbide = registerItem("test-titanium-carbide");
            Item thoriumAlloy = registerItem("test-thorium-alloy");
            Item[] testDusts = {copperDust, leadDust, graphiteDust, coalDust, titaniumDust, thoriumDust};
            Item[] testOres = {Items.copper, Items.lead, Items.graphite, Items.coal, Items.titanium, Items.thorium};

            Blocks.load();
            Blocks.siliconSmelter.init();
            Blocks.kiln.init();
            Blocks.surgeSmelter.init();
            Blocks.graphitePress.init();
            new Ic2mMod().loadContent();
            check("silicon smelter accepts copper ingot", Blocks.siliconSmelter.itemFilter[copperIngot.id]);
            check("kiln accepts lead ingot", Blocks.kiln.itemFilter[leadIngot.id]);
            check("surge smelter accepts titanium ingot", Blocks.surgeSmelter.itemFilter[titaniumIngot.id]);
            check("graphite press accepts coal ingot", Blocks.graphitePress.itemFilter[coalIngot.id]);

            MaceratorBlock maceratorBlock = new MaceratorBlock("eu-test-macerator");
            maceratorBlock.init();
            AlloyFurnaceBlock furnaceBlock = new AlloyFurnaceBlock("eu-test-furnace");
            furnaceBlock.init();

            MaceratorBlock.MaceratorBuild macerator = place(maceratorBlock, 6, 3);
            macerator.items.add(Items.copper, 1);
            macerator.energy = 2000f;
            tick(macerator, 220);

            check("macerator consumed copper ore", macerator.items.get(Items.copper) == 0);
            check("macerator produced two copper dusts", macerator.pendingOutput == copperDust
                && macerator.pendingOutputAmount == 2);
            for (int i = 1; i < testOres.length; i++) {
                macerator.pendingOutput = null;
                macerator.pendingOutputAmount = 0;
                macerator.items.add(testOres[i], 1);
                macerator.energy = 2000f;
                tick(macerator, 220);
                check("macerator processes " + testOres[i].name, macerator.pendingOutput == testDusts[i]
                    && macerator.pendingOutputAmount == 2);
            }
            macerator.pendingOutput = null;
            macerator.pendingOutputAmount = 0;

            AlloyFurnaceBlock.AlloyFurnaceBuild furnace = place(furnaceBlock, 10, 3);
            furnace.items.add(copperDust, 1);
            furnace.energy = 2000f;
            tick(furnace, 220);

            check("furnace consumed copper dust", furnace.items.get(copperDust) == 0);
            check("furnace produced a copper ingot", furnace.pendingOutput == copperIngot
                && furnace.pendingOutputAmount == 1);
            check("furnace resolves optional alloy content", furnaceBlock.titaniumCarbide == titaniumCarbide
                && furnaceBlock.thoriumAlloy == thoriumAlloy);
            furnaceBlock.isDust(copperDust);
            check("furnace resolves copper ingot", furnaceBlock.copperIngot != null);
            check("furnace resolves lead ingot", furnaceBlock.leadIngot != null);
            check("furnace resolves titanium ingot", furnaceBlock.titaniumIngot != null);
            check("furnace resolves graphite ingot", furnaceBlock.graphiteIngot != null);
            check("furnace resolves thorium ingot", furnaceBlock.thoriumIngot != null);

            ReceiverBlock receiverBlock = new ReceiverBlock();
            Vars.content.handleContent(receiverBlock);
            receiverBlock.init();
            ReceiverBlock.ReceiverBuild receiver = place(receiverBlock, 11, 3);
            furnace.update();
            check("blocked furnace output flushes to receiver", furnace.pendingOutput == null
                && receiver.items.get(copperIngot) == 1);

            AlloyFurnaceBlock.AlloyFurnaceBuild alloyFurnace = place(furnaceBlock, 20, 3);
            alloyFurnace.mode = AlloyFurnaceBlock.MODE_ALLOY;
            checkAlloy(alloyFurnace, furnaceBlock.copperIngot, furnaceBlock.leadIngot, Items.surgeAlloy, "surge alloy");
            checkAlloy(alloyFurnace, furnaceBlock.titaniumIngot, furnaceBlock.graphiteIngot, titaniumCarbide, "titanium carbide");
            checkAlloy(alloyFurnace, furnaceBlock.thoriumIngot, furnaceBlock.leadIngot, thoriumAlloy, "thorium alloy");
            alloyFurnace.items.clear();
            alloyFurnace.pendingOutput = null;
            alloyFurnace.pendingOutputAmount = 0;
            alloyFurnace.items.add(furnaceBlock.copperIngot, 1);
            check("furnace rejects invalid alloy pair", !alloyFurnace.acceptItem(alloyFurnace, furnaceBlock.titaniumIngot));

            MaceratorBlock.MaceratorBuild upgradedMacerator = place(maceratorBlock, 14, 3);
            upgradedMacerator.items.add(titaniumCarbide, 10);
            check("macerator tier 2 upgrade is available", upgradedMacerator.canUpgrade());
            upgradedMacerator.upgrade();
            upgradedMacerator.items.add(thoriumAlloy, 10);
            upgradedMacerator.upgrade();
            check("macerator reaches tier 3", upgradedMacerator.upgradeTier == 2
                && maceratorBlock.powerForTier(2) > maceratorBlock.powerForTier(0)
                && maceratorBlock.craftTimeForTier(2) < maceratorBlock.craftTimeForTier(0));

            macerator.pendingOutput = copperDust;
            macerator.pendingOutputAmount = 2;
            byte[] saved = save(macerator);
            MaceratorBlock.MaceratorBuild restored = place(maceratorBlock, 16, 3);
            restored.read(new arc.util.io.Reads(new DataInputStream(new ByteArrayInputStream(saved))), (byte)2);
            check("machine save restores upgrade and output buffer", restored.upgradeTier == macerator.upgradeTier
                && restored.pendingOutput == copperDust && restored.pendingOutputAmount == 2);
            System.out.println("EU ore processing harness passed.");
        } catch (Throwable error) {
            failure = error;
            error.printStackTrace();
            throw error;
        } finally {
            if (failure != null) System.exit(1);
            else System.exit(0);
        }
    }

    private Item registerItem(String name) {
        Item item = new Item(name, Color.white);
        Vars.content.handleContent(item);
        return item;
    }

    private void tick(Building building, int ticks) {
        for (int i = 0; i < ticks; i++) building.update();
    }

    private void checkAlloy(AlloyFurnaceBlock.AlloyFurnaceBuild furnace, Item a, Item b, Item output, String name) {
        furnace.items.clear();
        furnace.pendingOutput = null;
        furnace.pendingOutputAmount = 0;
        furnace.items.add(a, 1);
        furnace.items.add(b, 1);
        furnace.energy = 2000f;
        tick(furnace, 220);
        check("furnace produces " + name, furnace.pendingOutput == output && furnace.pendingOutputAmount == 1);
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

    private byte[] save(Building building) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            building.write(new arc.util.io.Writes(new DataOutputStream(bytes)));
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static class ReceiverBlock extends mindustry.world.Block {
        ReceiverBlock() {
            super("eu-test-receiver");
            hasItems = true;
            itemCapacity = 10;
            update = true;
            solid = true;
            buildType = ReceiverBuild::new;
        }

        static class ReceiverBuild extends Building {
            @Override
            public boolean acceptItem(Building source, Item item) {
                return items.total() < block.itemCapacity;
            }

            @Override
            public void handleItem(Building source, Item item) {
                items.add(item, 1);
            }
        }
    }
}
