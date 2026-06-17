# Tribulation — Feature Spec

Minecraft 1.21.1 Fabric mod. Difficulty overhaul: formula-driven mob scaling and opt-in death penalties.

**Asset philosophy:** Tribulation is overwhelmingly a behavioral mod — its surface is attribute math, AI tweaks, and player progression, not new visual content — so it ships almost entirely vanilla assets and reuses vanilla mechanics where they already read correctly. Only the genuinely mod-specific visuals are custom pixel art, authored through Concord's glyph pipeline (`/glyph`, the `mc-textures` skill, concord `design/DESIGN-SYSTEM.md` §8, with `.glyph` sources kept beside the masters in `art/glyphs/`): the `heart_fragment`/`heart_fragment_overlay` item texture and the HUD difficulty badge (`textures/gui/hud_icon.png`, a 32×32 master blitted at 16×16, tinted per tier). The `shatter_shard` item currently reuses the vanilla `minecraft:item/prismarine_shard` texture as a placeholder; a bespoke `/glyph` shard texture is planned. The mod registers no custom blocks and no custom `SoundEvent`s — every cue is a vanilla sound chosen to fit the moment (amethyst shatter for the shard, player level-up for the fragment), because synthesizing a bespoke sound would only make these one-shot feedbacks feel artificial. Custom synthesized cues via `/sfx` (concord `design/DESIGN-SYSTEM.md` §9) would be added only where a sound benefits from its own identity; nothing in the shipped feature set crosses that bar. Scaled-mob visual identity (Big/Speed zombies) reuses vanilla rendering: Big zombies drive `Attributes.SCALE`, which natively grows both model and hitbox, and charged creepers use the vanilla powered flag.

---

## 1. Mob Scaling — Three Axes

Every hostile mob's combat attributes are boosted at spawn time by three independent axes whose factors combine per attribute. Scaling is **frozen at spawn** — computed once when the entity loads, written as persistent attribute modifiers, and never recomputed for that entity.

### Behavior

On `ServerEntityEvents.ENTITY_LOAD`, `MobScalingHandler` processes each `Mob` that does not already carry the `tribulation_processed` scoreboard tag. It resolves a `MobScaling` profile for the entity, derives an effective player level, computes per-attribute factors across the three axes, applies them as `AttributeModifier`s, tops the mob's current HP up to its new max, and tags it processed.

The six scaled attributes:

| Key | Vanilla attribute | Modifier operation |
|---|---|---|
| `health` | `MAX_HEALTH` | `ADD_MULTIPLIED_BASE` |
| `damage` | `ATTACK_DAMAGE` | `ADD_MULTIPLIED_BASE` |
| `speed` | `MOVEMENT_SPEED` | `ADD_MULTIPLIED_BASE` |
| `follow_range` | `FOLLOW_RANGE` | `ADD_MULTIPLIED_BASE` |
| `armor` | `ARMOR` | `ADD_VALUE` |
| `toughness` | `ARMOR_TOUGHNESS` | `ADD_VALUE` |

Each axis applies as its own modifier, with ID `tribulation:<axis>_<attribute>` (e.g. `tribulation:time_health`, `tribulation:distance_armor`). Re-applying scaling overwrites the same IDs, so values never stack on reload.

### The Three Axes

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

### Per-Attribute Combination and Global Cap

For each attribute the three axis factors are summed and clipped to that attribute's **global cap** (`statCaps`). When clipping occurs, the three axes are scaled down **proportionally** so the per-axis breakdown still sums to the clipped total (preserves inspect/debug readability).

- For `ADD_MULTIPLIED_BASE` attributes the global cap is a dimensionless multiplier (`maxFactorHealth=4.0` → up to +400% of base).
- For `ADD_VALUE` attributes (`armor`, `toughness`) the per-axis distance/height factors are first multiplied by the attribute's own per-mob cap to convert them into native armor points, and the global cap is `maxFactorProtection × perMobCap`.

Global caps (`statCaps`): health `4.0`, damage `4.5`, speed `0.5`, protection `2.0`, follow_range `1.5`.

### Effective Level Resolution

`ScalingEngine.getEffectiveLevel` finds the player level that drives a mob's scaling, governed by `general.scalingMode` and `general.mobDetectionRange` (default 32 blocks). Returns 0 if the range is ≤0 or no qualifying player is in range.

