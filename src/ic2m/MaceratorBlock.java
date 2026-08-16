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
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.meta.Stat;

public class MaceratorBlock extends Ic2PowerBlock {
    public float powerPerTick = 5f;
    public float craftTime = 180f;
    transient Item titaniumCarbide, thoriumAlloy;

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

    private Item alloy(String suffix) {
        return Vars.content.items().find(i -> i.name.endsWith("-" + suffix));
    }

    private void ensureAlloys() {
        if (titaniumCarbide == null) titaniumCarbide = alloy("titanium-carbide");
        if (thoriumAlloy == null) thoriumAlloy = alloy("thorium-alloy");
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
    }

    public class MaceratorBuild extends Ic2PowerBuilding {
        public float progress = 0f;
        public Item currentOre = null;
        public int outputCount = 0;
        public int upgradeTier = 0;

        private static final int TIER2_COST = 10;
        private static final int TIER3_COST = 10;

        @Override
        public void created() {
            super.created();
            ensureAlloys();
            maxEnergy = basePowerCapacity;
        }

        @Override
        public void update() {
            super.update();
            flushOutput();
            if (!enabled) return;

            if (currentOre != null && outputCount > 0) {
                float power = powerForTier(upgradeTier);
                if (energy >= power) {
                    energy -= power;
                    progress += 1f;
                    if (progress >= craftTimeForTier(upgradeTier)) {
                        Item dust = getDustForOre(currentOre);
                        if (storeOutput(dust, outputCount)) {
                            progress = 0f;
                            currentOre = null;
                            outputCount = 0;
                        }
                    }
                }
            } else {
                if (items.any() && currentOre == null) {
                    for (Item item : Vars.content.items()) {
                        int count = items.get(item);
                        if (count > 0 && isOre(item)) {
                            currentOre = item;
                            items.remove(item, 1);
                            outputCount = 2;
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
            if (item == titaniumCarbide && upgradeTier == 0) return true;
            if (item == thoriumAlloy && upgradeTier == 1) return true;
            return isOre(item) && items.total() < itemCapacity;
        }

        boolean canUpgrade() {
            ensureAlloys();
            return (upgradeTier == 0 && titaniumCarbide != null && items.get(titaniumCarbide) >= TIER2_COST)
                || (upgradeTier == 1 && thoriumAlloy != null && items.get(thoriumAlloy) >= TIER3_COST);
        }

        void upgrade() {
            if (!canUpgrade()) return;
            Item ingredient = upgradeTier == 0 ? titaniumCarbide : thoriumAlloy;
            items.remove(ingredient, upgradeTier == 0 ? TIER2_COST : TIER3_COST);
            upgradeTier++;
        }

        @Override
        public void buildConfiguration(Table table) {
            ensureAlloys();
            table.add("Macerator Tier " + (upgradeTier + 1)).left().row();
            table.add("Speed: " + String.format("%.0f%%", craftTime / craftTimeForTier(upgradeTier) * 100f)
                + " | Power: " + String.format("%.1f", powerForTier(upgradeTier)) + " EU/t").left().row();
            table.add(outputStatus()).left().row();
            if (upgradeTier < 2) {
                Item ingredient = upgradeTier == 0 ? titaniumCarbide : thoriumAlloy;
                int cost = upgradeTier == 0 ? TIER2_COST : TIER3_COST;
                table.button("Upgrade to Tier " + (upgradeTier + 2), Styles.defaultt, this::upgrade).size(220f, 40f).row();
                table.add("Requires: " + cost + " " + ingredient.localizedName).left();
                table.add(new Image(ingredient.uiIcon)).size(16f).padLeft(4).row();
            } else {
                table.add("Maximum tier").left();
            }
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
