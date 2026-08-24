package ic2m;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.ui.Styles;
import mindustry.graphics.Pal;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.util.ArrayList;

/** Consumes itself to merge a 2x2 of same-type IC2 machines into one larger (multiblock) upgraded block.
 *  Requires the pattern to be formed, the per-block item requirements to be fed in, and a one-time EU charge. */
public class Ic2UpgradeNodeBlock extends Ic2PowerBlock {
    public static final float TIER2_COST = 108000f;
    public static final float TIER3_COST = 1080000f;
    public static final int MERGED = 3;

    public Ic2UpgradeNodeBlock(String name) {
        super(name);
        basePowerCapacity = 500000f;
        hasItems = true;
        itemCapacity = 2000;
        solid = true;
        update = true;
        configurable = true;
        saveConfig = true;
    }

    public class Ic2UpgradeNodeBuild extends Ic2PowerBuilding {
        private int detectedTier = 0;
        private Building sampleBuilding;
        private String sampleName;
        private int cornerX, cornerY;
        private final int[] ghostX = new int[3];
        private final int[] ghostY = new int[3];

        @Override
        public byte version() { return 1; }

        @Override
        protected boolean readsOutputState(byte revision) { return false; }

        @Override
        protected boolean writesOutputState() { return false; }

        @Override
        public boolean canAcceptEnergy() { return true; }

        @Override
        public boolean canProvideEnergy() { return false; }

        @Override
        public void created() {
            super.created();
            maxEnergy = basePowerCapacity;
        }

        @Override
        public void update() {
            super.update();
            detectedTier = detect();
            if (detectedTier != 0) {
                ItemStack[] reqs = requirements(detectedTier);
                if (hasItems(reqs) && energy >= cost(detectedTier)) {
                    performMerge(detectedTier);
                    return;
                }
            }
        }

        private int detect() {
            int t3 = matchTier(3);
            if (t3 != 0) return t3;
            return matchTier(2);
        }

        private int matchTier(int tier) {
            int s = 1 << (tier - 1);
            int slot = 1 << (tier - 2);
            int tx = tile.x, ty = tile.y;
            for (int cx : new int[]{ tx, tx - (s - 1) }) {
                for (int cy : new int[]{ ty, ty - (s - 1) }) {
                    int lx = tx - cx, ly = ty - cy;
                    int qx = lx / slot, qy = ly / slot;
                    boolean ok = true;
                    Building sample = null;
                    int gi = 0;
                    for (int dx = 0; dx < 2 && ok; dx++) {
                        for (int dy = 0; dy < 2 && ok; dy++) {
                            if (dx == qx && dy == qy) continue;
                            Building b = Vars.world.build(cx + dx * slot, cy + dy * slot);
                            if (!(b instanceof Ic2PowerBuilding pb)) { ok = false; break; }
                            if (b.block.size != slot) { ok = false; break; }
                            if (pb.upgradeTier != tier - 2) { ok = false; break; }
                            if (sample == null) {
                                sample = b;
                                ghostX[gi] = cx + dx * slot;
                                ghostY[gi] = cy + dy * slot;
                                gi++;
                            } else if (b.block != sample.block) {
                                ok = false;
                                break;
                            }
                        }
                    }
                    if (ok && sample != null) {
                        detectedTier = tier;
                        sampleBuilding = sample;
                        sampleName = sample.block.name;
                        cornerX = cx;
                        cornerY = cy;
                        return tier;
                    }
                }
            }
            return 0;
        }

        private ItemStack[] requirements(int tier) {
            if (sampleBuilding == null) return new ItemStack[0];
            ItemStack[] base = ((Ic2PowerBuilding) sampleBuilding).upgradeRequirements(tier);
            ArrayList<ItemStack> list = new ArrayList<>();
            for (ItemStack s : base) list.add(new ItemStack(s.item, s.amount * MERGED));
            return list.toArray(new ItemStack[0]);
        }

        private boolean hasItems(ItemStack[] reqs) {
            for (ItemStack s : reqs) if (items.get(s.item) < s.amount) return false;
            return true;
        }

        private float cost(int tier) {
            return tier == 2 ? TIER2_COST : TIER3_COST;
        }

        private int displayTier() {
            return detectedTier != 0 ? detectedTier : 2;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.total() >= itemCapacity) return false;
            for (ItemStack s : requirements(displayTier())) {
                if (s.item == item && items.get(item) < s.amount) return true;
            }
            return false;
        }

        private void performMerge(int tier) {
            Block target = Vars.content.block(sampleName + "-" + tier);
            if (target == null) return;
            int s = 1 << (tier - 1);
            for (int i = 0; i < s; i++) {
                for (int j = 0; j < s; j++) {
                    Tile t = Vars.world.tile(cornerX + i, cornerY + j);
                    if (t != null && t.build != null) t.remove();
                }
            }
            Tile corner = Vars.world.tile(cornerX, cornerY);
            if (corner != null) corner.setBlock(target, team, 0);
        }

        @Override
        public void draw() {
            super.draw();
            if (detectedTier != 0 && sampleName != null) {
                Block target = Vars.content.block(sampleName + "-" + detectedTier);
                if (target != null) {
                    int slot = 1 << (detectedTier - 2);
                    Draw.alpha(0.35f);
                    for (int i = 0; i < 3; i++) {
                        float cx = (ghostX[i] + slot / 2f) * Vars.tilesize;
                        float cy = (ghostY[i] + slot / 2f) * Vars.tilesize;
                        Draw.rect(target.fullIcon, cx, cy, slot * Vars.tilesize, slot * Vars.tilesize);
                    }
                    Draw.alpha(1f);
                }
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            int tier = displayTier();

            table.table(Styles.black3, p -> {
                p.margin(10f);
                if (detectedTier != 0) {
                    p.add("Pattern valid - upgrading to Tier " + tier).color(Pal.accent).left().row();
                } else {
                    p.add("No 2x2 pattern yet").color(Pal.remove).left().row();
                    p.add("Place this node at a corner of 4 same-tier machines.").color(Color.gray).left().row();
                }

                ItemStack[] reqs = requirements(tier);
                if (reqs.length == 0) {
                    p.add("Required items: none (EU charge only)").left().row();
                } else {
                    for (ItemStack s : reqs) {
                        float have = items.get(s.item);
                        float frac = Math.min(1f, have / (float) s.amount);
                        p.table(r -> {
                            r.left();
                            r.add(new Image(s.item.uiIcon)).size(20).padRight(4);
                            r.add(s.item.localizedName).left().growX();
                            r.add((int) have + "/" + s.amount).right();
                        }).fillX().padBottom(4f).row();
                        p.add(new Bar("", Pal.accent, () -> frac)).fillX().height(4f).padTop(2f).padBottom(8f).row();
                    }
                }

                float eFrac = Math.min(1f, energy / cost(tier));
                p.add("EU charge: " + formatEU(energy) + "/" + formatEU(cost(tier))).left().padTop(6f).padBottom(4f).row();
                p.add(new Bar("", Pal.powerBar, () -> eFrac)).fillX().height(4f).row();
            }).fillX();
        }
    }
}
