package ic2m;

import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.type.Item;

public class AlloyFurnaceBlock extends Ic2PowerBlock {
    public float powerPerTick = 5f;
    public float craftTime = 180f;

    public AlloyFurnaceBlock(String name) {
        super(name);
        basePowerCapacity = 200f;
        hasItems = true;
        itemCapacity = 4;
    }

    public Item getAlloyOutput(Item input1, Item input2) {
        if ((input1 == Items.copper && input2 == Items.lead) ||
            (input1 == Items.lead && input2 == Items.copper)) {
            return Items.surgeAlloy;
        }
        return null;
    }

    public boolean isMetal(Item item) {
        return item == Items.copper || item == Items.lead ||
               item == Items.titanium || item == Items.thorium;
    }

    public class AlloyFurnaceBuild extends Ic2PowerBuilding {
        public float progress = 0f;
        public Item input1 = null;
        public Item input2 = null;
        public Item alloyOutput = null;
        public boolean processing = false;

        @Override
        public void update() {
            super.update();
            if (!enabled) return;

            if (processing && alloyOutput != null) {
                if (energy >= powerPerTick) {
                    energy -= powerPerTick;
                    progress += 1f;
                    if (progress >= craftTime) {
                        offload(alloyOutput);
                        progress = 0f;
                        processing = false;
                        input1 = null;
                        input2 = null;
                        alloyOutput = null;
                    }
                }
            } else if (!processing) {
                if (input1 == null || input2 == null) {
                    tryLoadInputs();
                }
                if (input1 != null && input2 != null) {
                    alloyOutput = getAlloyOutput(input1, input2);
                    if (alloyOutput != null) {
                        processing = true;
                        progress = 0f;
                        items.remove(input1, 1);
                        items.remove(input2, 1);
                    }
                }
            }
        }

        private void tryLoadInputs() {
            if (items.total() < 2) return;
            Item first = null;
            Item second = null;
            for (Item item : Vars.content.items()) {
                int count = items.get(item);
                if (count > 0 && isMetal(item)) {
                    if (first == null) {
                        first = item;
                    } else if (second == null) {
                        second = item;
                        break;
                    }
                }
            }
            if (first != null && second != null) {
                input1 = first;
                input2 = second;
            }
        }

        @Override
        public boolean canAcceptEnergy() { return true; }

        @Override
        public boolean canProvideEnergy() { return false; }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (processing) return false;
            return isMetal(item) && items.total() < itemCapacity;
        }
    }
}
