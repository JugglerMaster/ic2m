package ic2m;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;


public class Ic2PowerBuilding extends Building {
    public float energy = 0f;
    public float maxEnergy = 100f;
    public Item pendingOutput;
    public int pendingOutputAmount;
    public int outputCapacity = 4;

    /** Tier this block was placed at (0 = base). Set from the block's baseTier on creation. */
    public int upgradeTier = 0;
    /** Tier baked into the block definition (read from hjson). */
    public int baseTier = 0;

    /** Direction (Geometry.d4 index: 0=right,1=up,2=left,3=down) this block accepts EU from; -1 = any side. */
    public int inputRotation = -1;

    /** Unit offsets for each rotation index: 0=right, 1=up, 2=left, 3=down. */
    private static final int[][] D4 = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    public float getEnergy() { return energy; }
    public float getMaxEnergy() { return maxEnergy; }
    public float getEnergyPercentage() { return maxEnergy > 0 ? energy / maxEnergy : 0; }

    /** Items the upgrade node must be fed to merge this block to the given user tier (2 or 3). */
    public ItemStack[] upgradeRequirements(int tier) {
        return new ItemStack[0];
    }

    /** Speed/output multiplier applied per upgrade tier (T1=1, T2=1.6, T3=2.4). */
    public static float tierMultiplier(int tier) {
        return tier == 0 ? 1f : tier == 1 ? 1.6f : 2.4f;
    }

    /** Resolves a content item by name suffix (e.g. "copper-ingot"). */
    protected Item resolveItem(String suffix) {
        return Vars.content.items().find(i -> i.name.endsWith("-" + suffix));
    }

    /** Shared base materials consumed by every IC2 upgrade node, scaled per user tier (1x at T2, 10x at T3). */
    protected ItemStack[] baseRequirements(int tier) {
        int scale = tier == 2 ? 1 : 10;
        return new ItemStack[]{
            new ItemStack(resolveItem("copper-ingot"), 1600 * scale),
            new ItemStack(resolveItem("lead-ingot"), 800 * scale),
            new ItemStack(resolveItem("graphite-ingot"), 600 * scale)
        };
    }

    /** Base materials for the tier plus a single alloy ingot requirement. */
    protected ItemStack[] withAlloy(int tier, Item alloy, int amount) {
        ItemStack[] base = baseRequirements(tier);
        ItemStack[] out = new ItemStack[base.length + 1];
        System.arraycopy(base, 0, out, 0, base.length);
        out[base.length] = new ItemStack(alloy, amount);
        return out;
    }

    public boolean canAcceptEnergy() { return true; }
    public boolean canProvideEnergy() { return true; }

    /** Per-tick EU a block may push to one neighbour; scales 10x8^tier so big buffers can actually fill. */
    protected float powerTransferRate() {
        return upgradeTier == 0 ? 32f : upgradeTier == 1 ? 128f : 512f;
    }

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

    /** Whether this block will accept EU pushed from the given (provider) building. */
    public boolean acceptsFrom(Building source) {
        if (!canAcceptEnergy()) return false;
        if (inputRotation < 0) return true;
        int sx = source.tile.x - tile.x;
        int sy = source.tile.y - tile.y;
        return D4[inputRotation][0] == sx && D4[inputRotation][1] == sy;
    }

    public void cycleInput() {
        inputRotation = inputRotation >= 3 ? -1 : inputRotation + 1;
    }

    protected String inputDirName(int r) {
        return r < 0 ? "any side" : r == 0 ? "right" : r == 1 ? "up" : r == 2 ? "left" : "down";
    }

    /** Adds a "set input side" control to a block's config panel (no-op for non-acceptors like solar). */
    protected void addInputControl(Table table) {
        if (!canAcceptEnergy()) return;
        TextButton btn = new TextButton("", Styles.defaultt);
        btn.update(() -> btn.setText("Input side: " + inputDirName(inputRotation) + "   (tap to change)"));
        btn.clicked(() -> cycleInput());
        table.row();
        table.add(btn).size(280f, 44f);
    }

