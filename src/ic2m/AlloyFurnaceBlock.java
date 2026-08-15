package ic2m;

import arc.func.Func;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.meta.Stat;

public class AlloyFurnaceBlock extends Ic2PowerBlock {
    public static final int MODE_SMELT = 0;
    public static final int MODE_ALLOY = 1;

    public float powerPerTick = 5f;
    public float craftTime = 180f;

    public Item copperDust, leadDust, titaniumDust, thoriumDust, graphiteDust, coalDust;
    public Item copperIngot, leadIngot, titaniumIngot, thoriumIngot, graphiteIngot, coalIngot;
    public Item titaniumCarbide, thoriumAlloy;

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
        return entity instanceof AlloyFurnaceBuild b ? b.progress / craftTime : 0f;
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
    }

    public class AlloyFurnaceBuild extends Ic2PowerBuilding {
        public float progress = 0f;
        public Item currentInput = null;
        public Item output = null;
        public int mode = MODE_SMELT;

        @Override
        public void update() {
            super.update();
            if (!enabled) return;

            if (output != null && currentInput != null) {
                if (energy >= powerPerTick) {
                    energy -= powerPerTick;
                    progress += 1f;
                    if (progress >= craftTime) {
                        offload(output);
                        progress = 0f;
                        currentInput = null;
                        output = null;
                    }
                }
            } else if (mode == MODE_SMELT) {
                startSmelt();
            } else {
                startAlloy();
            }
        }

        private void startSmelt() {
            for (Item item : Vars.content.items()) {
                if (items.get(item) > 0 && isDust(item)) {
                    items.remove(item, 1);
                    currentInput = item;
                    output = ingotForDust(item);
                    progress = 0f;
                    break;
                }
            }
        }

        private void startAlloy() {
            for (Item itemA : Vars.content.items()) {
                int countA = items.get(itemA);
                if (countA <= 0) continue;
                for (Item itemB : Vars.content.items()) {
                    if (itemB == itemA) continue;
                    int countB = items.get(itemB);
                    if (countB <= 0) continue;
                    Item result = recipe(itemA, itemB);
                    if (result != null) {
                        items.remove(itemA, 1);
                        items.remove(itemB, 1);
                        currentInput = itemA;
                        output = result;
                        progress = 0f;
                        return;
                    }
                }
            }
        }

        @Override
        public float progress(){
            return output != null && currentInput != null ? Math.min(progress / craftTime, 1f) : 0f;
        }

        @Override
        public boolean canAcceptEnergy() { return true; }

        @Override
        public boolean canProvideEnergy() { return false; }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (output != null && currentInput != null) return false;
            if (items.total() >= itemCapacity) return false;

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
        public Integer config() {
            return mode;
        }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof Integer i) mode = i;
        }

        @Override
        public void buildConfiguration(Table table) {
            TextButton button = new TextButton("", Styles.defaultt);
            button.update(() -> button.setText(mode == MODE_SMELT ? "Mode: Smelting (dust -> ingot)" : "Mode: Alloying (ingot + ingot)"));
            button.clicked(() -> configure(mode == MODE_SMELT ? MODE_ALLOY : MODE_SMELT));
            table.add(button).size(260f, 50f).row();
            table.add(mode == MODE_SMELT ? "[#9cf7ff]Feed dusts; each dust smelts into its ingot." : "[#ffd37f]Recipes:[/] copper+lead -> surge alloy, titanium+graphite -> titanium carbide, thorium+lead -> thorium alloy").width(260f).wrap();
        }

        @Override
        public byte version() { return 1; }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(mode);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 1) mode = read.i();
        }
    }
}
