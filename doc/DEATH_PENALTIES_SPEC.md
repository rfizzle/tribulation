# Death Penalties Spec

Two optional, independently toggleable subsystems that raise the stakes on player death beyond the existing death-relief level reduction.

## Feature 1: Hardcore Hearts

On each death the player permanently loses max health. Hearts can be restored through gameplay.

### Behaviour

1. When a player dies and `hardcoreHearts.enabled` is `true`:
   - Subtract `hardcoreHearts.heartsLostPerDeath` half-hearts from the player's max HP.
   - Floor at `hardcoreHearts.minimumHearts` half-hearts (default 2 = 1 heart). Never reduce below this.
   - Persist the penalty to `PlayerDifficultyState` (new field `heartsLost`).
2. On respawn, apply a persistent `generic.max_health` attribute modifier (`tribulation:hardcore_hearts`) with operation `ADD_VALUE` and amount `-heartsLost`. This is re-applied every login/respawn by reading the saved state.
3. The current HP is clamped to the new max on respawn (vanilla already does this).

### Heart Restoration

Players regain lost hearts by consuming a new item: **Heart Fragment**.

- Restores `hardcoreHearts.heartsRestoredPerFragment` half-hearts (default 2 = 1 heart).
- Caps at the vanilla 20 max HP — cannot exceed baseline.
- Obtained via:
  - Rare mob drop gated by difficulty level (like Shatter Shards). Uses config field `hardcoreHearts.fragmentDropChance` (default 0.002) and `hardcoreHearts.fragmentDropStartLevel` (default 50).
  - Crafted from Shatter Shards (4 shards + 1 golden apple → 1 Heart Fragment). Recipe registered via datagen.
  - Admin command `/tribulation hearts restore <player> <amount>`.

#### Item Texture

16x16 composite sprite: vanilla golden apple base with a hardcore heart icon (from the HUD) overlaid. Built from vanilla assets — no external art needed. Model at `assets/tribulation/models/item/heart_fragment.json`, texture at `assets/tribulation/textures/item/heart_fragment.png`.

### Config Section

```java
public static class HardcoreHearts {
    public boolean enabled = false;
    public int heartsLostPerDeath = 2;        // half-hearts
    public int minimumHearts = 2;             // half-hearts (floor)
    public int heartsRestoredPerFragment = 2; // half-hearts per item use
    public double fragmentDropChance = 0.002;
    public int fragmentDropStartLevel = 50;
}
```

Default is **disabled** — this is a punishing opt-in mode.

### Persistence (PlayerDifficultyState changes)

Add to `PlayerData`:

```java
public int heartsLost = 0; // cumulative half-hearts removed
```

NBT keys: `"HeartsLost"` (int). Backward-compatible: missing key defaults to 0.

### Attribute Modifier

| Field | Value |
|-------|-------|
| ID | `tribulation:hardcore_hearts` |
| Attribute | `generic.max_health` |
| Operation | `ADD_VALUE` |
| Amount | `-heartsLost` (negative) |

Applied in a `ServerPlayerEvents.COPY_FROM` handler (fires on respawn and dimension change) and on `ServerPlayConnectionEvents.JOIN` (first login / reconnect).

### Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/tribulation hearts <player>` | 2 | Show how many hearts the player has lost |
| `/tribulation hearts restore <player> <amount>` | 2 | Restore `amount` half-hearts (reduce penalty) |
| `/tribulation hearts reset <player>` | 2 | Clear all heart penalties |

### Integration with Death Relief

Both systems fire on the same death event (`AFTER_DEATH`). Order:
1. Death relief reduces difficulty level (existing).
2. Hardcore hearts applies heart loss (new).

They are independent — enabling one does not require the other.

---

## Feature 2: Soul Inventory (Void-on-Death)

On death, inventory is destroyed instead of dropped as item entities — unless an item is enchanted with Soulbound.

### Behaviour

1. When `soulInventory.enabled` is `true`, register a handler on `ServerLivingEntityEvents.ALLOW_DEATH` (or `AFTER_DEATH` + mixin if timing requires it) that:
   - Iterates all inventory slots (main, armor, offhand).
   - Items with the `tribulation:soulbound` enchantment are retained (stay in the same slot post-respawn).
   - All other items are **cleared** (not dropped). XP is still dropped per vanilla rules unless `soulInventory.destroyXp` is true.
