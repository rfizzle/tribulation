# Death Penalties — Implementation TODO

Reference: `companions/tribulation/doc/DEATH_PENALTIES_SPEC.md`

The soulbound enchantment is expected to come from an external mod (e.g., `meridian:tether`).
Tribulation ships a built-in `tribulation:soulbound` only as a fallback — conditionally
registered when the config field matches the default. No enchantment implementation work here.

---

## Phase 1: Config + Migration

- [ ] Add `HardcoreHearts` inner class to `TribulationConfig`
  - `enabled` (false), `heartsLostPerDeath` (2), `minimumHearts` (2),
    `heartsRestoredPerFragment` (2), `fragmentDropChance` (0.002), `fragmentDropStartLevel` (50)
- [ ] Add `SoulInventory` inner class to `TribulationConfig`
  - `enabled` (false), `soulboundEnchantment` ("tribulation:soulbound"),
    `destroyXp` (false), `respectKeepInventory` (true)
- [ ] Add both as fields on `TribulationConfig`
- [ ] Update `fillDefaults()` to null-check both new sections
- [ ] Update `validate()` to clamp all new numeric fields
- [ ] Add migration in `ConfigMigrator`: v1→v2, add empty `hardcoreHearts` and
  `soulInventory` JSON objects. Bump `CURRENT_VERSION` to 2, default `configVersion` to 2
- [ ] Tests: `TribulationConfigTest` — fillDefaults, validate clamping
- [ ] Tests: `ConfigMigratorTest` — v1→v2 migration, idempotent on v2

## Phase 2: Persistence

- [ ] Add `public int heartsLost = 0` to `PlayerDifficultyState.PlayerData`
- [ ] Add NBT key `"HeartsLost"` to `save()` and `load()` (default 0 when missing)
- [ ] Add `getHeartsLost(UUID)`, `addHeartsLost(UUID, int, int)`, `restoreHearts(UUID, int)`,
  `resetHearts(UUID)` methods with `setDirty()` calls
- [ ] Tests: `PlayerDifficultyStateTest` — serialization round-trip, backward compat,
  addHeartsLost floors, restoreHearts floors at 0

## Phase 3: Hardcore Hearts Handler

- [ ] Create `event/HardcoreHeartsHandler.java`
- [ ] `AFTER_DEATH` listener: increment `heartsLost`, send chat message
- [ ] `COPY_FROM` listener (respawn): apply `tribulation:hardcore_hearts` modifier
  on `generic.max_health` (`ADD_VALUE`, amount = `-heartsLost`)
- [ ] `JOIN` listener: same modifier application for login/reconnect
- [ ] Register in `Tribulation.onInitialize()`
- [ ] Tests: `HardcoreHeartsHandlerTest` — loss on death, floor at minimum,
  no-op when disabled, independent of death relief

## Phase 4: Heart Fragment Item

- [ ] Create `item/HeartFragmentItem.java` (follow `ShatterShardItem` pattern)
- [ ] `use()`: restore hearts, consume stack, re-apply modifier, play sound
- [ ] No-op + message when `heartsLost == 0`
- [ ] Register in `TribulationItems`
- [ ] Add model JSON + texture (16x16: golden apple base + hardcore heart overlay)
- [ ] Add fragment drops to mob kill handler (gated by config fields)
- [ ] Add crafting recipe (4 shatter shards + 1 golden apple → 1 heart fragment)
- [ ] Tests: `HeartFragmentItemTest` — restore works, floors at 0, no-op when full

## Phase 5: Soul Inventory Handler + Mixin

- [ ] Create `event/SoulInventoryHandler.java`
  - Resolve `soulboundEnchantment` config string to `ResourceLocation` at load/reload
  - Log warning if enchantment not in registry (fail-closed: all items voided)
- [ ] Create `mixin/SoulInventoryMixin.java` targeting `ServerPlayer#dropAllDeathLoot`
  - Guard: enabled, respectKeepInventory + gamerule
  - Stash soulbound items in `WeakHashMap<UUID, List<ItemStack>>`, clear the rest
  - If `destroyXp` is true, zero out XP before vanilla drops it
- [ ] `COPY_FROM` handler: restore stashed soulbound items to same slots on new player
- [ ] Add mixin to `tribulation.mixins.json`
- [ ] Register in `Tribulation.onInitialize()`
- [ ] Conditional `tribulation:soulbound` enchantment registration (only when config
  uses default ID) — data-driven JSON gated via dynamic resource pack or registry removal
- [ ] Tests: `SoulInventoryHandlerTest` — items voided, soulbound kept, keepInventory
  respected, disabled no-op, destroyXp, external ID resolution, invalid ID warning

## Phase 6: Commands

- [ ] `/tribulation hearts <player>` (perm 2) — show heartsLost + current max HP
- [ ] `/tribulation hearts restore <player> <amount>` (perm 2) — restore + re-apply modifier
- [ ] `/tribulation hearts reset <player>` (perm 2) — clear penalty + remove modifier
- [ ] `/tribulation inventory <player>` (perm 2) — count soulbound items
- [ ] Update `formatConfigSummary()` and `formatPlayerInfo()` for new features
- [ ] Tests: `TribulationCommandTest` — format helpers

## Phase 7: Mod Menu

- [ ] `addHardcoreHearts()` — enabled, heartsLostPerDeath, minimumHearts,
  heartsRestoredPerFragment, fragmentDropChance, fragmentDropStartLevel
- [ ] `addSoulInventory()` — enabled, soulboundEnchantment (string), destroyXp,
  respectKeepInventory
- [ ] Wire both into `getModConfigScreenFactory()`

## Phase 8: Lang Keys

- [ ] Add all `config.tribulation.hardcore_hearts.*` keys
- [ ] Add all `config.tribulation.soul_inventory.*` keys
- [ ] Add `item.tribulation.heart_fragment` + tooltip/used/full keys
- [ ] Add `message.tribulation.heart_lost` + `heart_lost_floor` keys

## Phase 9: Game Tests

- [ ] `hardcoreHearts_deathReducesMaxHealth`
- [ ] `hardcoreHearts_fragmentRestores`
- [ ] `soulInventory_nonSoulboundVoided`
- [ ] `soulInventory_soulboundRetained`
- [ ] `soulInventory_respectsKeepInventory`