| Mode | Behavior |
|---|---|
| `NEAREST` (default) | The nearest non-spectator player within range (vanilla `getNearestPlayer`, which excludes spectators) |
| `AVERAGE` | Floored mean of all non-spectator players' levels within range |
| `MAX` | Highest level among non-spectator players within range |

Creative players count in all three modes; spectators never do.

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
- Bosses route through a separate engine (§3).
- The frozen-at-spawn tag means existing mobs keep their stats across `/tribulation reload`; only newly loaded mobs use updated config.

### Implementation Notes

- Pure math lives in `ScalingEngine` as static methods independent of Minecraft attribute types (for unit testing); world-aware application is layered on top.
- The MAX_HEALTH axis sum (`readHealthScalingFactor`) is the canonical "how scaled is this mob" proxy, read back from persistent modifiers — it survives chunk reload and feeds XP/loot scaling and `/inspect` without extra per-entity state.

---

## 2. Player Difficulty Level (Time Progression)

Each player carries a difficulty **level** (0–`maxLevel`, default 250) that advances with cumulative playtime and feeds the time axis of mob scaling.

### Behavior

`Tribulation` registers a `ServerTickEvents.END_SERVER_TICK` handler that fires every 20 ticks (1 s). When `timeScaling.enabled` and at least one player is online, every online player's tick counter advances by 20. Crossing `levelUpTicks` (default 72000 ticks = 1 hour) grants one level, carrying the remainder. At `maxLevel` the counter is zeroed and no further levels are gained.

On a level gain the new level is synced to the client and `TribulationLevelCallback` fires. If `general.notifyLevelUp`, the player receives a chat message: `message.tribulation.level_max` at the cap, `message.tribulation.level_up_tier` when `notifyLevelUpShowTier` and the tier changed, otherwise `message.tribulation.level_up`.

### Persistence

Level state lives in `PlayerDifficultyState`, a `SavedData` stored on the overworld (`tribulation_players`). Per-player fields: `Level` (int), `Tick` (tick counter toward next level), `LastDeathTick` (long, death-relief cooldown anchor; sentinel `Long.MIN_VALUE` = never died), `HeartsLost` (int half-hearts). Survives restarts. Level is **not** reset on death (only reduced by the opt-in penalties).

### Scope

- Per-player and global to the player — the same level applies to every mob spawned near them regardless of dimension. Time scaling applies in **all** dimensions.
- On player join, the current level is synced to the client for the HUD.

---

## 3. Boss Scaling

Bosses use a separate, gentler formula with uniform rates.

### Behavior

A mob whose `EntityType` is in the `#c:bosses` tag (ships with `minecraft:ender_dragon`, `minecraft:wither`, `minecraft:elder_guardian`, `minecraft:warden`) routes through `BossScalingEngine` instead of the per-mob engine. When `bosses.affectBosses` is false, bosses are skipped entirely (no scaling, no processed tag short-circuit other than the normal path).

Only **health and damage** are scaled, over **time and distance** axes (no height). Both axes use flat boss rates rather than per-mob rates:
```
bossTime     = playerLevel × bossTimeFactor          (default 0.3)
bossDistance = distanceLevels × bossDistanceFactor    (default 0.1, same start/step thresholds as §1)
total        = min(bossTime + bossDistance, bossMaxFactor)   (default cap 3.0)
```
When the sum exceeds the cap, the two axes scale down proportionally. Distance ignores `excludeInOtherDimensions`, so boss scaling is live in the Nether/End. Modifiers use `boss_time_<attr>` / `boss_distance_<attr>` IDs (all `ADD_MULTIPLIED_BASE`) so inspect/debug can distinguish them. HP is topped to max after scaling.

### Scope

- Bosses receive no tier abilities, zombie variants, or equipment — only the boss health/damage buff.
- Boss-formula scaling is detectable via `TribulationAPI.isBossScaled` (reads the boss-axis modifiers).

---

## 4. Tier-Gated Mob Abilities

At five level thresholds, scaled vanilla mobs unlock special behaviors keyed off the mob's frozen tier.

### Tiers

