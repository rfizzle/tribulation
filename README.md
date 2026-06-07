<p align="center">
  <img src="logo.png" alt="Tribulation" width="800">
</p>

<p align="center"><strong>A comprehensive difficulty overhaul for Minecraft 1.21.1 Fabric.</strong></p>

Tribulation transforms Minecraft's flat difficulty curve into an escalating gauntlet. Mobs grow stronger as you play longer, venture further from spawn, and delve deeper underground. Beyond raw stat scaling, the mod layers in tier-gated mob abilities, opt-in permanent death penalties, and inventory risk mechanics — all configurable, all formula-driven, zero external dependencies.

**[Documentation](https://tribulation.rfizzle.com)** | **[Releases](https://github.com/rfizzle/tribulation/releases)**

---

## Core Systems

### Mob Scaling (3 Axes)

Every hostile mob's stats are computed from three independent factors at spawn time:

- **Time** — Your cumulative playtime advances a per-player level (0–250, 1 hour per level). Higher level = stronger mobs near you.
- **Distance** — Beyond 1000 blocks from world spawn, mobs gain bonus health, damage, armor, and toughness (caps at +150%).
- **Height** — Deviation from sea level (Y=62) in either direction adds threat (caps at +50%).

21 vanilla mob types have individually tuned scaling rates and caps. Modded hostile mobs get conservative fallback scaling automatically.

### Tier-Gated Abilities

At 5 level thresholds (50/100/150/200/250), mobs unlock special behaviors:

| Tier | Level | Examples |
|------|-------|----------|
| 1 | 50 | Zombie reinforcements, Creeper shorter fuse |
| 2 | 100 | Skeleton sword switch, Spider web placing |
| 3 | 150 | Zombie door-breaking, Wither Skeleton sprint |
| 4 | 200 | Skeleton flame arrows, Vindicator damage resistance |
| 5 | 250 | Zombie sprinting, Creeper charged (25%), Spider leap attack |

### Death Penalties

- **Death Relief** — Lose 2 levels on death (rubber-band mechanic, configurable)
- **Shatter Shards** — Rare mob drops (0.5%) that reduce your level by 5 on use
- **Hardcore Hearts** *(opt-in)* — Permanently lose max health on each death; restore with Heart Fragments
- **Soul Inventory** *(opt-in)* — Inventory destroyed on death unless items have the Soulbound enchantment

### Additional Features

- Special zombie variants (Big Zombie, Speed Zombie) with distinct stat profiles
- Boss scaling with separate, gentler formula (Ender Dragon, Wither, tagged bosses)
- Bonus XP proportional to mob difficulty (up to 2x)
- Per-mob toggle switches for granular control
- Full compatibility with modded mobs via namespace exclusion and per-entity overrides

---

## Installation

**Requirements:** Minecraft 1.21.1, Fabric Loader 0.16.10+, Fabric API, Java 21

Drop the jar into `mods/` on both server and client. Config generates at `config/tribulation.json` on first launch — tune everything with `/tribulation reload`.

---

## Commands

| Command | Perm | Description |
|---------|------|-------------|
| `/tribulation info` | 0 | Your level, tier, and progress |
| `/tribulation hearts` | 0 | Your heart penalty status |
| `/tribulation set <player> <level>` | 2 | Set a player's level |
| `/tribulation reload` | 2 | Hot-reload config |
| `/tribulation debug <player>` | 2 | Full scaling breakdown |
| `/tribulation inspect` | 2 | Inspect the mob you're looking at |

[Full command reference →](https://tribulation.rfizzle.com/commands.html)

---

## Configuration

Every value is tunable without restart. Key sections: `general`, `timeScaling`, `distanceScaling`, `heightScaling`, `statCaps`, `deathRelief`, `shards`, `hardcoreHearts`, `soulInventory`, `scaling` (per-mob), `bosses`, `tiers`, `abilities`.

[Full config reference →](https://tribulation.rfizzle.com/config.html)

---

## Building from Source

```sh
./gradlew build          # produces build/libs/tribulation-<version>.jar
./gradlew test           # runs unit tests
./gradlew runGametest    # runs Fabric gametest suite
```

### Releasing

```sh
make release BUMP=patch  # bumps version, tags, and pushes (triggers CI release)
make release BUMP=minor NO_PUSH=1  # local only
```

---

## Migrating from Other Mods

If you previously used a mob scaling mod, remove it before installing Tribulation. This mod provides a unified system with formula-driven scaling, per-player progression, and opt-in death penalty mechanics in a single jar with zero external dependencies.

---

## For Mod Developers

Tribulation provides a stable, read-only API and a level-change event for other mods to integrate with. You can use it as a soft dependency by compiling against it with `modCompileOnly` and guarding calls with `FabricLoader.isModLoaded("tribulation")`.

### Gradle Setup
```gradle
dependencies {
    modCompileOnly "maven.modrinth:tribulation:<version>"
}
```

### Usage Examples

**Reading Player Level:**
```java
if (FabricLoader.getInstance().isModLoaded("tribulation")) {
    int level = com.rfizzle.tribulation.api.TribulationAPI.getLevel(serverPlayer);
    int tier = com.rfizzle.tribulation.api.TribulationAPI.getTier(serverPlayer);
}
```

**Listening for Level Changes:**
```java
if (FabricLoader.getInstance().isModLoaded("tribulation")) {
    com.rfizzle.tribulation.api.TribulationLevelCallback.EVENT.register((player, oldLevel, newLevel) -> {
        player.sendMessage(Text.of("Your level changed from " + oldLevel + " to " + newLevel + "!"), false);
    });
}
```

**Safe Client-Side Access:**
```java
// Safe to call on client; returns -1 if unknown or mod not present
int clientLevel = com.rfizzle.tribulation.api.TribulationAPI.getClientLevel();
```

**Checking Mob Scaling State:**
```java
if (FabricLoader.getInstance().isModLoaded("tribulation")) {
    // Returns the tier (0-5) the mob was scaled to at spawn
    OptionalInt tier = com.rfizzle.tribulation.api.TribulationAPI.getScaledTier(mob);
    boolean wasScaled = com.rfizzle.tribulation.api.TribulationAPI.wasScaledByTribulation(mob);
}
```

**Overriding Armor Drop Chance:**
```java
if (FabricLoader.getInstance().isModLoaded("tribulation")) {
    // Hook armor drops for integration with loot mods
    com.rfizzle.tribulation.api.TribulationAPI.setArmorDropChanceProvider((mob, tier, slot, stack, defaultChance) -> {
        return 2.0f; // Force 100% drop chance (Mob.PRESERVE_ITEM_DROP_CHANCE)
    });
}
```

---

## License

MIT
