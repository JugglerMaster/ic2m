package ic2m;

import mindustry.gen.Building;
import mindustry.type.ItemStack;

public class BatteryBlock extends Ic2PowerBlock {
    public BatteryBlock(String name) {
        super(name);
        basePowerCapacity = 500f;
    }

    public class BatteryBuild extends Ic2PowerBuilding {
        @Override
        public byte version() { return 1; }

        @Override
        protected boolean readsOutputState(byte revision) { return false; }

        @Override
        protected boolean writesOutputState() { return false; }
        @Override
        public void created() {
            super.created();
            upgradeTier = baseTier;
            recalculateStats();
        }

        void recalculateStats() {
            if (upgradeTier == 0) maxEnergy = 40_000f;
            else if (upgradeTier == 1) maxEnergy = 300_000f;
            else maxEnergy = 4_000_000f;
        }

        @Override
        public boolean canAcceptEnergy() { return true; }

        @Override
        public boolean canProvideEnergy() { return true; }

        @Override
        public ItemStack[] upgradeRequirements(int tier) {
            if (tier == 2) return withAlloy(tier, resolveItem("titanium-carbide"), 100);
            if (tier == 3) return withAlloy(tier, resolveItem("thorium-alloy"), 400);
            return new ItemStack[0];
        }
    }
}
