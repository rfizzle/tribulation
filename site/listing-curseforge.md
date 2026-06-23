![Tribulation](https://raw.githubusercontent.com/rfizzle/tribulation/master/logo.png)

**Tribulation** transforms Minecraft's flat difficulty curve into an escalating gauntlet. The longer you play, the further you explore, and the deeper you dig — the harder the world fights back. Every system is formula-driven, fully configurable, and persists across server restarts.

Zero external dependencies. Just drop it in.

---

## Four-Axis Mob Scaling

Mob stats aren't random — they're computed from four independent factors the moment a mob spawns:

| Axis | How It Works | Cap |
|------|-------------|-----|
| **Time** | Your cumulative playtime advances a per-player difficulty level (0–250). Higher level = stronger mobs near you. ~1 level per hour of play. | Level 250 |
| **Distance** | Beyond 1,000 blocks from world spawn, mobs gain bonus health, damage, armor, and toughness. The frontier is dangerous. | +150% |
| **Height** | Deviation from sea level (Y=62) in either direction adds threat. Deep caves and tall mountains are deadlier. | +50% |
| **Moon Phase** | On Overworld nights, a triangle curve peaks at the full moon and tapers to zero at the new moon — the same coordinates feel different week to week. | +10% |

All four axes stack. A high-level player fighting a mob 3,000 blocks from spawn at Y=12 on a full-moon night faces the full weight of the system. On top of the axes, a flat per-dimension level offset keeps the Nether (+25) and End (+40) scarier than the late-game Overworld, lifting both mob stats and ability tiers.

21 vanilla mob types have individually tuned scaling rates and caps — zombies don't scale the same as skeletons or creepers. Modded hostile mobs are automatically supported with conservative fallback scaling (configurable per-namespace or per-entity).

In multiplayer, the `scalingMode` setting picks which player level drives a nearby mob — `NEAREST`, `AVERAGE`, or `MAX` of the players in range.

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

- **Big Zombie** — Larger, tankier (+10 HP, +2 damage), but slower (0.7x movement speed). Visually 1.3x scale.
- **Speed Zombie** — Trades health for raw speed (1.3x movement speed, −10 HP). Closes gaps fast.

Variants apply across the entire zombie family: Zombie, Husk, Drowned, and Zombified Piglin. Their bonuses stack on top of normal scaling.

---

## Special Skeleton Variants

Two mutually exclusive skeleton variants add unpredictability to ranged encounters:

- **Deadeye Skeleton** — Faster bow draw for a shorter gap between shots, traded for −10 HP. A glass-cannon archer.
- **Brute Skeleton** — +10 HP, 0.5 knockback resistance, and 1.3x size, with a slower bow draw. A heavy, hard-to-stagger archer.

Variants apply across the skeleton family: Skeleton, Stray, and Bogged (not Wither Skeleton). Their bonuses stack on top of normal scaling.

---

## Boss Scaling

The Ender Dragon, Wither, and Elder Guardian scale with a separate, gentler formula using only the time and distance axes. No sudden one-shots — just a steady ramp that keeps endgame bosses relevant at higher levels.

Any custom boss tagged `c:bosses` gets the same treatment.

---

## Raid & Patrol Scaling

Vanilla's own escalation system scales with you, driven by the tier of the targeted players — bells, Hero of the Village, and the raid bar all behave normally.

- **Bigger patrols** — a pillager patrol captain adds extra members as player tier climbs, each spawned through the normal scaling path with tier-appropriate stats, armor, and weapons.
- **Extra raid waves** — raids targeting high-tier players run additional waves on top of the vanilla count.

Tunable via the `raidScaling` config section.

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

### Totem Interaction

The `totems` config controls how a popped Totem of Undying interacts with the penalties: `countsAsDeathRelief` decides whether a totem pop still applies the Death Relief level loss, and `protectsHearts` decides whether it shields you from the Hardcore Hearts loss.

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

## Difficulty Statistics

Six custom statistics track your difficulty milestones — highest level reached, levels lost to death relief, Shatter Shards used, half-hearts lost, half-hearts restored, and Tier-5 mobs killed. They sit alongside vanilla's counters in the **Statistics → Custom** screen, so progress persists across sessions.

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
- **Totem interaction** — Whether a popped Totem of Undying counts as death relief and/or protects Hardcore Hearts
- **Distance/height/moon scaling** — Adjust starting distances, rates, and caps for each position axis
- **Dimension offsets** — Per-dimension flat level boosts (Nether, End, custom)
- **Raid & patrol scaling** — Patrol bonus rate, extra-wave tier threshold, and wave count
- **Boss scaling** — Separate factors and caps for boss entities
- **Modded mob support** — Namespace exclusions, per-entity overrides, fallback scaling toggle
- **Multiplayer scaling mode** — `scalingMode`: `NEAREST`, `AVERAGE`, or `MAX`
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
