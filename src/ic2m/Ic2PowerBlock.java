package ic2m;

import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;

public class Ic2PowerBlock extends Block {
    public float basePowerCapacity = 100f;

    public Ic2PowerBlock(String name) {
        super(name);
        group = BlockGroup.power;
        update = true;
        solid = true;
        hasItems = false;
    }
}
