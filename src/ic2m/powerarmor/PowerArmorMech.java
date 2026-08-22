package ic2m.powerarmor;

import mindustry.gen.MechUnit;

/** Concrete mech entity for the Power Armor Suit.
 *  MechUnit's no-arg constructor is protected, so we subclass it to obtain a
 *  public constructor usable as the UnitType's entity factory. */
public class PowerArmorMech extends MechUnit {
    public PowerArmorMech() {
        super();
    }
}