2. Suppress vanilla's item-drop-on-death behaviour via a mixin on `ServerPlayer#getInventory().dropAll()` / `ServerPlayer#drop(...)` when the feature is active. The cleanest hook is `PlayerInventory#dropAll` — return early after filtering.

### Soulbound Enchantment

#### Built-in: `tribulation:soulbound`

Registered **only** when `soulInventory.soulboundEnchantment` is set to `"tribulation:soulbound"` (the default). If the config points to a different enchantment ID, the built-in enchantment is not registered and will not appear in the game.

| Property | Value |
|----------|-------|
| Max level | 1 |
| Rarity | VERY_RARE |
| Treasure | true (not in enchanting table) |
| Tradeable | true (librarian villagers) |
| Applicable to | All equippable items + tools + weapons (uses `enchantable/durability` item tag or similar broad predicate) |
| Incompatible with | Vanishing Curse |

The enchantment does nothing at runtime outside the death handler — it's purely a tag that the Soul Inventory system checks for.

#### Registration (1.21.1 data-driven enchantments)

In 1.21.1, enchantments are data-driven via `data/<namespace>/enchantment/<name>.json`. The file lives at:

```
data/tribulation/enchantment/soulbound.json
```

The enchantment's effect is purely server-logic (the death handler checks for it), so no special `EnchantmentEffectComponent` entries are needed — only the metadata fields (supported items, weight, slots, etc.).

**Conditional registration:** Since data-driven enchantments are loaded from the datapack unconditionally, the registration is gated by a dynamic resource-pack approach: the enchantment JSON is injected via `ResourceManagerHelper.registerBuiltinResourcePack` only when the config field matches the default. Alternatively, the enchantment JSON is always present but a `ServerLifecycleEvents.SERVER_STARTED` hook removes it from the registry when the config points elsewhere.

#### External enchantment

When `soulboundEnchantment` is set to another ID (e.g., `meridian:tether`), the handler resolves it from the enchantment registry at runtime. If the enchantment is not found (mod not loaded, typo), a warning is logged and **all items are treated as non-soulbound** (fail-closed).

### Inventory Retention Mechanics

On the `COPY_FROM` event (which fires when the new ServerPlayer is created from the dead one):

1. Read the dead player's inventory.
2. For each slot: if the ItemStack has the enchantment specified by `soulInventory.soulboundEnchantment` (resolved at config load), copy it to the same slot on the new player.
3. All other slots remain empty on the new player (vanilla already clears them).

This approach avoids needing to suppress vanilla drop logic at all — vanilla drops happen on the *old* player entity, and `keepInventory` gamerule already gates that. The mixin instead targets `Player#dropAllDeathLoot` to clear non-soulbound items *before* vanilla would drop them, effectively voiding them.

### Mixin Target

```java
@Mixin(ServerPlayer.class)
public abstract class SoulInventoryMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void tribulation$voidNonSoulbound(DamageSource source, CallbackInfo ci) {
        // If soulInventory is disabled or keepInventory is on, bail.
        // Otherwise: iterate inventory, clear non-soulbound stacks,
        // stash soulbound stacks in a transient holder for COPY_FROM.
    }
}
```

The soulbound stacks are stored temporarily in a `ThreadLocal` or a static `WeakHashMap<UUID, List<ItemStack>>` keyed by player UUID, then restored in the `COPY_FROM` handler.

### Config Section

```java
public static class SoulInventory {
    public boolean enabled = false;
    public String soulboundEnchantment = "tribulation:soulbound"; // enchantment ID to check
    public boolean destroyXp = false;         // also void XP on death
    public boolean respectKeepInventory = true; // if gamerule keepInventory is on, skip entirely
}
```

Default is **disabled**.

The `soulboundEnchantment` field accepts any valid `namespace:path` enchantment ID. This allows the soul-inventory logic to use an enchantment from another mod (e.g., `meridian:tether`) instead of bundling `tribulation:soulbound`. When set to an external enchantment:
- The `data/tribulation/enchantment/soulbound.json` registration is still shipped but becomes inert (unused by the handler).
- To skip registering it entirely, set `soulboundEnchantment` to the external ID and no code references the built-in one at runtime.

