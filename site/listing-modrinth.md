<!--
Modrinth draft: slug `tribulation-difficulty-overhaul`, project ID `8KuQhMGI`
(see concord VISION.md §4). The listing copy below is final and ready to paste.

PUBLISH-TIME TODO (requires the live Modrinth project — cannot be done in-repo):
  1. Upload the 128×128 icon (art/icon-128.png).
  2. Upload a gallery: the full logo plus 3–5 in-game screenshots.
  3. Once the project is public, swap the canonical download link from GitHub
     Releases to modrinth.com/mod/tribulation-difficulty-overhaul, and add the
     img.shields.io/modrinth/dt/8KuQhMGI download badge.
-->

# Tribulation — Difficulty Overhaul

**_Survive what comes next._**

**Also on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tribulation-difficulty-overhaul)
and [GitHub Releases](https://github.com/rfizzle/tribulation/releases).**
Visit the [website](https://tribulation.rfizzle.com) for the full feature
list, config reference, and command guide.

---

Tribulation is a difficulty overhaul for **Minecraft 1.21.1 (Fabric)**. It
transforms Minecraft's flat difficulty curve into an escalating gauntlet: the
longer you play, the further you explore, and the deeper you dig — the harder
the world fights back. Every system is formula-driven, fully configurable,
and persists across server restarts.

**Vanilla+ by design.** No custom mobs, no new dimensions — vanilla mobs,
vanilla mechanics, sharpened. Zero external dependencies beyond Fabric API.

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16.10+), **Fabric API** required.
- Required on the **server**; install client-side too for the level badge HUD.
- Every system is **individually toggleable** through Mod Menu / Cloth Config
  or `config/tribulation.json` — hot-reload with `/tribulation reload`.
- MIT licensed.

## Features

### Four-Axis Mob Scaling

Mob stats are computed from four independent factors the moment a mob
spawns:

| Axis | How It Works | Cap |
|------|-------------|-----|
| **Time** | Your cumulative playtime advances a per-player difficulty level (0–250). Higher level = stronger mobs near you. ~1 level per hour of play. | Level 250 |
| **Distance** | Beyond 1,000 blocks from world spawn, mobs gain bonus health, damage, armor, and toughness. The frontier is dangerous. | +150% |
| **Height** | Deviation from sea level (Y=62) in either direction adds threat. Deep caves and tall mountains are deadlier. | +50% |
| **Moon Phase** | On Overworld nights, a triangle curve peaks at the full moon and tapers to zero at the new moon — the same coordinates feel different week to week. | +10% |

All four axes stack. On top of them, a flat per-dimension level offset keeps
the Nether (+25) and End (+40) scarier than the late-game Overworld, lifting
both mob stats and ability tiers. 21 vanilla mob types have individually tuned
scaling rates and caps — zombies don't scale the same as skeletons or
creepers. Modded hostile mobs are automatically supported with conservative
fallback scaling (configurable per-namespace or per-entity).

In multiplayer, choose which player level drives a nearby mob — the
**nearest**, the **average**, or the **maximum** of the players in range.

### Tier-Gated Mob Abilities

At 5 level thresholds (50/100/150/200/250), mobs unlock new behaviors that
change how you fight them:

- **Tier 1 (50)** — Zombies call reinforcements, creepers fuse in 0.75s,
  hoglins shrug off knockback
- **Tier 2 (100)** — Skeletons trade bows for swords, spiders snare you in
  webs, drowned carry tridents, piglins pick up crossbows
- **Tier 3 (150)** — Zombies break doors, spiders trample crops, wither
  skeletons sprint, zoglins walk through fire
- **Tier 4 (200)** — Skeleton arrows burn, husks inflict Hunger II,
  vindicators resist damage, wither skeleton blades gain Fire Aspect
- **Tier 5 (250)** — Zombies sprint, 25% of creepers spawn charged, spiders
  leap, zombified piglins aggro from far away

Every individual ability can be toggled in the config.

### Tier-Driven Equipment

Mobs spawn with armor and weapons that scale with their tier — leather and
gold at low tiers shifting toward iron, diamond, and netherite at the top,
with tier-capped Protection/Sharpness/Power enchantments. A clear threat
signal before you even engage.

### Special Zombie Variants

Zombie-family mobs (Zombie, Husk, Drowned, Zombified Piglin) can roll one of
two variants on top of normal scaling:

- **Big Zombie** (10%) — 1.3× size, +10 health, +2 damage, 0.7× speed. A
  lumbering tank.
- **Speed Zombie** (10%) — 1.3× movement speed, −10 health. Closes gaps fast.

### Special Skeleton Variants

Skeleton-family mobs (Skeleton, Stray, Bogged) can roll one of two variants on
top of normal scaling:

- **Deadeye Skeleton** (10%) — faster bow draw, −10 health. A glass-cannon
  archer.
- **Brute Skeleton** (10%) — +10 health, 0.5 knockback resistance, 1.3× size,
  slower bow draw. A heavy, hard-to-stagger archer.

### Boss Scaling

The Ender Dragon, Wither, Elder Guardian, and anything tagged `c:bosses` scale
with a separate, gentler formula — health and damage only, capped at +300%. No
sudden one-shots; endgame bosses just stay relevant.

### Raid & Patrol Scaling

Vanilla's own escalation system scales with you, driven by the tier of the
targeted players — while bells, Hero of the Village, and the raid bar all
behave normally.

- **Bigger patrols** — a pillager patrol captain adds extra members as your
  tier climbs, each spawned through the normal scaling path with
  tier-appropriate stats, armor, and weapons.
- **Extra raid waves** — raids targeting high-tier players run additional
  waves on top of the vanilla count.

### Death Penalty Systems

All penalties are independently toggleable. Mix and match the stakes you
want:

- **Death Relief** — lose 2 levels on death (rubber-band mechanic,
  cooldown-gated)
- **Shatter Shards** — rare drops (0.5%) that lower your level by 5, at the
  cost of 10s of Slowness, Mining Fatigue, and Weakness
- **Hardcore Hearts** *(opt-in)* — permanently lose max health per death;
  restore with craftable Heart Fragments
- **Soul Inventory** *(opt-in)* — items are destroyed on death unless they
  carry the Soulbound enchantment

A popped **Totem of Undying** can optionally interact with these penalties via
the `totems` config: have a totem pop still apply the Death Relief level loss
(`countsAsDeathRelief`), or shield you from the Hardcore Hearts loss
(`protectsHearts`).

### Rewards Scale Too

- **Bonus XP** — scaled mobs drop up to 2× base XP, proportional to their
  scaling factor
- **Extra loot** *(opt-in)* — a chance to duplicate a drop, scaling with mob
  difficulty

### Level Badge HUD

A compact, icon-only badge shows your current tier at a glance — tinted
white through yellow, orange, and red to dark crimson across tiers, with a
thin progress bar toward your next level. Flashes gold on level-up. Anchor
to any screen corner; stacks cleanly with HUDs from the other Concord suite
mods.

### Difficulty Statistics

Six custom statistics track your difficulty milestones — highest level
reached, levels lost to death relief, Shatter Shards used, half-hearts lost,
half-hearts restored, and Tier-5 mobs killed. They live alongside vanilla's
counters in the **Statistics → Custom** screen, so progress persists across
sessions.

## Commands

Player commands: `/tribulation info`, `/tribulation hearts`. Operator
commands cover level set/reset, hot-reload, and the `debug`/`inspect`
scaling-breakdown tools. Full reference:
[tribulation.rfizzle.com/commands.html](https://tribulation.rfizzle.com/commands.html)

## Optional integrations

Tribulation detects and integrates with these mods when present. **None are
bundled** — install whichever you already use.

- [Mod Menu](https://modrinth.com/mod/modmenu) — config screen entry
- [Cloth Config](https://modrinth.com/mod/cloth-config) — settings GUI
- [Jade](https://modrinth.com/mod/jade) / [WTHIT](https://modrinth.com/mod/wthit)
  — mob scaling tooltip overlays
- [EMI](https://modrinth.com/mod/emi) / [REI](https://modrinth.com/mod/rei) /
  [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — Shatter Shard and
  Heart Fragment recipes

For mod developers: a stable, read-only API
(`com.rfizzle.tribulation.api`) exposes player level/tier, mob scaling
state, and a level-change event — see the
[developer docs](https://tribulation.rfizzle.com/developers.html).

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.16.10+**
- Fabric API
- Java **21+**
- Works on **dedicated servers and singleplayer**

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for 1.21.1.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods/`
   folder.
3. Download Tribulation and place it into `mods/` as well — on both server
   and client.
4. *(Optional)* Add Mod Menu and Cloth Config for the in-game settings
   screen.

Config generates at `config/tribulation.json` on first launch.

## Links

- **Website:** <https://tribulation.rfizzle.com>
- **GitHub Releases (canonical downloads):** <https://github.com/rfizzle/tribulation/releases>
- **CurseForge:** <https://www.curseforge.com/minecraft/mc-mods/tribulation-difficulty-overhaul>
- **GitHub:** <https://github.com/rfizzle/tribulation>
- **Report an issue:** <https://github.com/rfizzle/tribulation/issues>
- **Changelog:** <https://tribulation.rfizzle.com/changelog.html>

## Companion mods

Tribulation is part of [Concord](https://github.com/rfizzle/concord) — a
Vanilla+ collection. Install any, combine all:

- [Meridian](https://meridian.rfizzle.com) — Chart your enchantments.
- [Mercantile](https://mercantile.rfizzle.com) — Every villager remembers.
- [Prosperity](https://prosperity.rfizzle.com) — Every chest, yours to discover.

## License & credits

Licensed under the [MIT License](https://github.com/rfizzle/tribulation/blob/master/LICENSE).
© 2026 rfizzle. Tribulation is not affiliated with Mojang Studios or
Microsoft.
