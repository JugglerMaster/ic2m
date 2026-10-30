package ic2m;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.meta.Stat;

/** Converts between two adjacent voltage tiers. Pick which pair to bridge (LV&harr;MV
 *  or MV&harr;HV) and whether this block steps energy up or down within that pair.
 *  Output goes out the face the block points at (rotate with R / scroll); input is
 *  accepted omnidirectionally from the other side of the pair, but only from the
 *  matching tier — the two tiers never mix at one face. */
public class Ic2TransformerBlock extends Ic2PowerBlock {
    /** Direction the conversion runs. */
    public static final int MODE_STEP_UP = 0;    // low tier -> high tier
    public static final int MODE_STEP_DOWN = 1;  // high tier -> low tier
    /** Which adjacent tier pair this transformer bridges. */
    public static final int PAIR_LV_MV = 0;      // tier 0 (LV) <-> tier 1 (MV)
    public static final int PAIR_MV_HV = 1;      // tier 1 (MV) <-> tier 2 (HV)
    public static final float CONVERSION_EFFICIENCY = 0.9f;

    private static final String[] TIER_NAMES = {"LV", "MV", "HV"};

    public Ic2TransformerBlock(String name) {
        super(name);
        configurable = true;
        saveConfig = true;
        rotate = true;
        basePowerCapacity = 500f;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(new Stat("Efficiency"), (int)(CONVERSION_EFFICIENCY * 100f) + "%");
    }

    public class Ic2TransformerBuild extends Ic2PowerBuilding {
        /** Direction: {@link #MODE_STEP_UP} or {@link #MODE_STEP_DOWN}. */
        public int mode = MODE_STEP_UP;
        /** Pair: {@link #PAIR_LV_MV} or {@link #PAIR_MV_HV}. */
        public int pair = PAIR_LV_MV;

        @Override
        public void created() {
            super.created();
            maxEnergy = basePowerCapacity;
        }

        /** Lower tier of the selected pair (0 or 1). */
        private int lowTier() { return pair; }
        /** Higher tier of the selected pair (1 or 2). */
        private int highTier() { return pair + 1; }
        /** Tier this transformer pulls energy from. */
        private int inputTier() { return mode == MODE_STEP_UP ? lowTier() : highTier(); }
        /** Tier this transformer pushes energy into. */
        private int outputTier() { return mode == MODE_STEP_UP ? highTier() : lowTier(); }

        private String lowName() { return TIER_NAMES[lowTier()]; }
        private String highName() { return TIER_NAMES[highTier()]; }

        @Override
        public void distributePower() {
            if (energy <= 0f || !canProvideEnergy()) return;
            int range = 2;

            for (int dx = -range; dx <= range; dx++) {
                for (int dy = -range; dy <= range; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (!(other instanceof Ic2PowerBuilding target) || !canConnectEnergy(other)
                        || !target.acceptsFrom(this) || !canOutputTo(other) || target.energy >= target.maxEnergy) continue;
                    // Only feed blocks of the tier we are stepping into, so the two
                    // tiers of the pair never mix at the output face.
                    if (target.voltageTier() != outputTier()) continue;
                    if (other instanceof Ic2CableBlock.Ic2CableBuild cable
                        && cable.links.size > 0 && !cable.hasLink(this)) continue;

                    float available = EuTransferRules.sourceAmount(energy, target.maxEnergy - target.energy,
                        powerTransferRate(), CONVERSION_EFFICIENCY);
                    if (available <= 0f) continue;
                    float remainder = target.acceptEnergy(EuTransferRules.targetAmount(available, CONVERSION_EFFICIENCY));
                    energy -= available - remainder / CONVERSION_EFFICIENCY;
                    if (energy <= 0f) return;
                }
            }
        }

        @Override
        public boolean acceptsFrom(Building source) {
            if (!canAcceptEnergy()) return false;
            // Only accept EU from the input side of the pair (its tier), never the
            // tier we are stepping into.
            return source instanceof Ic2PowerBuilding o && o.voltageTier() == inputTier();
        }

        @Override
        protected boolean canOutputTo(Building other) {
            int dx = other.tile.x - tile.x;
            int dy = other.tile.y - tile.y;
            return D4[rotation][0] == dx && D4[rotation][1] == dy;
        }

        @Override
        protected void drawOutputLink() {
            float tx = (tile.x + D4[rotation][0]) * Vars.tilesize + Vars.tilesize / 2f;
            float ty = (tile.y + D4[rotation][1]) * Vars.tilesize + Vars.tilesize / 2f;
            Draw.z(Layer.power + 1f);
            Draw.color(Pal.place);
            Lines.stroke(2f);
            Lines.line(x, y, tx, ty);
            Draw.reset();
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            drawConnections();
        }

        private int connectedBlocks(int tier) {
            int count = 0;
            int radius = 24;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (other instanceof Ic2PowerBuilding pb && pb.voltageTier() == tier
                        && dx * dx + dy * dy <= 6 * 6) count++;
                }
            }
            return count;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.add("[accent]Transformer[]").row();

            table.button("Pair: " + (pair == PAIR_LV_MV ? "[green]LV <-> MV[]" : "[orange]MV <-> HV[]"),
                () -> configure(encode(pair == PAIR_LV_MV ? PAIR_MV_HV : PAIR_LV_MV, mode))).size(260f, 42f).row();

            table.button("Direction: " + (mode == MODE_STEP_UP
                ? "[white]Step-Up (" + lowName() + " -> " + highName() + ")[]"
                : "[white]Step-Down (" + highName() + " -> " + lowName() + ")[]"),
                () -> configure(encode(pair, mode == MODE_STEP_UP ? MODE_STEP_DOWN : MODE_STEP_UP))).size(260f, 42f).row();

            table.row();
            table.add("Output (" + TIER_NAMES[outputTier()] + "): " + connectedBlocks(outputTier())
                + " in range | Input (" + TIER_NAMES[inputTier()] + "): " + connectedBlocks(inputTier()) + " in range");
            table.row();
            table.add("Efficiency: " + (int)(CONVERSION_EFFICIENCY * 100f) + "% | Buffer: " + (int)energy + "/" + (int)maxEnergy + " EU");
            table.row();
            table.add("Point the block at the output tier (rotate with R / scroll).");
        }

        private static int encode(int pair, int mode) {
            return pair * 2 + mode;
        }

        @Override
        public Integer config() {
            return encode(pair, mode);
        }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof Integer i) {
                pair = (i / 2) % 2;
                mode = i % 2;
            }
        }

        @Override
        public byte version() { return 4; }

        @Override
        protected boolean readsOutputState(byte revision) { return false; }

        @Override
        protected boolean writesOutputState() { return false; }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(mode);
            write.i(pair);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 1) mode = read.i();
            if (revision >= 4) pair = read.i();
        }
    }
}
