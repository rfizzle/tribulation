![Tribulation](https://raw.githubusercontent.com/rfizzle/tribulation/master/logo.png)

**Tribulation** transforms Minecraft's flat difficulty curve into an escalating gauntlet. The longer you play, the further you explore, and the deeper you dig — the harder the world fights back. Every system is formula-driven, fully configurable, and persists across server restarts.

Zero external dependencies. Just drop it in.

---

## Three-Axis Mob Scaling

Mob stats aren't random — they're computed from three independent factors the moment a mob spawns:

| Axis | How It Works | Cap |
|------|-------------|-----|
| **Time** | Your cumulative playtime advances a per-player difficulty level (0–250). Higher level = stronger mobs near you. ~1 level per hour of play. | Level 250 |
| **Distance** | Beyond 1,000 blocks from world spawn, mobs gain bonus health, damage, armor, and toughness. The frontier is dangerous. | +150% |
| **Height** | Deviation from sea level (Y=62) in either direction adds threat. Deep caves and tall mountains are deadlier. | +50% |

All three axes stack. A high-level player fighting a mob 3,000 blocks from spawn at Y=12 faces the full weight of the system.

21 vanilla mob types have individually tuned scaling rates and caps — zombies don't scale the same as skeletons or creepers. Modded hostile mobs are automatically supported with conservative fallback scaling (configurable per-namespace or per-entity).

---

## Tier-Gated Mob Abilities

Raw stats are only half the story. At 5 level thresholds, mobs unlock new behaviors that change how you fight them:

### Tier 1 — Level 50
- Zombies call reinforcements when hit
- Creepers have a shorter fuse (15 ticks)

### Tier 2 — Level 100
- Skeletons switch to swords in melee range
- Spiders place webs to slow you down

### Tier 3 — Level 150
- Zombies break down doors
- Spiders trample crops
- Wither Skeletons sprint

### Tier 4 — Level 200
- Skeletons fire flame arrows
- Vindicators gain damage resistance
- Hoglins and Zoglins get knockback bonuses
- Zombified Piglins aggro from further away

### Tier 5 — Level 250
- Zombies sprint
- Creepers have a 25% chance to spawn charged
- Spiders gain a leap attack

Every individual ability can be toggled on or off in the config.

---

## Special Zombie Variants

Two mutually exclusive zombie variants add unpredictability to encounters:

- **Big Zombie** — Larger, tankier (+10 HP, +0.25 damage), slightly slower. Visually 1.5x scale.
- **Speed Zombie** — Trades health for raw speed (+0.4 movement speed). Closes gaps fast.

Variants apply across the entire zombie family: Zombie, Husk, Drowned, and Zombified Piglin. Their bonuses stack on top of normal scaling.

---

## Special Skeleton Variants

Two mutually exclusive skeleton variants add unpredictability to ranged encounters:

- **Deadeye Skeleton** — Faster bow draw for a shorter gap between shots, traded for −10 HP. A glass-cannon archer.
- **Brute Skeleton** — +10 HP, 0.5 knockback resistance, and 1.3x size, with a slower bow draw. A heavy, hard-to-stagger archer.

Variants apply across the skeleton family: Skeleton, Stray, and Bogged (not Wither Skeleton). Their bonuses stack on top of normal scaling.

---

## Boss Scaling

The Ender Dragon and Wither scale with a separate, gentler formula using only the time and distance axes. No sudden one-shots — just a steady ramp that keeps endgame bosses relevant at higher levels.

Custom bosses can be tagged for the same treatment.

---

## Death Penalty Systems

All penalties are **opt-in** and independently toggleable. Mix and match to set the stakes you want.

### Death Relief
A rubber-band mechanic: lose 2 levels on death (configurable). Prevents infinite difficulty spirals for struggling players. Cooldown-gated to prevent abuse.

### Shatter Shards
Rare drops (0.5% chance) from scaled mobs. Using one reduces your level by 5 — but inflicts Slowness, Mining Fatigue, and Weakness for 10 seconds. A deliberate trade-off, not a free reset.

### Hardcore Hearts
Each death **permanently** reduces your max health by 1 heart. Minimum floor of 1 heart. Restoration is possible through **Heart Fragments**, a craftable recovery item. This system is completely disabled by default.

### Soul Inventory
All items are **destroyed on death** unless enchanted with Soulbound. Experience can be separately configured to survive or perish. Disabled by default — enable it when you want real consequences.

---

## Rewards Scale Too

Harder mobs aren't just punishment. They pay out:

- **Bonus XP** — Scaled mobs drop up to **2x base XP**, proportional to their health scaling factor
- **Extra Loot** — A chance to duplicate a random drop, scaling with mob difficulty (up to 15% base chance)

---

## HUD Overlay

A minimal shield icon and level number in the corner of your screen. Color-coded by tier:

**White** (0) > **Yellow** (1) > **Orange** (2) > **Light Red** (3) > **Red** (4) > **Dark Red** (5)

Flashes gold on level-up. Anchor position and visibility are configurable.

---

## Commands

| Command | Permission | What It Does |
|---------|-----------|-------------|
| `/tribulation info` | Anyone | View your level, tier, and progress to next tier |
| `/tribulation hearts` | Anyone | Check your heart penalty status |
| `/tribulation set <player> <level>` | Op | Set a player's difficulty level |
| `/tribulation reset <player>` | Op | Reset a player to level 0 |
| `/tribulation reload` | Op | Hot-reload config without restart |
| `/tribulation debug <player>` | Op | Full scaling breakdown for a player |
| `/tribulation inspect` | Op | Inspect the mob you're looking at |

---

## Configuration

Everything is tunable in `config/tribulation.json`. Key sections:

- **Per-mob scaling rates** — Individually tune all 21 vanilla mob types
- **Stat caps** — Global limits on health, damage, armor, speed, knockback resistance, and toughness
- **Tier thresholds** — Move the goalposts (default: 50/100/150/200/250)
- **Ability toggles** — Enable or disable every mob ability individually
- **Death penalties** — Each system has its own enable flag, amounts, cooldowns, and floors
- **Distance/height scaling** — Adjust starting distances, rates, and caps
- **Boss scaling** — Separate factors and caps for boss entities
- **Modded mob support** — Namespace exclusions, per-entity overrides, fallback scaling toggle
- **HUD** — Position, offset, and visibility

Changes apply immediately with `/tribulation reload`. No restart required.

Full config reference: [tribulation.rfizzle.com/config.html](https://tribulation.rfizzle.com/config.html)

---

## Compatibility

- **Minecraft** 1.21.1 (Fabric)
- **Fabric Loader** 0.16.10+
- **Fabric API** required
- **Java** 21+
- Works on **dedicated servers and singleplayer**
- **Modded mobs** automatically scaled with fallback formula (excludable by namespace)
- Optional integrations: **Jade/WTHIT** (mob scaling tooltips), **EMI/REI/JEI** (item recipes), **ModMenu** (config screen)

---

## Installation

Drop the jar into your `mods/` folder. Config generates automatically on first launch. That's it.

---

## Links

- [Documentation](https://tribulation.rfizzle.com)
- [Source Code](https://github.com/rfizzle/tribulation)
- [Issue Tracker](https://github.com/rfizzle/tribulation/issues)
