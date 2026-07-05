package com.rfizzle.tribulation.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rfizzle.tribulation.Tribulation;

/**
 * Runs ordered JSON-level migrations on the raw config before Gson
 * deserialization. Each migration transforms a {@link JsonObject} from
 * version N to N+1, operating on raw keys/values so renamed or
 * restructured fields are carried forward instead of silently dropped.
 *
 * <p>When adding a new migration:
 * <ol>
 *   <li>Bump {@link #CURRENT_VERSION}.</li>
 *   <li>Update the default {@code configVersion} field in
 *       {@link TribulationConfig} to match.</li>
 *   <li>Append the migration lambda to {@link #MIGRATIONS}.</li>
 *   <li>Add a test in {@code ConfigMigratorTest}.</li>
 * </ol>
 */
final class ConfigMigrator {

    static final int CURRENT_VERSION = 14;

    @FunctionalInterface
    interface Migration {
        void apply(JsonObject json);
    }

    /**
     * Ordered array of migrations. Index 0 = v0→v1, index 1 = v1→v2, etc.
     * Each entry MUST correspond to the transition from version {@code i} to
     * version {@code i+1}.
     */
    private static final Migration[] MIGRATIONS = {
            // v0 → v1: baseline version tag. Pre-versioned configs are
            // structurally identical to v1; fillDefaults() handles any
            // missing fields. This migration exists so the infrastructure
            // is exercised on first load of a version-0 file.
            json -> {},
            // v1 → v2: add hardcoreHearts and soulInventory sections.
            json -> {
                if (!json.has("hardcoreHearts")) {
                    json.add("hardcoreHearts", new JsonObject());
                }
                if (!json.has("soulInventory")) {
                    json.add("soulInventory", new JsonObject());
                }
            },
            // v2 → v3: add trialSpawner section.
            json -> {
                if (!json.has("trialSpawner")) {
                    json.add("trialSpawner", new JsonObject());
                }
            },
            // v3 → v4: add raidScaling section.
            json -> {
                if (!json.has("raidScaling")) {
                    json.add("raidScaling", new JsonObject());
                }
            },
            // v4 → v5: add threatParticles section.
            json -> {
                if (!json.has("threatParticles")) {
                    json.add("threatParticles", new JsonObject());
                }
            },
            // v5 → v6: rename xpAndLoot → xp and drop the extra-loot fields. The
            // section now governs XP rewards only; loot adjustments are out of
            // scope for a difficulty mod. Carry xpMultiplier forward under the
            // new key so existing tuning is preserved.
            json -> {
                JsonElement legacy = json.remove("xpAndLoot");
                if (!json.has("xp")) {
                    JsonObject xp = new JsonObject();
                    if (legacy != null && legacy.isJsonObject()) {
                        JsonElement mult = legacy.getAsJsonObject().get("xpMultiplier");
                        if (mult != null && mult.isJsonPrimitive()
                                && mult.getAsJsonPrimitive().isNumber()) {
                            xp.add("xpMultiplier", mult);
                        }
                    }
                    json.add("xp", xp);
                }
            },
            // v6 → v7: add bloodMoon section.
            json -> {
                if (!json.has("bloodMoon")) {
                    json.add("bloodMoon", new JsonObject());
                }
            },
            // v7 → v8: add champions section.
            json -> {
                if (!json.has("champions")) {
                    json.add("champions", new JsonObject());
                }
            },
            // v8 → v9: add biomeOffsets map. fillDefaults() seeds the default
            // deep_dark entry into the empty object, mirroring dimensionOffsets.
            json -> {
                if (!json.has("biomeOffsets")) {
                    json.add("biomeOffsets", new JsonObject());
                }
            },
            // v9 → v10: add packTactics section. fillDefaults() seeds the
            // default eligible-mob list into the empty object.
            json -> {
                if (!json.has("packTactics")) {
                    json.add("packTactics", new JsonObject());
                }
            },
            // v10 → v11: add structureBoosts section. Gson's no-arg
            // construction runs the field initializers, so the empty object
            // deserializes with the default margin and classic-loot-structure
            // boosts (fillDefaults only null-heals this section).
            json -> {
                if (!json.has("structureBoosts")) {
                    json.add("structureBoosts", new JsonObject());
                }
            },
            // v11 → v12: add levelDecay section. The empty object deserializes
            // with the field-initializer defaults (disabled, 7-day grace,
            // 2 levels/day, floor 0).
            json -> {
                if (!json.has("levelDecay")) {
                    json.add("levelDecay", new JsonObject());
                }
            },
            // v12 → v13: add groupHealthBonus section. The empty object
            // deserializes with the field-initializer defaults (disabled,
            // +20% per extra player, +100% cap).
            json -> {
                if (!json.has("groupHealthBonus")) {
                    json.add("groupHealthBonus", new JsonObject());
                }
            },
            // v13 → v14: add environmentalPressure section. The empty object
            // deserializes with the field-initializer defaults (master toggle
            // off; strikes at tier 3, oppressive nights at tier 4).
            json -> {
                if (!json.has("environmentalPressure")) {
                    json.add("environmentalPressure", new JsonObject());
                }
            }
    };

    private ConfigMigrator() {}

    /**
     * Run any pending migrations on the raw JSON object. Returns {@code true}
     * if at least one migration was applied (the caller should persist the
     * result to disk so the file reflects the latest schema).
     */
    static boolean migrate(JsonObject json) {
        if (json == null) return false;

        int version = readVersion(json);
        if (version >= CURRENT_VERSION) return false;

        boolean changed = false;
        for (int i = version; i < CURRENT_VERSION && i < MIGRATIONS.length; i++) {
            int from = i;
            int to = i + 1;
            try {
                MIGRATIONS[i].apply(json);
                Tribulation.LOGGER.info("Migrated tribulation.json from v{} to v{}", from, to);
                changed = true;
            } catch (Exception e) {
                Tribulation.LOGGER.warn("Migration v{} to v{} failed; skipping: {}", from, to, e.getMessage());
            }
        }

        if (changed) {
            json.addProperty("configVersion", CURRENT_VERSION);
        }

        return changed;
    }

    /**
     * Extract the config version from the raw JSON, defaulting to 0 if the
     * field is absent or not a number (pre-versioned configs).
     */
    static int readVersion(JsonObject json) {
        if (json == null) return 0;
        JsonElement element = json.get("configVersion");
        if (element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return 0;
    }
}
