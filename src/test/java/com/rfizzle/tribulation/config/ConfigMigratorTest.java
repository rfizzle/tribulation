// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    @Test
    void readVersion_missingField_returnsZero() {
        JsonObject json = new JsonObject();
        assertEquals(0, ConfigMigrator.readVersion(json));
    }

    @Test
    void readVersion_nullJson_returnsZero() {
        assertEquals(0, ConfigMigrator.readVersion(null));
    }

    @Test
    void readVersion_presentField_returnsValue() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 3);
        assertEquals(3, ConfigMigrator.readVersion(json));
    }

    @Test
    void readVersion_stringField_returnsZero() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", "not a number");
        assertEquals(0, ConfigMigrator.readVersion(json));
    }

    @Test
    void migrate_currentVersion_isNoOp() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", ConfigMigrator.CURRENT_VERSION);
        assertFalse(ConfigMigrator.migrate(json));
        assertEquals(ConfigMigrator.CURRENT_VERSION,
                json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_futureVersion_isNoOp() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", ConfigMigrator.CURRENT_VERSION + 10);
        assertFalse(ConfigMigrator.migrate(json));
    }

    @Test
    void migrate_nullJson_returnsFalse() {
        assertFalse(ConfigMigrator.migrate(null));
    }

    @Test
    void migrate_versionZero_upgradesToCurrent() {
        JsonObject json = new JsonObject();
        // No configVersion field → version 0
        assertTrue(ConfigMigrator.migrate(json));
        assertEquals(ConfigMigrator.CURRENT_VERSION,
                json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_preservesExistingFields() {
        JsonObject json = JsonParser.parseString("""
                {
                  "general": { "maxLevel": 123 },
                  "shards": { "dropChance": 0.42 }
                }
                """).getAsJsonObject();

        ConfigMigrator.migrate(json);

        assertEquals(123, json.getAsJsonObject("general").get("maxLevel").getAsInt());
        assertEquals(0.42, json.getAsJsonObject("shards").get("dropChance").getAsDouble(), 1e-9);
    }

    @Test
    void load_versionZeroFile_isMigratedAndSaved(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                {
                  "general": { "maxLevel": 77 }
                }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);
        assertEquals(77, loaded.general.maxLevel);

        // File should have been re-saved with configVersion set
        String saved = Files.readString(path);
        JsonObject savedJson = JsonParser.parseString(saved).getAsJsonObject();
        assertEquals(ConfigMigrator.CURRENT_VERSION,
                savedJson.get("configVersion").getAsInt());
    }

    @Test
    void load_currentVersionFile_isNotResaved(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.general.maxLevel = 88;
        original.save(path);

        long modifiedBefore = Files.getLastModifiedTime(path).toMillis();
        // Small sleep to ensure timestamp would differ if rewritten
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        TribulationConfig loaded = TribulationConfig.load(path);
        assertEquals(88, loaded.general.maxLevel);

        long modifiedAfter = Files.getLastModifiedTime(path).toMillis();
        assertEquals(modifiedBefore, modifiedAfter,
                "File should not be re-saved when already at current version");
    }

    @Test
    void currentVersion_matchesDefaultFieldValue() {
        TribulationConfig cfg = new TribulationConfig();
        assertEquals(ConfigMigrator.CURRENT_VERSION, cfg.configVersion,
                "TribulationConfig.configVersion default must match ConfigMigrator.CURRENT_VERSION");
    }

    @Test
    void migrate_v1ToV2_addsHardcoreHeartsAndSoulInventory() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 1);
        json.add("general", new JsonObject());

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("hardcoreHearts"), "hardcoreHearts section must be added");
        assertTrue(json.get("hardcoreHearts").isJsonObject());
        assertTrue(json.has("soulInventory"), "soulInventory section must be added");
        assertTrue(json.get("soulInventory").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v1ToV2_doesNotOverwriteExistingSections() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 1);
        JsonObject existingHearts = new JsonObject();
        existingHearts.addProperty("enabled", true);
        json.add("hardcoreHearts", existingHearts);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.getAsJsonObject("hardcoreHearts").get("enabled").getAsBoolean(),
                "pre-existing hardcoreHearts content must be preserved");
        assertTrue(json.has("soulInventory"));
    }

    @Test
    void migrate_v2ToV3_addsTrialSpawner() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 2);
        json.add("hardcoreHearts", new JsonObject());
        json.add("soulInventory", new JsonObject());

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("trialSpawner"), "trialSpawner section must be added");
        assertTrue(json.get("trialSpawner").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v2ToV3_doesNotOverwriteExistingTrialSpawner() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 2);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", false);
        json.add("trialSpawner", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertFalse(json.getAsJsonObject("trialSpawner").get("enabled").getAsBoolean(),
                "pre-existing trialSpawner content must be preserved");
    }

    @Test
    void migrate_v3ToV4_addsRaidScaling() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 3);
        json.add("hardcoreHearts", new JsonObject());
        json.add("soulInventory", new JsonObject());
        json.add("trialSpawner", new JsonObject());

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("raidScaling"), "raidScaling section must be added");
        assertTrue(json.get("raidScaling").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v3ToV4_doesNotOverwriteExistingRaidScaling() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 3);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", false);
        json.add("raidScaling", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertFalse(json.getAsJsonObject("raidScaling").get("enabled").getAsBoolean(),
                "pre-existing raidScaling content must be preserved");
    }

    @Test
    void migrate_v4ToV5_addsThreatParticles() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 4);
        json.add("hardcoreHearts", new JsonObject());
        json.add("soulInventory", new JsonObject());
        json.add("trialSpawner", new JsonObject());
        json.add("raidScaling", new JsonObject());

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("threatParticles"), "threatParticles section must be added");
        assertTrue(json.get("threatParticles").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v4ToV5_doesNotOverwriteExistingThreatParticles() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 4);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", false);
        json.add("threatParticles", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertFalse(json.getAsJsonObject("threatParticles").get("enabled").getAsBoolean(),
                "pre-existing threatParticles content must be preserved");
    }

    @Test
    void migrate_v5ToV6_renamesXpAndLootToXpAndDropsLootFields() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 5);
        JsonObject legacy = new JsonObject();
        legacy.addProperty("xpMultiplier", 2.5);
        legacy.addProperty("dropMoreLoot", true);
        legacy.addProperty("moreLootChance", 0.1);
        legacy.addProperty("maxLootChance", 0.9);
        json.add("xpAndLoot", legacy);

        assertTrue(ConfigMigrator.migrate(json));

        assertFalse(json.has("xpAndLoot"), "legacy xpAndLoot key must be removed");
        assertTrue(json.has("xp"), "renamed xp section must be present");
        JsonObject xp = json.getAsJsonObject("xp");
        assertEquals(2.5, xp.get("xpMultiplier").getAsDouble(), 1e-9,
                "xpMultiplier must carry forward under the new key");
        assertFalse(xp.has("dropMoreLoot"), "extra-loot fields must be dropped");
        assertFalse(xp.has("moreLootChance"));
        assertFalse(xp.has("maxLootChance"));
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v5ToV6_withoutXpAndLoot_addsEmptyXpSection() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 5);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("xp"), "xp section must be added even with no legacy block");
        assertFalse(json.getAsJsonObject("xp").has("xpMultiplier"),
                "fillDefaults supplies the default; the migration only seeds an empty object");
    }

    @Test
    void migrate_v5ToV6_doesNotOverwriteExistingXp() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 5);
        JsonObject existing = new JsonObject();
        existing.addProperty("xpMultiplier", 3.0);
        json.add("xp", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertEquals(3.0, json.getAsJsonObject("xp").get("xpMultiplier").getAsDouble(), 1e-9,
                "pre-existing xp content must be preserved");
    }

    @Test
    void migrate_v14_isIdempotent() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 14);
        json.add("hardcoreHearts", new JsonObject());
        json.add("soulInventory", new JsonObject());
        json.add("trialSpawner", new JsonObject());
        json.add("raidScaling", new JsonObject());
        json.add("threatParticles", new JsonObject());
        json.add("xp", new JsonObject());
        json.add("bloodMoon", new JsonObject());
        json.add("champions", new JsonObject());
        json.add("biomeOffsets", new JsonObject());
        json.add("packTactics", new JsonObject());
        json.add("structureBoosts", new JsonObject());
        json.add("levelDecay", new JsonObject());
        json.add("groupHealthBonus", new JsonObject());
        json.add("environmentalPressure", new JsonObject());

        assertFalse(ConfigMigrator.migrate(json));
        assertEquals(14, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v13ToV14_addsEnvironmentalPressure() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 13);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("environmentalPressure"), "environmentalPressure section must be added");
        assertTrue(json.get("environmentalPressure").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v13ToV14_doesNotOverwriteExistingEnvironmentalPressure() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 13);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", true);
        json.add("environmentalPressure", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.getAsJsonObject("environmentalPressure").get("enabled").getAsBoolean(),
                "pre-existing environmentalPressure content must be preserved");
    }

    @Test
    void migrate_v12ToV13_addsGroupHealthBonus() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 12);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("groupHealthBonus"), "groupHealthBonus section must be added");
        assertTrue(json.get("groupHealthBonus").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v12ToV13_doesNotOverwriteExistingGroupHealthBonus() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 12);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", true);
        json.add("groupHealthBonus", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.getAsJsonObject("groupHealthBonus").get("enabled").getAsBoolean(),
                "pre-existing groupHealthBonus content must be preserved");
    }

    @Test
    void migrate_v11ToV12_addsLevelDecay() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 11);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("levelDecay"), "levelDecay section must be added");
        assertTrue(json.get("levelDecay").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v11ToV12_doesNotOverwriteExistingLevelDecay() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 11);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", true);
        json.add("levelDecay", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.getAsJsonObject("levelDecay").get("enabled").getAsBoolean(),
                "pre-existing levelDecay content must be preserved");
    }

    @Test
    void migrate_v10ToV11_addsStructureBoosts() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 10);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("structureBoosts"), "structureBoosts section must be added");
        assertTrue(json.get("structureBoosts").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v10ToV11_doesNotOverwriteExistingStructureBoosts() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 10);
        JsonObject existing = new JsonObject();
        existing.addProperty("marginBlocks", 4);
        json.add("structureBoosts", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertEquals(4, json.getAsJsonObject("structureBoosts").get("marginBlocks").getAsInt(),
                "pre-existing structureBoosts content must be preserved");
    }

    @Test
    void migrate_v9ToV10_addsPackTactics() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 9);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("packTactics"), "packTactics section must be added");
        assertTrue(json.get("packTactics").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v9ToV10_doesNotOverwriteExistingPackTactics() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 9);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", false);
        json.add("packTactics", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertFalse(json.getAsJsonObject("packTactics").get("enabled").getAsBoolean(),
                "pre-existing packTactics content must be preserved");
    }

    @Test
    void migrate_v8ToV9_addsBiomeOffsets() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 8);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("biomeOffsets"), "biomeOffsets map must be added");
        assertTrue(json.get("biomeOffsets").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v8ToV9_doesNotOverwriteExistingBiomeOffsets() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 8);
        JsonObject existing = new JsonObject();
        existing.addProperty("minecraft:swamp", 15);
        json.add("biomeOffsets", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertEquals(15, json.getAsJsonObject("biomeOffsets").get("minecraft:swamp").getAsInt(),
                "pre-existing biomeOffsets content must be preserved");
    }

    @Test
    void migrate_v7ToV8_addsChampions() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 7);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("champions"), "champions section must be added");
        assertTrue(json.get("champions").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v7ToV8_doesNotOverwriteExistingChampions() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 7);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", false);
        json.add("champions", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertFalse(json.getAsJsonObject("champions").get("enabled").getAsBoolean(),
                "pre-existing champions content must be preserved");
    }

    @Test
    void migrate_v0ToCurrent_runsAllMigrations() {
        JsonObject json = new JsonObject();
        // No configVersion → version 0

        assertTrue(ConfigMigrator.migrate(json));

        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
        assertTrue(json.has("hardcoreHearts"));
        assertTrue(json.has("soulInventory"));
        assertTrue(json.has("trialSpawner"));
        assertTrue(json.has("raidScaling"));
        assertTrue(json.has("threatParticles"));
        assertTrue(json.has("xp"));
        assertTrue(json.has("champions"));
        assertTrue(json.has("biomeOffsets"));
        assertTrue(json.has("packTactics"));
        assertTrue(json.has("structureBoosts"));
        assertTrue(json.has("levelDecay"));
        assertTrue(json.has("groupHealthBonus"));
        assertTrue(json.has("environmentalPressure"));
    }

    @Test
    void migrate_v6ToV7_addsBloodMoon() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 6);

        assertTrue(ConfigMigrator.migrate(json));

        assertTrue(json.has("bloodMoon"), "bloodMoon section must be added");
        assertTrue(json.get("bloodMoon").isJsonObject());
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v6ToV7_doesNotOverwriteExistingBloodMoon() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 6);
        JsonObject existing = new JsonObject();
        existing.addProperty("enabled", false);
        json.add("bloodMoon", existing);

        assertTrue(ConfigMigrator.migrate(json));

        assertFalse(json.getAsJsonObject("bloodMoon").get("enabled").getAsBoolean(),
                "pre-existing bloodMoon content must be preserved");
    }

    @Test
    void load_v1File_migratesAndAddsNewSections(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                {
                  "configVersion": 1,
                  "general": { "maxLevel": 200 }
                }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(200, loaded.general.maxLevel);
        assertNotNull(loaded.hardcoreHearts);
        assertNotNull(loaded.soulInventory);
        assertFalse(loaded.hardcoreHearts.enabled);
        assertFalse(loaded.soulInventory.enabled);

        String saved = Files.readString(path);
        JsonObject savedJson = JsonParser.parseString(saved).getAsJsonObject();
        assertEquals(ConfigMigrator.CURRENT_VERSION, savedJson.get("configVersion").getAsInt());
    }
}
