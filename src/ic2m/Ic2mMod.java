package ic2m;

import arc.Events;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.TechTree;
import mindustry.content.UnitTypes;
import mindustry.entities.abilities.Ability;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.mod.Mod;
import mindustry.ctype.ContentType;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.consumers.ConsumeItemFilter;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.modules.ItemModule;
import ic2m.powerarmor.PowerArmorBench;
import ic2m.powerarmor.PowerArmorSuit;

public class Ic2mMod extends Mod {
    /** The shared power armor suit unit type. */
    public static PowerArmorSuit powerArmorSuit;
    public static PowerArmorBench powerArmorBench;
    public static NaniteRecombinator naniteRecombinator;

    /** Nanite Gel item (resolved after the HJSON content is loaded). */
    public static Item naniteGel;
    public static final int REPAIR_GEL_COST = 1;

    /** Jetpack tech item (resolved after the HJSON content is loaded). Gating the
     *  "jetpack" mobility option behind its research node. */
    public static Item jetpackItem;

    /** Active loadout (mirrors the unlocked bench's saved config). */
    public static boolean suitUnlocked = false;
    public static String wId = "rifle";
    public static String aId = "balanced";
    public static String sId = "none";
    public static String mId = "ground";

    public Ic2mMod() {
    }

    @Override
    public void loadContent() {
        addAlternateRecipes();

        powerArmorSuit = new PowerArmorSuit("ic2m-power-armor");
        Vars.content.handleContent(powerArmorSuit);

        powerArmorBench = new PowerArmorBench("ic2m-power-armor-bench");
        Vars.content.handleContent(powerArmorBench);

        naniteRecombinator = new NaniteRecombinator("ic2m-nanite-recombinator");
        Vars.content.handleContent(naniteRecombinator);

        // Resolve the jetpack tech item (loaded from HJSON content) so the
        // "jetpack" mobility option can be gated behind its research node.
        jetpackItem = Vars.content.getByName(ContentType.item, "ic2m-jetpack");

        // Re-apply the respawn unit (core unitType) whenever a world (re)loads,
        // using the last unlocked bench config.
        Events.on(EventType.WorldLoadEvent.class, e -> applySuitLoadout());

        // Per-respawn: re-apply the loadout and enforce Nanite Gel gating for repair.
        Events.on(EventType.UnitSpawnEvent.class, e -> {
            Unit u = e.unit;
            if (u != null && u.isPlayer() && u.type == powerArmorSuit) {
                applySuitLoadoutRespawn(u);
            }
        });
    }

    @Override
    public void init() {
        if (powerArmorBench == null) return;

        // Own tech-tree branch. Researching the suit unlocks bench + recombinator,
        // and the recombinator gates the jetpack flight module.
        TechTree.nodeRoot("ic2m-power-armor", powerArmorSuit, true, () -> {
            TechTree.node(powerArmorBench);
            if (naniteRecombinator != null) {
                TechTree.node(naniteRecombinator, ItemStack.with(Items.copper, 100), () -> {
                    if (jetpackItem != null) {
                        TechTree.node(jetpackItem, ItemStack.with(Items.surgeAlloy, 50, Items.titanium, 50), () -> {});
                    }
                });
            }
        });

        // Wire up the recombinator's output now that the Nanite Gel item exists.
        resolveNaniteGel();
        if (naniteGel != null && naniteRecombinator != null) {
            naniteRecombinator.outputItems = ItemStack.with(naniteGel, 1);
        }
    }

    /** Apply the active loadout for the player's team: find that team's unlocked
     *  bench (if any), make every core spawn the player as the suit, and rebuild
     *  the shared suit. Repair is assumed available here (the respawn handler
     *  re-applies with per-respawn gel gating). Defaults to the local player's
     *  team; pass {@code null} to search every team (used headlessly). */
    public static void applySuitLoadout() {
        applySuitLoadout(Vars.player != null ? Vars.player.team() : null);
    }

    /** @param team the team whose bench should drive the respawn unit, or
     *              {@code null} to accept any active bench. */
    public static void applySuitLoadout(Team team) {
        if (powerArmorSuit == null) return;
        PowerArmorBench.PowerArmorBenchBuild bench =
            team != null ? findActiveBench(team) : findActiveBench();
        if (bench == null) {
            suitUnlocked = false;
            setCoreUnitType(UnitTypes.alpha);
            return;
        }
        suitUnlocked = true;
        setCoreUnitType(powerArmorSuit);
        powerArmorSuit.rebuild(bench.weaponId, bench.armorId, bench.supportId, bench.mobilityId, true);
    }

