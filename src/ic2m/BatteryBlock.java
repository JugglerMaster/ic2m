package ic2m;

import mindustry.gen.Building;

public class BatteryBlock extends Ic2PowerBlock {
    public BatteryBlock(String name) {
        super(name);
        basePowerCapacity = 500f;
    }

    public class BatteryBuild extends Ic2PowerBuilding {
        @Override
        public void created() {
            super.created();
            maxEnergy = basePowerCapacity;
        }

        @Override
        public boolean canAcceptEnergy() { return true; }

        @Override
        public boolean canProvideEnergy() { return true; }
    }
}
