package ic2m;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.struct.IntSeq;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.meta.Stat;
import arc.util.io.Reads;
import arc.util.io.Writes;

/** EU cable node. HV nodes use the same pole-like range on every hop. */
public class Ic2CableBlock extends Ic2PowerBlock {
    public boolean highVoltage = false;
    public int powerTier = 0;
    public int nodeRange = 2;
    public float transferRate = 10f;
    public float loss = 0.02f;
    public float cableCapacity = 100f;

    public Ic2CableBlock(String name) {
        super(name);
        solid = false;
        hasItems = false;
        update = true;
        configurable = true;
        saveConfig = true;
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.powerUse, (int) transferRate + " EU/t, " + (int)(loss * 100f) + "% loss");
    }

    @Override
    protected float statsCapacity(){
        return cableCapacity;
    }

    @Override
    protected int statsRange(){
        return (int) nodeRange;
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        // Use the block's true placement offset so the range circle is centred on where the
        // building will actually land (matches how the placed building's x/y is computed).
        float cx = x * Vars.tilesize + offset;
        float cy = y * Vars.tilesize + offset;
        Draw.color(linkColor());
        Lines.stroke(1.5f);
        Lines.circle(cx, cy, nodeRange * Vars.tilesize);
        Draw.reset();
    }

    /** Line/range colour for this cable's voltage tier (LV blue, MV green, HV orange). */
    public Color linkColor() {
        if (powerTier >= 2) return Pal.powerLight;
        if (powerTier == 1) return Color.valueOf("7bff5a");
        return Pal.accent;
    }

    public class Ic2CableBuild extends Ic2PowerBuilding {

        @Override
        public int voltageTier() { return powerTier; }

        @Override
        public void created() {
            super.created();
            maxEnergy = cableCapacity;
        }

        public boolean hasLink(Building other) {
            return links.contains(other.pos());
        }

        private boolean linkable(Building other) {
            return canConnectEnergy(other);
        }

        private boolean withinRange(Building other) {
            float dx = other.tile.x - tile.x, dy = other.tile.y - tile.y;
            return dx * dx + dy * dy <= nodeRange * nodeRange;
        }

        /** Cables auto-link (and transfer) across their full nodeRange, not just to adjacent tiles. */
        @Override
        protected int linkRange() { return nodeRange; }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (!withinRange(other) || !linkable(other)) return false;
            if (links.contains(other.pos())) {
                links.removeValue(other.pos());
                if (other instanceof Ic2PowerBuilding pb && pb.links.contains(pos())) pb.links.removeValue(pos());
            } else {
                links.add(other.pos());
                if (other instanceof Ic2PowerBuilding pb && !pb.links.contains(pos())) pb.links.add(pos());
            }
            return true;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.add((highVoltage ? "HV" : "LV") + " Cable Network").left().row();
            table.add("Range: " + nodeRange + " tiles").left().row();
            table.add("Transfer: " + (int)transferRate + " EU/t | Loss: " + (int)(loss * 100f) + "%").left().row();
            table.add("Manual links: " + links.size + " (tap a compatible block in range to toggle)").left();
        }

        @Override
        public Object config() {
            return links.toArray();
        }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof int[] positions) {
                links.clear();
                links.addAll(positions);
            }
        }

        @Override
        public boolean canAcceptEnergy() {
            return true;
        }

        @Override
        public boolean canProvideEnergy() {
            return true;
        }

        @Override
        protected void distributePower() {
            if (energy <= 0f || !canProvideEnergy()) return;
            for (int i = 0; i < links.size; i++) {
                Building b = Vars.world.build(links.get(i));
                if (!(b instanceof Ic2PowerBuilding target) || !canConnectEnergy(b)
                    || !target.acceptsFrom(this) || !canOutputTo(b) || target.energy >= target.maxEnergy) continue;
                float space = target.maxEnergy - target.energy;
                float toSend = EuTransferRules.sourceAmount(energy, space, transferRate, 1f - loss);
                if (toSend > 0f) {
                    float remainder = target.acceptEnergy(EuTransferRules.targetAmount(toSend, 1f - loss));
                    energy -= toSend - remainder / (1f - loss);
                }
                if (energy <= 0f) return;
            }
        }

        @Override
        public byte version() { return 1; }

        @Override
        protected boolean readsOutputState(byte revision) { return false; }

        @Override
        protected boolean writesOutputState() { return false; }

        @Override
        public void write(Writes write) {
            super.write(write);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
        }
    }
}
