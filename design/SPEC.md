# Tribulation — Feature Spec

Minecraft 1.21.1 Fabric mod. Difficulty overhaul: formula-driven mob scaling and opt-in death penalties.

**Asset philosophy:** Tribulation is overwhelmingly a behavioral mod — its surface is attribute math, AI tweaks, and player progression, not new visual content — so it ships almost entirely vanilla assets and reuses vanilla mechanics where they already read correctly. The genuinely mod-specific visuals are custom pixel art authored through Concord's glyph pipeline (`/glyph`, the `mc-textures` skill, concord `design/DESIGN-SYSTEM.md` §8, with `.glyph` sources kept in `art/glyphs/`): the `heart_fragment`, `shatter_shard`, and `ascendant_shard` item textures, the HUD difficulty badge (`textures/gui/hud_icon.png`, a 32×32 master blitted at 16×16, tinted per tier), the tier-detail panel frame (`textures/gui/tier_detail_panel.png`, drawn nine-sliced), and three threat-telegraphing particle sprites (`textures/particle/threat_{tier,big,speed}.png`). The mod registers no custom blocks. It registers two custom `SoundEvent`s, both authored through the `/sfx` pipeline (concord `design/DESIGN-SYSTEM.md` §9) — `tribulation:tier_up`, a synthesized milestone sting (`art/audio/tier-up.sfx`), and `tribulation:blood_moon_warning`, the nightfall event cue — because a tier-up and a Blood Moon rising are the two moments that benefit from their own identity; every other cue is a vanilla sound chosen to fit the moment (amethyst shatter for the shards, player level-up for the fragment). Scaled-mob visual identity (Big/Speed zombies, Brute skeletons) reuses vanilla rendering: large variants drive `Attributes.SCALE`, which natively grows both model and hitbox, and charged creepers use the vanilla powered flag.

---

## 1. Mob Scaling — Four Axes

Every hostile mob's combat attributes are boosted at spawn time by four independent axes whose factors combine per attribute. Scaling is **frozen at spawn** — computed once when the entity loads, written as persistent attribute modifiers, and never recomputed for that entity.

### Behavior

On `ServerEntityEvents.ENTITY_LOAD`, `MobScalingHandler` processes each `Mob` that does not already carry the `tribulation_processed` scoreboard tag. It resolves a `MobScaling` profile for the entity, derives an effective player level, computes per-attribute factors across the four axes, applies them as `AttributeModifier`s, tops the mob's current HP up to its new max, and tags it processed.

The six scaled attributes:

| Key | Vanilla attribute | Modifier operation |
|---|---|---|
| `health` | `MAX_HEALTH` | `ADD_MULTIPLIED_BASE` |
| `damage` | `ATTACK_DAMAGE` | `ADD_MULTIPLIED_BASE` |
| `speed` | `MOVEMENT_SPEED` | `ADD_MULTIPLIED_BASE` |
| `follow_range` | `FOLLOW_RANGE` | `ADD_MULTIPLIED_BASE` |
| `armor` | `ARMOR` | `ADD_VALUE` |
| `toughness` | `ARMOR_TOUGHNESS` | `ADD_VALUE` |

Each axis applies as its own modifier, with ID `tribulation:<axis>_<attribute>` (e.g. `tribulation:time_health`, `tribulation:distance_armor`, `tribulation:moon_damage`). Re-applying scaling overwrites the same IDs, so values never stack on reload.

### The Four Axes

**Time (player level)** — applies to **all six** attributes. Per-attribute factor is `min(playerLevel × rate, perAttributeCap)`, where `rate` and `cap` come from the mob's profile.

**Distance from world spawn** — applies only to the **position-scaled** subset (`health`, `damage`, `armor`, `toughness`). Measured as 2D horizontal distance from `world.getSharedSpawnPos()` (Y excluded):
```
distanceLevels = max(0, (horizDist − startingDistance) / increasingDistance)
distanceFactor = min(distanceLevels × distanceFactor, maxDistanceFactor)
```
Defaults: `startingDistance=1000`, `increasingDistance=300`, `distanceFactor=0.1`, `maxDistanceFactor=1.5`. So beyond 1000 blocks, every 300 blocks adds +0.1, capped at +1.5 (+150%).

**Height (Y deviation from sea level)** — applies only to the position-scaled subset:
```
delta        = mobY − startingHeight        (startingHeight = 62)
heightLevels = |delta| / heightDistance      (skipped if the matching pos/neg toggle is off)
heightFactor = min(heightLevels × heightFactor, maxHeightFactor)
```
Defaults: `heightDistance=30`, `heightFactor=0.1`, `maxHeightFactor=0.5`. Both upward and downward deviation add threat; `positiveHeightScaling`/`negativeHeightScaling` gate each direction independently.

**Moon phase** — applies only to the position-scaled subset. A triangle curve over the 8 vanilla moon phases peaks at the full moon and falls to zero at the new moon (`ScalingEngine.computeMoonFactor`):
```
distFromFull = min(moonPhase, 8 − moonPhase)   (full = phase 0, new = phase 4)
moonFactor   = maxBonus × (1 − distFromFull / 4)
```
Default `maxBonus=0.1` (+10% at the full moon; the half-moons land at +5%). The moon axis only contributes when `moonAppliesAt` holds (`ScalingEngine.moonAppliesAt`):
- `moonPhaseScaling.enabled` is true and `maxBonus > 0`;
- the dimension has a real daylight cycle (`hasSkyLight()` **and not** `hasCeiling()` — the Overworld, not the Nether or End);
- it is night (`!world.isDay()`);
- if `surfaceOnly` (default false), the mob's Y is `≥ surfaceY` (default 63), keeping the bonus off deep-cave spawns.

### Effective Level Resolution

`ScalingEngine.getEffectiveLevel` finds the player level that drives a mob's scaling, governed by `general.scalingMode` and `general.mobDetectionRange` (default 32 blocks). Returns 0 if the range is ≤0 or no qualifying player is in range.

| Mode | Behavior |
|---|---|
| `NEAREST` (default) | The nearest non-spectator player within range (vanilla `getNearestPlayer`, which excludes spectators) |
| `AVERAGE` | Floored mean of all non-spectator players' levels within range |
| `MAX` | Highest level among non-spectator players within range |

Creative players count in all three modes; spectators never do.

**Effective-level offsets.** Once a qualifying player is found, a flat level boost is folded into the resolved raw level before the axes see it: `effectiveLevel = min(rawLevel + max(0, offset), maxLevel)`, where `offset` is the **sum** of three independent per-position boosts (each individually clamped to `≥ 0`). The offset is the single value returned by `getEffectiveLevel`/`getEffectiveLevelAt`, so it raises **both** scaled stats and the mob's ability/equipment/champion tier alike. The "no player in range" paths return 0 unchanged — the offset is never added to mobs that would not scale anyway.

- **Dimension offset** — `dimensionOffsets`, a `Map<dimensionId, int>` defaulting to `minecraft:the_nether → 25` and `minecraft:the_end → 40`; the Overworld and any unlisted dimension default to 0. A level-100 player near a mob in the End yields an effective level of 140.
- **Biome offset** — `biomeOffsets`, a `Map<biomeKey, int>` folded in only when non-empty (the `hasBiomeOffsets` guard keeps the biome lookup off the hot path when unconfigured). A key is a plain biome ID (`minecraft:deep_dark`) or a `#`-prefixed biome tag; an exact-ID entry wins outright, otherwise the **largest** offset among matching tags applies. Modded biomes and whole tag categories can carry their own boost. Default: `minecraft:deep_dark → 30`. Resolved through an immutable `BiomeOffsetResolver` rebuilt once per config generation.
- **Structure danger zones** — `structureBoosts.boosts`, a `Map<structureKey, int>` folded in only when non-empty (`hasStructureBoosts` guard). A mob spawning inside — or within `structureBoosts.marginBlocks` (default 16, max 128) of — a configured structure's overall bounding box uses the mapped boost, resolved with the same exact-ID-wins / largest-matching-tag rule over structure IDs (`minecraft:fortress`) or `#`-prefixed structure tags. Defaults: `fortress` & `bastion_remnant` `+20`, `monument` & `trial_chambers` `+15`, `ancient_city` `+30`, `end_city` `+25`. Only spawn-time position matters — a mob wandering into a structure later is unaffected. `StructureBoostManager` caches the resolved, margin-inflated zones per chunk (keyed by config identity, evicted on chunk/world unload) so the per-spawn cost is one containment test; membership is tested against the structure start's whole footprint, so corridors and courtyards between pieces count as inside.

### Per-Attribute Combination and Global Cap

For each attribute the four axis factors are summed and clipped to that attribute's **global cap** (`statCaps`). When clipping occurs, the axes are scaled down **proportionally** so the per-axis breakdown still sums to the clipped total (preserves inspect/debug readability).

- For `ADD_MULTIPLIED_BASE` attributes the global cap is a dimensionless multiplier (`maxFactorHealth=4.0` → up to +400% of base).
- For `ADD_VALUE` attributes (`armor`, `toughness`) the per-axis distance/height/moon factors are first multiplied by the attribute's own per-mob cap to convert them into native armor points, and the global cap is `maxFactorProtection × perMobCap`.

Global caps (`statCaps`): health `4.0`, damage `4.5`, speed `0.5`, protection `2.0`, follow_range `1.5`.

### Mob Profile Resolution and Modded Fallback

`resolveScalingForEntity` picks a mob's `MobScaling` profile by precedence:

1. **Full-ID override** — if `scaling` contains a key equal to the entity's full ID string (e.g. `"create:dark_zombie"`), use it. Works for any namespace; the hand-tuned escape hatch.
2. **Vanilla path lookup** — for the `minecraft` namespace only: if the path's `mobToggles` entry is `true`, return `scaling.get(path)`; if the toggle is **explicitly false**, return `null` (explicit no-scale wins — does NOT fall through to the modded fallback).
3. **Modded fallback** — when `unlistedHostileMobs.enabled` (default true), the entity is a `Monster`, and its namespace is not in `unlistedHostileMobs.excludedNamespaces`, use `unlistedHostileMobs.scaling`.
4. Otherwise `null` — no scaling.

