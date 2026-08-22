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
        stats.add(Stat.basePowerGeneration, "[orange]@[] EU/t", String.format("%.1f", basePowerPerTick * size * size));
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
            super.update();
            if (!enabled) return;

            float multiplier = Vars.state.rules.solarMultiplier;
            if (multiplier > 0f) {
                float generated = currentPowerPerTick * multiplier;
                energy = Math.min(maxEnergy, energy + generated);
            }
        }

        void recalculateStats() {
            float mult = block.size * block.size * tierMultiplier(upgradeTier);
            currentPowerPerTick = basePowerPerTick * mult;
            maxEnergy = basePowerCapacity * mult;
            if (energy > maxEnergy) energy = maxEnergy;
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            table.row();
            table.table(info -> {
                info.add("Tier " + (upgradeTier + 1)).left();
                info.row();
                info.add("Output: " + String.format("%.1f", currentPowerPerTick) + " EU/t").left();
                info.row();
                info.add("Storage: " + (int) energy + "/" + (int) maxEnergy).left();
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
