package ic2m;

import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.meta.Stat;

public class SolarPanel extends Ic2PowerBlock {
    public float basePowerPerTick = 1f;

    public SolarPanel(String name) {
        super(name);
        basePowerCapacity = 500f;
        hasItems = true;
        itemCapacity = 20;
        configurable = true;
        saveConfig = true;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.basePowerGeneration, "T1: 1 / T2: 8 / T3: 64 EU/t");
    }

    public class SolarPanelBuild extends Ic2PowerBuilding {
        public float currentPowerPerTick = basePowerPerTick;

        @Override
        public void created() {
            super.created();
            upgradeTier = baseTier;
            recalculateStats();
        }

        @Override
        public void update() {
            if (!enabled) return;
            super.update();
        }

        void recalculateStats() {
            currentPowerPerTick = upgradeTier == 0 ? 1f : upgradeTier == 1 ? 8f : 64f;
            // Solar panels are pass-through generators: no EU buffer. Generated power is pushed
            // directly to linked neighbours in distributePower() instead of being stored.
            maxEnergy = 0f;
            energy = 0f;
        }

        /** Pass-through: push this tick's generation straight to linked neighbours without buffering. */
        @Override
        protected void distributePower() {
            if (!canProvideEnergy()) return;
            float multiplier = Vars.state.rules.solarMultiplier;
            if (multiplier <= 0f) return;
            float amount = currentPowerPerTick * multiplier;
            for (int i = 0; i < links.size; i++) {
                Building b = Vars.world.build(links.get(i));
                if (!(b instanceof Ic2PowerBuilding target) || !canConnectEnergy(b)
                    || !target.acceptsFrom(this) || !canOutputTo(b) || target.energy >= target.maxEnergy) continue;
                float space = target.maxEnergy - target.energy;
                float toSend = Math.min(amount, space);
                if (toSend > 0f) {
                    float remainder = target.acceptEnergy(toSend);
                    amount -= toSend - remainder;
                }
                if (amount <= 0f) return;
            }
        }

        @Override
        public boolean canAcceptEnergy() { return false; }

        @Override
        protected boolean isGenerator() { return true; }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            table.row();
            table.table(info -> {
                info.add("Tier " + (upgradeTier + 1)).left();
                info.row();
                info.add("Output: " + String.format("%.1f", currentPowerPerTick) + " EU/t").left();
                info.row();
                info.add("Storage: none (pass-through)").left();
            }).fillX().pad(4);
            table.row();
            table.add("Tier 2-3 upgrades are performed by the IC2 Upgrade Node (arrange a 2x2 with this block).").left().pad(4);
        }

        @Override
        public void configured(Unit unit, Object value) {
            super.configured(unit, value);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (source instanceof SolarPanelBuild && source != this) return false;
            return false;
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
            write.i(upgradeTier);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            upgradeTier = read.i();
            recalculateStats();
        }

        @Override
        public ItemStack[] upgradeRequirements(int tier) {
            if (tier == 2) return withAlloy(tier, resolveItem("titanium-carbide"), 100);
            if (tier == 3) return withAlloy(tier, resolveItem("thorium-alloy"), 400);
            return new ItemStack[0];
        }
    }
}