At startup, the handler resolves the configured string to a `ResourceLocation` and looks up the enchantment in the registry. If the enchantment doesn't exist (mod not loaded, typo), a warning is logged and **all items are treated as non-soulbound** (fail-closed — no silent "everything is kept").

### Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/tribulation inventory <player>` | 2 | Show count of soulbound items in the player's inventory |

---

## Config Migration (v1 → v2)

Add the two new sections with their defaults. The migrator adds empty JSON objects:

```java
// v1 → v2: add hardcoreHearts and soulInventory sections
json -> {
    if (!json.has("hardcoreHearts")) {
        json.add("hardcoreHearts", new JsonObject());
    }
    if (!json.has("soulInventory")) {
        json.add("soulInventory", new JsonObject());
    }
}
```

Bump `CURRENT_VERSION` to 2 and `TribulationConfig.configVersion` default to 2.

---

## Mod Menu / Cloth Config Additions

### New Category: "Hardcore Hearts"

| Entry | Type | Default | Min | Max |
|-------|------|---------|-----|-----|
| Enabled | boolean | false | — | — |
| Hearts Lost per Death | int | 2 | 1 | 20 |
| Minimum Hearts | int | 2 | 1 | 20 |
| Hearts Restored per Fragment | int | 2 | 1 | 20 |
| Fragment Drop Chance | double | 0.002 | 0.0 | 1.0 |
| Fragment Drop Start Level | int | 50 | 0 | — |

### New Category: "Soul Inventory"

| Entry | Type | Default | Min | Max |
|-------|------|---------|-----|-----|
| Enabled | boolean | false | — | — |
| Soulbound Enchantment | string | `tribulation:soulbound` | — | — |
| Destroy XP | boolean | false | — | — |
| Respect keepInventory | boolean | true | — | — |

### Lang Keys

```json
{
    "config.tribulation.category.hardcore_hearts": "Hardcore Hearts",
    "config.tribulation.hardcore_hearts.enabled": "Enabled",
    "config.tribulation.hardcore_hearts.hearts_lost_per_death": "Hearts Lost per Death (half-hearts)",
    "config.tribulation.hardcore_hearts.minimum_hearts": "Minimum Hearts (half-hearts)",
    "config.tribulation.hardcore_hearts.hearts_restored_per_fragment": "Hearts Restored per Fragment",
    "config.tribulation.hardcore_hearts.fragment_drop_chance": "Fragment Drop Chance",
    "config.tribulation.hardcore_hearts.fragment_drop_start_level": "Fragment Drop Start Level",

    "config.tribulation.category.soul_inventory": "Soul Inventory",
    "config.tribulation.soul_inventory.enabled": "Enabled",
    "config.tribulation.soul_inventory.soulbound_enchantment": "Soulbound Enchantment ID",
    "config.tribulation.soul_inventory.destroy_xp": "Destroy XP on Death",
    "config.tribulation.soul_inventory.respect_keep_inventory": "Respect keepInventory Gamerule",

    "item.tribulation.heart_fragment": "Heart Fragment",
    "item.tribulation.heart_fragment.tooltip": "Right-click to restore a lost heart.",
    "item.tribulation.heart_fragment.used": "Restored %s hearts (%s/%s max HP)",
    "item.tribulation.heart_fragment.full": "You have not lost any hearts.",

    "enchantment.tribulation.soulbound": "Soulbound",
    "enchantment.tribulation.soulbound.desc": "Item is kept on death when Soul Inventory is active.",

    "message.tribulation.heart_lost": "You lost a heart. (%s/%s remaining)",
    "message.tribulation.heart_lost_floor": "You are at minimum hearts."
}
```

---

## New Files Summary

