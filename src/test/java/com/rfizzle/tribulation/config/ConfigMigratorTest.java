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
    void migrate_v2_isIdempotent() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", 2);
        json.add("hardcoreHearts", new JsonObject());
        json.add("soulInventory", new JsonObject());

        assertFalse(ConfigMigrator.migrate(json));
        assertEquals(2, json.get("configVersion").getAsInt());
    }

    @Test
    void migrate_v0ToV2_runsBothMigrations() {
        JsonObject json = new JsonObject();
        // No configVersion → version 0

        assertTrue(ConfigMigrator.migrate(json));

        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
        assertTrue(json.has("hardcoreHearts"));
        assertTrue(json.has("soulInventory"));
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
