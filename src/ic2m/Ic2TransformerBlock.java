package ic2m;

import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.world.meta.Stat;

/** Converts between the short-range LV network and long-range HV cables. */
public class Ic2TransformerBlock extends Ic2PowerBlock {
    public static final int MODE_STEP_UP = 0;
    public static final int MODE_STEP_DOWN = 1;
    public static final float CONVERSION_EFFICIENCY = 0.9f;

    public Ic2TransformerBlock(String name) {
        super(name);
        configurable = true;
        saveConfig = true;
        basePowerCapacity = 500f;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(new Stat("Efficiency"), (int)(CONVERSION_EFFICIENCY * 100f) + "%");
    }

    public class Ic2TransformerBuild extends Ic2PowerBuilding {
        public int mode = MODE_STEP_UP;

        @Override
        public void created() {
            super.created();
            maxEnergy = basePowerCapacity;
        }

        @Override
        protected void distributePower() {
            if (energy <= 0f || !canProvideEnergy()) return;
            int range = 2;

            for (int dx = -range; dx <= range; dx++) {
                for (int dy = -range; dy <= range; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (!(other instanceof Ic2PowerBuilding target) || !canConnectEnergy(other)
                        || !target.canAcceptEnergy() || target.energy >= target.maxEnergy) continue;
                    if (other instanceof Ic2CableBlock.Ic2CableBuild cable
                        && cable.links.size > 0 && !cable.hasLink(this)) continue;

                    float available = EuTransferRules.sourceAmount(energy, target.maxEnergy - target.energy,
                        10f, CONVERSION_EFFICIENCY);
                    if (available <= 0f) continue;
                    float remainder = target.acceptEnergy(EuTransferRules.targetAmount(available, CONVERSION_EFFICIENCY));
                    energy -= available - remainder / CONVERSION_EFFICIENCY;
                    if (energy <= 0f) return;
                }
            }
        }

        @Override
        protected boolean canConnectEnergy(Building other) {
            if (mode == MODE_STEP_UP) {
                return other instanceof Ic2CableBlock.Ic2CableBuild cable && ((Ic2CableBlock)cable.block).highVoltage;
            }
            return !(other instanceof Ic2CableBlock.Ic2CableBuild cable && ((Ic2CableBlock)cable.block).highVoltage)
                && !(other instanceof Ic2TransformerBlock.Ic2TransformerBuild);
        }

        private int connectedNodes() {
            int count = 0;
            int radius = 24;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (other instanceof Ic2CableBlock.Ic2CableBuild cable
                        && ((Ic2CableBlock)cable.block).highVoltage
                        && dx * dx + dy * dy <= ((Ic2CableBlock)cable.block).nodeRange * ((Ic2CableBlock)cable.block).nodeRange
                        && (cable.links.size == 0 || cable.hasLink(this))) count++;
                }
            }
            return count;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.button(mode == MODE_STEP_UP ? "Mode: Step-Up (LV -> HV)" : "Mode: Step-Down (HV -> LV)",
                () -> configure(mode == MODE_STEP_UP ? MODE_STEP_DOWN : MODE_STEP_UP)).size(240f, 45f);
            table.row();
            table.add("Conversion efficiency: " + (int)(CONVERSION_EFFICIENCY * 100f) + "%");
            table.row();
            table.add("Connected HV nodes: " + connectedNodes());
            table.row();
            table.add("EU buffer: " + (int)energy + "/" + (int)maxEnergy);
        }

        @Override
        public Integer config() {
            return mode;
        }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof Integer i && (i == MODE_STEP_UP || i == MODE_STEP_DOWN)) mode = i;
        }

        @Override
        public byte version() { return 2; }

        @Override
        protected boolean readsOutputState(byte revision) { return false; }

        @Override
        protected boolean writesOutputState() { return false; }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(mode);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 1) mode = read.i();
        }
    }
}
