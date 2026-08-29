package ic2m.powerarmor;

import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import mindustry.type.Category;
import ic2m.Ic2PowerBlock;
import ic2m.Ic2PowerBuilding;
import ic2m.Ic2mMod;

/** Base-upgrade block. Paying the one-time unlock cost (done in the bench UI, per
 *  map) sets the player's respawn unit to the Power Armor Suit. Each loadout option
 *  (weapon / armor / support / mobility) also costs IC2 EU to select. The bench draws
 *  that EU from the LV power network, so it must be wired to an LV cable. */
public class PowerArmorBench extends Ic2PowerBlock {
    /** One-time item cost to unlock all upgrade options for this map. */
    public ItemStack[] unlockCost = ItemStack.with(Items.copper, 200, Items.graphite, 100);
    /** EU drained from the grid each time a loadout option is selected. */
    public float optionPowerCost = 1000f;

    public PowerArmorBench(String name) {
        super(name);
        size = 2;
        solid = true;
        update = false;
        configurable = true;
        saveConfig = true;
        category = Category.power;
        buildVisibility = BuildVisibility.shown;
        basePowerCapacity = 5000f;
        requirements = ItemStack.with(
            ingot("copper-ingot", Items.copper), 1000,
            ingot("lead-ingot", Items.lead), 500,
            ingot("titanium-ingot", Items.titanium), 250
        );
        buildType = () -> new PowerArmorBenchBuild();
    }

    /** Resolve a mod ingot item by name suffix; fall back to a vanilla item if missing. */
    private static Item ingot(String suffix, Item fallback) {
        Item found = Vars.content.items().find(i -> i.name.endsWith("-" + suffix));
        return found == null ? fallback : found;
    }

    public class PowerArmorBenchBuild extends Ic2PowerBuilding {
        public boolean unlocked = false;
        public boolean enabled = true;
        public String weaponId = "rifle";
        public String armorId = "balanced";
        public String supportId = "none";
        public String mobilityId = "ground";
        /** Transient message shown at the top of the config panel (e.g. low-EU warning). */
        public String statusMsg = "";

        @Override
        public void created() {
            super.created();
            maxEnergy = basePowerCapacity;
        }

        @Override
        protected void distributePower() {
            // The bench only consumes EU; it never pushes it back onto the grid.
        }

        @Override
        public int voltageTier() {
            return 1; // MV: matches the 2x2 footprint and the bench's advanced role.
        }

        @Override
        public void buildConfiguration(Table table) {
            table.add("[accent]Power Armor Bench[]").row();
            if (!statusMsg.isEmpty()) {
                table.add(statusMsg).row();
                statusMsg = "";
            }
            table.add("[lightgray]Stored EU: " + (int) energy + " / " + (int) maxEnergy + "[]").row();

            if (!unlocked) {
                table.add("Unlock to respawn in the Power Armor Suit.").row();
                table.button("Unlock (" + costString() + ")", () -> {
                    if (tryUnlock()) {
                        unlocked = true;
                        syncLoadout();
                        applyLoadout();
                        refresh(table);
                    }
                }).growX().row();
                return;
            }

            table.add("[green]Unlocked[] — you respawn as the Power Armor Suit.").row();

            table.button(enabled ? "Suit: [green]ON[]" : "Suit: [red]OFF[]", () -> {
                enabled = !enabled;
                syncLoadout();
                applyLoadout();
                refresh(table);
            }).growX().row();

            category(table, "Weapon", SuitOptions.WEAPONS, weaponId, null,
                id -> selectOption(table, () -> weaponId = id));
            category(table, "Armor", SuitOptions.ARMORS, armorId, null,
                id -> selectOption(table, () -> armorId = id));
            category(table, "Support", SuitOptions.SUPPORTS, supportId, null,
                id -> selectOption(table, () -> supportId = id));
            category(table, "Mobility", SuitOptions.MOBILITY, mobilityId,
                id -> id.equals("jetpack") && !Ic2mMod.jetpackUnlocked(),
                id -> selectOption(table, () -> mobilityId = id));

            if (supportId.equals("repair")) {
                table.add("[scarlet]Repair consumes Nanite Gel each respawn.[]").row();
            }
        }

        /** Pay the EU cost for a loadout option. Refreshes with a warning if the grid has no charge. */
        private void selectOption(Table table, Runnable apply) {
            if (energy >= optionPowerCost) {
                energy -= optionPowerCost;
                apply.run();
                afterSelect(table);
            } else {
                statusMsg = "[scarlet]Needs " + (int) optionPowerCost + " EU — connect an MV cable.[]";
                refresh(table);
            }
        }

        private void category(Table table, String label, String[] options, String selected, Predicate<String> locked, Consumer<String> onPick) {
            table.add("[lightgray]" + label + "[]").row();
            for (String opt : options) {
                boolean sel = opt.equals(selected);
                boolean isLocked = locked != null && locked.test(opt);
                table.button(isLocked ? ("[gray]" + opt + " (locked)[]") : opt, () -> {
                    if (!isLocked) onPick.accept(opt);
                })
                    .growX()
                    .color(sel ? Pal.accent : new Color(0.3f, 0.3f, 0.3f, 1f))
                    .row();
            }
        }

        private void afterSelect(Table table) {
            syncLoadout();
            applyLoadout();
            refresh(table);
        }

        private void refresh(Table table) {
            table.clear();
            buildConfiguration(table);
        }

        private boolean tryUnlock() {
            if (team.core() == null || team.core().items == null) return false;
            var inv = team.core().items;
            for (ItemStack s : unlockCost) {
                if (inv.get(s.item) < s.amount) return false;
            }
            for (ItemStack s : unlockCost) {
                inv.remove(s.item, s.amount);
            }
            return true;
        }

        /** Push this block's loadout into the mod-level active state. The suit is
         *  only active when it is both unlocked AND switched on. */
        void syncLoadout() {
            Ic2mMod.suitUnlocked = unlocked && enabled;
            Ic2mMod.wId = weaponId;
            Ic2mMod.aId = armorId;
            Ic2mMod.sId = supportId;
            Ic2mMod.mId = mobilityId;
        }

        /** Apply the active loadout to the shared suit + core respawn unit. */
        void applyLoadout() {
            Ic2mMod.applySuitLoadout(this.team);
        }

        private String costString() {
            StringBuilder b = new StringBuilder();
            for (ItemStack s : unlockCost) {
                if (b.length() > 0) b.append(", ");
                b.append(s.amount).append(" ").append(s.item.localizedName);
            }
            return b.toString();
        }

        @Override
        public void update() {
        }

        @Override
        public byte version() {
            return 1;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(unlocked);
            write.bool(enabled);
            write.str(weaponId);
            write.str(armorId);
            write.str(supportId);
            write.str(mobilityId);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            unlocked = read.bool();
            enabled = read.bool();
            weaponId = read.str();
            armorId = read.str();
            supportId = read.str();
            mobilityId = read.str();
            if (unlocked) {
                syncLoadout();
                applyLoadout();
            }
        }
    }
}
