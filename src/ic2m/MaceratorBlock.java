package ic2m;

import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.type.Item;

public class MaceratorBlock extends Ic2PowerBlock {
    public float powerPerTick = 5f;
    public float craftTime = 180f;

    public MaceratorBlock(String name) {
        super(name);
        basePowerCapacity = 200f;
        hasItems = true;
    }

    public Item getDustForOre(Item ore) {
        if (ore == Items.copper) return Vars.content.item("copper-dust");
        if (ore == Items.lead) return Vars.content.item("lead-dust");
        if (ore == Items.graphite) return Vars.content.item("graphite-dust");
        if (ore == Items.coal) return Vars.content.item("coal-dust");
        if (ore == Items.titanium) return Vars.content.item("titanium-dust");
        if (ore == Items.thorium) return Vars.content.item("thorium-dust");
        if (ore == Items.sand) return Items.sand;
        if (ore == Items.scrap) return Items.scrap;
        return null;
    }

    public boolean isOre(Item item) {
        return getDustForOre(item) != null;
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
