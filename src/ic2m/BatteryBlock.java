package ic2m;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;

public class BatteryBlock extends Ic2PowerBlock {
    public BatteryBlock(String name) {
        super(name);
        basePowerCapacity = 500f;
        configurable = true;
        saveConfig = true;
    }

    public class BatteryBuild extends Ic2PowerBuilding {
        @Override
        public byte version() { return 2; }

        @Override
        protected boolean readsOutputState(byte revision) { return false; }

        @Override
        protected boolean writesOutputState() { return false; }

        @Override
        protected int defaultOutputRotation() { return 1; } // up by default

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

        /** When this battery is selected, draw the vanilla selection outline on whichever
         *  block sits on the output side so the player can see exactly where EU is being sent. */
        @Override
        protected void drawOutputLink() {
            if (outputRotation < 0) return;
            int ox = tile.x + D4[outputRotation][0];
            int oy = tile.y + D4[outputRotation][1];
            Building out = Vars.world.build(ox, oy);
            if (out != null) {
                Drawf.selected(out, Pal.accent);
            } else {
                // No block on the output side — draw a short stub so the aimed direction is still visible.
                float tx = (ox + 0.5f) * Vars.tilesize;
                float ty = (oy + 0.5f) * Vars.tilesize;
                Draw.z(Layer.power + 1f);
                Draw.color(Pal.accent);
                Lines.stroke(2f);
                Lines.line(x, y, tx, ty);
                Draw.reset();
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            table.row();
            table.add("Battery Tier " + (upgradeTier + 1)).left().row();
            table.add("Storage: " + (int) maxEnergy + " EU").left().row();
            table.add("Receives EU from all sides, sends it only out the chosen side.").left().row();
            addOutputControl(table);
        }

        @Override
        public Integer config() {
            return outputRotation;
        }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof Integer i && i >= -1 && i < 4) outputRotation = i;
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
