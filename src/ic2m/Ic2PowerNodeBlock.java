package ic2m;

import mindustry.Vars;
import mindustry.gen.Building;
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
        protected boolean canConnectEnergy(Building other) {
            if (other instanceof Ic2CableBlock.Ic2CableBuild cable) {
                return ((Ic2CableBlock)cable.block).powerTier == ((Ic2PowerNodeBlock) block).powerTier;
            }
            return true;
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
    }
}
