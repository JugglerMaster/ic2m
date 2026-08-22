# Plan: UU-Matter Fabrication Chain (IC2-style, 3 machines)

## Goal
Bring IndustrialCraft 2's UU-Matter chain to IC2M as **three distinct machines**:
1. **Scanner** — scans blocks to record replication patterns.
2. **UU-Matter Generator** — converts IC2 EU into UU-Matter.
3. **Fabricator** — consumes UU-Matter (+ EU) and a recorded pattern to replicate
   that item.

This splits IC2's combined Mass/Matter Fabricator into two machines for clearer
tile roles, and drops the standalone Pattern Storage block (patterns live on the
Scanner; the Fabricator reads them).

## IC2 reference (for behavior, not shape)
- Scanner records a block's pattern into its own memory; the pattern is reusable.
- Mass/Matter Fabricator: EU -> UU-Matter (no pattern); UU-Matter + EU -> item
  (with pattern). We separate these into Generator + Fabricator.

## New items (`content/items/*.hjson`)
- `uu-matter` — base matter (pale green/white). `alwaysUnlocked: false` (made in
  the Generator). Reference: `content/items/nanite-gel.hjson`.
- Patterns are **data, not items** (see Pattern model) — no per-pattern HJSON.

## New blocks

### 1. Scanner (`Ic2Scanner` extends `Ic2PowerBlock`)
- `buildConfiguration` UI: pick a target block from a curated **scannable list**
  (IC2M ingots/items + relevant vanilla items), or scan a block placed under it.
- On scan (consume EU over time) it records the pattern into the Scanner's own
  `scannedPatterns` list (a `String[]` of target content names), persisted via
  `write/read`.
- Does **not** output an item; it just unlocks that pattern for Fabricators.
- Scannable list = blocks/items with a meaningful output; curate to avoid blocks
  with no item output.

### 2. UU-Matter Generator (`Ic2UuMatterGenerator` extends `Ic2PowerBlock`)
- The "produce UU matter" machine. Draws EU via `Ic2PowerBuilding.provideEnergy(costPerTick)`.
- While powered: accumulate `progress`; on complete, `storeOutput(uuMatter, 1)`,
  which auto-flushes to adjacent conveyors/inventory (see `Ic2PowerBuilding`
  export logic ~L101).
- Power: `basePowerCapacity` (e.g. 5000 EU), `powerPerTick` tunable (e.g. 50 EU/t).
- `hasItems = false` (it only outputs). Output capacity via `storeOutput`.

### 3. Fabricator (`Ic2Fabricator` extends `Ic2PowerBlock`)
- The "fabricate with UU matter" machine. `hasItems = true`; accepts UU-Matter
  from adjacent conveyors (buffers it).
- `buildConfiguration` UI: lists patterns available from **all Scanners on the
  same team** (enumerated via `Groups.build`, like `findActiveBench` in the suit
  code) and lets the player pick the active pattern. Selected pattern stored in
  the Fabricator's own `Build` (persisted).
- While powered AND holding >= 1 UU-Matter AND a pattern is selected: consume
  UU-Matter + EU, then `storeOutput(targetItem, 1)`. The pattern is **reusable**
  (stays selected; not consumed).
- If no UU-Matter or no pattern: idle.

## Pattern model (handoff without a Pattern Storage block)
- A pattern = target content name (e.g. `content.name` of an `Item`/`Block`).
- Scanner owns the recorded patterns (`String[] scannedPatterns`, persisted).
- Fabricator reads patterns from any Scanner of the same team (tile lookup), so
  no separate storage block is needed. The only physical handoff between machines
  is **UU-Matter as an item** (Generator output -> Fabricator input via conveyors).
- Replication lookup: `Vars.content.getByName(ContentType.item, name)` (or block)
  to resolve the target for `storeOutput`.

## Tech tree
```java
TechTree.nodeRoot("ic2-fabrication", uuMatterGenerator, true, () -> {
    TechTree.node(scanner);
    TechTree.node(fabricator);
});
```

## Sprites
- Add `make_scanner`, `make_uu_matter_generator`, `make_fabricator` to
  `generate_sprites.py`; emit `sprites/blocks/ic2-scanner.png`,
  `ic2-uu-generator.png`, `ic2-fabricator.png`.

## Implementation phases
- **Phase A — UU-Matter generation**: Generator powered by IC2 EU producing
  `uu-matter`. Core loop; testable (item produced when powered).
- **Phase B — Scanning**: Scanner records patterns from selected blocks.
- **Phase C — Replication**: Fabricator consumes UU-Matter + a selected pattern
  to emit the target item.
- **Phase D (optional) — UU-Matter as universal craft input**: route `uu-matter`
  into existing `addAlternateRecipes` as a wildcard ingredient.

## Integration points / existing code to reuse
- Power: `Ic2PowerBlock` / `Ic2PowerBuilding` (`energy`, `maxEnergy`,
  `provideEnergy`, `storeOutput`, `canAcceptEnergy`).
- Tile lookup for patterns: `Groups.build` (mirror `Ic2mMod.findActiveBench`).
- Block UI/config + persistence: `MaceratorBlock`, `PowerArmorBench`
  (`buildConfiguration`, `write/read`, `saveConfig`).
- Items: HJSON pattern (`content/items/nanite-gel.hjson`).
- Sprites: `generate_sprites.py`.
- Content reg + tech tree: `Ic2mMod.loadContent` / `init` (mirror suit
  registration).

## Open questions / risks
- Confirm exact `provideEnergy` / `storeOutput` signatures and the output-flush
  path in `Ic2PowerBuilding` (~L101).
- Fabricator item intake: enable `hasItems`, confirm it can `acceptItem` UU-Matter
  from conveyors and buffer it (vs. needing a `ConsumeItems` like the recombinator).
- Which blocks are "scannable"? Curate a list (all item-producing blocks? vanilla
  + IC2M items). Avoid blocks with no item output.
- Balance: EU cost per UU-Matter, scan time, UU-Matter per replicated item,
  replication EU cost — start conservative, expose as `public` fields.
- Scanner/Fabricator pattern UI: large catalogs need a scrollable/paged config
  table (Mindustry `Table` scroll pane).

## Files to create
- `src/ic2m/Ic2Scanner.java`
- `src/ic2m/Ic2UuMatterGenerator.java`
- `src/ic2m/Ic2Fabricator.java`
- `content/items/uu-matter.hjson`
- sprite functions + PNGs in `generate_sprites.py`

## Files to edit
- `src/ic2m/Ic2mMod.java` (register 3 blocks, tech tree)
- `generate_sprites.py` (sprite functions)
- (optional, Phase D) `src/ic2m/Ic2mMod.java` `addAlternateRecipes`
