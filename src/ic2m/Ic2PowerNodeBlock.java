package ic2m;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.meta.Stat;

/** Bridges the cable network to IC2 buildings. Connects to same-tier adjacent/ranged cables and to
 *  adjacent/ranged buildings (machines, batteries, producers). Does not connect to machines directly. */
public class Ic2PowerNodeBlock extends Ic2PowerBlock {
    public int powerTier = 0;
    public int nodeRange = 6;

    public Ic2PowerNodeBlock(String name) {
        super(name);
        basePowerCapacity = 200f;
        hasItems = false;
        update = true;
        configurable = true;
        saveConfig = true;
    }

    public static float transferRateForTier(int tier) {
        return tier == 0 ? 32f : tier == 1 ? 128f : 512f;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerUse, (int) transferRateForTier(powerTier) + " EU/t, range " + nodeRange + " tiles");
    }

    public class Ic2PowerNodeBuild extends Ic2PowerBuilding {
        @Override
        public void created() {
            super.created();
            maxEnergy = basePowerCapacity * block.size * block.size * tierMultiplier(upgradeTier);
        }

        @Override
        public int voltageTier() { return powerTier; }

        public boolean hasLink(Building other) { return links.contains(other.pos()); }

        private boolean linkable(Building other) {
            return other instanceof Ic2PowerNodeBuild && canConnectEnergy(other);
        }

        private boolean withinRange(Building other) {
            float dx = other.tile.x - tile.x, dy = other.tile.y - tile.y;
            int r = ((Ic2PowerNodeBlock) block).nodeRange;
            return dx * dx + dy * dy <= r * r;
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (!withinRange(other) || !linkable(other)) return false;
            if (links.contains(other.pos())) {
                links.removeValue(other.pos());
                if (other instanceof Ic2PowerNodeBuild node) node.links.removeValue(pos());
            } else {
                links.add(other.pos());
                if (other instanceof Ic2PowerNodeBuild node && !node.links.contains(pos())) node.links.add(pos());
            }
            return true;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.add((powerTier >= 2 ? "HV" : powerTier == 1 ? "MV" : "LV") + " Power Node").left().row();
            table.add("Range: " + ((Ic2PowerNodeBlock) block).nodeRange + " tiles").left().row();
            table.add("Manual links: " + links.size + " (tap another node in range to toggle)").left();
        }

        @Override
        public Object config() { return links.toArray(); }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof int[] positions) {
                links.clear();
                links.addAll(positions);
            }
        }

        private void drawLink(Building other) {
            Draw.z(Layer.power + 1f);
            Draw.color(Pal.accent);
            Lines.stroke(powerTier >= 2 ? 1.5f : 1f);
            Lines.line(x, y, other.x, other.y);
            Draw.reset();
        }

        @Override
        public void draw() {
            super.draw();
            int r = ((Ic2PowerNodeBlock) block).nodeRange;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0 || dx * dx + dy * dy > r * r) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (other == null || !linkable(other) || !hasLink(other)) continue;
                    drawLink(other);
                }
            }
        }

        @Override
        protected void drawConnections() {
            int r = ((Ic2PowerNodeBlock) block).nodeRange;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx * dx + dy * dy > r * r) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (other == null || other == this) continue;
                    if (!canConnectEnergy(other)) continue;
                    boolean adjacent = Math.max(Math.abs(dx), Math.abs(dy)) <= 1;
                    boolean isNode = other instanceof Ic2PowerNodeBuild;
                    // Show adjacent grid ties and explicit links; don't draw a line for
                    // distant nodes that were never linked (those only connect on purpose).
                    if (!adjacent && !(isNode && hasLink(other))) continue;
                    Lines.stroke(1f);
                    Draw.color(isNode && hasLink(other) ? Pal.accent : Pal.gray);
                    Lines.line(x, y, other.x, other.y);
                    Draw.reset();
                }
            }
        }

        @Override
        protected void distributePower() {
            if (energy <= 0f || !canProvideEnergy()) return;
            int pt = ((Ic2PowerNodeBlock) block).powerTier;
            int range = ((Ic2PowerNodeBlock) block).nodeRange;
            float rate = transferRateForTier(pt);
            for (int dx = -range; dx <= range; dx++) {
                for (int dy = -range; dy <= range; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx * dx + dy * dy > range * range) continue;
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (!(other instanceof Ic2PowerBuilding target) || !canConnectEnergy(other)
                        || !target.acceptsFrom(this) || target.energy >= target.maxEnergy) continue;
                    if (other instanceof Ic2CableBlock.Ic2CableBuild
                        && ((Ic2CableBlock) other.block).powerTier != pt) continue;
                    boolean adjacent = Math.max(Math.abs(dx), Math.abs(dy)) <= 1;
                    // Adjacent blocks connect through the local grid. Distant *nodes* only
                    // exchange power when explicitly linked, so a line of nodes doesn't mesh-link.
                    if (!adjacent && other instanceof Ic2PowerNodeBuild && !hasLink(other)) continue;
                    float space = target.maxEnergy - target.energy;
                    float toSend = Math.min(energy, Math.min(space, rate));
                    if (toSend > 0f) {
                        float remainder = target.acceptEnergy(toSend);
                        energy -= toSend - remainder;
                    }
                    if (energy <= 0f) return;
                }
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