The fallback profile scales **health and damage only** (zombie's rates: health `0.010/2.50`, damage `0.015/3.75`); all other axes are zero, so modded mobs get conservative HP/damage growth without second-guessing the author's speed/AI/armor tuning.

### The 21 Tuned Vanilla Mobs

`MOB_KEYS` and the default `scaling` map cover 21 vanilla hostile types, each with individually tuned rate/cap pairs for all six attributes:

`zombie`, `skeleton`, `creeper`, `spider`, `cave_spider`, `endermite`, `silverfish`, `drowned`, `husk`, `stray`, `pillager`, `vindicator`, `witch`, `wither_skeleton`, `guardian`, `hoglin`, `zoglin`, `ravager`, `piglin`, `zombified_piglin`, `bogged`.

Reference values (`rate / cap`, full default set in `TribulationConfig.defaultScaling()`):

| Mob | health | damage | speed | follow_range | armor | toughness |
|---|---|---|---|---|---|---|
| zombie | 0.010 / 2.50 | 0.015 / 3.75 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.032 / 8 | 0.024 / 6 |
| skeleton | 0.010 / 2.50 | 0.012 / 3.00 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.020 / 5 | 0.015 / 4 |
| creeper | 0.008 / 2.00 | 0.010 / 2.50 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.016 / 4 | 0.012 / 3 |
| spider | 0.008 / 2.00 | 0.012 / 3.00 | 0.0020 / 0.50 | 0.010 / 1.0 | 0.016 / 4 | 0.012 / 3 |
| cave_spider | 0.006 / 1.50 | 0.010 / 2.50 | 0.0020 / 0.50 | 0.010 / 1.0 | 0.008 / 2 | 0.008 / 2 |
| endermite | 0.005 / 1.25 | 0.010 / 2.50 | 0.0024 / 0.60 | 0.008 / 0.8 | 0 / 0 | 0 / 0 |
| silverfish | 0.005 / 1.25 | 0.008 / 2.00 | 0.0010 / 0.25 | 0.008 / 0.8 | 0 / 0 | 0 / 0 |
| drowned | 0.010 / 2.50 | 0.014 / 3.50 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.024 / 6 | 0.020 / 5 |
| husk | 0.011 / 2.75 | 0.015 / 3.75 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.032 / 8 | 0.024 / 6 |
| stray | 0.010 / 2.50 | 0.012 / 3.00 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.020 / 5 | 0.015 / 4 |
| pillager | 0.010 / 2.50 | 0.014 / 3.50 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.024 / 6 | 0.020 / 5 |
| vindicator | 0.012 / 3.00 | 0.020 / 5.00 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.032 / 8 | 0.024 / 6 |
| witch | 0.008 / 2.00 | 0.008 / 2.00 | 0.0008 / 0.20 | 0.008 / 0.8 | 0.016 / 4 | 0.012 / 3 |
| wither_skeleton | 0.012 / 3.00 | 0.016 / 4.00 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.024 / 6 | 0.020 / 5 |
| guardian | 0.010 / 2.50 | 0.014 / 3.50 | 0.0010 / 0.25 | 0.010 / 1.0 | 0.020 / 5 | 0.020 / 5 |
| hoglin | 0.014 / 3.50 | 0.016 / 4.00 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.024 / 6 | 0.020 / 5 |
| zoglin | 0.014 / 3.50 | 0.015 / 3.75 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.024 / 6 | 0.020 / 5 |
| ravager | 0.016 / 4.00 | 0.014 / 3.50 | 0.0008 / 0.20 | 0.010 / 1.0 | 0.032 / 8 | 0.024 / 6 |
| piglin | 0.010 / 2.50 | 0.014 / 3.50 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.024 / 6 | 0.020 / 5 |
| zombified_piglin | 0.010 / 2.50 | 0.015 / 3.75 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.024 / 6 | 0.020 / 5 |
| bogged | 0.010 / 2.50 | 0.012 / 3.00 | 0.0012 / 0.30 | 0.010 / 1.0 | 0.020 / 5 | 0.015 / 4 |

### Armor / Toughness Ceilings

After scaling and any equipped armor, `MobScalingHandler` clamps the combined `ARMOR`/`ARMOR_TOUGHNESS` (when `armorEquipment.enabled`) and `ATTACK_DAMAGE` (when `weaponEquipment.enabled`) so the **total** value stays under a hard ceiling. Only the Tribulation buff portion is trimmed proportionally; base value, equipment, and foreign modifiers keep their full value. Ceilings: `armorCeiling=24.0`, `toughnessCeiling=15.0`, `damageCeiling=20.0`.

### Scope

- Only server-side `Mob` entities. Players, non-mob entities, and entities in `general.excludedEntities` (default contains `the_bumblezone:cosmic_crystal_entity`) are skipped.
- Bosses route through a separate engine (§3). Trial-spawner and patrol mobs route through dedicated handlers (§9, §10) but reuse this same per-mob formula.
- The frozen-at-spawn tag means existing mobs keep their stats across `/tribulation reload`; only newly loaded mobs use updated config.

### Implementation Notes

- Pure math lives in `ScalingEngine` as static methods independent of Minecraft attribute types (for unit testing); world-aware application is layered on top.
- The MAX_HEALTH axis sum (`readHealthScalingFactor`) is the canonical "how scaled is this mob" proxy, read back from persistent modifiers — it survives chunk reload and feeds XP/loot scaling and `/inspect` without extra per-entity state.

---

## 2. Player Difficulty Level (Time Progression)

Each player carries a difficulty **level** (0–`maxLevel`, default 250) that advances with cumulative playtime and feeds the time axis of mob scaling.

### Behavior

`Tribulation` registers a `ServerTickEvents.END_SERVER_TICK` handler that fires every 20 ticks (1 s). When `timeScaling.enabled` and at least one player is online, every online player's tick counter advances by 20. Crossing `levelUpTicks` (default 72000 ticks = 1 hour) grants one level, carrying the remainder. At `maxLevel` the counter is zeroed and no further levels are gained.

On a level gain the new level is synced to the client and `TribulationLevelCallback` fires. If `general.notifyLevelUp`, the player receives a chat message: `message.tribulation.level_max` at the cap, `message.tribulation.level_up_tier` when `notifyLevelUpShowTier` and the tier changed, otherwise `message.tribulation.level_up`. Crossing a tier boundary additionally fires the tier advancement and the tier-up sting (§26, §31).

### Persistence

Level state lives in `PlayerDifficultyState`, a `SavedData` stored on the overworld (`tribulation_players`). Per-player fields: `Level` (int), `Tick` (tick counter toward next level), `LastDeathTick` (long, death-relief cooldown anchor; sentinel `Long.MIN_VALUE` = never died), `HeartsLost` (int half-hearts). The backing map is a `ConcurrentHashMap`; entries are created lazily on first access and kept for life (no logout eviction, so a returning player keeps their level). Serialization sorts entries by UUID for deterministic on-disk order. Survives restarts. Level is **not** reset on death (only reduced by the opt-in penalties).

### Scope

- Per-player and global to the player — the same level applies to every mob spawned near them regardless of dimension. Time scaling applies in **all** dimensions; the per-dimension offset (§1) raises the effective level used in the Nether/End.
- On player join, the current level is synced to the client for the HUD.

---

## 3. Boss Scaling

Bosses use a separate, gentler formula with uniform rates.

### Behavior

A mob whose `EntityType` is in the `#c:bosses` tag (ships with `minecraft:ender_dragon`, `minecraft:wither`, `minecraft:elder_guardian`, `minecraft:warden`) routes through `BossScalingEngine` instead of the per-mob engine. When `bosses.affectBosses` is false, bosses are skipped entirely (no scaling, no processed tag short-circuit other than the normal path).

Only **health and damage** are scaled, over **time and distance** axes (no height, no moon). Both axes use flat boss rates rather than per-mob rates:
```
bossTime     = playerLevel × bossTimeFactor          (default 0.3)
bossDistance = distanceLevels × bossDistanceFactor    (default 0.1, same start/step thresholds as §1)
total        = min(bossTime + bossDistance, bossMaxFactor)   (default cap 3.0)
```
When the sum exceeds the cap, the two axes scale down proportionally. Distance ignores `excludeInOtherDimensions`, so boss scaling is live in the Nether/End. Modifiers use `boss_time_<attr>` / `boss_distance_<attr>` IDs (all `ADD_MULTIPLIED_BASE`) so inspect/debug can distinguish them. HP is topped to max after scaling.

### Scope

- Bosses receive no tier abilities, variants, or equipment — only the boss health/damage buff.
- Boss-formula scaling is detectable via `TribulationAPI.isBossScaled` (reads the boss-axis modifiers).

---

## 4. Tier-Gated Mob Abilities

At five level thresholds, scaled vanilla mobs unlock special behaviors keyed off the mob's frozen tier.

### Tiers

`TierManager` classifies a player level into tier 0–5. Thresholds (`tiers`, inclusive on the lower bound): tier1 `50`, tier2 `100`, tier3 `150`, tier4 `200`, tier5 `250`. A player at exactly the threshold is in that tier. A mob's tier is frozen at spawn alongside its scaling (stored in the `SCALED_TIER` attachment) and reflects the effective level — including the dimension offset (§1).

### Ability Table

`AbilityManager.applyAbilities` runs only for the 21 vanilla mob keys whose toggle is enabled; each ability also checks its own `abilities.*` flag. Abilities are expressed as attribute modifiers (`tribulation:ability_*` IDs), infinite-duration effects, vanilla setters, equipment, or scoreboard tags consumed by mixins, so they persist in NBT. `MobAbilities.REGISTRY` holds 31 entries (the 29 `abilities.*` flags, two of which — `spiderWebPlacing` and `silverfishCallSleepers` — cover two mob keys each).

| Mob | Tier ≥ | Ability | Mechanism | Config flag |
|---|---|---|---|---|
| Zombie | 1 | Reinforcements | `+0.10` `SPAWN_REINFORCEMENTS_CHANCE` (ADD_VALUE) | `zombieReinforcements` |
| Zombie | 3 | Door breaking | `setCanBreakDoors(true)` | `zombieDoorBreaking` |
| Zombie | 5 | Sprinting | `+0.15` `MOVEMENT_SPEED` (×base) | `zombieSprinting` |
| Creeper | 1 | Shorter fuse | max swell set to 15 ticks (`CreeperAccessor`) | `creeperShorterFuse` |
| Creeper | 5 | Charged | 25% chance to set the powered flag | `creeperCharged` |
| Skeleton | 2 | Sword switch | bow → stone sword | `skeletonSwordSwitch` |
| Skeleton | 4 | Flame arrows | infinite Fire Resistance + `setRemainingFireTicks(MAX)` (self ablaze; arrows ignite) | `skeletonFlameArrows` |
| Spider | 2 | Web placing | `tribulation_web` tag (on-hit cobweb, mobGriefing-gated) | `spiderWebPlacing` |
| Spider | 3 | Crop trample | `tribulation_crop_trample` tag (on-hit destroys crops/farmland, mobGriefing-gated) | `spiderCropTrample` |
| Spider | 5 | Leap attack | `+0.5` `JUMP_STRENGTH` (×base) | `spiderLeapAttack` |
| Cave Spider | 2 | Web placing | `tribulation_web` tag (shares the spider toggle) | `spiderWebPlacing` |
| Endermite | 2 | Call sleepers | `tribulation_call_sleepers` tag → `SilverfishAbilityHandler` wakes nearby silverfish from infested blocks when hurt | `silverfishCallSleepers` |
| Silverfish | 2 | Call sleepers | `tribulation_call_sleepers` tag (relies on the vanilla wake-friends goal) | `silverfishCallSleepers` |
| Drowned | 2 | Trident | empty main hand → trident | `drownedTrident` |
| Husk | 4 | Hunger II | `tribulation_hunger2` tag (upgrades vanilla Hunger I → II on hit) | `huskHunger` |
| Stray | 2 | Slowness II arrows | `tribulation_slow2` tag → `StrayAbilityMixin` adds `MOVEMENT_SLOWDOWN` amp 1 to fired arrows | `straySlownessUpgrade` |
| Bogged | 2 | Poison II arrows | `tribulation_poison2` tag → `BoggedAbilityMixin` adds `POISON` amp 1 to fired arrows | `boggedPoisonUpgrade` |
| Pillager | 2 | Quick Charge | enchants held crossbow with Quick Charge I | `pillagerQuickCharge` |
| Pillager | 4 | Multishot | enchants held crossbow with Multishot | `pillagerMultishot` |
| Vindicator | 3 | Door breaking | `tribulation_door_break` tag → `BreakDoorGoalMixin` allows door-breaking on any difficulty | `vindicatorDoorBreaking` |
| Vindicator | 4 | Resistance | infinite Resistance I | `vindicatorResistance` |
| Witch | 3 | Lingering potions | `tribulation_lingering_potions` tag → `WitchAbilityMixin` redirects SPLASH → LINGERING potions | `witchLingeringPotions` |
| Witch | 5 | Aggressive healing | `tribulation_aggro_heal` tag → `WitchAbilityMixin` raises the drink-potion chance 0.05 → 0.25 | `witchAggressiveHealing` |
| Wither Skeleton | 3 | Sprint | `+0.15` `MOVEMENT_SPEED` (×base) | `witherSkeletonSprint` |
| Wither Skeleton | 4 | Fire Aspect | Fire Aspect I on the main-hand weapon (if held) | `witherSkeletonFireAspect` |
| Guardian | 3 | Faster beam | `tribulation_guardian_beam` tag → `GuardianBeamMixin` halves attack duration (floor 20 ticks) | `guardianFasterBeam` |
| Hoglin | 1 | Knockback resist | `+0.5` `KNOCKBACK_RESISTANCE` (ADD_VALUE) | `hoglinKnockbackResist` |
| Zoglin | 3 | Fire resist | infinite Fire Resistance | `zoglinFireResist` |
| Ravager | 3 | Roar expansion | `tribulation_ravager_roar` tag → `RavagerRoarMixin` inflates the roar AABB 4.0 → 6.0 | `ravagerRoarExpansion` |
| Piglin | 2 | Crossbow | empty main hand → crossbow | `piglinCrossbow` |
| Zombified Piglin | 5 | Aggro range | `+0.5` `FOLLOW_RANGE` (×base) | `zombifiedPiglinAggro` |

The 29 `abilities.*` config flags all default to `true`.

### Implementation Notes

- On-hit abilities (`spiderWebPlacing`, `spiderCropTrample`) are driven by `MobAbilityMixin` on `Mob#doHurtTarget`, gated by the `mobGriefing` gamerule; `huskHunger` is driven by `HuskAbilityMixin` on `Husk#doHurtTarget`.
- Arrow-effect abilities (`straySlownessUpgrade`, `boggedPoisonUpgrade`) inject in the respective `*AbilityMixin` on `AbstractSkeleton#getArrow`, reading the mob's tag.
- Skeleton flame arrows are implemented by keeping the skeleton permanently on fire (fire-immune) so its arrows inherit ignition; there is no dedicated arrow-modification code.
- Crossbow enchants (`pillagerQuickCharge`, `pillagerMultishot`) use the `enchantHeldCrossbow` helper and no-op when the main hand is not a crossbow.

---

## 5. Special Zombie Variants

Zombie-family mobs can roll a Big or Speed variant on top of base scaling.

### Behavior

`ZombieVariantHandler.apply` runs for the eligible keys `zombie`, `husk`, `drowned`, `zombified_piglin` (zombie villagers and babies are excluded) when `specialZombies.enabled`. It is layered **after** base scaling, so bonuses stack on already-scaled values. The roll is **speed-first, then big, mutually exclusive**, each gated by an independent percent chance, and the result is tagged (`tribulation_variant_processed`) so a failed roll is not retried on reload.

**Speed variant:** `MOVEMENT_SPEED` `×total` by `speedZombieSpeedFactor − 1` (default 1.3 → +30% of final speed); `MAX_HEALTH` `−speedZombieMalusHealth` (default `−10`, ADD_VALUE).

**Big variant:** `MAX_HEALTH` `+bigZombieBonusHealth` (default `+10`); `ATTACK_DAMAGE` `+bigZombieBonusDamage` (default `+2`); `MOVEMENT_SPEED` `×total` by `bigZombieSlowness − 1` (default 0.7 → −30%); `SCALE` `×base` by `bigZombieSize − 1` (default 1.3 → +30% model and hitbox).

Variant modifiers use distinct `tribulation:variant_*` IDs so they never collide with axis modifiers. Defaults: `bigZombieChance=10`, `speedZombieChance=10` (percent).

### Implementation Notes

- `Attributes.SCALE` natively drives both the rendered model and the entity bounding box in 1.21, syncing through the existing attribute packet — no TrackedData or renderer mixin.
- Variants are detectable via `/tribulation inspect` (Big wins if both ID families are somehow present, for deterministic output) and drive the variant threat particles (§19).

---

## 6. Special Skeleton Variants

Skeleton-family mobs can roll a Deadeye or Brute variant on top of base scaling — the archer counterpart to the zombie variants.

### Behavior

`SkeletonVariantHandler.apply` runs for the eligible keys `skeleton`, `stray`, `bogged` (Wither Skeleton is excluded — it carries tier abilities) when `specialSkeletons.enabled`. It is layered **after** base scaling. The roll is **deadeye-first, then brute, mutually exclusive**, each gated by an independent percent chance, and the result is tagged (`tribulation_skeleton_variant_processed`) so a failed roll is not retried on reload. Each variant also carries a detection tag (`tribulation_variant_deadeye` / `tribulation_variant_brute`).

**Deadeye variant** (glass-cannon archer): `MAX_HEALTH` `−deadeyeSkeletonMalusHealth` (default `−10`, ADD_VALUE); bow attack interval set to `deadeyeSkeletonAttackInterval` (default 20 ticks — faster than vanilla). ID `tribulation:variant_deadeye_health`.

**Brute variant** (heavy, hard-to-stagger archer): `MAX_HEALTH` `+bruteSkeletonBonusHealth` (default `+10`); `KNOCKBACK_RESISTANCE` `+bruteSkeletonBonusKnockbackResistance` (default `+0.5`, ADD_VALUE); `SCALE` `×base` by `bruteSkeletonSize − 1` (default 1.3 → +30%); bow attack interval set to `bruteSkeletonAttackInterval` (default 60 ticks — slower than vanilla). IDs `tribulation:variant_brute_health` / `_knockback` / `_size`.

### Implementation Notes

- Bow cadence is not an attribute in 1.21.1. `AbstractSkeletonMixin` injects at `RETURN` of `reassessWeaponGoal`, reads the variant tag, and calls `setMinAttackInterval` on the bow goal; the handler re-runs `reassessWeaponGoal()` after tagging so the mixin sees the tag.
- Like the Big zombie, Brute size drives `Attributes.SCALE` (model + hitbox), syncing through the vanilla attribute packet. Detectable via `/tribulation inspect` and the Jade/WTHIT overlay.

---

## 7. Tier-Driven Equipment

Scaled humanoid mobs can roll armor and weapons matched to their tier.

### Armor

`ArmorEquipmentHandler.processArmor` runs (when `armorEquipment.enabled`) only for mobs that visibly wear armor: `Zombie`, `AbstractSkeleton`, `AbstractPiglin` families. Babies and non-humanoids are skipped (and tagged). The handler **clears all four armor slots first** (taking over vanilla's roll), then rolls:

1. `wearChancePercent` gate — fail → mob stays bare.
2. Material chosen from `materialWeights` (weighted). `materialRollMode` `PER_MOB` (default) picks one material for the whole mob; `PER_SLOT` rolls per slot.
3. Per slot, `slotCoveragePercent` gate decides whether that slot is filled.
4. Optional Protection enchant: `enchantChancePercent` gate, level low-biased up to `maxProtectionLevel`.

Drop chance per piece is `armorEquipment.armorDropChance` (default 0.0 — tier armor does not drop), routed through `TribulationAPI.resolveArmorDropChance` so loot mods can override. Armored mobs have `setCanPickUpLoot(false)`.

Default armor tiers (`wear% / coverage% / enchant% / maxProt`, material weights):

| Tier | wear | coverage | enchant | maxProt | materials |
|---|---|---|---|---|---|
| tier1 | 12 | 60 | 0 | 0 | leather 80, gold 15, chain 5 |
| tier2 | 18 | 68 | 10 | 1 | leather 55, gold 25, chain 15, iron 5 |
| tier3 | 25 | 75 | 20 | 2 | leather 35, gold 25, chain 20, iron 18, diamond 2 |
| tier4 | 35 | 80 | 30 | 2 | leather 20, gold 18, chain 20, iron 30, diamond 11, netherite 1 |
| tier5 | 45 | 85 | 40 | 3 | leather 12, gold 12, chain 16, iron 30, diamond 25, netherite 5 |

### Weapons

`WeaponEquipmentHandler.processWeapon` runs (when `weaponEquipment.enabled`) for `Zombie`, `AbstractSkeleton`, `AbstractPiglin`, `AbstractIllager` families. Unlike armor it does **not** strip the main hand first (that would disarm vanilla-armed mobs and break their AI). After a `wearChancePercent` gate it rolls a material, then:
- Empty hand or standard melee (sword/digger/mace) → replace with the rolled material's sword (or axe, if currently holding an axe).
- Standard ranged (bow/crossbow/trident) → keep the item, proceed to enchanting.
- Unrecognized item → left untouched.

Enchants (`enchantChancePercent` gate, low-biased to `maxEnchantmentLevel`): melee gets Sharpness, with rare Knockback (tier ≥4) and Fire Aspect (tier ≥5); bow gets Power, rare Punch (≥4) and Flame (≥5); crossbow gets Quick Charge plus Piercing or Multishot; trident gets Impaling. Drop chance `weaponEquipment.weaponDropChance` (default 0.0), routed through `TribulationAPI.resolveWeaponDropChance`.

Default weapon tiers (`wear% / enchant% / maxEnchLevel`, materials):

| Tier | wear | enchant | maxLevel | materials |
|---|---|---|---|---|
| tier1 | 10 | 0 | 0 | wood 80, stone 20 |
| tier2 | 20 | 10 | 1 | wood 40, stone 45, iron 15 |
| tier3 | 30 | 20 | 2 | stone 30, iron 60, diamond 10 |
| tier4 | 45 | 30 | 3 | iron 40, diamond 55, netherite 5 |
| tier5 | 60 | 40 | 4 | iron 15, diamond 65, netherite 20 |

### Scope

- Equipment rolls only for the `mobToggles`-enabled vanilla path (not for boss or modded-fallback mobs).
- `armorEquipment` and `weaponEquipment` both default `enabled=true`, but with `drop_chance=0.0` so the gear is a difficulty buff that does not flood the player with loot.

---

## 8. Bonus XP

Scaled mobs reward proportionally more XP to compensate for their increased difficulty.

### Behavior

**XP** (`xp.xpMultiplier`, default `1.0`): `LivingEntityExperienceMixin` hooks `LivingEntity#getExperienceReward` and multiplies a mob's base XP by `1 + healthFactor × xpMultiplier`, where `healthFactor` is the mob's MAX_HEALTH axis sum. There is no separate XP ceiling — `healthFactor` is already bounded by `statCaps.maxFactorHealth`, so `xpMultiplier` is a plain gain dial (a fully-scaled reference zombie reaches ~3.5× XP at the default; a mob at the global health cap reaches ~5×). `xpMultiplier=0` disables the bonus. The result is rounded to the nearest int; only `Mob`s with positive scaling are affected.

### Scope

- XP scaling reads the persistent health modifier (via `getExperienceReward`, not `dropExperience`), so it survives chunk reload/restart, shows in `/xp query`, and stacks correctly with Looting modifiers.
- Item loot is left untouched — the mod does not modify any vanilla loot table or drop.

---

## 9. Trial Spawner Scaling

Mobs from 1.21 trial spawners get the full Tribulation treatment (stats, abilities, equipment), keeping trial chambers in step with a high-tier world.

### Behavior

`TrialSpawnerMixin` `@WrapOperation`s the `ServerLevel.tryAddFreshEntityWithPassengers` call inside `TrialSpawner#spawnMob`: it scales the mob and stamps `MobScalingHandler.PROCESSED_TAG` **before** the entity is added, so the normal proximity `ENTITY_LOAD` path sees it already processed and skips it (no re-scale race).

Scaling uses the **spawner's detected-player set**, not a nearest-player world scan. With `NEAREST` it picks the nearest detected player by distance to the spawn origin; with `MAX`/`AVERAGE` it folds the detected players' levels via `ScalingEngine.foldLevels`. An empty detected set yields level 0 (effectively vanilla).

**Ominous upgrade** (`trialSpawner.ominousUpgrade`, default **off**): `TrialSpawnerDataMixin` injects at `tryDetectPlayers`, once per activation. When enabled and the folded detected-player tier is `≥ minimumTier` (default 3), a `chance` roll (default 0.10) calls `spawner.applyOminous` — a high-tier spawner rolls into ominous mode **with no Bad Omen effect on the player**. Vanilla cooldown, reward ejection, and Bad-Omen ominous behavior are otherwise untouched.

### Config

`trialSpawner.enabled` (true); `trialSpawner.ominousUpgrade.enabled` (false), `.chance` (0.10), `.minimumTier` (3).

---

## 10. Raid & Patrol Scaling

Vanilla's built-in escalation — pillager patrols and raids — scales with the tier of the targeted player(s). Bells, Hero of the Village, and the raid bar all behave normally; this system governs only structural escalation (raider stats/armor/weapons already flow through §1/§7).

### Patrol growth

`RaidScalingHandler` listens on `ServerEntityEvents.ENTITY_LOAD` for a `Pillager` that `isPatrolLeader()` and is not yet tagged `tribulation_patrol_processed` (tagged immediately so a chunk reload never re-spawns extras). It resolves the captain's effective level, derives a tier, and adds `extraPatrolMembers(tier) = tier / patrolBonusRate` members (integer-floored, default +1 per 2 tiers). Extras are spawned on the **next** server tick via a `TickTask` (avoids mutating the entity list mid-iteration) with `MobSpawnType.PATROL`, tagged only `tribulation_patrol_processed` — deliberately **not** the scaling processed tag — so the normal `ENTITY_LOAD` path gives each extra tier-appropriate stats, armor, and weapons.

### Extra raid waves

`RaidScalingMixin` `@ModifyReturnValue`s `Raid#getNumGroups`, returning `original + extraWaves`. `extraWaves(maxTier) = maxTier ≥ extraWaveTierThreshold ? extraWaveCount : 0` (defaults threshold tier 4, count 1). The extra-wave count is computed once and memoized so the frozen `numGroups` stays consistent across the raid (the captain banner lands on the right wave). The targeted tier comes from the non-spectator players within `general.mobDetectionRange` of the raid center, folded via `ScalingEngine.foldLevels`.

### Config

`raidScaling.enabled` (true), `patrolBonusRate` (2), `extraWaveTierThreshold` (4), `extraWaveCount` (1). `patrolBonusRate ≤ 0` disables the patrol bonus.

---

## 11. Pack Tactics

At or above a configurable tier threshold, the classic pack mobs stop fighting alone: hurting one alerts its packmates, and natural spawn groups grow. Below the threshold — and for any type not on the eligible list — behavior is fully vanilla. Eligible types default to `minecraft:zombie`, `minecraft:skeleton`, `minecraft:spider`; the list resolves against the entity-type registry once per config generation (unknown IDs are logged and ignored) so hot-path checks are a set lookup.

### Shared aggro

`PackTacticsHandler` listens on `ServerLivingEntityEvents.AFTER_DAMAGE` — alerting happens only on the damage event, so there is no per-tick cost outside combat. When a non-spectator player damages an eligible mob whose frozen `SCALED_TIER` is ≥ `tierThreshold`, one bounded AABB query collects same-type mobs within `alertRadius` of the victim. A packmate is retargeted onto the attacker only if it has line-of-sight to the **victim** (no alerting through walls — it "saw the attack") and is not already fighting a living target (an engaged mob is never yanked off another player). At most 16 packmates are alerted per hit.

### Larger spawn groups

`NaturalSpawnerGroupSizeMixin` redirects the `minCount`/`maxCount` field reads in `NaturalSpawner#spawnCategoryForPosition`'s group-size pick (`minCount + nextInt(1 + maxCount − minCount)`), boosting each read by `spawnGroupBonus(tier)` — which shifts the whole group size by exactly the bonus while leaving the random bound untouched. The tier is the effective tier at the spawn position (`ScalingEngine.getEffectiveLevelAt`, the position-based twin of the entity resolver). Mob caps are unaffected, so total density stays bounded.

### Config

`packTactics.enabled` (true), `tierThreshold` (3), `alertRadius` (16.0, clamped to [0, 64]), `groupSizeBonus` (2, clamped to [0, 16]), `eligibleMobs` (zombie/skeleton/spider).

---

## 12. Death Relief

A rubber-band penalty that lowers difficulty on death.

### Behavior

On `AFTER_DEATH` for a player (when `deathRelief.enabled`, default true), the player's level is reduced by `deathRelief.amount` (default 2), floored at `deathRelief.minimumLevel` (default 0). A cooldown of `deathRelief.cooldownTicks` (default 6000 = 5 min) since the last qualifying death suppresses rapid-suicide farming; `cooldownTicks=0` means every death counts. All death causes qualify — the cooldown is the only gate.

A successful application syncs the level, fires `TribulationLevelCallback`, and awards the `levels_lost_to_death_relief` stat. The death-relief cooldown anchor (`LastDeathTick`) updates whenever a death is off-cooldown, even if the level was already at the floor.

### Scope

- The level floor is shared with Shatter Shards (`deathRelief.minimumLevel`).

---

## 13. Shatter Shards

A rare mob drop that voluntarily lowers difficulty.

### Behavior

**Drop** (`shards.enabled`, default true): on `AFTER_DEATH` of a `Mob` killed (credit or direct) by a `ServerPlayer`, if the killer's level ≥ `shards.dropStartLevel` (default 25) and a `shards.dropChance` roll (default 0.005 = 0.5%) succeeds, a Shatter Shard item entity spawns at the mob.

**Use:** right-clicking the `tribulation:shatter_shard` item reduces the player's level by `shards.shardPower` (default 5), floored at `deathRelief.minimumLevel`. The stack is consumed (kept in creative). Plays `minecraft:block.amethyst_block.break` at pitch 1.2. If `shards.sideEffects` (default true), the user also gets 10 s (200 ticks) of Slowness II, Mining Fatigue II, and Weakness II. The action-bar message reports the level change or that the player is already at the floor.

### Item

`tribulation:shatter_shard` — stacks to 16, Uncommon rarity, enchantment-glint override on. Custom 16×16 texture (`textures/item/shatter_shard.png`) with its `.glyph` source at `art/glyphs/shatter-shard-32.glyph`. Flat `item/generated` model, no custom layering.

### Scope

- The drop roll happens at death; there is no spawn-time pre-marking of carrier mobs.

---

## 14. Hardcore Hearts (opt-in)

Permanent max-health loss on death, recoverable with Heart Fragments.

### Behavior

`hardcoreHearts.enabled` defaults **false**. When on, each player death (`AFTER_DEATH`) increases `HeartsLost` by `heartsLostPerDeath` (default 2 half-hearts), floored so the player keeps at least `minimumHearts` half-hearts of max HP (default 2). The penalty is applied as a `tribulation:hardcore_hearts` `MAX_HEALTH` modifier (`ADD_VALUE`, `−heartsLost`), re-applied on join and after respawn (`COPY_FROM`). A message (`message.tribulation.heart_lost` or `…heart_lost_floor`) and the `hearts_lost` stat fire on loss.

**Heart Fragment** (`tribulation:heart_fragment`): right-click restores `heartsRestoredPerFragment` half-hearts (default 2), reducing the penalty toward 0, re-applying the modifier, consuming the stack, and playing `minecraft:entity.player.levelup` at pitch 1.4. No-op (passes) when the player has no penalty.

### Item & Recipe

`tribulation:heart_fragment` — stacks to 16, Rare rarity, glint override on. Custom 16×16 texture (`textures/item/heart_fragment.png`) with its `.glyph` source at `art/glyphs/heart-fragment-32.glyph`. Flat `item/generated` model. Crafted from a plus-shape of 4 Shatter Shards around a central golden apple:
```
 S
SAS     S = tribulation:shatter_shard, A = minecraft:golden_apple → 1 heart_fragment
 S
```

### Config validation

`heartsLostPerDeath`, `minimumHearts`, `heartsRestoredPerFragment` are each clamped to `[1, 20]`.

---

## 15. Soul Inventory (opt-in)

Death destroys the inventory unless items carry the Soulbound enchantment.

### Behavior

`soulInventory.enabled` defaults **false**. When on, `SoulInventoryMixin` hooks `LivingEntity#dropAllDeathLoot` (HEAD) and calls `processDeathInventory` before vanilla drops anything. The handler iterates every inventory slot: items carrying the Soulbound enchantment (`soulboundEnchantment`, default `tribulation:soulbound`) are stashed in a server-side per-player map keyed by slot; all other slots are emptied (**voided, not dropped**). On respawn (`COPY_FROM`, dead branch) stashed items are restored to their original slots.

- If `respectKeepInventory` (default true) and the `keepInventory` gamerule is on, the handler no-ops (vanilla keep-inventory wins).
- If `destroyXp` (default false), XP points/levels are also zeroed.
- If the configured enchantment ID is blank, unparseable, or not in the registry, a warning is logged and **all items are voided** (nothing qualifies as soulbound).

### Soulbound Enchantment

`tribulation:soulbound` — datapack-defined: applies to `#minecraft:enchantable/durability`, weight 1, max level 1, anvil cost 8, slot `any`, exclusive set `#tribulation:exclusive_set/soulbound`, no vanilla effects (it is a pure marker the handler reads). `/tribulation inventory <player>` counts a player's soulbound items.

---

## 16. Totem Interaction

`LivingEntityTotemMixin` hooks `checkTotemDeathProtection`. When a player is saved by a totem:
- If `deathRelief.enabled` **and** `totems.countsAsDeathRelief` (default false), death relief is applied as if the player had died.
- If `hardcoreHearts.enabled` **and not** `totems.protectsHearts` (default true), the hardcore-hearts penalty is applied.

By default a totem fully shields the player from both penalties.

---

## 17. HUD Difficulty Badge

A persistent client HUD element showing difficulty level/tier, following the Concord shared HUD standard.

### Behavior

`TribulationHudOverlay` (a `HudRenderCallback`) draws an **icon-only** badge: the 32×32 `hud_icon.png` blitted at 16×16, tinted by tier color, with a 2px progress bar beneath showing the fraction of ticks toward the next level. Tier color ramp (0→5): white, yellow, orange, light red, red, dark crimson. On a level-up the tint flashes gold and lerps to the tier color over 2 s.

The element occupies **priority 1** (topmost) in the shared HUD strip and contributes a standard 20px + 2px gap when visible. It is hidden during F1 (`hideGui`), any open screen, spectator mode, and death/dying. Position is set by `hudAnchor` (default `TOP_LEFT`) and `hudOffsetX`/`hudOffsetY` (default 4/4).

### Implementation Notes

- The badge conveys the level only via its progress bar and tint — the underlying level value comes from `ClientTribulationState`, synced from the server via `TribulationLevelPayload(level, progressTicks, goalTicks)`. The exact number is available on demand through the tier detail panel (§18) or `/tribulation info`.
- `TribulationAPI.isHudVisible()` / `getHudHeight()` expose the element's state (reflection-backed, cached `MethodHandle`s) so sibling Concord mods stack below it without hardcoding its height.

---

## 18. Tier Detail Panel (Peek)

A hold-to-peek client overlay that shows the full difficulty picture on demand.

### Behavior

Binding `key.tribulation.peek_detail` ("Peek Tier Detail", category Tribulation) defaults to **Left Alt** — unused by vanilla and ergonomic to hold, deliberately not Tab (which holds the vanilla player list this panel imitates). While held — like vanilla's hold-Tab player list, never capturing the mouse or pausing the game — `TierDetailPanelRenderer` (a `HudRenderCallback`) overlays a framed panel showing:

- the exact level, the tier as **"Tier n / 5"** in the tier color, and a progress bar with the precise `progressTicks / goalTicks` figures and percent;
- the level at which the next tier unlocks (or "Maximum tier reached" at tier 5);
- grouped by mob, the abilities that **nearby** scaled hostiles have at the player's current tier.

The "nearby" list is built from an AABB scan around the player (range = `general.mobDetectionRange`, clamped `[1, 64]`, `minecraft`-namespace mobs only), refreshed at most every 10 game ticks and cached, so per-frame cost is a registry lookup, not an entity scan. The ability entries come from `MobAbilities.activeForMob` — the same registry the server applies — so disabling an ability in config also removes it from the panel.

The panel keeps a fixed body size: when more mob types are present than fit, it pages through them with a soft cross-fade and page dots (no scrolling), and a `fitScale` net shrinks the whole panel if it would exceed 92% of the screen. The frame is `textures/gui/tier_detail_panel.png` drawn nine-sliced, tinted with a tier-colored accent. Visibility follows the same rules as the HUD badge (hidden during F1, open screens, spectator/death) and additionally requires the key to be held. Entirely client-side.

---

## 19. Threat Telegraphing Particles

Dangerous mobs give themselves away with a faint particle cue — a secondary threat tell alongside equipment scaling, readable without Jade/WTHIT.

### Behavior

`TribulationParticleEmitter` runs on `ClientTickEvents.END_CLIENT_TICK`, scanning mobs within a fixed 16-block radius that carry the synced `SCALED_TIER` attachment. `ThreatCue.decide` selects the cue:

- a **Big** zombie variant emits the heavy sooty `threat_big` cue and a **Speed** zombie variant emits the pale `threat_speed` streak — both at **any** tier (a hulking or blink-fast zombie is dangerous the moment it spawns), ignoring `minimumTier`;
- otherwise a mob at `tier ≥ minimumTier` (default 4) trails the generic `threat_tier` cursed mote.

Each eligible mob emits with probability `1/particleFrequencyTicks` per client tick (default frequency 40). Variant detection reads only client-syncable attribute modifiers (`SCALE`/`MAX_HEALTH`/`MOVEMENT_SPEED`). Invisible mobs emit nothing. The cue reads data the entity already syncs, so there is no server-tick cost.

### Particles & assets

Three custom `SimpleParticleType`s are registered into `BuiltInRegistries.PARTICLE_TYPE` (`TribulationParticles`): `tribulation:threat_tier`, `tribulation:threat_big`, `tribulation:threat_speed`, each with a particle-definition JSON, a 16×16 sprite (`textures/particle/threat_*.png`, `.glyph` sources under `art/glyphs/threat-*-16.glyph`), and a `ThreatParticle` provider (a translucent `TextureSheetParticle` with TIER/BIG/SPEED style presets). Purely cosmetic — no physics, collision, or light.

### Config

`threatParticles.enabled` (true), `minimumTier` (4), `particleFrequencyTicks` (40). Read client-side but hot-reloadable via `/tribulation reload`.

---

## 20. Group Health Bonus (Multiplayer)

An opt-in multiplayer knob that scales a mob's health — and only its health — with the size of the group it spawns near, so a raid party does not trivialize scaling tuned for one player.

### Behavior

`groupHealthBonus.enabled` defaults **false**. When on, `MobScalingHandler` layers a group bonus on top of the frozen axis scaling for every non-boss mob: it counts the non-spectator players within `general.mobDetectionRange` of the spawn (`ScalingEngine.countNearbyPlayers`, the same proximity and creative/spectator rules as the effective-level scan) and applies

```
groupHealthBonus = min((nearbyPlayers − 1) × perPlayerBonus, maxBonus)
```

as a `tribulation:group_health` `MAX_HEALTH` modifier (`ADD_MULTIPLIED_BASE`). A lone player (or an empty scan) yields 0, so single-player behavior is untouched. Defaults: `perPlayerBonus=0.2` (+20% of base HP per extra player), `maxBonus=1.0` (+100% ceiling).

### Scope

- **Health only.** Damage, speed, and XP are deliberately left alone — the bonus is extra health for the group to chew through, not extra reward or extra threat per hit. Because it lives outside the axes with its own modifier, it does **not** inflate the MAX_HEALTH axis sum that drives XP/loot scaling (§8).
- **Outside the global cap.** The bonus stacks *on top of* `statCaps.maxFactorHealth`, so when it is enabled the axis health cap is not the mob's absolute HP ceiling.
- **Bosses excluded** — they keep the gentler `BossScalingEngine` formula (§3) untouched.
- **Frozen at spawn** like all scaling: the player count is sampled once when the mob loads and never recomputed, so a mob does not gain or shed health as players come and go.
- Trial-spawner mobs are counted by proximity like any other spawn; since trial spawners already add mobs per detected player (§9), a group there faces both more and tougher mobs — tune `perPlayerBonus` down if that compounds too hard.

### Config

`groupHealthBonus.enabled` (false), `perPlayerBonus` (0.2, clamped `≥ 0`), `maxBonus` (1.0, clamped `≥ 0` — a `0` cap disables the bonus).

### Implementation Notes

- `ScalingEngine.computeGroupHealthBonus` is pure and unit-tested; `applyGroupHealthBonus` writes the single modifier and is idempotent (remove-before-add). The count uses `countNearbyPlayers`, the allocation-light twin of the effective-level player scan.

---

## 21. Champions

Above a level threshold, a small share of hostile spawns are promoted to named elites carrying one or two affixes, boosted stats, and a richer reward — a spike of danger that punctuates a high-tier world.

### Behavior

`champions.enabled` defaults **true**. `ChampionManager.tryApply` runs from `MobScalingHandler` **after** normal scaling, for any non-boss `Enemy` (the `Enemy` interface, so hoglins, slimes, ghasts, and phantoms outside the `Monster` hierarchy are eligible too). The gate: the effective player level (including offsets, §1) must be `≥ levelThreshold` (default 50) **and** a `championChance` roll (default 0.05 = 5%) must succeed. On promotion the mob:

1. Draws `1..maxAffixes` (default max 2) distinct affixes uniformly from the enabled pool (count is uniform over `[1, maxAffixes]`, clamped to the pool size) and stores their ids in the persistent, client-synced `CHAMPION_AFFIXES` attachment — both the "is champion" flag and the source of truth for affix behavior.
2. Gains `tribulation:champion_health` / `champion_damage` modifiers (`ADD_MULTIPLIED_TOTAL`, so they stack on the fully-scaled value) of `healthMultiplier` (1.5) on `MAX_HEALTH` and `damageMultiplier` (1.25) on `ATTACK_DAMAGE`.
3. When `showNameTag` (default true), gets an always-visible custom name (e.g. "Vampiric Zombie Champion", or "Vampiric Thorns Zombie Champion" for two affixes).

### Affixes

Five affixes, each with its own `champions.affixes.*` toggle (all default `true`); a zero fraction/strength/power disables that affix's effect independently of its toggle.

| Affix (`id`) | Trigger | Effect | Default knobs |
|---|---|---|---|
| Vampiric (`vampiric`) | `AFTER_DAMAGE`, champion is the direct attacker | Heals `vampiricHealFraction` of unblocked damage dealt (thorns-typed damage excluded, so it never heals off a reflected hit) | `vampiricHealFraction=0.5` |
| Explosive (`explosive`) | `AFTER_DEATH` | An explosion of power `explosivePower` that hurts nearby entities but spares dropped items and XP orbs (cosmetic terrain, `ExplosionInteraction.NONE`) | `explosivePower=2.0` |
| Knockback aura (`knockback_aura`) | Server-tick pulse every `knockbackAuraIntervalTicks` | Knocks non-spectator, non-creative players within `knockbackAuraRadius` away with strength `knockbackAuraStrength` | `knockbackAuraStrength=0.8`, `knockbackAuraRadius=4.0`, `knockbackAuraIntervalTicks=60` |
| Thorns (`thorns`) | `AFTER_DAMAGE`, champion is the direct victim | Reflects `thornsFraction` of unblocked damage back at a living attacker (thorns-typed sources skipped, so two thorns champions cannot ping-pong) | `thornsFraction=0.3` |
| Regenerating (`regenerating`) | Server-tick pulse each second (20 ticks) | Heals `regenHealthPerSecond` while below max HP | `regenHealthPerSecond=1.0` |

### Rewards

A champion's XP reward is multiplied by `xpMultiplier` (default 3.0) in the XP hook (`ChampionManager.applyChampionXp`, applied after the health-factor XP scaling of §8). On death it rolls its **own** loot table `bonusLootRolls` (default 1) extra times — fresh draws, not copies — gated by the `doMobLoot` gamerule. No custom loot tables ship.

### Scope

- Server-authoritative; effects gate on the champion attachment, which syncs to clients only for the particle aura.
- The regen and knockback-aura pulses only scan champions within 16 blocks of a player (an aura no one can see costs nothing) and de-dupe a champion seen from multiple players in one tick.
- Bosses never roll champion (they route through §3 before the champion gate). Modded hostiles that implement `Enemy` are eligible.
- `championChance ≤ 0` or an empty enabled-affix pool means no promotion.

### Config

`champions.enabled` (true), `levelThreshold` (50, `≥ 0`), `championChance` (0.05, `[0, 1]`), `maxAffixes` (2, `≥ 1`), `healthMultiplier` (1.5, `≥ 1`), `damageMultiplier` (1.25, `≥ 1`), `xpMultiplier` (3.0, `≥ 1`), `bonusLootRolls` (1, `≥ 0`), `showNameTag` (true), `particleAura` (true), plus the per-affix `affixes.*` toggles and knobs above.

### Implementation Notes

- `ChampionManager` (roll/apply; the pure gate and affix-selection helpers are unit-tested), `ChampionEffectHandler` (`AFTER_DAMAGE` / `AFTER_DEATH` / server-tick runtime), and the `ChampionAffix` enum (stable ids, per-affix toggles).
- Stat modifiers and the affix attachment persist in NBT and surface in `/tribulation inspect`.
- The particle aura is emitted client-side by `TribulationParticleEmitter` (a vanilla soul-fire mote about every 5 ticks), gated by `champions.particleAura` — it telegraphs at any tier, independent of the threat cues (§19).

---

## 22. Blood Moon

On rare full-moon nights the moon axis stops being a quiet stat curve and becomes an event the player plans around: a night of amplified scaling, denser spawns, no sleep, and a blood-red sky.

### Behavior

`bloodMoon.enabled` defaults **true**. `BloodMoonHandler` checks every second (20 ticks) in the **Overworld only**. At nightfall on a full moon (moon phase 0) it rolls once per in-game day: with probability `chance` (default 0.25) the night becomes a Blood Moon that runs until dawn. The roll is spent whether or not it succeeds (`lastRolledDay`), so one full-moon night gets exactly one roll. While active:

- the moon scaling axis (§1) is multiplied by `moonBonusMultiplier` (default 3.0) via `BloodMoonHandler.moonMultiplier`;
- hostile spawn caps are multiplied by `spawnCapMultiplier` (default 2.0), rounded and never below the vanilla base, through `LocalMobCapCalculatorMixin` and `NaturalSpawnerSpawnStateMixin`;
- sleeping is blocked when `blockSleep` (default true): a bed attempt is denied with `message.tribulation.blood_moon_no_sleep`, anyone already asleep when the event begins is woken with the same message, and each scheduler pass re-ejects any sleeper while the event runs (covering a `blockSleep` flipped on mid-event), so vanilla's night skip — which needs 100 ticks of continuous sleep against the 20-tick pass — can never fire during a Blood Moon;
- when `clientEffects` (default true), every player gets the nightfall warning (`message.tribulation.blood_moon_rises` plus the `tribulation:blood_moon_warning` sting) and clients render the red sky, fog, and moon tint.

Everything reverts at dawn — `shouldEnd` fires the moment it is no longer night.

### Persistence & Multiplayer

Event state lives in `BloodMoonState`, overworld `SavedData` (`tribulation_blood_moon`): the active flag and `lastRolledDay`. The live flag is mirrored into a `volatile` so the per-spawn scaling and spawn-cap hot paths never touch the SavedData lookup; the mirror is rebuilt from disk on server start, so a restart mid-event resumes the night. Start and stop broadcast the tint flag to every player, and a joining player is synced on connect. The client tint honors the server's `clientEffects` toggle — the sync sends "inactive" when it is off, so no client-side config read is needed.

### Scope

- Overworld only — dimensions without a daylight cycle are out of scope by design.
- Admin `/tribulation bloodmoon start` forces the event on and spends the night's roll (so stopping a command-started event will not be instantly re-rolled by the scheduler); `stop` ends it at once (§28).
- Disabling the feature mid-event ends any active Blood Moon on the next tick.

### Config

`bloodMoon.enabled` (true), `chance` (0.25, `[0, 1]`), `moonBonusMultiplier` (3.0, `≥ 1`), `spawnCapMultiplier` (2.0, `≥ 1`), `blockSleep` (true), `clientEffects` (true).

### Implementation Notes

- `BloodMoonHandler` (scheduler plus the pure `rollDue` / `shouldEnd` / `scaledMobCap` helpers, unit-tested), `BloodMoonState` (persistence), `BloodMoonPayload` (S2C tint sync), the two spawn-cap mixins, and client `BloodMoonClientEffects` with `BloodMoonSkyColorMixin` / `BloodMoonFogMixin` / `BloodMoonMoonTintMixin` for the visuals.
- The warning sting is a targeted `ClientboundSoundPacket` (`SoundSource.AMBIENT`), not a broadcast.

---

## 23. Ascendant Shard (opt-in path to difficulty)

The Shatter Shard's dark twin: a craftable consumable that *raises* the user's difficulty level, trading safety for the tougher mobs, champion spawns, and richer XP/loot payoff of a higher tier without waiting out the passive climb.

### Behavior

`ascension.enabled` defaults **true**. Right-clicking `tribulation:ascendant_shard` raises the player's level by `raisePower` (default 25), clamped at `general.maxLevel` (`PlayerDifficultyState.raisePlayerLevel`). An ascendant shard only ever raises: if the player is already at (or above) the cap — or the raise is zero — the use is a **no-op that keeps the item** and shows `item.tribulation.ascendant_shard.at_ceiling`; the level is never pushed down. On a successful raise the stack is consumed (kept in creative), the `ascendant_shards_used` stat and the `ascendant_shard_used` advancement fire, `minecraft:block.amethyst_block.break` plays at pitch 0.8, the client level is synced, `TribulationLevelCallback` fires, and the player sees `item.tribulation.ascendant_shard.used` (before → after). When `ascension.sideEffects` (default **false**), the user also gains Strength II and Resistance II for 10 s (200 ticks).

### Interaction with Death Relief

Death Relief (§12, on by default) bleeds 2 levels back off on each death, so a raised level is a standing wager rather than a permanent gift — intended risk/reward, not a bug.

### Item & Recipe

`tribulation:ascendant_shard` — stacks to 16, Rare rarity, enchantment-glint override on. Custom 16×16 texture (`textures/item/ascendant_shard.png`) with its `.glyph` source at `art/glyphs/ascendant-shard-32.glyph`, flat `item/generated` model. Crafted from a plus-shape of 4 Shatter Shards around a central Nether Star:

```
 S
SNS     S = tribulation:shatter_shard, N = minecraft:nether_star → 1 ascendant_shard
 S
```

So opting into difficulty is a deliberate, expensive choice.

### Config

`ascension.enabled` (true), `raisePower` (25, clamped `≥ 0`), `sideEffects` (false).

### Implementation Notes

- `AscendantShardItem#use` runs server-side only; `PlayerDifficultyState.raisePlayerLevel` clamps at `maxLevel` and never lowers.
- The shift-tooltip detail is built by `AscendantInfoFormatter`, and the recipe-viewer plugins (EMI/REI/JEI) show an info panel for the shard (§30).

---

## 24. Level Decay (opt-in)

An opt-in anti-staleness rule for long-absent players: after a grace period away, a returning player's difficulty level bleeds down, so a level earned months ago does not greet them with mobs they have fallen out of practice against.

### Behavior

`levelDecay.enabled` defaults **false**. Each disconnect stamps a wall-clock anchor in `PlayerDifficultyState` (`lastSeen`); the next login computes the absence and sheds

```
decay = floor(levelsPerDay × max(0, absenceDays − graceDays))
```

levels, floored at `floor`. No decay accrues within `graceDays` (default 7) real-time days; beyond it, `levelsPerDay` (default 2) per day, whole levels only (a partial day's remainder is dropped, not banked). The anchor is re-stamped to *now* on every login, so a re-login a minute later decays nothing more, and mid-session level changes are never decayed retroactively. On an actual drop the level syncs, `levels_lost_to_decay` is awarded, `TribulationLevelCallback` fires, and the player sees `message.tribulation.level_decay` (days away, before, after).

### Scope

- **Only ever pulls down.** A player already at or below `floor` (fresh, or lowered via `/tribulation set`) is never lifted up to it by the clamp.
- **Robust to clock anomalies.** A crashed server whose DISCONNECT never fired decays at worst from the previous login, never from an ancient stamp; negative elapsed time (host clock stepped back) decays nothing; a pathological product saturates at `Integer.MAX_VALUE` instead of overflowing.
- **Disabled is invisible.** When off, nothing is stamped or decayed and the persisted state stays byte-identical to pre-feature saves; a player whose anchor is `NEVER_SEEN` (fresh player, old save, or feature just enabled) never decays on that login — the stamp cycle just begins from it.
- `floor` is clamped to `[0, general.maxLevel]`; non-positive `levelsPerDay` disables decay.

### Config

`levelDecay.enabled` (false), `graceDays` (7.0, clamped `≥ 0`), `levelsPerDay` (2.0, clamped `≥ 0`), `floor` (0, clamped `[0, general.maxLevel]`).

### Implementation Notes

- `LevelDecayHandler` on the `JOIN` / `DISCONNECT` play-connection events; `computeDecayLevels` is pure and unit-tested.
- The `lastSeen` epoch-millisecond anchor persists in `PlayerDifficultyState`, written only when non-`NEVER_SEEN` so a disabled server never grows the extra NBT field.

---

## 25. Environmental Pressure (opt-in)

An opt-in pair of tier-gated pressures that let the world itself push back on high-level players — each gated on the *player's own* level, so a low-level player on the same server is untouched.

### Behavior

`environmentalPressure.enabled` defaults **false** (master switch); each of the two effects has its own toggle and tier threshold, both keyed to the victim player's own stored-level tier.

**Debilitating Strikes** (`debilitatingStrikes`, default on, tier ≥ 3) — hooked on `AFTER_DAMAGE`, so there is no per-tick cost. A landed, unblocked **melee** hit (`mob_attack` / `mob_attack_no_aggro` damage types only — projectiles, explosions, warden booms, guardian beams, and thorns are all excluded by type) from a Tribulation-scaled hostile (one carrying the `SCALED_TIER` attachment) applies short debuffs to the player: Weakness (`applyWeakness` default true, `weaknessDurationTicks` 100, amplifier `weaknessAmplifier` 0) and/or Slowness (`applySlowness` default **false**, `slownessDurationTicks` 100, amplifier `slownessAmplifier` 0).

**Oppressive Nights** (`oppressiveNights`, default on, tier ≥ 4) — two halves:

- *Night senses (mechanical):* hostiles scaled at night in a daylight-cycle dimension near an affected player spawn with a `FOLLOW_RANGE` multiplier (`followRangeMultiplier`, default 1.5) — they notice and pursue from farther. Applied through `tribulation:oppressive_night_senses`, frozen at spawn like all scaling.
- *Night dimming (the tell):* the server computes a per-player darkness strength (`maxDarkness`, default 0.25, hard cap 0.6) and syncs it only when it changes, piggybacked on the level syncs that already fire on join, level-up, decay, relief, shards, and admin commands. Rendering is client-side (`EnvironmentalPressureClientEffects`): it applies only at night in daylight-cycle dimensions and honors both the client's local `clientEnabled` opt-out and the vanilla Darkness Pulsing accessibility slider.

### Scope

- **Per-player.** Both effects read the victim's own tier, so mixed-level servers pressure each player individually.
- **"Night" is pure time math** — the fully-dark band of the day cycle, not `Level.isDay()` — so a thunderstorm at noon never triggers it and a fixed-time modded dimension never counts as permanent night. The mechanical gate opens exactly where the client dimming reaches full strength.
- A `followRangeMultiplier` of 1.0 (or a closed gate) applies nothing; a fresh client defaults to 0 darkness, so an initial 0 is never sent.
- `/tribulation reload` re-evaluates and resyncs every online player's night pressure immediately (`broadcast`).

### Config

`environmentalPressure.enabled` (false). `debilitatingStrikes`: `enabled` (true), `tierThreshold` (3, `≥ 0`), `applyWeakness` (true), `weaknessDurationTicks` (100, `[0, 2400]`), `weaknessAmplifier` (0, `[0, 4]`), `applySlowness` (false), `slownessDurationTicks` (100, `[0, 2400]`), `slownessAmplifier` (0, `[0, 4]`). `oppressiveNights`: `enabled` (true), `tierThreshold` (4, `≥ 0`), `maxDarkness` (0.25, `[0, 0.6]`), `clientEnabled` (true, read only from the client's local config), `followRangeMultiplier` (1.5, `[1, 3]`).

### Implementation Notes

- `EnvironmentalPressureHandler` — the `AFTER_DAMAGE` strike hook, `applyNightSenses` called from the mob-scaling pipeline, and `syncNightPressure` (per-player change dedup), each with a pure or injectable core exercised by gametests — plus `EnvironmentalPressurePayload` (S2C darkness) and client `EnvironmentalPressureClientEffects`.
- The strike / night-senses / darkness tier predicates live on the config class (`strikesActiveAtTier`, `nightDarknessAtTier`, `nightFollowRangeMultiplierAtTier`) and are unit-tested.

---

## 26. Advancements

A dedicated `tribulation` advancement tab records progression milestones, evaluated server-side (works on dedicated servers). Generated by `TribulationAdvancementProvider` (datagen).

| Advancement | Title | Earned by |
|---|---|---|
| `tribulation:root` | Tribulation | join (tick trigger) |
| `tribulation:tier_1` | First Blood | reach tier 1 |
| `tribulation:tier_2` | Hardening | reach tier 2 |
| `tribulation:tier_3` | Trial by Fire | reach tier 3 |
| `tribulation:tier_4` | No Quarter | reach tier 4 |
| `tribulation:tier_5` | Apex Tribulation | reach tier 5 |
| `tribulation:soulbound_survived` | Beyond the Grave | carry a Soulbound item through your own death |
| `tribulation:shatter_shard_used` | A Moment's Mercy | use a Shatter Shard |
| `tribulation:ascendant_shard_used` | Courting Ruin | use an Ascendant Shard |
| `tribulation:heart_fragment_used` | Mended | restore a heart with a Heart Fragment |
| `tribulation:tier_five_mob_killed` | Giant Slayer | kill a mob scaled to the maximum tier |

The tier ladder is chained (each tier parents the previous, off `root`); the five milestone leaves parent off `root`. Tier criteria use `TierReachedCriterion`; the five leaves use simple player triggers in `TribulationCriteria`. Tier-reached fires from `Tribulation.onTierCrossed` when the player's tier changes, which also plays the tier-up sting (§31).

**Statistics.** Custom stats registered by `TribulationStats` and awarded across the systems above (eight total): `highest_level_reached`, `levels_lost_to_death_relief`, `levels_lost_to_decay`, `shatter_shards_used`, `ascendant_shards_used`, `hearts_lost`, `hearts_restored`, `tier_5_mobs_killed`.

---

## 27. Public API — `TribulationAPI`

`com.rfizzle.tribulation.api.TribulationAPI` is the stable (`@Stable`) soft-dependency surface. All methods are safe to call when guarded by `FabricLoader.isModLoaded("tribulation")`.

| Method | Returns | Notes |
|---|---|---|
| `getLevel(ServerPlayer)` | `int` | Authoritative server-side level |
| `getTier(ServerPlayer)` | `int` | Tier 0–5 for the player's level |
| `getEffectiveLevel(Entity)` | `int` | Level that would scale a mob at this entity's position (includes the dimension offset); 0 off-server |
| `getClientLevel()` | `int` | Client-side last-synced level; `−1` if unknown or called off-client (reflection-backed, cached `MethodHandle`) |
| `isHudVisible()` | `boolean` | Whether the HUD badge is drawn right now (client only) |
| `getHudHeight()` | `int` | HUD stacking contribution in px (20+2 visible, else 0) |
| `getScaledTier(Entity)` | `OptionalInt` | Frozen tier the entity was scaled to; empty if never scaled |
| `wasScaledByTribulation(Entity)` | `boolean` | Whether the `SCALED_TIER` attachment is present |
| `isBossScaled(Entity)` | `boolean` | Whether boss-formula modifiers are attached |
| `getTierThresholds()` | `int[]` | Fresh `[tier1..tier5]` array (config-driven) |
| `getMobScalingSummary(Entity)` | `Optional<MobScalingSummary>` | Frozen tier, boss flag, health/damage modifier sums; empty if unscaled |
| `setArmorDropChanceProvider(provider)` | `void` | Override armor drop chance (last writer wins) |
| `setWeaponDropChanceProvider(provider)` | `void` | Override weapon drop chance (last writer wins) |

**`TribulationLevelCallback`** (`@Stable`): a Fabric `Event` firing `onLevelChanged(ServerPlayer, oldLevel, newLevel)`. Server-only; fires on playtime progression, death relief, Shatter Shard use, `/tribulation set`, and `/tribulation reset`.

Drop-chance providers (`ArmorDropChanceProvider`, `WeaponDropChanceProvider`) are functional interfaces; a misbehaving provider (throws or returns non-finite) falls back to the configured default and never breaks mob spawning. The three reflection-backed client accessors (`getClientLevel`/`isHudVisible`/`getHudHeight`) resolve their target once into a cached `MethodHandle` and log the first failure, so the per-render calls from sibling mods stay cheap.

---

## 28. Commands

### `/tribulation` Command Tree

Root `tribulation`. Permission 0 = any player (self-service); permission 2 = operator.

| Command | Perm | Description |
|---|---|---|
| `/tribulation info` | 0 | Your level, tier, and progress to next level (and hearts if penalty active) |
| `/tribulation hearts` | 0 | Your own heart-penalty status |
| `/tribulation level <player>` | 2 | Another player's level and time to next |
| `/tribulation set <player> <level>` | 2 | Set a player's level (clamped to `[0, maxLevel]`, resets tick counter) |
| `/tribulation reset <player>` | 2 | Reset a player to level 0 |
| `/tribulation reload` | 2 | Hot-reload `config/tribulation.json` |
| `/tribulation config` | 2 | Print a summary of current configuration |
| `/tribulation debug <player>` | 2 | Full scaling breakdown at the player's position (reference mob: zombie), including the live moon factor or the reason the moon axis is inactive |
| `/tribulation inspect` | 2 | Inspect the `Mob` you're looking at within 10 blocks (type, HP, scaling factor, variant, every Tribulation modifier) |
| `/tribulation hearts <player>` | 2 | Another player's heart penalty |
| `/tribulation hearts <player> restore <amount>` | 2 | Restore lost half-hearts |
| `/tribulation hearts <player> reset` | 2 | Clear a player's heart penalty |
| `/tribulation inventory <player>` | 2 | Count soulbound items in a player's inventory |
| `/tribulation bloodmoon` | 2 | Whether a Blood Moon is currently active |
| `/tribulation bloodmoon start` | 2 | Force-start a Blood Moon in the current world |
| `/tribulation bloodmoon stop` | 2 | End the active Blood Moon |

### Implementation Notes

- Registered via `CommandRegistrationCallback`. All mutations route through `PlayerDifficultyState` (persist + `setDirty()`) and sync the client + fire `TribulationLevelCallback` where appropriate.
- `/tribulation reload` re-reads the config; existing mobs keep their modifiers, new mobs use new values.
- The website `commands.json` page lists the full, correct tree.

---

## 29. Configuration

Config lives at `config/tribulation.json` (created with defaults on first launch), is hot-reloadable, and is editable in-game via the ModMenu/Cloth Config screen. `configVersion` is **14**; `ConfigMigrator` migrates older files on load (v0→v14: v2 adds hardcore-hearts/soul-inventory, v3 adds trial-spawner, v4 adds raid-scaling, v5 adds threat-particles, v6 renames `xpAndLoot`→`xp` and drops the extra-loot fields, v7 adds blood-moon, v8 adds champions, v9 adds biome-offsets, v10 adds pack-tactics, v11 adds structure-boosts, v12 adds level-decay, v13 adds group-health-bonus, v14 adds environmental-pressure). Unknown/missing fields are filled with defaults and clamped to valid ranges on load; unknown legacy keys are silently dropped.

### General

| Key | Type | Default |
|---|---|---|
| `general.maxLevel` | int | 250 |
| `general.levelUpTicks` | int | 72000 |
| `general.mobDetectionRange` | double | 32.0 |
| `general.scalingMode` | enum | NEAREST |
| `general.excludedEntities` | list | `["the_bumblezone:cosmic_crystal_entity"]` |
| `general.notifyLevelUp` | bool | true |
| `general.notifyLevelUpShowTier` | bool | true |
| `dimensionOffsets` | map\<id,int\> | `{the_nether: 25, the_end: 40}` |
| `biomeOffsets` | map\<id-or-#tag,int\> | `{deep_dark: 30}` |
| `structureBoosts.marginBlocks` | int `[0,128]` | 16 |
| `structureBoosts.boosts` | map\<id-or-#tag,int\> | `{fortress:20, bastion_remnant:20, monument:15, trial_chambers:15, ancient_city:30, end_city:25}` |

Offset map values are each clamped `≥ 0`; unparseable keys are logged and dropped. An empty `biomeOffsets`/`structureBoosts.boosts` map disables that feature and its spawn-path lookups entirely.

### Scaling Axes

| Key | Type | Default |
|---|---|---|
| `timeScaling.enabled` | bool | true |
| `distanceScaling.enabled` | bool | true |
| `distanceScaling.startingDistance` | double | 1000 |
| `distanceScaling.increasingDistance` | double | 300 |
| `distanceScaling.distanceFactor` | double | 0.1 |
| `distanceScaling.maxDistanceFactor` | double | 1.5 |
| `distanceScaling.excludeInOtherDimensions` | bool | true |
| `heightScaling.enabled` | bool | true |
| `heightScaling.startingHeight` | double | 62 |
| `heightScaling.heightDistance` | double | 30 |
| `heightScaling.heightFactor` | double | 0.1 |
| `heightScaling.maxHeightFactor` | double | 0.5 |
| `heightScaling.positiveHeightScaling` | bool | true |
| `heightScaling.negativeHeightScaling` | bool | true |
| `heightScaling.excludeInOtherDimensions` | bool | true |
| `moonPhaseScaling.enabled` | bool | true |
| `moonPhaseScaling.maxBonus` | double | 0.1 |
| `moonPhaseScaling.surfaceOnly` | bool | false |
| `moonPhaseScaling.surfaceY` | double | 63 |
| `bloodMoon.enabled` | bool | true |
| `bloodMoon.chance` | double `[0,1]` | 0.25 |
| `bloodMoon.moonBonusMultiplier` | double `≥1` | 3.0 |
| `bloodMoon.spawnCapMultiplier` | double `≥1` | 2.0 |
| `bloodMoon.blockSleep` | bool | true |
| `bloodMoon.clientEffects` | bool | true |
| `groupHealthBonus.enabled` | bool | **false** |
| `groupHealthBonus.perPlayerBonus` | double `≥0` | 0.2 |
| `groupHealthBonus.maxBonus` | double `≥0` | 1.0 |

### Stat Caps & Tiers

| Key | Type | Default |
|---|---|---|
| `statCaps.maxFactorHealth` | double | 4.0 |
| `statCaps.maxFactorDamage` | double | 4.5 |
| `statCaps.maxFactorSpeed` | double | 0.5 |
| `statCaps.maxFactorProtection` | double | 2.0 |
| `statCaps.maxFactorFollowRange` | double | 1.5 |
| `tiers.tier1` … `tier5` | int | 50 / 100 / 150 / 200 / 250 |

### Per-Mob Scaling & Toggles

- `scaling` — map of mob key (or full ID for overrides) → `MobScaling` (`healthRate/healthCap`, `damageRate/damageCap`, `speedRate/speedCap`, `followRangeRate/followRangeCap`, `armorRate/armorCap`, `toughnessRate/toughnessCap`). Defaults per §1 table.
- `mobToggles` — map of the 21 mob keys → bool, all default `true`.
- `unlistedHostileMobs.enabled` (true), `unlistedHostileMobs.excludedNamespaces` (`[]`), `unlistedHostileMobs.scaling` (health+damage only).

### Death Penalties

| Key | Type | Default |
|---|---|---|
| `deathRelief.enabled` | bool | true |
| `deathRelief.amount` | int | 2 |
| `deathRelief.cooldownTicks` | int | 6000 |
| `deathRelief.minimumLevel` | int | 0 |
| `levelDecay.enabled` | bool | **false** |
| `levelDecay.graceDays` | double `≥0` | 7.0 |
| `levelDecay.levelsPerDay` | double `≥0` | 2.0 |
| `levelDecay.floor` | int `[0,maxLevel]` | 0 |
| `shards.enabled` | bool | true |
| `shards.dropStartLevel` | int | 25 |
| `shards.shardPower` | int | 5 |
| `shards.dropChance` | double | 0.005 |
| `shards.sideEffects` | bool | true |
| `ascension.enabled` | bool | true |
| `ascension.raisePower` | int `≥0` | 25 |
| `ascension.sideEffects` | bool | false |
| `hardcoreHearts.enabled` | bool | **false** |
| `hardcoreHearts.heartsLostPerDeath` | int | 2 |
| `hardcoreHearts.minimumHearts` | int | 2 |
| `hardcoreHearts.heartsRestoredPerFragment` | int | 2 |
| `soulInventory.enabled` | bool | **false** |
| `soulInventory.soulboundEnchantment` | string | `tribulation:soulbound` |
| `soulInventory.destroyXp` | bool | false |
| `soulInventory.respectKeepInventory` | bool | true |
| `totems.countsAsDeathRelief` | bool | false |
| `totems.protectsHearts` | bool | true |

### Variants, Bosses, XP/Loot

| Key | Type | Default |
|---|---|---|
| `specialZombies.enabled` | bool | true |
| `specialZombies.bigZombieChance` | int (%) | 10 |
| `specialZombies.bigZombieSize` | double | 1.3 |
| `specialZombies.bigZombieBonusHealth` | double | 10 |
| `specialZombies.bigZombieBonusDamage` | double | 2 |
| `specialZombies.bigZombieSlowness` | double | 0.7 |
| `specialZombies.speedZombieChance` | int (%) | 10 |
| `specialZombies.speedZombieSpeedFactor` | double | 1.3 |
| `specialZombies.speedZombieMalusHealth` | double | 10 |
| `specialSkeletons.enabled` | bool | true |
| `specialSkeletons.deadeyeSkeletonChance` | int (%) | 10 |
| `specialSkeletons.deadeyeSkeletonAttackInterval` | int | 20 |
| `specialSkeletons.deadeyeSkeletonMalusHealth` | double | 10 |
| `specialSkeletons.bruteSkeletonChance` | int (%) | 10 |
| `specialSkeletons.bruteSkeletonAttackInterval` | int | 60 |
| `specialSkeletons.bruteSkeletonBonusHealth` | double | 10 |
| `specialSkeletons.bruteSkeletonBonusKnockbackResistance` | double | 0.5 |
| `specialSkeletons.bruteSkeletonSize` | double | 1.3 |
| `bosses.affectBosses` | bool | true |
| `bosses.bossMaxFactor` | double | 3.0 |
| `bosses.bossDistanceFactor` | double | 0.1 |
| `bosses.bossTimeFactor` | double | 0.3 |
| `xp.xpMultiplier` | double | 1.0 |
| `champions.enabled` | bool | true |
| `champions.levelThreshold` | int `≥0` | 50 |
| `champions.championChance` | double `[0,1]` | 0.05 |
| `champions.maxAffixes` | int `≥1` | 2 |
| `champions.healthMultiplier` | double `≥1` | 1.5 |
| `champions.damageMultiplier` | double `≥1` | 1.25 |
| `champions.xpMultiplier` | double `≥1` | 3.0 |
| `champions.bonusLootRolls` | int `≥0` | 1 |
| `champions.showNameTag` | bool | true |
| `champions.particleAura` | bool | true |
| `champions.affixes.vampiric` / `.vampiricHealFraction` | bool / double `[0,1]` | true / 0.5 |
| `champions.affixes.explosive` / `.explosivePower` | bool / double `≥0` | true / 2.0 |
| `champions.affixes.knockbackAura` / `.knockbackAuraStrength` / `.knockbackAuraRadius` / `.knockbackAuraIntervalTicks` | bool / double `≥0` / double `≥0` / int `≥1` | true / 0.8 / 4.0 / 60 |
| `champions.affixes.thorns` / `.thornsFraction` | bool / double `≥0` | true / 0.3 |
| `champions.affixes.regenerating` / `.regenHealthPerSecond` | bool / double `≥0` | true / 1.0 |

### Equipment

| Key | Type | Default |
|---|---|---|
| `armorEquipment.enabled` | bool | true |
| `armorEquipment.materialRollMode` | enum | PER_MOB |
| `armorEquipment.armorDropChance` | double `[0,2]` | 0.0 |
| `armorEquipment.armorCeiling` | double | 24.0 |
| `armorEquipment.toughnessCeiling` | double | 15.0 |
| `armorEquipment.tiers.*` | ArmorTier | per §7 |
| `weaponEquipment.enabled` | bool | true |
| `weaponEquipment.weaponDropChance` | double `[0,2]` | 0.0 |
| `weaponEquipment.damageCeiling` | double | 20.0 |
| `weaponEquipment.tiers.*` | WeaponTier | per §7 |

### Trial Spawner, Raid, Pack Tactics, Abilities, HUD & Particles

| Key | Type | Default |
|---|---|---|
| `trialSpawner.enabled` | bool | true |
| `trialSpawner.ominousUpgrade.enabled` | bool | false |
| `trialSpawner.ominousUpgrade.chance` | float | 0.10 |
| `trialSpawner.ominousUpgrade.minimumTier` | int | 3 |
| `raidScaling.enabled` | bool | true |
| `raidScaling.patrolBonusRate` | int | 2 |
| `raidScaling.extraWaveTierThreshold` | int | 4 |
| `raidScaling.extraWaveCount` | int | 1 |
| `packTactics.enabled` | bool | true |
| `packTactics.tierThreshold` | int | 3 |
| `packTactics.alertRadius` | double `[0,64]` | 16.0 |
| `packTactics.groupSizeBonus` | int `[0,16]` | 2 |
| `packTactics.eligibleMobs` | string list | zombie, skeleton, spider |
| `environmentalPressure.enabled` | bool | **false** |
| `environmentalPressure.debilitatingStrikes.enabled` | bool | true |
| `environmentalPressure.debilitatingStrikes.tierThreshold` | int `≥0` | 3 |
| `environmentalPressure.debilitatingStrikes.applyWeakness` | bool | true |
| `environmentalPressure.debilitatingStrikes.weaknessDurationTicks` | int `[0,2400]` | 100 |
| `environmentalPressure.debilitatingStrikes.weaknessAmplifier` | int `[0,4]` | 0 |
| `environmentalPressure.debilitatingStrikes.applySlowness` | bool | false |
| `environmentalPressure.debilitatingStrikes.slownessDurationTicks` | int `[0,2400]` | 100 |
| `environmentalPressure.debilitatingStrikes.slownessAmplifier` | int `[0,4]` | 0 |
| `environmentalPressure.oppressiveNights.enabled` | bool | true |
| `environmentalPressure.oppressiveNights.tierThreshold` | int `≥0` | 4 |
| `environmentalPressure.oppressiveNights.maxDarkness` | double `[0,0.6]` | 0.25 |
| `environmentalPressure.oppressiveNights.clientEnabled` | bool (client) | true |
| `environmentalPressure.oppressiveNights.followRangeMultiplier` | double `[1,3]` | 1.5 |
| `abilities.*` | bool | true (29 flags, one per ability in §4) |
| `enableTierHud` | bool (client) | true |
| `hudAnchor` | enum (client) | TOP_LEFT |
| `hudOffsetX` / `hudOffsetY` | int (client) | 4 / 4 |
| `threatParticles.enabled` | bool | true |
| `threatParticles.minimumTier` | int | 4 |
| `threatParticles.particleFrequencyTicks` | int | 40 |

Drop-chance fields accept `[0, 2]` — a value ≥1.0 requests a guaranteed + pristine drop (vanilla's PRESERVE threshold).

---

## 30. Compatibility

### Required
- Fabric Loader ≥0.16.10, Fabric API, Minecraft ~1.21.1, Java ≥21. **Zero hard third-party dependencies.**

### Optional Integrations
- **ModMenu + Cloth Config** — config screen (`ModMenuIntegration`); enum dropdowns render friendly title-cased labels.
- **Jade / WTHIT** — mob-scaling tooltip overlay (`JadeTribulationPlugin`, `WthitClientPlugin`/`WthitCommonPlugin`) showing the looked-at mob's scaling state via a shared `MobScalingDataCollector` + `TribulationTooltipFormatter`.
- **EMI / REI / JEI** — Shatter Shard / Ascendant Shard / Heart Fragment recipe and info display (`EmiShardPlugin`, `ReiShardPlugin`, `JeiShardPlugin`).

### Modded Mob Support
- Modded hostile mobs (any `Monster` not in `excludedNamespaces`) get conservative health+damage fallback scaling automatically.
- Per-mob full-ID overrides in `scaling` work for any namespace.
- Modded mobs receive no tier abilities, variants, or equipment — those are vanilla-only.

### Migration
Tribulation is a single self-contained jar; users should remove any prior mob-scaling mod before installing.

---

## 31. Sound Design

Tribulation registers **two** custom `SoundEvent`s (`TribulationSounds`), each with a `/sfx` synthesis source and a subtitle:

| Sound event | Trigger | Delivery | Subtitle |
|---|---|---|---|
| `tribulation:tier_up` (`art/audio/tier-up.sfx`, a rising arpeggio milestone sting) | `Tribulation.onTierCrossed` fires on a tier change | Targeted `ClientboundSoundPacket` (`SoundSource.PLAYERS`) to the **single** leveling player, layered over the HUD gold flash and tier toast | `tribulation.subtitle.tier_up` |
| `tribulation:blood_moon_warning` (the nightfall event cue) | A Blood Moon begins (§22) | Targeted `ClientboundSoundPacket` (`SoundSource.AMBIENT`) to each player, gated by `bloodMoon.clientEffects` | `tribulation.subtitle.blood_moon_warning` |

The item-use feedbacks reuse vanilla sounds whose character already fits:

| Feature | Vanilla Sound | Pitch |
|---|---|---|
| Shatter Shard — use | `minecraft:block.amethyst_block.break` | 1.2 |
| Ascendant Shard — use | `minecraft:block.amethyst_block.break` | 0.8 |
| Heart Fragment — use | `minecraft:entity.player.levelup` | 1.4 |

All other feedback is visual (HUD tint flash, threat particles, chat/action-bar messages) or relies on the vanilla sounds attached to the abilities themselves (charged-creeper, trident).

---

## 32. Localization

All user-facing text uses translation keys in `assets/tribulation/lang/en_us.json`.

| Pattern | Example | Used for |
|---|---|---|
| `item.tribulation.*` | `item.tribulation.shatter_shard.used` | Item names, tooltips, use messages |
| `message.tribulation.*` | `message.tribulation.level_up_tier` | Level-up / heart-loss system messages |
| `config.tribulation.*` | `config.tribulation.tiers.tier1` | Cloth Config screen labels/categories |
| `enchantment.tribulation.soulbound` | — | Soulbound enchantment name + `.desc` |
| `stat.tribulation.*` | `stat.tribulation.shatter_shards_used` | Custom statistics |
| `advancements.tribulation.*` | `advancements.tribulation.tier_5.title` | Advancement titles/descriptions |
| `key.tribulation.peek_detail` | — | Peek Tier Detail keybind |
| `subtitles`/`tribulation.subtitle.tier_up` | — | Tier-up sound subtitle |
| `itemGroup.tribulation.main` | — | Creative tab |

Parameterized messages use `%s`-style placeholders. Command output (`/info`, `/debug`, `/inspect`, `/config`) is built as literal `String.format` text in `TribulationCommand`, not translation keys.

---

## 33. Testing Strategy

### Unit Tests (`src/test/`, fabric-loader-junit)
Pure-math and config logic with no Minecraft runtime: scaling-mode resolution, dimension-/biome-/structure-offset resolution, moon-factor math, group-health-bonus math, ability-manager dispatch, config parse/migrate, command formatting, scaling-engine factor math and attribute-bridge, boss-scaling math, tier classification, shard/XP/loot roll gates, zombie- and skeleton-variant rolls, champion gate/affix-selection/XP math, blood-moon roll/end/spawn-cap math, level-decay math, environmental-pressure tier predicates and night-time math, soul-inventory and hardcore-hearts logic, payload round-trip, HUD overlay geometry/color, threat-cue decisions.

### Gametests (`src/gametest/`, Fabric Gametest API)
`MobScalingGameTest`, `DeathPenaltiesGameTest`, `DeathReliefGameTest`, `TotemGameTest`, `APIGameTest`, `ArmorEquipmentGameTest`, `WeaponEquipmentGameTest`, `StatisticsGameTest`, `TrialSpawnerGameTest`, `SkeletonVariantGameTest`, `RaidScalingGameTest`, `PackTacticsGameTest`, `AdvancementsGameTest`, `AbilitiesGameTest`, `ParticleRegistrationGameTest`, `ChampionGameTest`, `BloodMoonGameTest`, `LevelDecayGameTest`, `EnvironmentalPressureGameTest` — verify end-to-end behavior on a running server (scaling application, penalty and death-relief flows, totem interaction, API surface, equipment rolls, trial-spawner and raid/patrol scaling, variant rolls, advancement grants, ability dispatch, pack-tactics shared aggro, particle registration, stat awards, champion promotion and affix effects, blood-moon transitions, sleeper ejection, and spawn-cap boost, offline level decay, and environmental-pressure strikes and night senses).
