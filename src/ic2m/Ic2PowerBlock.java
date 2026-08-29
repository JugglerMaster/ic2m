package ic2m;

import arc.func.Func;
import mindustry.ui.Bar;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Stat;

public class Ic2PowerBlock extends Block {
    public float basePowerCapacity = 100f;

    public Ic2PowerBlock(String name) {
        super(name);
        group = BlockGroup.power;
        update = true;
        solid = true;
        hasItems = false;
    }

    protected float getEnergy(Building entity){
        return entity instanceof Ic2PowerBuilding ic2b ? ic2b.getEnergyPercentage() : 0f;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.powerCapacity, "[orange]@[] EU", (int)statsCapacity());
        stats.add(Stat.powerRange, statsRange());
    }

    protected float statsCapacity(){
        return basePowerCapacity;
    }

    protected int statsRange(){
        return 2;
    }
}
