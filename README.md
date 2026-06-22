<p align="center">
  <img src="art/logo.png" alt="Tribulation" width="800">
</p>

<p align="center"><strong>Survive what comes next.</strong></p>

<p align="center">
  <a href="https://www.minecraft.net/"><img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white"></a>
  <a href="https://fabricmc.net/"><img alt="Fabric" src="https://img.shields.io/badge/Mod_Loader-Fabric-DBB69B"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/github/license/rfizzle/tribulation"></a>
  <a href="https://github.com/rfizzle/tribulation/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/rfizzle/tribulation?include_prereleases"></a>
  <a href="https://github.com/rfizzle/tribulation/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/rfizzle/tribulation/actions/workflows/ci.yml/badge.svg"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/tribulation-difficulty-overhaul"><img alt="CurseForge downloads" src="https://img.shields.io/curseforge/dt/1546072?logo=curseforge&label=CurseForge"></a>
</p>

A comprehensive difficulty overhaul for Minecraft 1.21.1 (Fabric). Tribulation transforms Minecraft's flat difficulty curve into an escalating gauntlet. Mobs grow stronger as you play longer, venture further from spawn, and delve deeper underground. Beyond raw stat scaling, the mod layers in tier-gated mob abilities, opt-in permanent death penalties, and inventory risk mechanics — all configurable, all formula-driven, zero external dependencies.

## Download

| [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tribulation-difficulty-overhaul) | [GitHub Releases](https://github.com/rfizzle/tribulation/releases) | [Website](https://tribulation.rfizzle.com) | [Report an issue](https://github.com/rfizzle/tribulation/issues) |
| --- | --- | --- | --- |

---

## Core Systems

### Mob Scaling (4 Axes)

Every hostile mob's stats are computed from four independent factors at spawn time:

- **Time** — Your cumulative playtime advances a per-player level (0–250, 1 hour per level). Higher level = stronger mobs near you.
- **Distance** — Beyond 1000 blocks from world spawn, mobs gain bonus health, damage, armor, and toughness (caps at +150%).
- **Height** — Deviation from sea level (Y=62) in either direction adds threat (caps at +50%).
- **Moon phase** — On Overworld nights, mob threat rises toward the full moon and tapers to zero at the new moon (caps at +10%); optionally surface-only.

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
- **Totem interaction** — Configure how a popped Totem of Undying interacts with the penalties via the `totems` section: `countsAsDeathRelief` (whether a totem pop still applies the Death Relief level loss) and `protectsHearts` (whether a totem pop shields you from the Hardcore Hearts loss)

### Statistics

Tribulation registers six custom statistics (highest level reached, levels lost to death relief, Shatter Shards used, half-hearts lost, half-hearts restored, and Tier-5 scaled mobs killed). View them in-game from the vanilla **Statistics** screen under *Custom*.

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
| `/tribulation info` | 0 | Show your own level, tier, and progress to next level |
| `/tribulation hearts` | 0 | Show your own heart penalty (Hardcore Hearts) |
| `/tribulation level <player>` | 2 | View another player's level and time to next |
| `/tribulation set <player> <level>` | 2 | Set a player's difficulty level (clamped to maxLevel) |
| `/tribulation reset <player>` | 2 | Reset a player to level 0 |
| `/tribulation reload` | 2 | Hot-reload the config file without restarting |
| `/tribulation config` | 2 | Print a summary of the current configuration |
| `/tribulation debug <player>` | 2 | Show detailed scaling breakdown for a player's position |
| `/tribulation inspect` | 2 | Inspect the mob you're looking at (within 10 blocks) |
| `/tribulation hearts <player>` | 2 | View another player's heart penalty |
| `/tribulation hearts <player> restore <n>` | 2 | Restore lost half-hearts for a player |
| `/tribulation hearts <player> reset` | 2 | Clear all heart penalties for a player |
| `/tribulation inventory <player>` | 2 | Count soulbound items in a player's inventory |

[Full command reference →](https://tribulation.rfizzle.com/commands.html)

---

## Configuration

Every value is tunable without restart. Key sections: `general`, `timeScaling`, `distanceScaling`, `heightScaling`, `moonPhaseScaling`, `statCaps`, `totems`, `deathRelief`, `shards`, `hardcoreHearts`, `soulInventory`, `scaling` (per-mob), `unlistedHostileMobs`, `specialZombies`, `bosses`, `xpAndLoot`, `tiers`, `mobToggles`, `abilities`, `armorEquipment`, `weaponEquipment`, `hud`.

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

## Part of Concord

Part of [Concord](https://github.com/rfizzle/concord) — a Vanilla+ collection.
Install any, combine all.

- [Meridian](https://meridian.rfizzle.com) — Chart your enchantments.
- [Mercantile](https://mercantile.rfizzle.com) — Every villager remembers.
- [Prosperity](https://prosperity.rfizzle.com) — Every chest, yours to discover.

---

## License

Licensed under the [MIT License](LICENSE). © 2026 rfizzle. Tribulation is not
affiliated with Mojang Studios or Microsoft.
