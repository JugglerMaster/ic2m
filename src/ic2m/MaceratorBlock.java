package ic2m;

import arc.func.Func;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.meta.Stat;

public class MaceratorBlock extends Ic2PowerBlock {
    public float powerPerTick = 2f;
    public float craftTime = 180f;

    public MaceratorBlock(String name) {
        super(name);
        basePowerCapacity = 200f;
        hasItems = true;
        itemCapacity = 10;
        configurable = true;
        saveConfig = true;

        addBar("ic2progress", (Func<Building, Bar>)entity -> new Bar(
            () -> "Progress " + (int)(progressOf(entity) * 100f) + "%",
            () -> Pal.accent,
            () -> progressOf(entity)
        ));
    }

    private float progressOf(Building entity){
        return entity instanceof MaceratorBuild b
            ? b.progress / craftTimeForTier(b.upgradeTier) : 0f;
    }

    public Item getDustForOre(Item ore) {
        if (ore == Items.copper) return dust("copper-dust");
        if (ore == Items.lead) return dust("lead-dust");
        if (ore == Items.graphite) return dust("graphite-dust");
        if (ore == Items.coal) return dust("coal-dust");
        if (ore == Items.titanium) return dust("titanium-dust");
        if (ore == Items.thorium) return dust("thorium-dust");
        if (ore == Items.sand) return Items.sand;
        if (ore == Items.scrap) return Items.scrap;
        return null;
    }

    private Item dust(String suffix) {
        return Vars.content.items().find(i -> i.name.endsWith("-" + suffix));
    }

    public float powerForTier(int tier) {
        return powerPerTick * (tier == 0 ? 1f : tier == 1 ? 1.6f : 2.4f);
    }

    public float craftTimeForTier(int tier) {
        return craftTime / (tier == 0 ? 1f : tier == 1 ? 1.5f : 2.25f);
    }

    public boolean isOre(Item item) {
        return getDustForOre(item) != null;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.powerUse, "[orange]@[] EU/t", String.format("%.1f", powerPerTick));
        stats.add(Stat.input, "Copper/Lead/Graphite/Coal/Titanium/Thorium Ore -> 2 Dusts; Sand/Scrap -> 2 items");
    }

    public class MaceratorBuild extends Ic2PowerBuilding {
        public float progress = 0f;
        public Item currentOre = null;
        public int outputCount = 0;
        public int upgradeTier = 0;

        @Override
        public void created() {
            super.created();
            upgradeTier = baseTier;
            maxEnergy = basePowerCapacity;
            itemCapacity = block.size * block.size * 10;
            outputCapacity = block.size * block.size * 4;
        }

        @Override
        public void update() {
            super.update();
            flushOutput();
            if (!enabled) return;

            int parallel = block.size * block.size;
            if (currentOre != null && outputCount > 0) {
                Item dust = getDustForOre(currentOre);
                if (dust != null && canStoreOutput(dust, outputCount)) {
                    float power = powerForTier(upgradeTier);
                    float eff = consumePower(power);
                    if (eff > 0f) {
                        progress += eff;
                        if (progress >= craftTimeForTier(upgradeTier)) {
                            if (storeOutput(dust, outputCount)) {
                                progress = 0f;
                                currentOre = null;
                                outputCount = 0;
                            }
                        }
                    }
                }
            } else {
                if (items.any() && currentOre == null) {
                    for (Item item : Vars.content.items()) {
                        int count = items.get(item);
                        if (count > 0 && isOre(item)) {
                            int taken = Math.min(count, parallel);
                            items.remove(item, taken);
                            currentOre = item;
                            outputCount = 2 * taken;
                            progress = 0f;
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public float progress(){
            return currentOre != null && outputCount > 0
                ? Math.min(progress / craftTimeForTier(upgradeTier), 1f) : 0f;
        }

        @Override
        public boolean canAcceptEnergy() { return true; }

        @Override
        public boolean canProvideEnergy() { return false; }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (currentOre != null) return false;
            if (items.total() >= itemCapacity) return false;
            return isOre(item) && items.total() < itemCapacity;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.add("Macerator Tier " + (upgradeTier + 1)).left().row();
            table.add("Speed: " + String.format("%.0f%%", craftTime / craftTimeForTier(upgradeTier) * 100f)
                + " | Power: " + String.format("%.1f", powerForTier(upgradeTier)) + " EU/t").left().row();
            table.add("Recipes").left().row();
            addRecipe(table, Items.copper, dust("copper-dust"));
            addRecipe(table, Items.lead, dust("lead-dust"));
            addRecipe(table, Items.graphite, dust("graphite-dust"));
            addRecipe(table, Items.coal, dust("coal-dust"));
            addRecipe(table, Items.titanium, dust("titanium-dust"));
            addRecipe(table, Items.thorium, dust("thorium-dust"));
            addRecipe(table, Items.sand, Items.sand);
            addRecipe(table, Items.scrap, Items.scrap);
            table.add(outputStatus()).left().row();
            table.add("Tier 2-3 upgrades are performed by the IC2 Upgrade Node (2x2 with this block).").left().row();
            addInputControl(table);
        }

        @Override
        public ItemStack[] upgradeRequirements(int tier) {
            if (tier == 2) return withAlloy(tier, resolveItem("titanium-carbide"), 100);
            if (tier == 3) return withAlloy(tier, resolveItem("thorium-alloy"), 400);
            return new ItemStack[0];
        }

        private void addRecipe(Table table, Item input, Item output) {
            if (output == null) return;
            table.add(input.localizedName + " -> 2 " + output.localizedName).left().row();
        }

        @Override
        public byte version() { return 2; }

        @Override
        protected boolean readsOutputState(byte revision) { return revision >= 2; }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(upgradeTier);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 1) upgradeTier = read.i();
        }
    }
}
