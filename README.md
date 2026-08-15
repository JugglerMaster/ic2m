# IC2 for Mindustry (IC2M)

An Industrial Craft 2-inspired tech mod for Mindustry v8 (build 159).

## What It Does

Adds a **separate power system** (EU) to Mindustry, independent from vanilla power. Build solar panels, store energy, and process ores using IC2-style machines.

## Features

### Custom Power System
- Completely separate from Mindustry's vanilla power network
- Adjacent IC2 blocks share energy within 2 tiles (no cables needed)
- Energy displayed in block UI

### Blocks

| Block | Description |
|---|---|
| **Solar Panel** | Generates EU from sunlight. Upgradeable with raw resources. |
| **RE Battery** | Stores EU. Passes energy to adjacent consumers. |
| **Macerator** | Doubles ore output. Turns 1 ore into 2 dusts. |
| **Alloy Furnace** | Combines copper + lead into surge alloy. |

### Solar Panel Upgrades

Pump resources into a solar panel, right-click to upgrade:

| Upgrade | Cost | Effect |
|---|---|---|
| Efficiency | 100 Graphite | +50% EU/tick output |
| Capacity | 100 Lead | +200 EU storage |

Upgrades persist when the panel is moved.

### Items (Dusts)

Copper Dust, Lead Dust, Graphite Dust, Coal Dust, Titanium Dust, Thorium Dust

Produced by the Macerator from ores.

## Installation

1. Download `ic2m.jar` from releases (or build it yourself)
2. Copy to `~/.local/share/Mindustry/mods/` (Linux)
3. Launch Mindustry - mod should appear in mod list

## Building from Source

```bash
./gradlew jar
# Output: build/libs/ic2m.jar
```

Requires Java 17+.

## How to Use

1. Build a **Solar Panel** - it generates EU during daytime
2. Build an **RE Battery** nearby (within 2 tiles) - stores EU
3. Build a **Macerator** nearby - processes ores into dusts
4. Conveyor graphite into the solar panel for efficiency upgrades
5. Right-click the solar panel to access upgrade UI

## Progression

```
Solar Panel → Battery → Macerator → Alloy Furnace
     ↓
  Upgrade with Graphite (efficiency) or Lead (capacity)
```

## Roadmap

- [ ] Tier 2 upgrades (require dusts from macerator)
- [ ] Tier 3 upgrades (late-game resources)
- [ ] Sieve (sand → random ores)
- [ ] Tin ore/item (for bronze alloy recipe)
- [ ] Higher-tier solar panels (Upgraded, Hybrid, Quantum)
- [ ] Higher-tier batteries (BatBox, MFE, MFSU)
- [ ] Electric Furnace
- [ ] Compressor, Extractor
- [ ] Physical cable blocks
- [ ] Tech tree integration

## Tech Stack

- Java 17
- Mindustry v159.7
- Gradle 8.5

## License

TBD
