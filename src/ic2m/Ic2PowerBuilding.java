package ic2m;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.Core;
import arc.graphics.g2d.Lines;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.io.Reads;
import arc.util.io.Writes;
import arc.struct.IntSeq;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Tile;


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

    /** Side (0=right,1=up,2=left,3=down) this block outputs EU to; -1 = any side. Used by batteries. */
    public int outputRotation = -1;

    /** Persistent links discovered when this block was placed (vanilla-style: each block links to its
     *  single nearest compatible power block within linkRange). Power flows only along these links. */
    public IntSeq links = new IntSeq();

    /** Set once auto-linking has run so it is not repeated on every proximity update / load. */
    public boolean autoLinked = false;

    /** Unit offsets for each rotation index: 0=right, 1=up, 2=left, 3=down. */
    protected static final int[][] D4 = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    public float getEnergy() { return energy; }
    public float getMaxEnergy() { return maxEnergy; }
    public float getEnergyPercentage() { return maxEnergy > 0 ? energy / maxEnergy : 0; }

    /** Operating voltage tier for connection rules. Machines derive it from their merge tier
     *  (0 = LV / T1, 1 = MV / T2, 2 = HV / T3); cables and nodes override to report powerTier. */
    public int voltageTier() { return upgradeTier; }

    /** Inherent tier from the block's name suffix (-2 -> 1 / T2, -3 -> 2 / T3; base is 0 / T1).
     *  The hjson 'baseTier' field is not applied by the content loader, so tiers are read from
     *  the block name to keep connections and capacities correct. */
    public int inherentTier() {
        if (block.name.endsWith("-3")) return 2;
        if (block.name.endsWith("-2")) return 1;
        return 0;
    }

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

    /** True for blocks that only produce EU (e.g. solar panels) so cables accept them. */
    protected boolean isGenerator() { return false; }

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
        return canAcceptEnergy();
    }

    /** Whether this block may push EU out to the given neighbour; batteries restrict to one side. */
    protected boolean canOutputTo(Building other) {
        return true;
    }

    /** Default output side for this block type; -1 = none (omnidirectional). Batteries/transformers override to a side. */
    protected int defaultOutputRotation() { return -1; }

    @Override
    public void created() {
        super.created();
        outputRotation = defaultOutputRotation();
        baseTier = inherentTier();
    }

    /** Connection reach used when auto-linking on placement. Cables override to their nodeRange so a
     *  cable line can span gaps; machines default to adjacent (1). */
    protected int linkRange() { return 1; }

    @Override
    public void onProximityAdded() {
        super.onProximityAdded();
        if (autoLinked || !links.isEmpty()) return;
        autoLinked = true;
        // Power nodes and transformers keep their own connection logic.
        if (this instanceof Ic2PowerNodeBlock.Ic2PowerNodeBuild
            || this instanceof Ic2TransformerBlock.Ic2TransformerBuild) return;

        int r = linkRange();
        boolean cable = this instanceof Ic2CableBlock.Ic2CableBuild;

        if (cable) {
            // Cables connect to their single nearest compatible block (vanilla-style), so a cable
            // run does not mesh-link to every nearby cable.
            Ic2PowerBuilding nearest = null;
            float best = Float.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0 || dx * dx + dy * dy > r * r) continue;
                    Building o = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (!(o instanceof Ic2PowerBuilding cb) || cb == this) continue;
                    if (cb instanceof Ic2PowerNodeBlock.Ic2PowerNodeBuild
                        || cb instanceof Ic2TransformerBlock.Ic2TransformerBuild) continue;
                    if (!canConnectEnergy(o)) continue;
                    float d = dx * dx + dy * dy;
                    if (d < best) { best = d; nearest = cb; }
                }
            }
            if (nearest != null) {
                links.add(nearest.pos());
                if (!nearest.links.contains(pos())) nearest.links.add(pos());
            }
        } else {
            // Machines, batteries and generators link to EVERY adjacent compatible block, so a
            // machine placed next to other machines or a source is connected on all sides instead
            // of only to whichever neighbour happened to be closest.
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0 || dx * dx + dy * dy > r * r) continue;
                    Building o = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (!(o instanceof Ic2PowerBuilding cb) || cb == this) continue;
                    if (cb instanceof Ic2PowerNodeBlock.Ic2PowerNodeBuild
                        || cb instanceof Ic2TransformerBlock.Ic2TransformerBuild) continue;
                    if (!canConnectEnergy(o)) continue;
                    // Consumers (machines, upgrade nodes) may only connect through cables, never
                    // directly to another block, so power is always routed via the cable network.
                    boolean otherCable = cb instanceof Ic2CableBlock.Ic2CableBuild;
                    if ((!canProvideEnergy() && !otherCable) || !cb.canProvideEnergy()) continue;
                    if (!links.contains(cb.pos())) links.add(cb.pos());
                    if (!cb.links.contains(pos())) cb.links.add(pos());
                }
            }
        }
    }

    /** True for blocks that persistently render their link lines; power nodes and transformers keep
     *  their own selection-only overlay instead. */
    protected boolean drawsLinks() {
        return !(this instanceof Ic2PowerNodeBlock.Ic2PowerNodeBuild
            || this instanceof Ic2TransformerBlock.Ic2TransformerBuild);
    }

    /** Link line colour for this block's voltage tier. */
    protected Color linkColor() { return Pal.accent; }

    /** Draws one line per established link. */
    protected void drawLinks() {
        if (links.isEmpty()) return;
        Draw.z(Layer.power + 1f);
        Draw.color(linkColor());
        Lines.stroke(1f);
        for (int i = 0; i < links.size; i++) {
            Building b = Vars.world.build(links.get(i));
            if (b == null || b == this) continue;
            Lines.line(x, y, b.x, b.y);
        }
        Draw.reset();
    }

    @Override
    public void onRemoved() {
        for (int i = 0; i < links.size; i++) {
            Building b = Vars.world.build(links.get(i));
            if (b instanceof Ic2PowerBuilding pb && pb.links.contains(pos())) pb.links.removeValue(pos());
        }
        links.clear();
        super.onRemoved();
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

        for (int i = 0; i < links.size; i++) {
            Building b = Vars.world.build(links.get(i));
            if (!(b instanceof Ic2PowerBuilding target) || !canConnectEnergy(b)
                || !target.acceptsFrom(this) || !canOutputTo(b) || target.energy >= target.maxEnergy) continue;
            float space = target.maxEnergy - target.energy;
            float toSend = Math.min(energy, Math.min(space, powerTransferRate()));
            if (toSend > 0f) {
                float remainder = target.acceptEnergy(toSend);
                energy -= toSend - remainder;
            }
            if (energy <= 0f) return;
        }
    }

    protected boolean canConnectEnergy(Building other) {
        if (!(other instanceof Ic2PowerBuilding o)) return false;
        // Transformers bridge voltage tiers; two transformers never link to each other.
        boolean thisXf = this instanceof Ic2TransformerBlock.Ic2TransformerBuild;
        boolean otherXf = o instanceof Ic2TransformerBlock.Ic2TransformerBuild;
        if (thisXf && otherXf) return false;
        if (thisXf || otherXf) return true;
        // Otherwise only same-voltage blocks connect (LV<->LV, MV<->MV, HV<->HV).
        return voltageTier() == o.voltageTier();
    }

    @Override
    public void draw() {
        super.draw();
        drawEnergyBar();
        drawProgressBar();
        if (drawsLinks()) drawLinks();
    }

    /** Progress between 0 and 1 for crafting machines; 0 for plain power blocks. */
    @Override
    public float progress() { return 0f; }

    @Override
    public void drawSelect() {
        super.drawSelect();
        drawRange();
        if (!drawsLinks()) drawLinks();
        drawOutputLink();
    }

    /** Red line from this block to its configured output side (batteries). */
    protected void drawOutputLink() {
        if (outputRotation < 0) return;
        float tx = (tile.x + D4[outputRotation][0]) * Vars.tilesize + Vars.tilesize / 2f;
        float ty = (tile.y + D4[outputRotation][1]) * Vars.tilesize + Vars.tilesize / 2f;
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
            Draw.color(cableBlock.linkColor());
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
        // Floating EU fill bar for any block that actually stores power (batteries, machines,
        // generators with a buffer). Pass-through generators (solar, maxEnergy 0) and cables are
        // excluded so cable runs stay clean.
        if (maxEnergy <= 0f || this instanceof Ic2CableBlock.Ic2CableBuild) return;
        float half = block.size * Vars.tilesize / 2f;
        float barW = half;            // narrower bar hugging the right half
        float cx = x + half / 2f;     // bottom-right
        float cy = y - half + 2f;     // near the bottom edge
        drawBar(cx, cy, barW, getEnergyPercentage(), Pal.powerBar);

        // Numeric readout only while the player is hovering the block.
        Tile hovered = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        if (hovered != null && hovered.build == this) {
            float ty = y + half + 3f;
            Drawf.text(formatEU(energy) + " / " + formatEU(maxEnergy) + " EU", x, ty, Color.white, 1.5f, Align.center);
        }
    }

    protected void drawProgressBar() {
        float p = progress();
        if (p <= 0f) return;
        drawBar(x, y - block.size * Vars.tilesize / 2f + 5.5f, p, Pal.accent);
    }

    protected void drawBar(float cx, float cy, float fraction, Color color) {
        drawBar(cx, cy, block.size * Vars.tilesize - 4f, fraction, color);
    }

    protected void drawBar(float cx, float cy, float w, float fraction, Color color) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        float h = 3f;
        float z = Draw.z();
        Draw.z(Layer.power + 1);
        Draw.color(0f, 0f, 0f, 0.7f);
        Fill.rect(cx, cy, w, h);
        Draw.color(color);
        float fillW = w * fraction;
        // Left-anchored within the background so it grows rightward and never
        // overflows the sprite.
        Fill.rect(cx - w / 2f + fillW / 2f, cy, fillW, h);
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
        write.i(links.size);
        for (int i = 0; i < links.size; i++) write.i(links.get(i));
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
        int count = read.i();
        links.clear();
        for (int i = 0; i < count; i++) links.add(read.i());
        autoLinked = true;
    }
}
