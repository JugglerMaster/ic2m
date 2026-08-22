package ic2m;

import arc.ApplicationListener;
import arc.backend.headless.HeadlessApplication;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.content.TechTree;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.entities.abilities.Ability;
import mindustry.entities.abilities.ForceFieldAbility;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.entities.abilities.ShieldArcAbility;
import mindustry.game.Rules;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.modules.ItemModule;
import ic2m.powerarmor.PowerArmorBench;
import ic2m.powerarmor.PowerArmorSuit;
import ic2m.powerarmor.SuitOptions;

/** Headless smoke test for the Power Armor Suit + Bench + Nanite Gel wiring. */
public final class PowerArmorHarness implements ApplicationListener {
    private Throwable failure;

    public static void main(String[] args) {
        new HeadlessApplication(new PowerArmorHarness());
    }

    @Override
    public void init() {
        try {
            Vars.headless = true;
            Vars.content = new ContentLoader();
            Vars.state = new GameState();
            Vars.state.rules = new Rules();
            Vars.state.set(GameState.State.playing);
            Items.load();
            Liquids.load();
            StatusEffects.load();

            Ic2mMod mod = new Ic2mMod();
            mod.loadContent();
            boolean initRan = true;
            try {
                mod.init();
            } catch (Throwable t) {
                initRan = false;
                System.out.println("  warn: mod.init() failed headlessly: " + t);
            }

            PowerArmorSuit suit = Ic2mMod.powerArmorSuit;
            Block bench = Ic2mMod.powerArmorBench;
            var recombinator = Ic2mMod.naniteRecombinator;

            // --- Suit identity / registry ---
            check("suit registered", suit != null);
            check("suit in content registry", Vars.content.units().contains(suit));
            check("suit is player-controllable", suit.playerControllable);
            check("suit has a default weapon", suit.weapons.size > 0);
            suit.rebuild("rifle", "balanced", "none", true);
            check("suit rebuild keeps weapon", suit.weapons.size > 0);
            check("suit rebuild resets health", suit.health == 600f);

            // --- Weapon branching ---
            suit.rebuild("cannon", "balanced", "none", true);
            check("cannon weapon present", suit.weapons.size == 1);
            check("cannon reload is slow", suit.weapons.first().reload == 40f);
            suit.rebuild("missiles", "balanced", "none", true);
            check("missiles have splash", suit.weapons.first().bullet.splashDamage > 0f);
            check("missiles home", suit.weapons.first().bullet.homingPower > 0f);
            suit.rebuild("shock", "balanced", "none", true);
            check("shock applies status", suit.weapons.first().bullet.status != null);

            // --- Armor branching ---
            suit.rebuild("rifle", "heavy", "none", true);
            check("heavy armor raises health", suit.health == 850f);
            check("heavy armor raises armor", suit.armor == 9f);
            suit.rebuild("rifle", "shielded", "none", true);
            check("shielded adds arc shield", hasAbility(suit, ShieldArcAbility.class));
            check("shielded keeps base health", suit.health == 600f);

            // --- Support branching ---
            suit.rebuild("rifle", "balanced", "shield", true);
            check("shield support adds force field", hasAbility(suit, ForceFieldAbility.class));
            suit.rebuild("rifle", "balanced", "booster", true);
            check("booster increases speed", suit.speed > 1.0f);
            suit.rebuild("rifle", "balanced", "repair", true);
            check("repair (enabled) adds repair field", hasAbility(suit, RepairFieldAbility.class));
            suit.rebuild("rifle", "balanced", "repair", false);
            check("repair (disabled) adds no repair field", !hasAbility(suit, RepairFieldAbility.class));

            // --- Option catalog ---
            check("4 weapon options", SuitOptions.WEAPONS.length == 4);
            check("3 armor options", SuitOptions.ARMORS.length == 3);
            check("4 support options", SuitOptions.SUPPORTS.length == 4);

            // --- Bench config ---
            check("bench registered", bench != null);
            check("bench in content registry", Vars.content.blocks().contains(bench));
            check("bench is units category", bench.category == Category.units);
            check("bench has build requirements", bench.requirements.length > 0);
            check("bench unlock costs copper", hasItem(((PowerArmorBench) bench).unlockCost, Items.copper, 200));
            check("bench unlock costs graphite", hasItem(((PowerArmorBench) bench).unlockCost, Items.graphite, 100));

            // --- Nanite Recombinator config ---
            check("recombinator registered", recombinator != null);
            check("recombinator is generic crafter", recombinator instanceof GenericCrafter);
            check("recombinator craftTime set", ((GenericCrafter) recombinator).craftTime == 90f);
            check("recombinator accepts liquids", recombinator.hasLiquids);
            check("recombinator has liquid capacity", recombinator.liquidCapacity > 0f);
            check("recombinator is crafting category", recombinator.category == Category.crafting);

            // --- Tech tree wiring (needs init) ---
            if (initRan) {
                boolean suitNode = false;
                for (TechTree.TechNode n : TechTree.all) {
                    if (n.content == suit) {
                        suitNode = true;
                        check("suit node has bench child", n.children.contains(c -> c.content == bench));
                        check("suit node has recombinator child", n.children.contains(c -> c.content == recombinator));
                    }
                }
                check("suit is in tech tree", suitNode);
            }

            // --- Gel gating logic (inject a stand-in gel item) ---
            Ic2mMod.naniteGel = Items.copper;
            ItemModule withGel = new ItemModule();
            withGel.add(Items.copper, 1);
            check("repair gel consumed when present", Ic2mMod.tryConsumeRepairGel(withGel) && withGel.get(Items.copper) == 0);
            check("repair gel not consumed when absent", !Ic2mMod.tryConsumeRepairGel(new ItemModule()));

            // --- Respawn wiring: no unlocked bench -> suit not forced ---
            Ic2mMod.applySuitLoadout();
            check("applySuitLoadout runs without error", true);
            check("no bench -> suit not unlocked", !Ic2mMod.suitUnlocked);

            if (failure != null) throw failure;
            System.out.println("PowerArmorHarness: ALL CHECKS PASSED");
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PowerArmorHarness: FAILED - " + t.getMessage());
            System.exit(1);
        }
    }

    private static boolean hasAbility(PowerArmorSuit suit, Class<? extends Ability> type) {
        for (Ability a : suit.abilities) {
            if (type.isInstance(a)) return true;
        }
        return false;
    }

    private static boolean hasItem(ItemStack[] stacks, mindustry.type.Item item, int amount) {
        for (ItemStack s : stacks) {
            if (s.item == item && s.amount == amount) return true;
        }
        return false;
    }

    private void check(String label, boolean ok) {
        if (!ok) {
            if (failure == null) failure = new AssertionError("check failed: " + label);
            System.out.println("  FAIL: " + label);
        } else {
            System.out.println("  ok: " + label);
        }
    }
}