    /** Pushes `amount` EU directly into neighbouring acceptors without storing it (used by solar panels). */
    protected void injectPower(float amount) {
        if (amount <= 0f) return;
        int range = 1;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (dx == 0 && dy == 0) continue;
                Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                if (other instanceof Ic2PowerBuilding target && canConnectEnergy(other) && target.acceptsFrom(this)) {
                    float space = target.maxEnergy - target.energy;
                    float toSend = Math.min(amount, space);
                    if (toSend > 0f) {
                        float remainder = target.acceptEnergy(toSend);
                        amount -= toSend - remainder;
                    }
                    if (amount <= 0f) return;
                }
            }
        }
    }

    /** Consumes up to `needed` EU for one craft tick; returns the progress increment (penalized when starved). */
    protected float consumePower(float needed) {
        float have = Math.min(energy, needed);
        energy -= have;
        if (have >= needed - 1e-4f) return 1f;
        if (have <= 1e-4f) return 0f;
        return (have / needed) * 0.5f;
    }

    protected boolean storeOutput(Item item, int amount) {
        if (pendingOutput != null && pendingOutput != item) return false;
        if (pendingOutputAmount + amount > outputCapacity) return false;
        pendingOutput = item;
        pendingOutputAmount += amount;
        return true;
    }

    protected boolean canStoreOutput(Item item, int amount) {
        if (pendingOutput != null && pendingOutput != item) return false;
        return pendingOutputAmount + amount <= outputCapacity;
    }

    protected void flushOutput() {
        if (pendingOutput == null || pendingOutputAmount <= 0) return;
        for (Building target : proximity) {
            while (pendingOutputAmount > 0 && target.acceptItem(this, pendingOutput)
                && canDump(target, pendingOutput)) {
                target.handleItem(this, pendingOutput);
                pendingOutputAmount--;
            }
            if (pendingOutputAmount <= 0) {
                pendingOutput = null;
                return;
            }
        }
    }

    public String outputStatus() {
        return pendingOutput == null ? "Output: empty" : "Output: " + pendingOutputAmount + "/" + outputCapacity
            + " " + pendingOutput.localizedName;
    }

    protected boolean readsOutputState(byte revision) {
        return revision >= 1;
    }

    protected boolean writesOutputState() {
        return true;
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

        int range = 1;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (dx == 0 && dy == 0) continue;
                Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                if (other instanceof Ic2PowerBuilding ic2b && canConnectEnergy(other)
                    && ic2b.acceptsFrom(this) && ic2b.energy < ic2b.maxEnergy) {
                    float space = ic2b.maxEnergy - ic2b.energy;
                    float toSend = Math.min(energy, Math.min(space, powerTransferRate()));
                    if (toSend > 0f) {
                        float remainder = ic2b.acceptEnergy(toSend);
                        energy -= toSend - remainder;
                    }
                    if (energy <= 0f) return;
                }
            }
        }
    }

    protected boolean canConnectEnergy(Building other) {
        if (!(other instanceof Ic2PowerBuilding)) return false;
        boolean thisCable = this instanceof Ic2CableBlock.Ic2CableBuild;
        boolean otherCable = other instanceof Ic2CableBlock.Ic2CableBuild;
        if (thisCable && !otherCable) {
            if (other instanceof Ic2TransformerBlock.Ic2TransformerBuild) return true;
            if (other instanceof Ic2PowerNodeBlock.Ic2PowerNodeBuild) return true;
            return false;
        }
        if (!thisCable && otherCable) {
            if (this instanceof Ic2TransformerBlock.Ic2TransformerBuild) return true;
            if (this instanceof Ic2PowerNodeBlock.Ic2PowerNodeBuild) return true;
            return false;
        }
        return true;
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

    @Override
    public void drawSelect() {
        super.drawSelect();
        drawRange();
        if (!(this instanceof Ic2CableBlock.Ic2CableBuild)) {
            drawConnections();
        }
        drawInputLink();
    }

    /** Red line from this block to the tile it accepts EU from (its configured input side). */
    protected void drawInputLink() {
        if (inputRotation < 0) return;
        float tx = (tile.x + D4[inputRotation][0]) * Vars.tilesize + Vars.tilesize / 2f;
        float ty = (tile.y + D4[inputRotation][1]) * Vars.tilesize + Vars.tilesize / 2f;
        Draw.z(Layer.power + 1f);
        Draw.color(Pal.remove);
        Lines.stroke(2f);
        Lines.line(x, y, tx, ty);
        Draw.reset();
    }

    /** Draws the tile area this building can exchange EU across; circle for cables, square for LV blocks. */
    protected void drawRange() {
        if (this instanceof Ic2CableBlock.Ic2CableBuild cable) {
            Ic2CableBlock cableBlock = (Ic2CableBlock) cable.block;
            float r = cableBlock.nodeRange * Vars.tilesize;
            Draw.color(cableBlock.highVoltage ? Pal.powerLight : Pal.accent);
            Lines.stroke(1.5f);
            Lines.circle(x, y, r);
            Draw.reset();
        }
    }

    /** Selection-only overlay showing the live LV power mesh to neighbouring IC2 blocks. */
    protected void drawConnections() {
        int range = 2;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (dx == 0 && dy == 0) continue;
                Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                if (other == null || other == this) continue;
                if (!canConnectEnergy(other)) continue;
                if (other instanceof Ic2PowerBuilding opb && !opb.canAcceptEnergy()) continue;
                if (other.pos() < pos()) continue;
                Lines.stroke(1f);
                Draw.color(Pal.accent);
                Lines.line(x, y, other.x, other.y);
                Draw.reset();
            }
        }
    }

    /** Compact EU label, e.g. 1200 -> "1.2k", 1080000 -> "1.1M". */
    protected String formatEU(float value) {
        if (value >= 1_000_000f) return String.format("%.1fM", value / 1_000_000f);
        if (value >= 1_000f) return String.format("%.1fk", value / 1_000f);
        return String.valueOf((int) value);
    }

    protected void drawEnergyBar() {
        boolean hv = this instanceof Ic2CableBlock.Ic2CableBuild cable && ((Ic2CableBlock) cable.block).highVoltage;
        drawBar(x, y, getEnergyPercentage(), hv ? Pal.powerLight : Pal.powerBar);
    }

    protected void drawProgressBar() {
        float p = progress();
        if (p <= 0f) return;
        drawBar(x, y - block.size * Vars.tilesize / 2f + 5.5f, p, Pal.accent);
    }

    protected void drawBar(float cx, float cy, float fraction, Color color) {
        float size = block.size * Vars.tilesize;
        float w = size - 4f;
        float h = 3f;
        float z = Draw.z();
        Draw.z(Layer.power + 1);
        Draw.color(0f, 0f, 0f, 0.7f);
        Fill.rect(cx, cy, w, h);
        Draw.color(color);
        Fill.rect(cx - w / 2f, cy, w * fraction, h);
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
        if (writesOutputState()) {
            write.i(pendingOutput == null ? -1 : pendingOutput.id);
            write.i(pendingOutputAmount);
        }
    }

    @Override
    public void read(Reads read, byte revision) {
        super.read(read, revision);
        energy = read.f();
        maxEnergy = read.f();
        if (readsOutputState(revision)) {
            int outputId = read.i();
            pendingOutput = outputId >= 0 ? Vars.content.item(outputId) : null;
            pendingOutputAmount = read.i();
        }
    }
}
