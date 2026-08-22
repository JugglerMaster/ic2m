package ic2m;

import arc.func.Func;
import arc.scene.ui.TextButton;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.meta.Stat;

public class AlloyFurnaceBlock extends Ic2PowerBlock {
    public static final int MODE_SMELT = 0;
    public static final int MODE_ALLOY = 1;

    public float powerPerTick = 5f;
    public float craftTime = 180f;

    transient Item copperDust, leadDust, titaniumDust, thoriumDust, graphiteDust, coalDust;
    transient Item copperIngot, leadIngot, titaniumIngot, thoriumIngot, graphiteIngot, coalIngot;
    transient Item titaniumCarbide, thoriumAlloy;

    public float powerForTier(int tier) {
        return powerPerTick * (tier == 0 ? 1f : tier == 1 ? 1.6f : 2.4f);
    }

    public float craftTimeForTier(int tier) {
        return craftTime / (tier == 0 ? 1f : tier == 1 ? 1.5f : 2.25f);
    }

    public AlloyFurnaceBlock(String name) {
        super(name);
        basePowerCapacity = 200f;
        hasItems = true;
        itemCapacity = 4;
        configurable = true;
        saveConfig = true;
        update = true;

        addBar("ic2progress", (Func<Building, Bar>)entity -> new Bar(
            () -> "Progress " + (int)(progressOf(entity) * 100f) + "%",
            () -> Pal.accent,
            () -> progressOf(entity)
        ));
    }

    private Item item(String suffix) {
        return Vars.content.items().find(i -> i.name.endsWith("-" + suffix));
    }

    private boolean initialized = false;

    private void ensureItems() {
        if (initialized) return;
        initialized = true;
        copperDust = item("copper-dust");
        leadDust = item("lead-dust");
        titaniumDust = item("titanium-dust");
        thoriumDust = item("thorium-dust");
        graphiteDust = item("graphite-dust");
        coalDust = item("coal-dust");
        copperIngot = item("copper-ingot");
        leadIngot = item("lead-ingot");
        titaniumIngot = item("titanium-ingot");
        thoriumIngot = item("thorium-ingot");
        graphiteIngot = item("graphite-ingot");
        coalIngot = item("coal-ingot");
        titaniumCarbide = item("titanium-carbide");
        thoriumAlloy = item("thorium-alloy");
    }

    private float progressOf(Building entity) {
        return entity instanceof AlloyFurnaceBuild b
            ? b.progress / craftTimeForTier(b.upgradeTier) : 0f;
    }

    public Item ingotForDust(Item dust) {
        ensureItems();
        if (dust == copperDust) return copperIngot;
        if (dust == leadDust) return leadIngot;
        if (dust == titaniumDust) return titaniumIngot;
        if (dust == thoriumDust) return thoriumIngot;
        if (dust == graphiteDust) return graphiteIngot;
        if (dust == coalDust) return coalIngot;
        return null;
    }

    public boolean isDust(Item item) {
        return ingotForDust(item) != null;
    }

    public Item recipe(Item a, Item b) {
        ensureItems();
        if (pair(a, b, copperIngot, leadIngot)) return Items.surgeAlloy;
        if (pair(a, b, titaniumIngot, graphiteIngot)) return titaniumCarbide;
        if (pair(a, b, thoriumIngot, leadIngot)) return thoriumAlloy;
        return null;
    }

    private boolean pair(Item a, Item b, Item x, Item y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    public boolean isRecipeIngot(Item item) {
        ensureItems();
        return item == copperIngot || item == leadIngot || item == titaniumIngot
            || item == graphiteIngot || item == thoriumIngot;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.powerUse, "[orange]@[] EU/t", String.format("%.1f", powerPerTick));
        stats.add(Stat.input, "Dust -> matching ingot; Copper + Lead -> Surge Alloy; Titanium + Graphite -> Titanium Carbide; Thorium + Lead -> Thorium Alloy");
    }

    public class AlloyFurnaceBuild extends Ic2PowerBuilding {
        public float progress = 0f;
        public Item currentInput = null;
        public Item output = null;
        public int outputAmount = 0;
        public int mode = MODE_SMELT;
        public int upgradeTier = 0;

        @Override
        public void created() {
            super.created();
            upgradeTier = baseTier;
            itemCapacity = block.size * block.size * 10;
            outputCapacity = block.size * block.size * 4;
        }

        @Override
        public void update() {
            super.update();
            flushOutput();
            if (!enabled) return;

            int parallel = block.size * block.size;
            if (output != null && currentInput != null) {
                float power = powerForTier(upgradeTier);
                if (energy >= power) {
                    energy -= power;
                    progress += 1f;
                    if (progress >= craftTimeForTier(upgradeTier)) {
                        if (storeOutput(output, outputAmount)) {
                            progress = 0f;
                            currentInput = null;
                            output = null;
                            outputAmount = 0;
                        }
                    }
                }
            } else if (mode == MODE_SMELT) {
                startSmelt(parallel);
            } else {
                startAlloy(parallel);
            }
        }