`TierManager` classifies a player level into tier 0–5. Thresholds (`tiers`, inclusive on the lower bound): tier1 `50`, tier2 `100`, tier3 `150`, tier4 `200`, tier5 `250`. A player at exactly the threshold is in that tier. A mob's tier is frozen at spawn alongside its scaling (stored in the `SCALED_TIER` attachment).

### Ability Table

`AbilityManager.applyAbilities` runs only for the 21 vanilla mob keys whose toggle is enabled; each ability also checks its own `abilities.*` flag. Abilities are expressed as attribute modifiers (`tribulation:ability_*` IDs), infinite-duration effects, vanilla setters, equipment, or scoreboard tags consumed by mixins, so they persist in NBT.

| Mob | Tier ≥ | Ability | Mechanism |
|---|---|---|---|
| Zombie | 1 | Reinforcements | `+0.10` `SPAWN_REINFORCEMENTS_CHANCE` (ADD_VALUE) |
| Zombie | 3 | Door breaking | `setCanBreakDoors(true)` |
| Zombie | 5 | Sprinting | `+0.15` `MOVEMENT_SPEED` (×base) |
| Creeper | 1 | Shorter fuse | max swell set to 15 ticks (via `CreeperAccessor`) |
| Creeper | 5 | Charged | 25% chance to set powered flag |
| Skeleton | 2 | Sword switch | bow → stone sword |
| Skeleton | 4 | Flame arrows | infinite Fire Resistance + `setRemainingFireTicks(MAX)` (self ablaze; arrows ignite) |
| Spider | 2 | Web placing | `tribulation_web` tag (on-hit cobweb, mobGriefing-gated) |
| Spider | 3 | Crop trample | `tribulation_crop_trample` tag (on-hit destroys crops/farmland, mobGriefing-gated) |
| Spider | 5 | Leap attack | `+0.5` `JUMP_STRENGTH` (×base) |
| Cave Spider | 2 | Web placing | `tribulation_web` tag |
| Husk | 4 | Hunger II | `tribulation_hunger2` tag (upgrades vanilla Hunger I → II on hit) |
| Drowned | 2 | Trident | empty main hand → trident |
| Zombified Piglin | 5 | Aggro range | `+0.5` `FOLLOW_RANGE` (×base) |
| Hoglin | 1 | Knockback resist | `+0.5` `KNOCKBACK_RESISTANCE` (ADD_VALUE) |
| Zoglin | 3 | Fire resist | infinite Fire Resistance |
| Vindicator | 4 | Resistance | infinite Resistance I |
| Wither Skeleton | 3 | Sprint | `+0.15` `MOVEMENT_SPEED` (×base) |
| Wither Skeleton | 4 | Fire Aspect | Fire Aspect I on main-hand weapon (if held) |
| Piglin | 2 | Crossbow | empty main hand → crossbow |

The corresponding `abilities.*` config flags all default to `true`.

### Implementation Notes

- On-hit abilities (`spiderWebPlacing`, `spiderCropTrample`) are driven by `MobAbilityMixin` on `Mob#doHurtTarget`, gated by the `mobGriefing` gamerule. `huskHunger` is driven by `HuskAbilityMixin` on `Husk#doHurtTarget`.
- The `abilities` config also exposes `creeperShorterFuse`, `creeperCharged`, `zombifiedPiglinAggro`, `zoglinFireResist`, etc. — every entry in the table maps to a flag.
- Skeleton flame arrows are implemented by keeping the skeleton permanently on fire (fire-immune) so its arrows inherit ignition; there is no dedicated arrow-modification code.

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
- Variants are detectable via `/tribulation inspect` (Big wins if both ID families are somehow present, for deterministic output).

---

## 6. Tier-Driven Equipment

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

## 7. Bonus XP and Extra Loot

Scaled mobs reward proportionally more XP and, optionally, duplicate loot.

### Behavior

**XP** (`xpAndLoot.extraXp`, default true): `LivingEntityExperienceMixin` hooks `LivingEntity#getExperienceReward` and multiplies a mob's base XP by `1 + min(healthFactor, maxXpFactor − 1)`, where `healthFactor` is the mob's MAX_HEALTH axis sum. Capped at `maxXpFactor` (default 2.0 → up to 2× XP). Result is rounded to the nearest int; only `Mob`s with positive scaling are affected.

