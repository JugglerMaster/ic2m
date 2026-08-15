package ic2m;

import arc.func.Func;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;

public class MaceratorBlock extends Ic2PowerBlock {
    public float powerPerTick = 5f;
    public float craftTime = 180f;

    public MaceratorBlock(String name) {
        super(name);
        basePowerCapacity = 200f;
        hasItems = true;

        addBar("ic2progress", (Func<Building, Bar>)entity -> new Bar(
            () -> "Progress " + (int)(progressOf(entity) * 100f) + "%",
            () -> Pal.accent,
            () -> progressOf(entity)
        ));
    }

    private float progressOf(Building entity){
        return entity instanceof MaceratorBuild b ? b.progress / craftTime : 0f;
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

        @Override
        public void update() {
            super.update();
            if (!enabled) return;

            if (currentOre != null && outputCount > 0) {
                if (energy >= powerPerTick) {
                    energy -= powerPerTick;
                    progress += 1f;
                    if (progress >= craftTime) {
                        Item dust = getDustForOre(currentOre);
                        for (int i = 0; i < outputCount; i++) {
                            offload(dust);
                        }
                        progress = 0f;
                        currentOre = null;
                        outputCount = 0;
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
            return currentOre != null && outputCount > 0 ? Math.min(progress / craftTime, 1f) : 0f;
        }

        @Override
        public boolean canAcceptEnergy() { return true; }

        @Override
        public boolean canProvideEnergy() { return false; }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (currentOre != null) return false;
            return isOre(item) && items.total() < itemCapacity;
        }
    }
}
