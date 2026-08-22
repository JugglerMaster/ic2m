package ic2m.powerarmor;

import mindustry.content.StatusEffects;
import mindustry.entities.abilities.ForceFieldAbility;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.entities.abilities.ShieldArcAbility;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/** Catalog of modular Power Armor options and the code that turns a chosen
 *  (weapon, armor, support) loadout into concrete suit stats. Single-select per
 *  category: exactly one weapon, one armor, one support is active. */
public class SuitOptions {
    public static final String[] WEAPONS = {"rifle", "cannon", "missiles", "shock"};
    public static final String[] ARMORS = {"balanced", "heavy", "shielded"};
    public static final String[] SUPPORTS = {"none", "booster", "shield", "repair"};

    private SuitOptions() {}

    public static Weapon makeWeapon(String id, String suitName) {
        BulletType b;
        switch (id) {
            case "cannon" -> {
                b = new BasicBulletType(4f, 55f, "bullet");
                b.lifetime = 40f;
                b.knockback = 5f;
                b.collidesTiles = true;
                b.ammoMultiplier = 1;
            }
            case "missiles" -> {
                b = new BasicBulletType(3f, 22f, "bullet");
                b.lifetime = 55f;
                b.homingPower = 0.15f;
                b.homingRange = 140f;
                b.splashDamage = 18f;
                b.splashDamageRadius = 24f;
                b.collidesTiles = true;
            }
            case "shock" -> {
                b = new BasicBulletType(5f, 14f, "bullet");
                b.lifetime = 30f;
                b.status = StatusEffects.shocked;
                b.statusDuration = 45f;
                b.collidesTiles = true;
            }
            default -> { // rifle
                b = new BasicBulletType(3.5f, 16f, "bullet");
                b.lifetime = 35f;
                b.collidesTiles = true;
            }
        }

        Weapon w = new Weapon(suitName + "-" + id);
        w.bullet = b;
        w.x = 0f;
        w.y = 4f;
        w.reload = id.equals("cannon") ? 40f : id.equals("missiles") ? 30f : 22f;
        w.recoil = 1f;
        w.inaccuracy = id.equals("cannon") ? 4f : 2f;
        return w;
    }

    public static void applyArmor(UnitType t, String id) {
        switch (id) {
            case "heavy" -> {
                t.health = 850f;
                t.armor = 9f;
            }
            case "shielded" -> {
                t.health = 600f;
                t.armor = 4f;
            }
            default -> { // balanced
                t.health = 600f;
                t.armor = 3f;
            }
        }
    }

    /** Adds support abilities to the suit.
     *  @param repairEnabled when false, the "repair" option is suppressed for this
     *                        life (Nanite Gel not available at respawn). */
    public static void applySupport(UnitType t, String id, boolean repairEnabled) {
        switch (id) {
            case "booster" -> {
                t.speed *= 1.35f;
                t.buildSpeed *= 1.5f;
            }
            case "shield" -> {
                t.abilities.add(new ForceFieldAbility(70f, 0.8f, 250f, 45f));
            }
            case "repair" -> {
                if (repairEnabled) {
                    t.abilities.add(new RepairFieldAbility(6f, 30f, 90f));
                }
            }
            default -> { /* none */ }
        }
    }

    /** A regenerating arc shield used by the "shielded" armor type. */
    public static ShieldArcAbility shieldedArc() {
        ShieldArcAbility a = new ShieldArcAbility();
        a.radius = 55f;
        a.regen = 0.4f;
        a.max = 220f;
        a.cooldown = 240f;
        a.angle = 110f;
        a.y = 4f;
        return a;
    }
}
