package ic2m;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.struct.IntSeq;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import arc.util.io.Reads;
import arc.util.io.Writes;

/** EU cable node. HV nodes use the same pole-like range on every hop. */
public class Ic2CableBlock extends Ic2PowerBlock {
    public boolean highVoltage = false;
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

    public class Ic2CableBuild extends Ic2PowerBuilding {
        public IntSeq links = new IntSeq();

        @Override
        public void created() {
            super.created();
            maxEnergy = cableCapacity;
        }

        public boolean hasLink(Building other) {
            return links.contains(other.pos());
        }

        private boolean useTarget(Building other) {
            return links.size == 0 || hasLink(other);
        }

        private boolean linkable(Building other) {
            if (other instanceof Ic2CableBlock.Ic2CableBuild cable) {
                return ((Ic2CableBlock)cable.block).highVoltage == highVoltage;
            }
            return highVoltage && other instanceof Ic2TransformerBlock.Ic2TransformerBuild;
        }

        private void drawLink(Building other) {
            Draw.z(Layer.power + 1f);
            Draw.color(highVoltage ? Pal.powerLight : Pal.accent);
            Lines.stroke(highVoltage ? 1.5f : 1f);
            Lines.line(x, y, other.x, other.y);
            Draw.reset();
        }

        @Override
        public void draw() {
            super.draw();
            for (int dx = -nodeRange; dx <= nodeRange; dx++) {
                for (int dy = -nodeRange; dy <= nodeRange; dy++) {
                    if (dx == 0 && dy == 0 || dx * dx + dy * dy > nodeRange * nodeRange) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (other == null || other.pos() < pos() || !linkable(other) || !useTarget(other)) continue;
                    drawLink(other);
                }
            }
        }

        private boolean withinRange(Building other) {
            float dx = other.tile.x - tile.x, dy = other.tile.y - tile.y;
            return dx * dx + dy * dy <= nodeRange * nodeRange;
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (!withinRange(other) || !linkable(other)) return false;
            if (links.contains(other.pos())) {
                links.removeValue(other.pos());
                if (other instanceof Ic2CableBlock.Ic2CableBuild cable) cable.links.removeValue(pos());
            } else {
                links.add(other.pos());
                if (other instanceof Ic2CableBlock.Ic2CableBuild cable && !cable.links.contains(pos())) cable.links.add(pos());
            }
            return true;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.add((highVoltage ? "HV" : "LV") + " Cable Network").left().row();
            table.add("Range: " + nodeRange + " tiles").left().row();
            table.add("Transfer: " + (int)transferRate + " EU/t | Loss: " + (int)(loss * 100f) + "%").left().row();
            table.add("Manual links: " + links.size + " (tap compatible nodes to toggle)").left();
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

            for (int dx = -nodeRange; dx <= nodeRange; dx++) {
                for (int dy = -nodeRange; dy <= nodeRange; dy++) {
                    if (dx == 0 && dy == 0 || dx * dx + dy * dy > nodeRange * nodeRange) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (!(other instanceof Ic2PowerBuilding target) || !canConnectEnergy(other) || !useTarget(other)
                        || !target.canAcceptEnergy() || target.energy >= target.maxEnergy) continue;

                    float toSend = EuTransferRules.sourceAmount(energy, target.maxEnergy - target.energy,
                        transferRate, 1f - loss);
                    if (toSend > 0f) {
                        float remainder = target.acceptEnergy(EuTransferRules.targetAmount(toSend, 1f - loss));
                        energy -= toSend - remainder / (1f - loss);
                    }
                    if (energy <= 0f) return;
                }
            }
        }

        @Override
        protected boolean canConnectEnergy(Building other) {
            if (highVoltage) {
                return other instanceof Ic2CableBlock.Ic2CableBuild cable && ((Ic2CableBlock)cable.block).highVoltage
                    || other instanceof Ic2TransformerBlock.Ic2TransformerBuild transformer
                        && transformer.mode == Ic2TransformerBlock.MODE_STEP_DOWN;
            }
            return !(other instanceof Ic2CableBlock.Ic2CableBuild cable && ((Ic2CableBlock)cable.block).highVoltage)
                && !(other instanceof Ic2TransformerBlock.Ic2TransformerBuild transformer
                    && transformer.mode == Ic2TransformerBlock.MODE_STEP_DOWN);
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
            write.i(links.size);
            for (int i = 0; i < links.size; i++) write.i(links.get(i));
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            links.clear();
            if (revision >= 1) {
                int count = read.i();
                for (int i = 0; i < count; i++) links.add(read.i());
            }
        }
    }
}
