package ic2m;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;

public class Ic2PowerBuilding extends Building {
    public float energy = 0f;
    public float maxEnergy = 100f;

    public float getEnergy() { return energy; }
    public float getMaxEnergy() { return maxEnergy; }
    public float getEnergyPercentage() { return maxEnergy > 0 ? energy / maxEnergy : 0; }

    public boolean canAcceptEnergy() { return true; }
    public boolean canProvideEnergy() { return true; }

    public float acceptEnergy(float amount) {
        if (!canAcceptEnergy()) return 0f;
        float space = maxEnergy - energy;
        float accepted = Math.min(amount, space);
        energy += accepted;
        return amount - accepted;
    }

    public float provideEnergy(float amount) {
        if (!canProvideEnergy()) return 0f;
        float provided = Math.min(amount, energy);
        energy -= provided;
        return provided;
    }

    @Override
    public void update() {
        super.update();
        if (enabled) {
            distributePower();
        }
    }

    protected void distributePower() {
        if (energy <= 0f || !canProvideEnergy()) return;

        int range = 2;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (dx == 0 && dy == 0) continue;
                Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                if (other instanceof Ic2PowerBuilding ic2b && ic2b.canAcceptEnergy() && ic2b.energy < ic2b.maxEnergy) {
                    float space = ic2b.maxEnergy - ic2b.energy;
                    float toSend = Math.min(energy, Math.min(space, 10f));
                    if (toSend > 0f) {
                        ic2b.energy += toSend;
                        energy -= toSend;
                    }
                    if (energy <= 0f) return;
                }
            }
        }
    }

    @Override
    public void draw() {
        super.draw();
        drawEnergyBar();
        drawProgressBar();
    }

    /** Progress between 0 and 1 for crafting machines; 0 for plain power blocks. */
    @Override
    public float progress() { return 0f; }

    protected void drawEnergyBar() {
        drawBar(x, y - block.size * Vars.tilesize / 2f + 3f, getEnergyPercentage(), Pal.powerBar);
    }

    protected void drawProgressBar() {
        float p = progress();
        if (p <= 0f) return;
        drawBar(x, y - block.size * Vars.tilesize / 2f + 5.5f, p, Pal.accent);
    }

    protected void drawBar(float cx, float cy, float fraction, Color color) {
        float size = block.size * Vars.tilesize;
        float w = size - 4f;
        float h = 2.4f;
        float z = Draw.z();
        Draw.z(Layer.power + 1);
        Draw.color(0f, 0f, 0f, 0.7f);
        Fill.rect(cx, cy, w, h);
        Draw.color(color);
        Fill.rect(cx - w / 2f * (1f - fraction), cy, w * fraction, h);
        Draw.color();
        Draw.z(z);
    }

    @Override
    public boolean acceptItem(Building source, Item item) {
        return false;
    }

    @Override
    public void write(Writes write) {
        super.write(write);
        write.f(energy);
        write.f(maxEnergy);
    }

    @Override
    public void read(Reads read, byte revision) {
        super.read(read, revision);
        energy = read.f();
        maxEnergy = read.f();
    }
}