**Extra loot** (`xpAndLoot.dropMoreLoot`, default **false**): on `AFTER_DEATH`, `XpLootHandler` rolls `min(healthFactor × moreLootChance, maxLootChance)` (defaults `moreLootChance=0.02`, `maxLootChance=0.7`). On success it scans freshly-spawned `ItemEntity`s (age ≤1 tick, within 1.5 blocks) and duplicates one at random.

### Scope

- XP scaling reads the persistent health modifier, so it survives chunk reload/restart with no extra state.
- Loot duplication is off by default.

---

## 8. Death Relief

A rubber-band penalty that lowers difficulty on death.

### Behavior

On `AFTER_DEATH` for a player (when `deathRelief.enabled`, default true), the player's level is reduced by `deathRelief.amount` (default 2), floored at `deathRelief.minimumLevel` (default 0). A cooldown of `deathRelief.cooldownTicks` (default 6000 = 5 min) since the last qualifying death suppresses rapid-suicide farming; `cooldownTicks=0` means every death counts. All death causes qualify — the cooldown is the only gate.

A successful application syncs the level, fires `TribulationLevelCallback`, and awards the `levels_lost_to_death_relief` stat. The death-relief cooldown anchor (`LastDeathTick`) updates whenever a death is off-cooldown, even if the level was already at the floor.

### Scope

- The level floor is shared with Shatter Shards (`deathRelief.minimumLevel`).

---

## 9. Shatter Shards

A rare mob drop that voluntarily lowers difficulty.

### Behavior

**Drop** (`shards.enabled`, default true): on `AFTER_DEATH` of a `Mob` killed (credit or direct) by a `ServerPlayer`, if the killer's level ≥ `shards.dropStartLevel` (default 25) and a `shards.dropChance` roll (default 0.005 = 0.5%) succeeds, a Shatter Shard item entity spawns at the mob.

**Use:** right-clicking the `tribulation:shatter_shard` item reduces the player's level by `shards.shardPower` (default 5), floored at `deathRelief.minimumLevel`. The stack is consumed (kept in creative). Plays `minecraft:block.amethyst_block.break` at pitch 1.2. If `shards.sideEffects` (default true), the user also gets 10 s (200 ticks) of Slowness II, Mining Fatigue II, and Weakness II. The action-bar message reports the level change or that the player is already at the floor.

### Item

`tribulation:shatter_shard` — stacks to 16, Uncommon rarity, enchantment-glint override on. Currently reuses the vanilla `minecraft:item/prismarine_shard` texture as a placeholder; a bespoke 16×16 `/glyph` texture with its `.glyph` source under `art/glyphs/` is planned. No custom model layering.

### Scope

- The drop roll happens at death; there is no spawn-time pre-marking of carrier mobs.

---

## 10. Hardcore Hearts (opt-in)

Permanent max-health loss on death, recoverable with Heart Fragments.

### Behavior

`hardcoreHearts.enabled` defaults **false**. When on, each player death (`AFTER_DEATH`) increases `HeartsLost` by `heartsLostPerDeath` (default 2 half-hearts), floored so the player keeps at least `minimumHearts` half-hearts of max HP (default 2). The penalty is applied as a `tribulation:hardcore_hearts` `MAX_HEALTH` modifier (`ADD_VALUE`, `−heartsLost`), re-applied on join and after respawn (`COPY_FROM`). A message (`message.tribulation.heart_lost` or `…heart_lost_floor`) and the `hearts_lost` stat fire on loss.

**Heart Fragment** (`tribulation:heart_fragment`): right-click restores `heartsRestoredPerFragment` half-hearts (default 2), reducing the penalty toward 0, re-applying the modifier, consuming the stack, and playing `minecraft:entity.player.levelup` at pitch 1.4. No-op (passes) when the player has no penalty.

### Item & Recipe

`tribulation:heart_fragment` — stacks to 16, Rare rarity, glint override on. Custom texture with an overlay layer (`heart_fragment.png` + `heart_fragment_overlay.png`; both a flat and a layered model exist). Crafted from a plus-shape of 4 Shatter Shards around a central golden apple:
```
 S
SAS     S = tribulation:shatter_shard, A = minecraft:golden_apple → 1 heart_fragment
 S
```

### Config validation

`heartsLostPerDeath`, `minimumHearts`, `heartsRestoredPerFragment` are each clamped to `[1, 20]`.

---

