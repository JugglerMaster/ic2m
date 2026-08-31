package ic2m;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;

public class BatteryBlock extends Ic2PowerBlock {
    public BatteryBlock(String name) {
        super(name);
        basePowerCapacity = 500f;
        rotate = true;
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
            int dx = other.tile.x - tile.x;
            int dy = other.tile.y - tile.y;
            return D4[rotation][0] == dx && D4[rotation][1] == dy;
        }

        /** Output direction follows the block's facing; rotating the battery (tap, vanilla config) aims it. */
        @Override
        protected void drawOutputLink() {
            float tx = (tile.x + D4[rotation][0]) * Vars.tilesize + Vars.tilesize / 2f;
            float ty = (tile.y + D4[rotation][1]) * Vars.tilesize + Vars.tilesize / 2f;
            Draw.z(Layer.power + 1f);
            Draw.color(Pal.remove);
            Lines.stroke(2f);
            Lines.line(x, y, tx, ty);
            Draw.reset();
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            table.row();
            table.add("Battery Tier " + (upgradeTier + 1)).left().row();
            table.add("Storage: " + (int) maxEnergy + " EU").left().row();
            table.add("Output side: rotate the block to aim it").left().row();
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

        @Override
        public void drawSelect() {
            super.drawSelect();
            drawOutputLink();
        }
    }
}