| Path | Purpose |
|------|---------|
| `src/main/java/.../event/HardcoreHeartsHandler.java` | Death listener + respawn/join modifier application |
| `src/main/java/.../event/SoulInventoryHandler.java` | Death listener for inventory voiding + soulbound retention |
| `src/main/java/.../item/HeartFragmentItem.java` | Consumable item to restore hearts |
| `src/main/java/.../mixin/SoulInventoryMixin.java` | Suppress vanilla death drops when soul inventory is active |
| `src/main/resources/data/tribulation/enchantment/soulbound.json` | Enchantment definition (conditionally active — only when config uses default ID) |
| `src/main/resources/data/tribulation/recipe/heart_fragment.json` | Crafting recipe (4 shatter shards + golden apple) |

---

## Test Plan

### Unit Tests (JUnit, no server)

| Test Class | Cases |
|-----------|-------|
| `HardcoreHeartsHandlerTest` | Heart loss on death reduces `heartsLost`; floors at minimum; zero loss when disabled; respects cooldown independence from death relief |
| `HeartFragmentItemTest` | Restore reduces `heartsLost`; cannot exceed 0 (full health); returns pass when already full |
| `SoulInventoryHandlerTest` | Non-soulbound items cleared; soulbound items retained; respects `keepInventory` gamerule; respects disabled flag; XP handling when `destroyXp` is true/false; external enchantment ID (e.g. `meridian:tether`) resolved and checked correctly; invalid/missing enchantment ID logs warning and voids all items |
| `PlayerDifficultyStateTest` | New field serialization/deserialization round-trip; backward compat (missing `HeartsLost` key defaults to 0) |
| `ConfigMigratorTest` | v1→v2 migration adds `hardcoreHearts` and `soulInventory` objects; idempotent on v2 input |
| `TribulationConfigTest` | Validation clamps `heartsLostPerDeath`, `minimumHearts` etc.; `fillDefaults` populates missing subsections |

### Integration / Fabric-Loader Tests

| Test Class | Cases |
|-----------|-------|
| `HardcoreHeartsIntegrationTest` | Attribute modifier applied on join; modifier recalculated on config reload; modifier absent when feature disabled |
| `SoulboundEnchantmentTest` | Enchantment registered in registry; applicable items validated; incompatible with vanishing curse |

### Game Tests (fabric-gametest)

| Test | Validates |
|------|-----------|
| `hardcoreHearts_deathReducesMaxHealth` | Spawn player, kill, verify max_health attribute modifier present with correct amount |
| `hardcoreHearts_fragmentRestores` | Give player heart penalty, use fragment, verify modifier updated |
| `soulInventory_nonSoulboundVoided` | Give player items, kill, verify inventory empty on respawn |
| `soulInventory_soulboundRetained` | Give player soulbound sword, kill, verify sword present post-respawn |
| `soulInventory_respectsKeepInventory` | Set gamerule keepInventory true, kill, verify all items retained regardless |

### Manual QA Checklist

- [ ] Enable hardcore hearts, die, verify HUD shows reduced hearts
- [ ] Use Heart Fragment, verify hearts visually restored
- [ ] Enable soul inventory, die with mixed inventory, verify soulbound items kept and others gone
- [ ] Verify Soulbound enchantment appears on enchanted books from villager trades
- [ ] Verify Soulbound does NOT appear in enchanting table
- [ ] Verify both features work independently and together
- [ ] Verify Mod Menu config screen shows both new categories with all fields
- [ ] Verify config changes take effect after save (no restart needed for toggles)
- [ ] Verify `/tribulation hearts` commands work correctly
- [ ] Verify death messages display heart loss notifications

---

## Implementation Order

1. **Config + Migration**: Add `HardcoreHearts` and `SoulInventory` sections to `TribulationConfig`, write migration, update `fillDefaults`/`validate`.
2. **PlayerDifficultyState**: Add `heartsLost` field + NBT serialization.
3. **HardcoreHeartsHandler**: Death listener + attribute modifier on respawn/join.
4. **HeartFragmentItem**: Item registration, use logic, drop handler (extend `ShardDropHandler` pattern).
5. **Soulbound Enchantment**: Data-driven JSON + datagen if applicable.
6. **SoulInventoryHandler + Mixin**: Death inventory clearing logic.
7. **Commands**: Extend `TribulationCommand` with `hearts` subcommands.
8. **Mod Menu**: Add two new categories to `ModMenuIntegration`.
9. **Lang**: Add all new translation keys.
10. **Tests**: Unit tests alongside each step; game tests last.