## 11. Soul Inventory (opt-in)

Death destroys the inventory unless items carry the Soulbound enchantment.

### Behavior

`soulInventory.enabled` defaults **false**. When on, `SoulInventoryMixin` hooks `LivingEntity#dropAllDeathLoot` (HEAD) and calls `processDeathInventory` before vanilla drops anything. The handler iterates every inventory slot: items carrying the Soulbound enchantment (`soulboundEnchantment`, default `tribulation:soulbound`) are stashed in a server-side per-player map keyed by slot; all other slots are emptied (**voided, not dropped**). On respawn (`COPY_FROM`, dead branch) stashed items are restored to their original slots.

- If `respectKeepInventory` (default true) and the `keepInventory` gamerule is on, the handler no-ops (vanilla keep-inventory wins).
- If `destroyXp` (default false), XP points/levels are also zeroed.
- If the configured enchantment ID is blank, unparseable, or not in the registry, a warning is logged and **all items are voided** (nothing qualifies as soulbound).

### Soulbound Enchantment

`tribulation:soulbound` — datapack-defined: applies to `#minecraft:enchantable/durability`, weight 1, max level 1, anvil cost 8, slot `any`, exclusive set `#tribulation:exclusive_set/soulbound`, no vanilla effects (it is a pure marker the handler reads). `/tribulation inventory <player>` counts a player's soulbound items.

---

## 12. Totem Interaction

`LivingEntityTotemMixin` hooks `checkTotemDeathProtection`. When a player is saved by a totem:
- If `deathRelief.enabled` **and** `totems.countsAsDeathRelief` (default false), death relief is applied as if the player had died.
- If `hardcoreHearts.enabled` **and not** `totems.protectsHearts` (default true), the hardcore-hearts penalty is applied.

By default a totem fully shields the player from both penalties.

---

## 13. HUD Difficulty Badge

A persistent client HUD element showing difficulty level/tier, following the Concord shared HUD standard.

### Behavior

`TribulationHudOverlay` (a `HudRenderCallback`) draws an **icon-only** badge: the 32×32 `hud_icon.png` blitted at 16×16, tinted by tier color, with a 2px progress bar beneath showing the fraction of ticks toward the next level. Tier color ramp (0→5): white, yellow, orange, light red, red, dark crimson. On a level-up the tint flashes gold and lerps to the tier color over 2 s.

The element occupies **priority 1** (topmost) in the shared HUD strip and contributes a standard 20px + 2px gap when visible. It is hidden during F1 (`hideGui`), any open screen, spectator mode, and death/dying. Position is set by `hud.anchor` (default `TOP_LEFT`) and `hud.offsetX`/`offsetY` (default 4/4).

### Implementation Notes

- The badge conveys the level numerically only via its progress bar and tint — the displayed level value comes from `ClientTribulationState`, synced from the server via `TribulationLevelPayload(level, progressTicks, goalTicks)`.
- `TribulationAPI.isHudVisible()` / `getHudHeight()` expose the element's state (reflection-backed) so sibling Concord mods stack below it without hardcoding its height. The DESIGN doc describes a `Lv. 127 · T3` text label; the shipped badge is icon + progress bar only (no inline text) — see discrepancy note below.

---

## 14. Public API — `TribulationAPI`

`com.rfizzle.tribulation.api.TribulationAPI` is the stable (`@Stable`) soft-dependency surface. All methods are safe to call when guarded by `FabricLoader.isModLoaded("tribulation")`.

| Method | Returns | Notes |
|---|---|---|
| `getLevel(ServerPlayer)` | `int` | Authoritative server-side level |
| `getTier(ServerPlayer)` | `int` | Tier 0–5 for the player's level |
| `getEffectiveLevel(Entity)` | `int` | Level that would scale a mob at this entity's position; 0 off-server |
| `getClientLevel()` | `int` | Client-side last-synced level; `−1` if unknown or called off-client (reflection-backed) |
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

Drop-chance providers (`ArmorDropChanceProvider`, `WeaponDropChanceProvider`) are functional interfaces; a misbehaving provider (throws or returns non-finite) falls back to the configured default and never breaks mob spawning. The README also shows `getScaledTier` returning an `OptionalInt` and a level-change listener — both match the shipped surface.

---

