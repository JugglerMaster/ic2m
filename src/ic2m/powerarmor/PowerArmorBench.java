package ic2m.powerarmor;

import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import java.util.function.Consumer;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.type.Category;
import ic2m.Ic2mMod;

/** Base-upgrade block. Paying the one-time unlock cost (done in the bench UI, per
 *  map) sets the player's respawn unit to the Power Armor Suit. The chosen loadout
 *  (weapon / armor / support) is stored on the block so it survives saves. */
public class PowerArmorBench extends Block {
    /** One-time cost to unlock all upgrade options for this map. */
    public ItemStack[] unlockCost = ItemStack.with(Items.copper, 200, Items.graphite, 100);

    public PowerArmorBench(String name) {
        super(name);
        size = 2;
        solid = true;
        update = false;
        configurable = true;
        saveConfig = true;
        category = Category.units;
        requirements = ItemStack.with(Items.copper, 100, Items.lead, 50);
        buildType = () -> new PowerArmorBenchBuild();
    }

    public class PowerArmorBenchBuild extends Building {
        public boolean unlocked = false;
        public boolean enabled = true;
        public String weaponId = "rifle";
        public String armorId = "balanced";
        public String supportId = "none";

        @Override
        public void buildConfiguration(Table table) {
            table.add("[accent]Power Armor Bench[]").row();

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

            category(table, "Weapon", SuitOptions.WEAPONS, weaponId,
                id -> { weaponId = id; afterSelect(table); });
            category(table, "Armor", SuitOptions.ARMORS, armorId,
                id -> { armorId = id; afterSelect(table); });
            category(table, "Support", SuitOptions.SUPPORTS, supportId,
                id -> { supportId = id; afterSelect(table); });

            if (supportId.equals("repair")) {
                table.add("[scarlet]Repair consumes Nanite Gel each respawn.[]").row();
            }
        }

        private void category(Table table, String label, String[] options, String selected, Consumer<String> onPick) {
            table.add("[lightgray]" + label + "[]").row();
            for (String opt : options) {
                boolean sel = opt.equals(selected);
                table.button(opt, () -> onPick.accept(opt))
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
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            unlocked = read.bool();
            enabled = read.bool();
            weaponId = read.str();
            armorId = read.str();
            supportId = read.str();
            if (unlocked) {
                syncLoadout();
                applyLoadout();
            }
        }
    }
}
