package ic2m;

import arc.func.Func;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
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

        addBar("ic2energy", (Func<Building, Bar>)entity -> new Bar(
            () -> "IC2 Power " + (int)(getEnergy(entity) * 100f) + "%",
            () -> Pal.powerBar,
            () -> getEnergy(entity)
        ));
    }

    private float getEnergy(Building entity){
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

    /** Placement preview: Mindustry already draws the block ghost, so here we only
     *  add a faint circle covering the adjacent tiles it exchanges EU with. */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        if (this instanceof Ic2CableBlock) return;
        float cx = x * Vars.tilesize + Vars.tilesize * size / 2f;
        float cy = y * Vars.tilesize + Vars.tilesize * size / 2f;
        Draw.color(Pal.accent);
        Lines.stroke(1f);
        Lines.circle(cx, cy, 1.5f * Vars.tilesize);
        Draw.reset();
    }
}