        private void startSmelt(int parallel) {
            for (Item item : Vars.content.items()) {
                int count = items.get(item);
                if (count > 0 && isDust(item)) {
                    int taken = Math.min(count, parallel);
                    items.remove(item, taken);
                    currentInput = item;
                    output = ingotForDust(item);
                    outputAmount = taken;
                    progress = 0f;
                    break;
                }
            }
        }

        private void startAlloy(int parallel) {
            for (Item itemA : Vars.content.items()) {
                int countA = items.get(itemA);
                if (countA <= 0) continue;
                for (Item itemB : Vars.content.items()) {
                    if (itemB == itemA) continue;
                    int countB = items.get(itemB);
                    if (countB <= 0) continue;
                    Item result = recipe(itemA, itemB);
                    if (result != null) {
                        int taken = Math.min(parallel, Math.min(countA, countB));
                        items.remove(itemA, taken);
                        items.remove(itemB, taken);
                        currentInput = itemA;
                        output = result;
                        outputAmount = taken;
                        progress = 0f;
                        return;
                    }
                }
            }
        }

        @Override
        public float progress(){
            return output != null && currentInput != null
                ? Math.min(progress / craftTimeForTier(upgradeTier), 1f) : 0f;
        }

        @Override
        public boolean canAcceptEnergy() { return true; }

        @Override
        public boolean canProvideEnergy() { return false; }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (output != null && currentInput != null) return false;
            if (items.total() >= itemCapacity) return false;
            ensureItems();
            if (mode == MODE_SMELT) {
                return isDust(item);
            } else {
                if (!isRecipeIngot(item)) return false;
                if (items.total() == 0) return true;
                for (Item other : Vars.content.items()) {
                    if (items.get(other) > 0 && recipe(item, other) != null) return true;
                }
                return false;
            }
        }

        @Override
        public ItemStack[] upgradeRequirements(int tier) {
            if (tier == 2) return withAlloy(tier, resolveItem("titanium-carbide"), 100);
            if (tier == 3) return withAlloy(tier, resolveItem("thorium-alloy"), 400);
            return new ItemStack[0];
        }

        @Override
        public Integer config() {
            return mode;
        }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof Integer i) mode = i;
        }

        @Override
        public void buildConfiguration(Table table) {
            ensureItems();
            TextButton button = new TextButton("", Styles.defaultt);
            button.update(() -> button.setText(mode == MODE_SMELT ? "Mode: Smelting (dust -> ingot)" : "Mode: Alloying (ingot + ingot)"));
            button.clicked(() -> configure(mode == MODE_SMELT ? MODE_ALLOY : MODE_SMELT));
            table.add(button).size(260f, 50f).row();
            table.add("Recipes").left().row();
            if (mode == MODE_SMELT) {
                addRecipe(table, copperDust, copperIngot);
                addRecipe(table, leadDust, leadIngot);
                addRecipe(table, graphiteDust, graphiteIngot);
                addRecipe(table, coalDust, coalIngot);
                addRecipe(table, titaniumDust, titaniumIngot);
                addRecipe(table, thoriumDust, thoriumIngot);
            } else {
                addAlloyRecipe(table, copperIngot, leadIngot, Items.surgeAlloy);
                addAlloyRecipe(table, titaniumIngot, graphiteIngot, titaniumCarbide);
                addAlloyRecipe(table, thoriumIngot, leadIngot, thoriumAlloy);
            }
            table.row();
            table.add("Furnace Tier " + (upgradeTier + 1)).left().row();
            table.add("Speed: " + String.format("%.0f%%", craftTime / craftTimeForTier(upgradeTier) * 100f)
                + " | Power: " + String.format("%.1f", powerForTier(upgradeTier)) + " EU/t").left().row();
            table.add(outputStatus()).left().row();
            table.add("Tier 2-3 upgrades are performed by the IC2 Upgrade Node (2x2 with this block).").left().row();
        }

        private void addRecipe(Table table, Item input, Item output) {
            if (input == null || output == null) return;
            table.add(input.localizedName + " -> " + output.localizedName).left().row();
        }

        private void addAlloyRecipe(Table table, Item a, Item b, Item output) {
            if (a == null || b == null || output == null) return;
            table.add(a.localizedName + " + " + b.localizedName + " -> " + output.localizedName).left().row();
        }

        @Override
        public byte version() { return 3; }

        @Override
        protected boolean readsOutputState(byte revision) { return revision >= 3; }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(mode);
            write.i(upgradeTier);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 1) mode = read.i();
            if (revision >= 2) upgradeTier = read.i();
        }
    }
}
