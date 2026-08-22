# Plan: Mass Fabricator & UU-Matter (IC2 replication chain)

## Goal
Bring IndustrialCraft 2's mass-fabrication chain to IC2M: **scan** blocks to
record patterns, **generate UU-Matter** from IC2 EU, and **replicate** scanned
items. This mirrors IC2's Mass Fabricator + Scanner + pattern storage.

## Reference (IC2 behavior)
- **Mass Fabricator**: consumes EU, slowly produces UU-Matter. If a pattern
  (scanned item) is loaded, it produces that item instead of UU-Matter (still
  EU-driven).
- **Scanner**: records the pattern of a block/item, producing a scannable
  pattern.
- Patterns are stored and selected; the fabricator replicates the chosen pattern.

## New items (`content/items/*.hjson`)
- `uu-matter` — base matter (pale green/white). `alwaysUnlocked: false` (made in
  the fabricator). Reference: `content/items/nanite-gel.hjson`.
- No per-pattern items — patterns are *data* referencing existing content.

## New blocks

### 1. Mass Fabricator (`Ic2MassFabricator` extends `Ic2PowerBlock`)
- Draws EU from the IC2 power network via `Ic2PowerBuilding.provideEnergy(costPerTick)`
  (buffer `energy` / `maxEnergy`).
- While powered: accumulate `progress`; on complete, `storeOutput(uuMatter, 1)`
  (or replicate the selected pattern). `storeOutput` auto-flushes to adjacent
  conveyors/inventory (see `Ic2PowerBuilding` export logic ~L101).
- `buildConfiguration` UI: output mode = **UU-Matter** (default) or a selected
  pattern (from Pattern Storage / internal scanned slots). With a pattern
  selected, emits the target item instead of UU-Matter, consuming its EU cost.
- Power: `basePowerCapacity` (e.g. 5000 EU buffer), `powerPerTick` tunable
  (e.g. 50 EU/t). Expose as `public` fields for balancing.

### 2. Scanner (`Ic2Scanner` extends `Ic2PowerBlock`)
- `buildConfiguration`: pick a target block type from a curated scannable list
  (or scan a block placed under it). On scan (consume EU + time) it writes a
  pattern into the linked Pattern Storage (or an internal slot).
- Scannable list = blocks/items with a meaningful output (IC2M ingots/items +
  relevant vanilla items). Curate to avoid blocks with no item output.
- Result: a pattern entry (target content name) stored in Pattern Storage.

### 3. Pattern Storage (`Ic2PatternStorage` extends `Ic2PowerBlock`, optional but recommended)
- Multi-slot storage of scanned patterns (each slot = one target content name).
- UI: list scanned patterns; select which is "active" for the fabricator.
- Can be merged into the fabricator (internal scanned slots) for a smaller
  footprint; start separate for clarity.

## Pattern data model
- A pattern = identifier of the target content (e.g. `content.name` of an
  `Item`/`Block`). Stored as `String[]` in the building, persisted via
  `write/read` (mirror `PowerArmorBench` persistence).
- Replication: fabricator looks up the target via `Vars.content.getByName(...)`
  and outputs it. No new HJSON needed.

## Tech tree
```java
TechTree.nodeRoot("ic2-fabrication", massFabricator, true, () -> {
    TechTree.node(scanner);
    TechTree.node(patternStorage);
});
```

## Sprites
- Add `make_mass_fabricator`, `make_scanner`, `make_pattern_storage` to
  `generate_sprites.py`; emit `sprites/blocks/ic2-mass-fabricator.png`, etc.

## Implementation phases
- **Phase A — UU-Matter generation**: Mass Fabricator powered by IC2 EU
  producing `uu-matter`. Core loop; testable (item produced when powered).
- **Phase B — Scanning**: Scanner + Pattern Storage record patterns.
- **Phase C — Replication**: Fabricator uses a stored pattern to emit the target
  item instead of UU-Matter.
- **Phase D (optional) — UU-Matter as universal craft input**: route `uu-matter`
  into existing `addAlternateRecipes` as a wildcard ingredient.

## Integration points / existing code to reuse
- Power: `Ic2PowerBlock` / `Ic2PowerBuilding` (`energy`, `maxEnergy`,
  `provideEnergy`, `storeOutput`, `canAcceptEnergy`).
- Block UI/config: `MaceratorBlock`, `PowerArmorBench`
  (`buildConfiguration`, `write/read`, `saveConfig`).
- Items: HJSON pattern (`content/items/nanite-gel.hjson`).
- Sprites: `generate_sprites.py`.
- Content reg + tech tree: `Ic2mMod.loadContent` / `init` (mirror suit
  registration).

## Open questions / risks
- Confirm exact `provideEnergy` / `storeOutput` signatures and the output-flush
  path in `Ic2PowerBuilding` (~L101).
- Which blocks are "scannable"? Curate a list (all item-producing blocks? vanilla
  + IC2M items). Avoid blocks with no item output.
- Balance: EU cost per UU-Matter, scan time, replication cost — start
  conservative, expose as `public` fields.
- Pattern UI: large catalogs need a scrollable/paged config table (Mindustry
  `Table` scroll pane).

## Files to create
- `src/ic2m/Ic2MassFabricator.java`
- `src/ic2m/Ic2Scanner.java`
- `src/ic2m/Ic2PatternStorage.java`
- `content/items/uu-matter.hjson`
- sprite functions + PNGs in `generate_sprites.py`

## Files to edit
- `src/ic2m/Ic2mMod.java` (register content, tech tree)
- `generate_sprites.py` (sprite functions)
- (optional, Phase D) `src/ic2m/Ic2mMod.java` `addAlternateRecipes`
