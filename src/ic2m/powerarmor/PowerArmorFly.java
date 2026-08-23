package ic2m.powerarmor;

import mindustry.gen.UnitEntity;

/** Flying variant of the Power Armor Suit, used by the "jetpack" mobility option.
  * Flight is expressed by the {@code flying} flag on the UnitType; the entity is a
  * generic UnitEntity (same pattern as vanilla flying units). UnitEntity's no-arg
  * constructor is protected, so we subclass it to obtain a public constructor
  * usable as the UnitType's entity factory. */
public class PowerArmorFly extends UnitEntity {
    public PowerArmorFly() {
        super();
    }
}