    /** Apply the loadout at a player's respawn. The freshly spawned suit unit is
     *  already initialized from the shared suit (weapons/abilities/health), so we
     *  only enforce the per-respawn Nanite Gel gate for the "repair" support here.
     *  Teams without an active bench that were nonetheless forced into the suit by
     *  the global core unitType have their suit perks stripped. */
    private static void applySuitLoadoutRespawn(Unit u) {
        if (powerArmorSuit == null || u == null) return;
        PowerArmorBench.PowerArmorBenchBuild bench = findActiveBench(u.team);

        if (bench == null) {
            if (u.type() == powerArmorSuit) stripSuitPerks(u);
            return;
        }

        if (bench.supportId.equals("repair")) {
            ItemModule inv = bench.team.core() != null ? bench.team.core().items : null;
            if (!tryConsumeRepairGel(inv)) {
                u.abilities = withoutRepair(u.abilities);
            }
        }

        u.maxHealth = powerArmorSuit.health;
        u.health = u.maxHealth;
    }

    /** Returns true (and consumes {@link #REPAIR_GEL_COST} Nanite Gel from the core)
     *  when the core can afford it; false otherwise. Used to gate the "repair"
     *  support option per-respawn. Package-visible so the harness can unit-test it. */
    static boolean tryConsumeRepairGel(ItemModule inv) {
        resolveNaniteGel();
        if (naniteGel == null) return false;
        if (inv == null || inv.get(naniteGel) < REPAIR_GEL_COST) return false;
        inv.remove(naniteGel, REPAIR_GEL_COST);
        return true;
    }

    /** Drop the regenerating repair field from a spawned unit (gel unavailable). */
    private static Ability[] withoutRepair(Ability[] src) {
        int kept = 0;
        for (Ability a : src) if (!(a instanceof RepairFieldAbility)) kept++;
        if (kept == src.length) return src;
        Ability[] out = new Ability[kept];
        int i = 0;
        for (Ability a : src) if (!(a instanceof RepairFieldAbility)) out[i++] = a;
        return out;
    }

    /** Remove the overpowered suit loadout from a unit that spawned as the suit but
     *  belongs to a team with no active bench (global core unitType leakage). */
    private static void stripSuitPerks(Unit u) {
        u.abilities = new Ability[0];
        u.maxHealth = 600f;
        u.health = u.maxHealth;
    }

    private static void setCoreUnitType(UnitType t) {
        for (Block b : Vars.content.blocks()) {
            if (b instanceof CoreBlock cb) cb.unitType = t;
        }
    }

    private static PowerArmorBench.PowerArmorBenchBuild findActiveBench() {
        if (Groups.build == null) return null;
        for (Building b : Groups.build) {
            if (b instanceof PowerArmorBench.PowerArmorBenchBuild pb && pb.unlocked && pb.enabled) return pb;
        }
        return null;
    }

    private static PowerArmorBench.PowerArmorBenchBuild findActiveBench(mindustry.game.Team team) {
        if (Groups.build == null) return null;
        for (Building b : Groups.build) {
            if (b instanceof PowerArmorBench.PowerArmorBenchBuild pb && pb.unlocked && pb.enabled && b.team == team) return pb;
        }
        return null;
    }

    private static void resolveNaniteGel() {
        if (naniteGel == null) {
            naniteGel = Vars.content.getByName(ContentType.item, "nanite-gel");
        }
    }

    /** True once the jetpack tech node has been researched for the current team.
     *  Public so {@code PowerArmorSuit.applyMobility} can gate flight. */
    public static boolean jetpackUnlocked() {
        return jetpackItem != null && jetpackItem.unlockedNow();
    }

    private void addAlternateRecipes() {
        Item copperIngot = findItem("copper-ingot");
        Item leadIngot = findItem("lead-ingot");
        Item titaniumIngot = findItem("titanium-ingot");
        Item coalIngot = findItem("coal-ingot");

        if (copperIngot == null || leadIngot == null || titaniumIngot == null || coalIngot == null) return;

        // These are alternatives, not replacements: the vanilla ingredient remains valid.
        replaceItemRecipes(Blocks.siliconSmelter,
            new Item[]{Items.copper, Items.coal}, new Item[]{copperIngot, coalIngot});
        replaceItemRecipes(Blocks.kiln,
            new Item[]{Items.lead}, new Item[]{leadIngot});
        replaceItemRecipes(Blocks.graphitePress,
            new Item[]{Items.coal}, new Item[]{coalIngot});
        replaceItemRecipes(Blocks.surgeSmelter,
            new Item[]{Items.titanium}, new Item[]{titaniumIngot});
    }

    private Item findItem(String suffix) {
        return Vars.content.items().find(item -> item.name.endsWith("-" + suffix));
    }

    private void replaceItemRecipes(Block block, Item[] vanilla, Item[] alternate) {
        block.removeConsumers(consume -> consume instanceof ConsumeItems);
        for (int i = 0; i < vanilla.length; i++) {
            Item normal = vanilla[i];
            Item replacement = alternate[i];
            block.consume(new ConsumeItemFilter(item -> item == normal || item == replacement));
        }
        block.reinitializeConsumers();
    }
}
