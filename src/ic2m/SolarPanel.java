package ic2m;

import arc.graphics.Color;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.meta.Stat;

public class SolarPanel extends Ic2PowerBlock {
    public float basePowerPerTick = 1f;
    public int maxUpgradeSlots = 2;

    public SolarPanel(String name) {
        super(name);
        basePowerCapacity = 500f;
        hasItems = true;
        itemCapacity = 20;
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
        stats.add(Stat.basePowerGeneration, "[orange]@[] EU/t", String.format("%.1f", basePowerPerTick));
    }

    public class SolarPanelBuild extends Ic2PowerBuilding {
        public int efficiencyLevel = 0;
        public int capacityLevel = 0;
        public float currentPowerPerTick = basePowerPerTick;

        private static final Color lockedColor = new Color(0.5f, 0.5f, 0.5f, 1f);
        private static final Color reqColor = new Color(0.7f, 0.7f, 0.7f, 1f);

        // Tier 1 upgrade costs
        public static final int EFFICIENCY_TIER1_COST = 100;
        public static final int CAPACITY_TIER1_COST = 100;

        @Override
        public void created() {
            super.created();
            maxEnergy = basePowerCapacity;
            currentPowerPerTick = basePowerPerTick;
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

        public boolean canUpgradeEfficiency() {
            if (efficiencyLevel >= 1) return false;
            return items.get(Items.graphite) >= EFFICIENCY_TIER1_COST;
        }

        public boolean canUpgradeCapacity() {
            if (capacityLevel >= 1) return false;
            return items.get(Items.lead) >= CAPACITY_TIER1_COST;
        }

        public void upgradeEfficiency() {
            if (!canUpgradeEfficiency()) return;
            items.remove(Items.graphite, EFFICIENCY_TIER1_COST);
            efficiencyLevel++;
            recalculateStats();
        }

        public void upgradeCapacity() {
            if (!canUpgradeCapacity()) return;
            items.remove(Items.lead, CAPACITY_TIER1_COST);
            capacityLevel++;
            recalculateStats();
        }

        private void recalculateStats() {
            float effMult = 1f + efficiencyLevel * 0.5f;
            float capMult = 1f + capacityLevel * 2f;

            currentPowerPerTick = basePowerPerTick * effMult;
            maxEnergy = basePowerCapacity * capMult;
            if (energy > maxEnergy) energy = maxEnergy;
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            table.row();

            // Efficiency upgrade
            table.table(eff -> {
                eff.add("Efficiency Lv." + efficiencyLevel).left().padRight(8);
                eff.add("+" + (efficiencyLevel * 50) + "%").left().padRight(8);
                if (canUpgradeEfficiency()) {
                    eff.button("+", this::upgradeEfficiency).size(30).pad(2);
                } else {
                    eff.add("[ locked ]").color(lockedColor);
                }
                eff.row();
                eff.add("  Requires: " + EFFICIENCY_TIER1_COST + " Graphite").left().color(reqColor);
                eff.add(new Image(Items.graphite.uiIcon)).size(12).padLeft(4);
            }).fillX().pad(4);

            table.row();

            // Capacity upgrade
            table.table(cap -> {
                cap.add("Capacity Lv." + capacityLevel).left().padRight(8);
                cap.add("+" + (capacityLevel * 200) + " EU").left().padRight(8);
                if (canUpgradeCapacity()) {
                    cap.button("+", this::upgradeCapacity).size(30).pad(2);
                } else {
                    cap.add("[ locked ]").color(lockedColor);
                }
                cap.row();
                cap.add("  Requires: " + CAPACITY_TIER1_COST + " Lead").left().color(reqColor);
                cap.add(new Image(Items.lead.uiIcon)).size(12).padLeft(4);
            }).fillX().pad(4);

            table.row();

            // Status
            table.table(info -> {
                info.add("Output: " + String.format("%.1f", currentPowerPerTick) + " EU/t").left();
                info.row();
                info.add("Storage: " + (int) energy + "/" + (int) maxEnergy).left();
            }).fillX().pad(4);
        }

        @Override
        public void configured(Unit unit, Object value) {
            super.configured(unit, value);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (source instanceof SolarPanelBuild && source != this) return false;
            if (item == Items.graphite && efficiencyLevel < 1) return true;
            if (item == Items.lead && capacityLevel < 1) return true;
            return false;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(efficiencyLevel);
            write.i(capacityLevel);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            efficiencyLevel = read.i();
            capacityLevel = read.i();
            recalculateStats();
        }
    }
}
