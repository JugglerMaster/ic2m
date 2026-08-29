package ic2m;

import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.type.ItemStack;

public class BatteryBlock extends Ic2PowerBlock {
    public BatteryBlock(String name) {
        super(name);
        basePowerCapacity = 500f;
    }

    public class BatteryBuild extends Ic2PowerBuilding {
        @Override
        public byte version() { return 2; }

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
        public boolean acceptsFrom(Building source) {
            return canAcceptEnergy();
        }

        @Override
        protected boolean canOutputTo(Building other) {
            if (outputRotation < 0) return true;
            int dx = other.tile.x - tile.x;
            int dy = other.tile.y - tile.y;
            return D4[outputRotation][0] == dx && D4[outputRotation][1] == dy;
        }

        @Override
        protected int defaultOutputRotation() { return 1; }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            table.row();
            table.add("Battery Tier " + (upgradeTier + 1)).left().row();
            table.add("Storage: " + (int) maxEnergy + " EU").left().row();
            addOutputControl(table);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(outputRotation);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 2) outputRotation = read.i();
        }

        @Override
        public ItemStack[] upgradeRequirements(int tier) {
            if (tier == 2) return withAlloy(tier, resolveItem("titanium-carbide"), 100);
            if (tier == 3) return withAlloy(tier, resolveItem("thorium-alloy"), 400);
            return new ItemStack[0];
        }
    }
}