## 15. Commands

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
| `/tribulation debug <player>` | 2 | Full scaling breakdown at the player's position (reference mob: zombie) |
| `/tribulation inspect` | 2 | Inspect the `Mob` you're looking at within 10 blocks (type, HP, scaling factor, variant, every Tribulation modifier) |
| `/tribulation hearts <player>` | 2 | Another player's heart penalty |
| `/tribulation hearts <player> restore <amount>` | 2 | Restore lost half-hearts |
| `/tribulation hearts <player> reset` | 2 | Clear a player's heart penalty |
| `/tribulation inventory <player>` | 2 | Count soulbound items in a player's inventory |

### Implementation Notes

- Registered via `CommandRegistrationCallback`. All mutations route through `PlayerDifficultyState` (persist + `setDirty()`) and sync the client + fire `TribulationLevelCallback` where appropriate.
- `/tribulation reload` re-reads the config; existing mobs keep their modifiers, new mobs use new values.
- The shipped root command is `level` (not the README's `set`-as-lookup framing); the README command table omits `level`, `reset`, `config`, `inventory`, and the `hearts <player> restore/reset` subcommands and lists only six — see discrepancy note. The website `commands.json` page lists the full, correct tree.

---

## 16. Configuration

Config lives at `config/tribulation.json` (created with defaults on first launch), is hot-reloadable, and is editable in-game via the ModMenu/Cloth Config screen. `configVersion` is 2; `ConfigMigrator` migrates older files on load. Unknown/missing fields are filled with defaults and clamped to valid ranges on load.

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
| `shards.enabled` | bool | true |
| `shards.dropStartLevel` | int | 25 |
| `shards.shardPower` | int | 5 |
| `shards.dropChance` | double | 0.005 |
| `shards.sideEffects` | bool | true |
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
| `bosses.affectBosses` | bool | true |
| `bosses.bossMaxFactor` | double | 3.0 |
| `bosses.bossDistanceFactor` | double | 0.1 |
| `bosses.bossTimeFactor` | double | 0.3 |
| `xpAndLoot.extraXp` | bool | true |
| `xpAndLoot.maxXpFactor` | double | 2.0 |
| `xpAndLoot.dropMoreLoot` | bool | false |
| `xpAndLoot.moreLootChance` | double | 0.02 |
| `xpAndLoot.maxLootChance` | double | 0.7 |

### Equipment

| Key | Type | Default |
|---|---|---|
| `armorEquipment.enabled` | bool | true |
| `armorEquipment.materialRollMode` | enum | PER_MOB |
| `armorEquipment.armorDropChance` | double `[0,2]` | 0.0 |
| `armorEquipment.armorCeiling` | double | 24.0 |
| `armorEquipment.toughnessCeiling` | double | 15.0 |
| `armorEquipment.tiers.*` | ArmorTier | per §6 |
| `weaponEquipment.enabled` | bool | true |
| `weaponEquipment.weaponDropChance` | double `[0,2]` | 0.0 |
| `weaponEquipment.damageCeiling` | double | 20.0 |
| `weaponEquipment.tiers.*` | WeaponTier | per §6 |

### Abilities & HUD

- `abilities.*` — 19 boolean flags (one per ability in §4), all default `true`.
- `hud.enabled` (true), `hud.anchor` (TOP_LEFT), `hud.offsetX` (4), `hud.offsetY` (4).

Drop-chance fields accept `[0, 2]` — a value ≥1.0 requests a guaranteed + pristine drop (vanilla's PRESERVE threshold).

---

## 17. Compatibility

### Required
- Fabric Loader ≥0.16.10, Fabric API, Minecraft ~1.21.1, Java ≥21. **Zero hard third-party dependencies.**

### Optional Integrations
- **ModMenu + Cloth Config** — config screen (`ModMenuIntegration`).
- **Jade / WTHIT** — mob-scaling tooltip overlay (`JadeTribulationPlugin`, `WthitClientPlugin`/`WthitCommonPlugin`) showing the looked-at mob's scaling state via a shared `MobScalingDataCollector` + `TribulationTooltipFormatter`.
- **EMI / REI / JEI** — Shatter Shard / Heart Fragment recipe display (`EmiShardPlugin`, `ReiShardPlugin`, `JeiShardPlugin`).

### Modded Mob Support
- Modded hostile mobs (any `Monster` not in `excludedNamespaces`) get conservative health+damage fallback scaling automatically.
- Per-mob full-ID overrides in `scaling` work for any namespace.
- Modded mobs receive no tier abilities, variants, or equipment — those are vanilla-only.

### Migration
Tribulation is a single self-contained jar; users should remove any prior mob-scaling mod before installing.

---

## 18. Sound Design

Tribulation registers **no custom `SoundEvent`s**. The only two cues are item-use feedbacks, mapped to vanilla sounds whose character already fits:

| Feature | Event | Vanilla Sound | Pitch |
|---|---|---|---|
| Shatter Shard — use | crystalline shatter | `minecraft:block.amethyst_block.break` | 1.2 |
| Heart Fragment — use | vitality restored | `minecraft:entity.player.levelup` | 1.4 |

All other feedback is visual (HUD tint flash, chat/action-bar messages) or relies on the vanilla sounds attached to the abilities themselves (e.g. charged-creeper, trident). A bespoke `/sfx`-synthesized cue would be considered only where a sound benefits from its own identity per concord `design/DESIGN-SYSTEM.md` §9; nothing shipped meets that bar.

---

## 19. Localization

All user-facing text uses translation keys in `assets/tribulation/lang/en_us.json`.

| Pattern | Example | Used for |
|---|---|---|
| `item.tribulation.*` | `item.tribulation.shatter_shard.used` | Item names, tooltips, use messages |
| `message.tribulation.*` | `message.tribulation.level_up_tier` | Level-up / heart-loss system messages |
| `config.tribulation.*` | `config.tribulation.tiers.tier1` | Cloth Config screen labels/categories |
| `enchantment.tribulation.soulbound` | — | Soulbound enchantment name + `.desc` |
| `stat.tribulation.*` | `stat.tribulation.shatter_shards_used` | Custom statistics |
| `itemGroup.tribulation.main` | — | Creative tab |

Parameterized messages use `%s`-style placeholders. Command output (`/info`, `/debug`, `/inspect`, `/config`) is built as literal `String.format` text in `TribulationCommand`, not translation keys.

---

## 20. Statistics

Custom stats registered by `TribulationStats` and awarded across the systems above: `highest_level_reached`, `levels_lost_to_death_relief`, `shatter_shards_used`, `hearts_lost`, `hearts_restored`, `tier_5_mobs_killed`.

---

## 21. Persistence & Networking Architecture

- **Player state** — `PlayerDifficultyState extends SavedData`, stored on the overworld (`tribulation_players`): level, tick counter, last-death tick, hearts lost. Survives restarts.
- **Per-entity state** — `TribulationAttachments.SCALED_TIER` records a mob's frozen tier; the `tribulation_processed` (and `tribulation_variant_processed`, `tribulation_armor_processed`, `tribulation_weapon_processed`) scoreboard tags prevent re-processing on reload. Scaling values themselves live as persistent attribute modifiers, so they survive reload for free.
- **Networking** — one S2C payload, `TribulationLevelPayload(level, progressTicks, goalTicks)`, sent on join and on any level change to drive the client HUD. Registered in `TribulationNetworking`.

---

## 22. Testing Strategy

### Unit Tests (`src/test/`, fabric-loader-junit)
Pure-math and config logic with no Minecraft runtime: scaling-mode resolution, ability-manager dispatch, config parse/migrate, command formatting, scaling-engine factor math and attribute-bridge, boss-scaling math, tier classification, shard/XP/loot roll gates, zombie-variant rolls, soul-inventory and hardcore-hearts logic, payload round-trip, HUD overlay geometry/color.

### Gametests (`src/gametest/`, Fabric Gametest API)
`MobScalingGameTest`, `DeathPenaltiesGameTest`, `TotemGameTest`, `APIGameTest`, `ArmorEquipmentGameTest`, `WeaponEquipmentGameTest`, `StatisticsGameTest` — verify end-to-end behavior on a running server (scaling application, penalty flows, totem interaction, API surface, equipment rolls, stat awards).

---

## 23. Future Considerations
- Tier threshold icon set and death-penalty mode icons (DESIGN §3) are designed but not yet rendered in-game; only the HUD badge and two item textures ship.
- No custom blocks ship; the mod's footprint is intentionally behavioral.
