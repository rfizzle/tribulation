// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TribulationConfigTest {

    static Stream<String> mobKeys() {
        return Stream.of(TribulationConfig.MOB_KEYS);
    }

    @Test
    void defaultConfig_hasValidValues() {
        TribulationConfig cfg = new TribulationConfig();

        assertTrue(cfg.general.maxLevel > 0, "maxLevel must be > 0");
        assertTrue(cfg.general.levelUpTicks > 0, "levelUpTicks must be > 0");
        assertTrue(cfg.general.mobDetectionRange >= 0, "mobDetectionRange must be >= 0");

        assertTrue(cfg.distanceScaling.startingDistance >= 0);
        assertTrue(cfg.distanceScaling.increasingDistance > 0);
        assertTrue(cfg.distanceScaling.distanceFactor >= 0);
        assertTrue(cfg.distanceScaling.maxDistanceFactor >= 0);

        assertTrue(cfg.heightScaling.heightDistance > 0);
        assertTrue(cfg.heightScaling.heightFactor >= 0);
        assertTrue(cfg.heightScaling.maxHeightFactor >= 0);

        assertTrue(cfg.statCaps.maxFactorHealth > 0);
        assertTrue(cfg.statCaps.maxFactorDamage > 0);
        assertTrue(cfg.statCaps.maxFactorSpeed >= 0);
        assertTrue(cfg.statCaps.maxFactorProtection >= 0);
        assertTrue(cfg.statCaps.maxFactorFollowRange >= 0);

        assertTrue(cfg.shards.dropChance >= 0 && cfg.shards.dropChance <= 1);
        assertTrue(cfg.specialZombies.bigZombieChance >= 0 && cfg.specialZombies.bigZombieChance <= 100);
        assertTrue(cfg.specialZombies.speedZombieChance >= 0 && cfg.specialZombies.speedZombieChance <= 100);

        assertTrue(cfg.tiers.tier1 <= cfg.tiers.tier2);
        assertTrue(cfg.tiers.tier2 <= cfg.tiers.tier3);
        assertTrue(cfg.tiers.tier3 <= cfg.tiers.tier4);
        assertTrue(cfg.tiers.tier4 <= cfg.tiers.tier5);
    }

    @Test
    void defaultLevelDecay_isDisabledWithSaneDefaults() {
        TribulationConfig cfg = new TribulationConfig();
        assertFalse(cfg.levelDecay.enabled, "level decay must be opt-in");
        assertEquals(7.0, cfg.levelDecay.graceDays);
        assertEquals(2.0, cfg.levelDecay.levelsPerDay);
        assertEquals(0, cfg.levelDecay.floor);
    }

    @Test
    void validate_clampsLevelDecayFields() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.levelDecay.graceDays = -3.0;
        cfg.levelDecay.levelsPerDay = -1.0;
        cfg.levelDecay.floor = -5;

        cfg.validate();

        assertEquals(0.0, cfg.levelDecay.graceDays);
        assertEquals(0.0, cfg.levelDecay.levelsPerDay);
        assertEquals(0, cfg.levelDecay.floor);
    }

    @Test
    void validate_clampsLevelDecayFloorToMaxLevel() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.general.maxLevel = 100;
        cfg.levelDecay.floor = 500;

        cfg.validate();

        assertEquals(100, cfg.levelDecay.floor);
    }

    @Test
    void defaultGroupHealthBonus_isDisabledWithSaneDefaults() {
        TribulationConfig cfg = new TribulationConfig();
        assertFalse(cfg.groupHealthBonus.enabled, "group health bonus must be opt-in");
        assertEquals(0.2, cfg.groupHealthBonus.perPlayerBonus);
        assertEquals(1.0, cfg.groupHealthBonus.maxBonus);
    }

    @Test
    void validate_clampsGroupHealthBonusFields() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.groupHealthBonus.perPlayerBonus = -0.5;
        cfg.groupHealthBonus.maxBonus = -2.0;

        cfg.validate();

        assertEquals(0.0, cfg.groupHealthBonus.perPlayerBonus);
        assertEquals(0.0, cfg.groupHealthBonus.maxBonus);
    }

    @Test
    void defaultBloodMoon_hasValidValues() {
        TribulationConfig cfg = new TribulationConfig();
        assertTrue(cfg.bloodMoon.enabled);
        assertTrue(cfg.bloodMoon.chance >= 0 && cfg.bloodMoon.chance <= 1);
        assertTrue(cfg.bloodMoon.moonBonusMultiplier >= 1.0);
        assertTrue(cfg.bloodMoon.spawnCapMultiplier >= 1.0);
    }

    @Test
    void validate_clampsBloodMoonFields() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.bloodMoon.chance = 1.5;
        cfg.bloodMoon.moonBonusMultiplier = 0.5;
        cfg.bloodMoon.spawnCapMultiplier = -2.0;

        cfg.validate();

        assertEquals(1.0, cfg.bloodMoon.chance);
        assertEquals(1.0, cfg.bloodMoon.moonBonusMultiplier);
        assertEquals(1.0, cfg.bloodMoon.spawnCapMultiplier);
    }

    @Test
    void validate_clampsNegativeBloodMoonChance() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.bloodMoon.chance = -0.1;
        cfg.validate();
        assertEquals(0.0, cfg.bloodMoon.chance);
    }

    @Test
    void defaultConfig_populatesAllMobScalingEntries() {
        TribulationConfig cfg = new TribulationConfig();
        assertEquals(TribulationConfig.MOB_KEYS.length, cfg.scaling.size());
        assertEquals(TribulationConfig.MOB_KEYS.length, cfg.mobToggles.size());
    }

    @ParameterizedTest
    @MethodSource("mobKeys")
    void defaultScaling_hasValidRatesAndCaps(String key) {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling m = cfg.scaling.get(key);
        assertNotNull(m, "missing scaling for " + key);
        assertTrue(m.healthRate >= 0 && m.healthCap > 0, "bad health");
        assertTrue(m.damageRate >= 0 && m.damageCap > 0, "bad damage");
        assertTrue(m.speedRate >= 0 && m.speedCap >= 0, "bad speed");
        assertTrue(m.followRangeRate >= 0 && m.followRangeCap >= 0, "bad followRange");
        assertTrue(m.armorRate >= 0 && m.armorCap >= 0, "bad armor");
        assertTrue(m.toughnessRate >= 0 && m.toughnessCap >= 0, "bad toughness");
        assertTrue(cfg.mobToggles.getOrDefault(key, Boolean.FALSE), "toggle should default true");
    }

    @Test
    void getMobScaling_unknownMob_fallsBackToZombie(@TempDir Path tmp) {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling zombie = cfg.scaling.get("zombie");
        TribulationConfig.MobScaling fallback = cfg.getMobScaling("not_a_real_mob");
        // Same instance — fallback returns the zombie entry directly.
        assertEquals(zombie.healthRate, fallback.healthRate);
        assertEquals(zombie.damageRate, fallback.damageRate);
    }

    @Test
    void isMobEnabled_unknownMob_isFalse() {
        TribulationConfig cfg = new TribulationConfig();
        assertFalse(cfg.isMobEnabled("not_a_real_mob"));
    }

    @Test
    void load_missingFile_writesDefaultsAndReturnsThem(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig loaded = TribulationConfig.load(path);

        assertTrue(Files.exists(path), "load() should have created the missing file");
        assertEquals(250, loaded.general.maxLevel);
        assertEquals(72000, loaded.general.levelUpTicks);
        assertEquals(TribulationConfig.MOB_KEYS.length, loaded.scaling.size());
    }

    @Test
    void roundTrip_preservesValues(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.general.maxLevel = 123;
        original.general.levelUpTicks = 4567;
        original.shards.dropChance = 0.42;
        original.scaling.get("zombie").healthRate = 0.05;
        original.mobToggles.put("zombie", false);
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertEquals(123, reloaded.general.maxLevel);
        assertEquals(4567, reloaded.general.levelUpTicks);
        assertEquals(0.42, reloaded.shards.dropChance);
        assertEquals(0.05, reloaded.scaling.get("zombie").healthRate);
        assertFalse(reloaded.mobToggles.get("zombie"));
    }

    @Test
    void load_emptyJsonObject_fillsAllDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.general);
        assertNotNull(loaded.timeScaling);
        assertNotNull(loaded.distanceScaling);
        assertNotNull(loaded.heightScaling);
        assertNotNull(loaded.statCaps);
        assertNotNull(loaded.deathRelief);
        assertNotNull(loaded.shards);
        assertNotNull(loaded.specialZombies);
        assertNotNull(loaded.bosses);
        assertNotNull(loaded.xp);
        assertNotNull(loaded.tiers);
        assertNotNull(loaded.abilities);
        assertEquals(250, loaded.general.maxLevel);
        assertEquals(TribulationConfig.MOB_KEYS.length, loaded.scaling.size());
        assertEquals(TribulationConfig.MOB_KEYS.length, loaded.mobToggles.size());
    }

    @Test
    void load_partialScaling_fillsMissingMobsWithDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                {
                  "scaling": {
                    "zombie": { "healthRate": 0.99 }
                  },
                  "mobToggles": {
                    "zombie": false
                  }
                }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(0.99, loaded.scaling.get("zombie").healthRate);
        assertFalse(loaded.mobToggles.get("zombie"));
        // Other mobs filled in.
        for (String key : TribulationConfig.MOB_KEYS) {
            assertNotNull(loaded.scaling.get(key), "scaling missing for " + key);
            assertNotNull(loaded.mobToggles.get(key), "toggle missing for " + key);
        }
        // Untouched zombie fields fall back to MobScaling defaults (Gson leaves unset primitives as 0,
        // since Gson doesn't run the field initializer when only some fields are present in JSON).
        // So we don't assert damageRate here — it'll be 0, which validate() accepts.
        // The contract is "missing mobs filled", not "missing fields within a mob filled".
    }

    @Test
    void load_malformedJson_returnsDefaultsWithoutOverwriting(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{ this is not valid json");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(250, loaded.general.maxLevel);
        // Bad file is preserved, not overwritten — operator can fix it.
        assertEquals("{ this is not valid json", Files.readString(path));
    }

    @Test
    void load_emptyFile_overwritesWithDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(250, loaded.general.maxLevel);
        assertTrue(Files.size(path) > 0, "empty file should be replaced with defaults");
    }

    @Test
    void load_negativeRates_clampedToZero(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                {
                  "scaling": {
                    "zombie": {
                      "healthRate": -1.0,
                      "damageRate": -5.0,
                      "armorRate": -0.1
                    }
                  },
                  "statCaps": {
                    "maxFactorHealth": -2.0
                  }
                }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(0.0, loaded.scaling.get("zombie").healthRate);
        assertEquals(0.0, loaded.scaling.get("zombie").damageRate);
        assertEquals(0.0, loaded.scaling.get("zombie").armorRate);
        assertEquals(0.0, loaded.statCaps.maxFactorHealth);
    }

    @Test
    void load_maxLevelBelowOne_clampedToOne(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "general": { "maxLevel": 0, "levelUpTicks": 0, "mobDetectionRange": -10 } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(1, loaded.general.maxLevel);
        assertEquals(1, loaded.general.levelUpTicks);
        assertEquals(0.0, loaded.general.mobDetectionRange);
    }

    @Test
    void load_dropChanceOutOfRange_clampedToUnitInterval(@TempDir Path tmp) throws IOException {
        Path tooHigh = tmp.resolve("high.json");
        Files.writeString(tooHigh, "{ \"shards\": { \"dropChance\": 5.0 } }");
        assertEquals(1.0, TribulationConfig.load(tooHigh).shards.dropChance);

        Path tooLow = tmp.resolve("low.json");
        Files.writeString(tooLow, "{ \"shards\": { \"dropChance\": -2.0 } }");
        assertEquals(0.0, TribulationConfig.load(tooLow).shards.dropChance);
    }

    @Test
    void load_zombieChanceOutOfRange_clampedToPercentRange(@TempDir Path tmp) throws IOException {
        Path tooHigh = tmp.resolve("high.json");
        Files.writeString(tooHigh, """
                { "specialZombies": { "bigZombieChance": 9999, "speedZombieChance": 9999 } }
                """);
        TribulationConfig high = TribulationConfig.load(tooHigh);
        assertEquals(100, high.specialZombies.bigZombieChance);
        assertEquals(100, high.specialZombies.speedZombieChance);

        Path tooLow = tmp.resolve("low.json");
        Files.writeString(tooLow, """
                { "specialZombies": { "bigZombieChance": -50, "speedZombieChance": -50 } }
                """);
        TribulationConfig low = TribulationConfig.load(tooLow);
        assertEquals(0, low.specialZombies.bigZombieChance);
        assertEquals(0, low.specialZombies.speedZombieChance);
    }

    @Test
    void load_nonPositiveDistanceClampedToOne(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                {
                  "distanceScaling": { "increasingDistance": 0 },
                  "heightScaling": { "heightDistance": -5 },
                  "specialZombies": { "bigZombieSize": 0 }
                }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(1.0, loaded.distanceScaling.increasingDistance);
        assertEquals(1.0, loaded.heightScaling.heightDistance);
        assertEquals(1.0, loaded.specialZombies.bigZombieSize);
    }

    @Test
    void load_nonMonotonicTiers_areCorrected(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "tiers": { "tier1": -10, "tier2": 5, "tier3": 3, "tier4": 1, "tier5": 0 } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(0, loaded.tiers.tier1);
        assertEquals(5, loaded.tiers.tier2);
        assertEquals(5, loaded.tiers.tier3);
        assertEquals(5, loaded.tiers.tier4);
        assertEquals(5, loaded.tiers.tier5);
    }

    @Test
    void load_negativeDeathReliefValues_clampedToZero(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                {
                  "deathRelief": { "amount": -3, "cooldownTicks": -100, "minimumLevel": -1 },
                  "shards": { "dropStartLevel": -7, "shardPower": -2 }
                }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(0, loaded.deathRelief.amount);
        assertEquals(0, loaded.deathRelief.cooldownTicks);
        assertEquals(0, loaded.deathRelief.minimumLevel);
        assertEquals(0, loaded.shards.dropStartLevel);
        assertEquals(0, loaded.shards.shardPower);
    }

    @Test
    void roundTrip_afterClamping_isStable(@TempDir Path tmp) throws IOException {
        // Bad values get clamped on first load, then a save+load should be a no-op.
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "general": { "maxLevel": -5 }, "shards": { "dropChance": 99 } }
                """);
        TribulationConfig first = TribulationConfig.load(path);
        Path second = tmp.resolve("again.json");
        first.save(second);
        TribulationConfig reloaded = TribulationConfig.load(second);

        assertEquals(first.general.maxLevel, reloaded.general.maxLevel);
        assertEquals(first.shards.dropChance, reloaded.shards.dropChance);
        assertEquals(1, reloaded.general.maxLevel);
        assertEquals(1.0, reloaded.shards.dropChance);
    }

    @Test
    void defaultScaling_zombieMatchesDesignReferenceRates() {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling z = cfg.scaling.get("zombie");
        // Reference values pulled straight from DESIGN.md — any drift here means
        // the reference mob no longer matches the documented level-breakpoint table.
        assertEquals(0.010, z.healthRate);
        assertEquals(2.5, z.healthCap);
        assertEquals(0.015, z.damageRate);
        assertEquals(3.75, z.damageCap);
        assertEquals(0.0012, z.speedRate);
        assertEquals(0.3, z.speedCap);
        assertEquals(0.010, z.followRangeRate);
        assertEquals(1.0, z.followRangeCap);
        assertEquals(0.032, z.armorRate);
        assertEquals(8.0, z.armorCap);
        assertEquals(0.024, z.toughnessRate);
        assertEquals(6.0, z.toughnessCap);
    }

    @Test
    void defaultScaling_speedRolesAreTuned() {
        TribulationConfig cfg = new TribulationConfig();
        Map<String, TribulationConfig.MobScaling> s = cfg.scaling;

        assertTrue(s.get("spider").speedRate > s.get("zombie").speedRate,
                "spider should scale speed faster than zombie");
        assertTrue(s.get("cave_spider").speedRate > s.get("zombie").speedRate,
                "cave spider should scale speed faster than zombie");
        assertTrue(s.get("endermite").speedRate >= s.get("spider").speedRate,
                "endermite should scale speed at least as fast as spider");
    }

    @ParameterizedTest
    @MethodSource("mobKeys")
    void defaultScaling_ravagerHasHighestHealthRate(String key) {
        TribulationConfig cfg = new TribulationConfig();
        double ravagerHealth = cfg.scaling.get("ravager").healthRate;
        assertTrue(ravagerHealth >= cfg.scaling.get(key).healthRate,
                "ravager health rate should be >= " + key);
    }

    @ParameterizedTest
    @MethodSource("mobKeys")
    void defaultScaling_vindicatorHasHighestDamageRate(String key) {
        TribulationConfig cfg = new TribulationConfig();
        double vindicatorDamage = cfg.scaling.get("vindicator").damageRate;
        assertTrue(vindicatorDamage >= cfg.scaling.get(key).damageRate,
                "vindicator damage rate should be >= " + key);
    }

    @Test
    void defaultScaling_miscRoleInvariants() {
        TribulationConfig cfg = new TribulationConfig();
        Map<String, TribulationConfig.MobScaling> s = cfg.scaling;

        assertTrue(s.get("endermite").healthRate < s.get("zombie").healthRate);
        assertTrue(s.get("silverfish").healthRate < s.get("zombie").healthRate);
        assertTrue(s.get("creeper").healthRate < s.get("zombie").healthRate);

        assertEquals(0.0, s.get("endermite").armorRate);
        assertEquals(0.0, s.get("endermite").armorCap);
        assertEquals(0.0, s.get("silverfish").armorRate);
        assertEquals(0.0, s.get("silverfish").armorCap);

        assertTrue(s.get("husk").healthRate > s.get("zombie").healthRate);
        assertTrue(s.get("wither_skeleton").healthRate > s.get("skeleton").healthRate);

        assertEquals(s.get("skeleton").healthRate, s.get("bogged").healthRate);
        assertEquals(s.get("skeleton").damageRate, s.get("bogged").damageRate);
    }

    @ParameterizedTest
    @MethodSource("mobKeys")
    void defaultScaling_capsMatchRatesAtMaxLevel(String key) {
        TribulationConfig cfg = new TribulationConfig();
        int maxLevel = cfg.general.maxLevel;
        TribulationConfig.MobScaling m = cfg.scaling.get(key);
        assertEquals(m.healthRate * maxLevel, m.healthCap, 1e-9,
                "healthCap should equal healthRate * maxLevel");
        assertEquals(m.damageRate * maxLevel, m.damageCap, 1e-9,
                "damageCap should equal damageRate * maxLevel");
        assertEquals(m.speedRate * maxLevel, m.speedCap, 1e-9,
                "speedCap should equal speedRate * maxLevel");
    }

    @Test
    void load_missingUnlistedHostileMobs_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.unlistedHostileMobs);
        assertTrue(loaded.unlistedHostileMobs.enabled);
        assertNotNull(loaded.unlistedHostileMobs.excludedNamespaces);
        assertNotNull(loaded.unlistedHostileMobs.scaling);
        assertEquals(0.010, loaded.unlistedHostileMobs.scaling.healthRate);
        assertEquals(0.015, loaded.unlistedHostileMobs.scaling.damageRate);
    }

    @Test
    void load_partialUnlistedHostileMobs_preservesUserValuesAndFillsRest(@TempDir Path tmp) throws IOException {
        // User disables fallback and sets an excluded namespace but omits the
        // nested scaling block — we should preserve their overrides and fill
        // in the missing scaling object with defaults.
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                {
                  "unlistedHostileMobs": {
                    "enabled": false,
                    "excludedNamespaces": ["somemod"]
                  }
                }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertFalse(loaded.unlistedHostileMobs.enabled);
        assertEquals(1, loaded.unlistedHostileMobs.excludedNamespaces.size());
        assertEquals("somemod", loaded.unlistedHostileMobs.excludedNamespaces.get(0));
        assertNotNull(loaded.unlistedHostileMobs.scaling);
        assertEquals(0.010, loaded.unlistedHostileMobs.scaling.healthRate);
    }

    @Test
    void load_negativeUnlistedRates_clampedToZero(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                {
                  "unlistedHostileMobs": {
                    "scaling": {
                      "healthRate": -1.0,
                      "damageRate": -2.5,
                      "armorRate": -0.3
                    }
                  }
                }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(0.0, loaded.unlistedHostileMobs.scaling.healthRate);
        assertEquals(0.0, loaded.unlistedHostileMobs.scaling.damageRate);
        assertEquals(0.0, loaded.unlistedHostileMobs.scaling.armorRate);
    }

    @Test
    void roundTrip_unlistedHostileMobs_preservesValues(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.unlistedHostileMobs.enabled = false;
        original.unlistedHostileMobs.excludedNamespaces.add("mutantmonsters");
        original.unlistedHostileMobs.scaling.healthRate = 0.02;
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertFalse(reloaded.unlistedHostileMobs.enabled);
        assertTrue(reloaded.unlistedHostileMobs.excludedNamespaces.contains("mutantmonsters"));
        assertEquals(0.02, reloaded.unlistedHostileMobs.scaling.healthRate);
    }

    @Test
    void mobToggles_extraKeyFromUserIsPreserved(@TempDir Path tmp) throws IOException {
        // User-added entries (e.g., for modded mobs) should survive load/fillDefaults.
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "mobToggles": { "modded:custom_mob": false } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        Map<String, Boolean> toggles = loaded.mobToggles;
        assertFalse(toggles.get("modded:custom_mob"));
        for (String key : TribulationConfig.MOB_KEYS) {
            assertTrue(toggles.containsKey(key), "default mob toggle missing: " + key);
        }
    }

    @Test
    void defaultConfig_hudHasValidDefaults() {
        TribulationConfig cfg = new TribulationConfig();
        assertNotNull(cfg.hud);
        assertTrue(cfg.hud.enabled);
        assertEquals(TribulationConfig.Anchor.TOP_LEFT, cfg.hud.anchor);
        assertEquals(4, cfg.hud.offsetX);
        assertEquals(4, cfg.hud.offsetY);
    }

    @Test
    void load_missingHud_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.hud);
        assertTrue(loaded.hud.enabled);
        assertEquals(TribulationConfig.Anchor.TOP_LEFT, loaded.hud.anchor);
        assertEquals(4, loaded.hud.offsetX);
        assertEquals(4, loaded.hud.offsetY);
    }

    @Test
    void roundTrip_hudPreservesValues(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.hud.enabled = false;
        original.hud.anchor = TribulationConfig.Anchor.BOTTOM_RIGHT;
        original.hud.offsetX = 12;
        original.hud.offsetY = 20;
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertFalse(reloaded.hud.enabled);
        assertEquals(TribulationConfig.Anchor.BOTTOM_RIGHT, reloaded.hud.anchor);
        assertEquals(12, reloaded.hud.offsetX);
        assertEquals(20, reloaded.hud.offsetY);
    }

    @Test
    void defaultConfig_hardcoreHeartsHasValidDefaults() {
        TribulationConfig cfg = new TribulationConfig();
        assertNotNull(cfg.hardcoreHearts);
        assertFalse(cfg.hardcoreHearts.enabled);
        assertEquals(2, cfg.hardcoreHearts.heartsLostPerDeath);
        assertEquals(2, cfg.hardcoreHearts.minimumHearts);
        assertEquals(2, cfg.hardcoreHearts.heartsRestoredPerFragment);
    }

    @Test
    void defaultConfig_soulInventoryHasValidDefaults() {
        TribulationConfig cfg = new TribulationConfig();
        assertNotNull(cfg.soulInventory);
        assertFalse(cfg.soulInventory.enabled);
        assertEquals("tribulation:soulbound", cfg.soulInventory.soulboundEnchantment);
        assertFalse(cfg.soulInventory.destroyXp);
        assertTrue(cfg.soulInventory.respectKeepInventory);
    }

    @Test
    void load_missingHardcoreHearts_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.hardcoreHearts);
        assertFalse(loaded.hardcoreHearts.enabled);
        assertEquals(2, loaded.hardcoreHearts.heartsLostPerDeath);
    }

    @Test
    void load_missingSoulInventory_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.soulInventory);
        assertFalse(loaded.soulInventory.enabled);
        assertEquals("tribulation:soulbound", loaded.soulInventory.soulboundEnchantment);
    }

    @Test
    void load_hardcoreHearts_heartsLostPerDeath_clampedToRange(@TempDir Path tmp) throws IOException {
        Path tooLow = tmp.resolve("low.json");
        Files.writeString(tooLow, """
                { "hardcoreHearts": { "heartsLostPerDeath": 0 } }
                """);
        assertEquals(1, TribulationConfig.load(tooLow).hardcoreHearts.heartsLostPerDeath);

        Path tooHigh = tmp.resolve("high.json");
        Files.writeString(tooHigh, """
                { "hardcoreHearts": { "heartsLostPerDeath": 99 } }
                """);
        assertEquals(20, TribulationConfig.load(tooHigh).hardcoreHearts.heartsLostPerDeath);
    }

    @Test
    void load_hardcoreHearts_minimumHearts_clampedToRange(@TempDir Path tmp) throws IOException {
        Path tooLow = tmp.resolve("low.json");
        Files.writeString(tooLow, """
                { "hardcoreHearts": { "minimumHearts": -5 } }
                """);
        assertEquals(1, TribulationConfig.load(tooLow).hardcoreHearts.minimumHearts);

        Path tooHigh = tmp.resolve("high.json");
        Files.writeString(tooHigh, """
                { "hardcoreHearts": { "minimumHearts": 50 } }
                """);
        assertEquals(20, TribulationConfig.load(tooHigh).hardcoreHearts.minimumHearts);
    }

    @Test
    void load_hardcoreHearts_heartsRestoredPerFragment_clampedToRange(@TempDir Path tmp) throws IOException {
        Path tooLow = tmp.resolve("low.json");
        Files.writeString(tooLow, """
                { "hardcoreHearts": { "heartsRestoredPerFragment": 0 } }
                """);
        assertEquals(1, TribulationConfig.load(tooLow).hardcoreHearts.heartsRestoredPerFragment);

        Path tooHigh = tmp.resolve("high.json");
        Files.writeString(tooHigh, """
                { "hardcoreHearts": { "heartsRestoredPerFragment": 25 } }
                """);
        assertEquals(20, TribulationConfig.load(tooHigh).hardcoreHearts.heartsRestoredPerFragment);
    }

    @Test
    void roundTrip_hardcoreHeartsAndSoulInventory_preserveValues(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.hardcoreHearts.enabled = true;
        original.hardcoreHearts.heartsLostPerDeath = 4;
        original.hardcoreHearts.minimumHearts = 6;
        original.soulInventory.enabled = true;
        original.soulInventory.soulboundEnchantment = "meridian:tether";
        original.soulInventory.destroyXp = true;
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertTrue(reloaded.hardcoreHearts.enabled);
        assertEquals(4, reloaded.hardcoreHearts.heartsLostPerDeath);
        assertEquals(6, reloaded.hardcoreHearts.minimumHearts);
        assertTrue(reloaded.soulInventory.enabled);
        assertEquals("meridian:tether", reloaded.soulInventory.soulboundEnchantment);
        assertTrue(reloaded.soulInventory.destroyXp);
    }

    @Test
    void defaultConfig_trialSpawnerHasValidDefaults() {
        TribulationConfig cfg = new TribulationConfig();
        assertNotNull(cfg.trialSpawner);
        // Scaling on by default so trial chambers participate; ominous upgrade off
        // so vanilla ominous behaviour is unchanged unless opted in.
        assertTrue(cfg.trialSpawner.enabled);
        assertNotNull(cfg.trialSpawner.ominousUpgrade);
        assertFalse(cfg.trialSpawner.ominousUpgrade.enabled);
        assertEquals(0.10f, cfg.trialSpawner.ominousUpgrade.chance);
        assertEquals(3, cfg.trialSpawner.ominousUpgrade.minimumTier);
    }

    @Test
    void load_missingTrialSpawner_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.trialSpawner);
        assertTrue(loaded.trialSpawner.enabled);
        assertNotNull(loaded.trialSpawner.ominousUpgrade);
        assertFalse(loaded.trialSpawner.ominousUpgrade.enabled);
    }

    @Test
    void load_partialTrialSpawner_fillsMissingOminousUpgrade(@TempDir Path tmp) throws IOException {
        // User toggles the master switch off but omits the nested ominousUpgrade
        // block — we should preserve their override and fill the rest.
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "trialSpawner": { "enabled": false } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertFalse(loaded.trialSpawner.enabled);
        assertNotNull(loaded.trialSpawner.ominousUpgrade);
        assertEquals(0.10f, loaded.trialSpawner.ominousUpgrade.chance);
        assertEquals(3, loaded.trialSpawner.ominousUpgrade.minimumTier);
    }

    @Test
    void load_trialSpawnerOminousChance_clampedToUnitInterval(@TempDir Path tmp) throws IOException {
        Path tooHigh = tmp.resolve("high.json");
        Files.writeString(tooHigh, """
                { "trialSpawner": { "ominousUpgrade": { "chance": 5.0 } } }
                """);
        assertEquals(1.0f, TribulationConfig.load(tooHigh).trialSpawner.ominousUpgrade.chance);

        Path tooLow = tmp.resolve("low.json");
        Files.writeString(tooLow, """
                { "trialSpawner": { "ominousUpgrade": { "chance": -2.0 } } }
                """);
        assertEquals(0.0f, TribulationConfig.load(tooLow).trialSpawner.ominousUpgrade.chance);
    }

    @Test
    void load_trialSpawnerMinimumTier_clampedToNonNegative(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "trialSpawner": { "ominousUpgrade": { "minimumTier": -4 } } }
                """);

        assertEquals(0, TribulationConfig.load(path).trialSpawner.ominousUpgrade.minimumTier);
    }

    @Test
    void roundTrip_trialSpawnerPreservesValues(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.trialSpawner.enabled = false;
        original.trialSpawner.ominousUpgrade.enabled = true;
        original.trialSpawner.ominousUpgrade.chance = 0.5f;
        original.trialSpawner.ominousUpgrade.minimumTier = 4;
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertFalse(reloaded.trialSpawner.enabled);
        assertTrue(reloaded.trialSpawner.ominousUpgrade.enabled);
        assertEquals(0.5f, reloaded.trialSpawner.ominousUpgrade.chance);
        assertEquals(4, reloaded.trialSpawner.ominousUpgrade.minimumTier);
    }

    @Test
    void defaultConfig_championsHasValidDefaults() {
        TribulationConfig cfg = new TribulationConfig();
        assertNotNull(cfg.champions);
        assertTrue(cfg.champions.enabled);
        assertEquals(50, cfg.champions.levelThreshold);
        assertEquals(0.05, cfg.champions.championChance);
        assertEquals(2, cfg.champions.maxAffixes);
        assertTrue(cfg.champions.healthMultiplier >= 1.0);
        assertTrue(cfg.champions.damageMultiplier >= 1.0);
        assertTrue(cfg.champions.xpMultiplier >= 1.0);
        assertTrue(cfg.champions.bonusLootRolls >= 0);
        assertNotNull(cfg.champions.affixes);
        assertTrue(cfg.champions.affixes.vampiric);
        assertTrue(cfg.champions.affixes.explosive);
        assertTrue(cfg.champions.affixes.knockbackAura);
        assertTrue(cfg.champions.affixes.thorns);
        assertTrue(cfg.champions.affixes.regenerating);
    }

    @Test
    void validate_clampsChampionFields() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.champions.championChance = 1.5;
        cfg.champions.levelThreshold = -10;
        cfg.champions.maxAffixes = 0;
        cfg.champions.healthMultiplier = 0.5;
        cfg.champions.damageMultiplier = -1.0;
        cfg.champions.xpMultiplier = 0.0;
        cfg.champions.bonusLootRolls = -2;
        cfg.champions.affixes.vampiricHealFraction = 2.0;
        cfg.champions.affixes.explosivePower = -3.0;
        cfg.champions.affixes.knockbackAuraIntervalTicks = 0;

        cfg.validate();

        assertEquals(1.0, cfg.champions.championChance);
        assertEquals(0, cfg.champions.levelThreshold);
        assertEquals(1, cfg.champions.maxAffixes);
        assertEquals(1.0, cfg.champions.healthMultiplier);
        assertEquals(1.0, cfg.champions.damageMultiplier);
        assertEquals(1.0, cfg.champions.xpMultiplier);
        assertEquals(0, cfg.champions.bonusLootRolls);
        assertEquals(1.0, cfg.champions.affixes.vampiricHealFraction);
        assertEquals(0.0, cfg.champions.affixes.explosivePower);
        assertEquals(1, cfg.champions.affixes.knockbackAuraIntervalTicks);
    }

    @Test
    void load_missingChampions_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.champions);
        assertTrue(loaded.champions.enabled);
        assertNotNull(loaded.champions.affixes);
        assertEquals(50, loaded.champions.levelThreshold);
    }

    @Test
    void roundTrip_championsPreservesValues(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.champions.enabled = false;
        original.champions.levelThreshold = 120;
        original.champions.championChance = 0.2;
        original.champions.affixes.explosive = false;
        original.champions.affixes.thornsFraction = 0.75;
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertFalse(reloaded.champions.enabled);
        assertEquals(120, reloaded.champions.levelThreshold);
        assertEquals(0.2, reloaded.champions.championChance);
        assertFalse(reloaded.champions.affixes.explosive);
        assertEquals(0.75, reloaded.champions.affixes.thornsFraction);
    }

    @Test
    void defaultConfig_threatParticlesHasValidDefaults() {
        TribulationConfig cfg = new TribulationConfig();
        assertNotNull(cfg.threatParticles);
        assertTrue(cfg.threatParticles.enabled);
        assertEquals(4, cfg.threatParticles.minimumTier);
        assertEquals(40, cfg.threatParticles.particleFrequencyTicks);
    }

    @Test
    void roundTrip_preservesThreatParticles(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.threatParticles.enabled = false;
        original.threatParticles.minimumTier = 2;
        original.threatParticles.particleFrequencyTicks = 100;
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertFalse(reloaded.threatParticles.enabled);
        assertEquals(2, reloaded.threatParticles.minimumTier);
        assertEquals(100, reloaded.threatParticles.particleFrequencyTicks);
    }

    @Test
    void load_missingThreatParticles_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.threatParticles);
        assertTrue(loaded.threatParticles.enabled);
        assertEquals(4, loaded.threatParticles.minimumTier);
        assertEquals(40, loaded.threatParticles.particleFrequencyTicks);
    }

    @Test
    void load_threatParticlesOutOfRange_clamped(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "threatParticles": { "minimumTier": -3, "particleFrequencyTicks": 0 } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(0, loaded.threatParticles.minimumTier);
        assertEquals(1, loaded.threatParticles.particleFrequencyTicks);
    }

    // ---- Biome offsets ----

    @Test
    void defaultConfig_biomeOffsetsSeedsDeepDarkOnly() {
        TribulationConfig cfg = new TribulationConfig();
        assertEquals(Map.of("minecraft:deep_dark", 30), cfg.biomeOffsets);
    }

    @Test
    void load_missingBiomeOffsets_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(Integer.valueOf(30), loaded.biomeOffsets.get("minecraft:deep_dark"));
        assertTrue(loaded.hasBiomeOffsets());
    }

    @Test
    void load_negativeBiomeOffset_clampedToZero(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "biomeOffsets": { "minecraft:swamp": -10 } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(Integer.valueOf(0), loaded.biomeOffsets.get("minecraft:swamp"));
    }

    @Test
    void load_invalidBiomeOffsetKey_skippedNotFatal(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "biomeOffsets": { "not a valid id!!": 20, "#c:is_swamp": 15, "minecraft:swamp": 5 } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertFalse(loaded.biomeOffsets.containsKey("not a valid id!!"),
                "unparseable key must be dropped");
        assertEquals(Integer.valueOf(15), loaded.biomeOffsets.get("#c:is_swamp"));
        assertEquals(Integer.valueOf(5), loaded.biomeOffsets.get("minecraft:swamp"));
    }

    @Test
    void roundTrip_biomeOffsetsPreservesValues(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.biomeOffsets.put("#c:is_swamp", 12);
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertEquals(Integer.valueOf(12), reloaded.biomeOffsets.get("#c:is_swamp"));
        assertEquals(Integer.valueOf(30), reloaded.biomeOffsets.get("minecraft:deep_dark"));
    }

    @Test
    void isValidBiomeOffsetKey_acceptsIdsAndTags() {
        assertTrue(TribulationConfig.isValidBiomeOffsetKey("minecraft:deep_dark"));
        assertTrue(TribulationConfig.isValidBiomeOffsetKey("#c:is_swamp"));
        assertTrue(TribulationConfig.isValidBiomeOffsetKey("deep_dark"));
        assertFalse(TribulationConfig.isValidBiomeOffsetKey(""));
        assertFalse(TribulationConfig.isValidBiomeOffsetKey("#"));
        assertFalse(TribulationConfig.isValidBiomeOffsetKey("bad id with spaces"));
        assertFalse(TribulationConfig.isValidBiomeOffsetKey(null));
    }

    // ---- Structure boosts ----

    @Test
    void defaultConfig_structureBoostsSeedsClassicLootStructures() {
        TribulationConfig cfg = new TribulationConfig();
        assertEquals(16, cfg.structureBoosts.marginBlocks);
        assertEquals(Integer.valueOf(20), cfg.structureBoosts.boosts.get("minecraft:fortress"));
        assertEquals(Integer.valueOf(30), cfg.structureBoosts.boosts.get("minecraft:ancient_city"));
        assertEquals(Integer.valueOf(20), cfg.structureBoosts.boosts.get("minecraft:bastion_remnant"));
        assertEquals(Integer.valueOf(15), cfg.structureBoosts.boosts.get("minecraft:monument"));
        assertEquals(Integer.valueOf(15), cfg.structureBoosts.boosts.get("minecraft:trial_chambers"));
        assertEquals(Integer.valueOf(25), cfg.structureBoosts.boosts.get("minecraft:end_city"));
        assertTrue(cfg.hasStructureBoosts());
    }

    @Test
    void load_missingStructureBoosts_fillsDefaults(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, "{}");

        TribulationConfig loaded = TribulationConfig.load(path);

        assertNotNull(loaded.structureBoosts);
        assertEquals(16, loaded.structureBoosts.marginBlocks);
        assertEquals(Integer.valueOf(20), loaded.structureBoosts.boosts.get("minecraft:fortress"));
        assertTrue(loaded.hasStructureBoosts());
    }

    @Test
    void load_structureBoostMarginOutOfRange_clamped(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "structureBoosts": { "marginBlocks": -8 } }
                """);
        assertEquals(0, TribulationConfig.load(path).structureBoosts.marginBlocks);

        Files.writeString(path, """
                { "structureBoosts": { "marginBlocks": 4096 } }
                """);
        assertEquals(TribulationConfig.StructureBoosts.MAX_MARGIN_BLOCKS,
                TribulationConfig.load(path).structureBoosts.marginBlocks);
    }

    @Test
    void load_negativeStructureBoost_clampedToZero(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "structureBoosts": { "boosts": { "minecraft:stronghold": -10 } } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertEquals(Integer.valueOf(0), loaded.structureBoosts.boosts.get("minecraft:stronghold"));
    }

    @Test
    void load_invalidStructureBoostKey_skippedNotFatal(@TempDir Path tmp) throws IOException {
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "structureBoosts": { "boosts": { "not a valid id!!": 20, "#c:dungeons": 15, "minecraft:stronghold": 5 } } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertFalse(loaded.structureBoosts.boosts.containsKey("not a valid id!!"),
                "unparseable key must be dropped");
        assertEquals(Integer.valueOf(15), loaded.structureBoosts.boosts.get("#c:dungeons"));
        assertEquals(Integer.valueOf(5), loaded.structureBoosts.boosts.get("minecraft:stronghold"));
    }

    @Test
    void roundTrip_structureBoostsPreservesValues(@TempDir Path tmp) {
        Path path = tmp.resolve("tribulation.json");
        TribulationConfig original = new TribulationConfig();
        original.structureBoosts.marginBlocks = 4;
        original.structureBoosts.boosts.put("#c:dungeons", 12);
        original.save(path);

        TribulationConfig reloaded = TribulationConfig.load(path);

        assertEquals(4, reloaded.structureBoosts.marginBlocks);
        assertEquals(Integer.valueOf(12), reloaded.structureBoosts.boosts.get("#c:dungeons"));
        assertEquals(Integer.valueOf(20), reloaded.structureBoosts.boosts.get("minecraft:fortress"));
    }

    @Test
    void hasStructureBoosts_falseWhenMapEmptied() {
        // An empty map is the documented off-switch: behavior must be identical
        // to today, and the spawn path must not consult the structure cache.
        TribulationConfig cfg = new TribulationConfig();
        cfg.structureBoosts.boosts.clear();
        assertFalse(cfg.hasStructureBoosts());

        cfg.structureBoosts = null;
        assertFalse(cfg.hasStructureBoosts());
    }

    @Test
    void load_emptiedStructureBoosts_staysEmpty(@TempDir Path tmp) throws IOException {
        // The empty map must survive the load path — fillDefaults must not
        // re-seed defaults into it, or the off-switch silently re-arms.
        Path path = tmp.resolve("tribulation.json");
        Files.writeString(path, """
                { "structureBoosts": { "boosts": {} } }
                """);

        TribulationConfig loaded = TribulationConfig.load(path);

        assertTrue(loaded.structureBoosts.boosts.isEmpty());
        assertFalse(loaded.hasStructureBoosts());
    }
}
