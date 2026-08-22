package ic2m.powerarmor;

import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Sounds;
import mindustry.type.Weapon;
import mindustry.type.UnitType;
import ic2m.powerarmor.PowerArmorMech;

/** Player-controllable power armor mech. The player "becomes" this unit on respawn
 *  (via CoreBlock.unitType) once the Power Armor Bench is unlocked. Stats are
 *  recomputed from the chosen loadout via {@link #rebuild(String, String, String)}. */
public class PowerArmorSuit extends UnitType {
    public PowerArmorSuit(String name) {
        super(name);
        constructor = PowerArmorMech::new;
        playerControllable = true;
        // The piloted suit keeps full player capabilities.
        buildSpeed = 1f;
        // Jet-assisted power armor: can boost into the air while piloted.
        canBoost = true;
        boostMultiplier = 1.5f;

        health = 600f;
        armor = 3f;
        speed = 0.9f;
        hitSize = 9f;
        rotateSpeed = 6f;
        baseRotateSpeed = 6f;
        drag = 0.4f;
        accel = 0.6f;
        mineSpeed = 1f;
        buildRange = 220f;
        drawCell = true;
        drawItems = true;

        weapons.add(defaultWeapon());
    }

    private Weapon defaultWeapon() {
        BulletType shot = new BasicBulletType(3.5f, 16f, "bullet");
        shot.lifetime = 35f;
        shot.collidesTiles = true;

        Weapon w = new Weapon(name + "-blaster");
        w.bullet = shot;
        w.x = 0f;
        w.y = 4f;
        w.reload = 22f;
        w.recoil = 1f;
        w.shootSound = Sounds.shoot;
        w.inaccuracy = 2f;
        return w;
    }

    /** Recompute the suit from the chosen loadout (single-select per category).
     *  @param repairEnabled when false, the "repair" support option is suppressed
     *                       for this life (Nanite Gel not available at respawn). */
    public void rebuild(String weaponId, String armorId, String supportId, boolean repairEnabled) {
        weapons.clear();
        abilities.clear();
        health = 600f;
        armor = 3f;
        speed = 0.9f;
        buildSpeed = 1f;

        applyArmor(armorId);
        weapons.add(SuitOptions.makeWeapon(weaponId, name));
        applySupport(supportId, repairEnabled);
        if (armorId.equals("shielded")) {
            abilities.add(SuitOptions.shieldedArc());
        }
    }

    private void applyArmor(String id) {
        SuitOptions.applyArmor(this, id);
    }

    private void applySupport(String id, boolean repairEnabled) {
        SuitOptions.applySupport(this, id, repairEnabled);
    }
}
