// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.config.TribulationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math tests for {@link ScalingEngine}. These run without Minecraft
 * bootstrap — nothing touches Mob, ServerLevel, or attribute registry.
 */
class ScalingEngineTest {

    private static final double EPS = 1e-9;

    // ---- Time factor (DESIGN.md zombie breakpoints) ----

    @Test
    void timeFactor_zombieHealth_matchesDesignBreakpoints() {
        // Zombie health: rate=0.01/level, cap=2.5 — so level 50 → +0.5 (30 HP), level 250 → +2.5 (70 HP).
        double rate = 0.01;
        double cap = 2.5;
        assertEquals(0.0, ScalingEngine.computeTimeFactor(0, rate, cap), EPS);
        assertEquals(0.5, ScalingEngine.computeTimeFactor(50, rate, cap), EPS);
        assertEquals(1.0, ScalingEngine.computeTimeFactor(100, rate, cap), EPS);
        assertEquals(1.5, ScalingEngine.computeTimeFactor(150, rate, cap), EPS);
        assertEquals(2.0, ScalingEngine.computeTimeFactor(200, rate, cap), EPS);
        assertEquals(2.5, ScalingEngine.computeTimeFactor(250, rate, cap), EPS);
    }

    @Test
    void timeFactor_zombieDamage_matchesDesignBreakpoints() {
        // Damage: rate=0.015, cap=3.75 — level 250 → +3.75 (base 3 → 14.25 total).
        double rate = 0.015;
        double cap = 3.75;
        assertEquals(0.0, ScalingEngine.computeTimeFactor(0, rate, cap), EPS);
        assertEquals(0.75, ScalingEngine.computeTimeFactor(50, rate, cap), EPS);
        assertEquals(3.75, ScalingEngine.computeTimeFactor(250, rate, cap), EPS);
    }

    @Test
    void timeFactor_cappedBeforeMaxLevel() {
        // rate * level would be 5.0 at level 500, but cap is 2.5.
        double rate = 0.01;
        double cap = 2.5;
        assertEquals(2.5, ScalingEngine.computeTimeFactor(500, rate, cap), EPS);
        assertEquals(2.5, ScalingEngine.computeTimeFactor(250, rate, cap), EPS);
    }

    @Test
    void timeFactor_zeroInputs_returnZero() {
        assertEquals(0.0, ScalingEngine.computeTimeFactor(0, 0.01, 2.5));
        assertEquals(0.0, ScalingEngine.computeTimeFactor(100, 0, 2.5));
        assertEquals(0.0, ScalingEngine.computeTimeFactor(100, 0.01, 0));
        assertEquals(0.0, ScalingEngine.computeTimeFactor(-5, 0.01, 2.5));
    }

    // ---- Distance factor ----

    @Test
    void distanceLevels_zeroBelowStartingDistance() {
        assertEquals(0.0, ScalingEngine.computeDistanceLevels(0, 1000, 300), EPS);
        assertEquals(0.0, ScalingEngine.computeDistanceLevels(500, 1000, 300), EPS);
        assertEquals(0.0, ScalingEngine.computeDistanceLevels(1000, 1000, 300), EPS);
    }

    @Test
    void distanceLevels_beyondStart_scalesLinearly() {
        assertEquals(1.0, ScalingEngine.computeDistanceLevels(1300, 1000, 300), EPS);
        assertEquals(10.0, ScalingEngine.computeDistanceLevels(4000, 1000, 300), EPS);
        assertEquals(30.0, ScalingEngine.computeDistanceLevels(10000, 1000, 300), EPS);
    }

    @Test
    void distanceFactor_matchesDesignExample() {
        // DESIGN.md example: 4000 blocks from spawn → (4000-1000)/300 * 0.1 = 1.0, capped at 1.5.
        TribulationConfig cfg = new TribulationConfig();
        assertEquals(0.0, ScalingEngine.computeDistanceFactor(0, cfg.distanceScaling), EPS);
        assertEquals(0.0, ScalingEngine.computeDistanceFactor(500, cfg.distanceScaling), EPS);
        assertEquals(0.0, ScalingEngine.computeDistanceFactor(1000, cfg.distanceScaling), EPS);
        assertEquals(1.0 / 3.0, ScalingEngine.computeDistanceFactor(2000, cfg.distanceScaling), EPS);
        assertEquals(1.0, ScalingEngine.computeDistanceFactor(4000, cfg.distanceScaling), EPS);
        // 10000 blocks → levels=30, raw=3.0, capped at 1.5.
        assertEquals(1.5, ScalingEngine.computeDistanceFactor(10000, cfg.distanceScaling), EPS);
    }

    @Test
    void distanceFactor_disabled_returnsZero() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.distanceScaling.enabled = false;
        assertEquals(0.0, ScalingEngine.computeDistanceFactor(50000, cfg.distanceScaling), EPS);
    }

    // ---- Height factor ----

    @Test
    void heightLevels_atBaseline_isZero() {
        TribulationConfig cfg = new TribulationConfig();
        assertEquals(0.0, ScalingEngine.computeHeightLevels(62, cfg.heightScaling), EPS);
    }

    @Test
    void heightLevels_belowBaseline_scalesWithAbsoluteDelta() {
        TribulationConfig cfg = new TribulationConfig();
        // Y=32 → |32-62|/30 = 1.0
        assertEquals(1.0, ScalingEngine.computeHeightLevels(32, cfg.heightScaling), EPS);
        // Y=2 → |2-62|/30 = 2.0
        assertEquals(2.0, ScalingEngine.computeHeightLevels(2, cfg.heightScaling), EPS);
        // Y=-30 → |-30-62|/30 = 92/30
        assertEquals(92.0 / 30.0, ScalingEngine.computeHeightLevels(-30, cfg.heightScaling), EPS);
    }

    @Test
    void heightLevels_aboveBaseline_scalesWithAbsoluteDelta() {
        TribulationConfig cfg = new TribulationConfig();
        // Y=92 → 1.0
        assertEquals(1.0, ScalingEngine.computeHeightLevels(92, cfg.heightScaling), EPS);
        // Y=200 → |200-62|/30 = 138/30
        assertEquals(138.0 / 30.0, ScalingEngine.computeHeightLevels(200, cfg.heightScaling), EPS);
    }

    @Test
    void heightFactor_matchesDesignExample() {
        // DESIGN.md: mob at Y=2 → 2.0 levels * 0.1 = 0.2 factor.
        TribulationConfig cfg = new TribulationConfig();
        assertEquals(0.2, ScalingEngine.computeHeightFactor(2, cfg.heightScaling), EPS);
        // Mob at Y=62 → baseline → 0.
        assertEquals(0.0, ScalingEngine.computeHeightFactor(62, cfg.heightScaling), EPS);
    }

    @Test
    void heightFactor_cappedAtMax() {
        TribulationConfig cfg = new TribulationConfig();
        // Y=-300 → very deep; factor would exceed 0.5 cap.
        assertEquals(0.5, ScalingEngine.computeHeightFactor(-300, cfg.heightScaling), EPS);
    }

    @Test
    void heightFactor_positiveDisabled_ignoresAboveBaseline() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.heightScaling.positiveHeightScaling = false;
        assertEquals(0.0, ScalingEngine.computeHeightFactor(200, cfg.heightScaling), EPS);
        // But below baseline still scales.
        assertTrue(ScalingEngine.computeHeightFactor(2, cfg.heightScaling) > 0);
    }

    @Test
    void heightFactor_negativeDisabled_ignoresBelowBaseline() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.heightScaling.negativeHeightScaling = false;
        assertEquals(0.0, ScalingEngine.computeHeightFactor(-30, cfg.heightScaling), EPS);
        assertTrue(ScalingEngine.computeHeightFactor(200, cfg.heightScaling) > 0);
    }

    @Test
    void heightFactor_disabled_returnsZero() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.heightScaling.enabled = false;
        assertEquals(0.0, ScalingEngine.computeHeightFactor(2, cfg.heightScaling), EPS);
    }

    // ---- Combine / global cap ----

    @Test
    void combineFactor_sumsAndRespectsGlobalCap() {
        assertEquals(1.1, ScalingEngine.combineFactor(0.3, 0.4, 0.3, 0.1, 4.0), EPS);
        assertEquals(4.0, ScalingEngine.combineFactor(3.0, 1.5, 0.5, 0.2, 4.0), EPS);
    }

    @Test
    void combineFactor_zeroCap_returnsRawSum() {
        assertEquals(1.1, ScalingEngine.combineFactor(0.3, 0.4, 0.3, 0.1, 0.0), EPS);
    }

    // ---- Moon factor ----

    @ParameterizedTest
    @CsvSource({
            "0, 0.1, 0.1",   // Full Moon
            "1, 0.1, 0.075", // Waning Gibbous
            "2, 0.1, 0.05",  // Last Quarter
            "3, 0.1, 0.025", // Waning Crescent
            "4, 0.1, 0.0",   // New Moon
            "5, 0.1, 0.025", // Waxing Crescent
            "6, 0.1, 0.05",  // First Quarter
            "7, 0.1, 0.075", // Waxing Gibbous
            "0, 0.0, 0.0"    // maxBonus 0
    })
    void computeMoonFactor_matchesTriangleCurve(int phase, double maxBonus, double expected) {
        assertEquals(expected, ScalingEngine.computeMoonFactor(phase, maxBonus), EPS);
    }

    // ---- Per-attribute factor: ADD_MULTIPLIED_BASE ----

    @Test
    void attributeFactor_health_positionScaled_combinesAllFourAxes() {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling zombie = cfg.scaling.get("zombie");
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_HEALTH, 100, 0.5, 0.2, 0.1, zombie, cfg.statCaps
        );
        // Time: min(100*0.01, 2.5) = 1.0. Distance: 0.5. Height: 0.2. Moon: 0.1. Sum: 1.8 < cap 4.0.
        assertEquals(1.0, f.timeFactor(), EPS);
        assertEquals(0.5, f.distanceFactor(), EPS);
        assertEquals(0.2, f.heightFactor(), EPS);
        assertEquals(0.1, f.moonFactor(), EPS);
        assertEquals(1.8, f.totalFactor(), EPS);
    }

    @Test
    void attributeFactor_speed_timeOnly_ignoresPositionAxes() {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling zombie = cfg.scaling.get("zombie");
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_SPEED, 250, 1.5, 0.5, 0.1, zombie, cfg.statCaps
        );
        // Speed: rate=0.0012, cap=0.3, level=250 → 0.3. Distance/height/moon not applied.
        assertEquals(0.3, f.timeFactor(), EPS);
        assertEquals(0.0, f.distanceFactor(), EPS);
        assertEquals(0.0, f.heightFactor(), EPS);
        assertEquals(0.0, f.moonFactor(), EPS);
        assertEquals(0.3, f.totalFactor(), EPS);
    }

    @Test
    void attributeFactor_followRange_timeOnly_ignoresPositionAxes() {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling zombie = cfg.scaling.get("zombie");
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_FOLLOW_RANGE, 100, 1.0, 0.5, 0.1, zombie, cfg.statCaps
        );
        // followRange: rate=0.01, cap=1.0, level=100 → 1.0. No position scaling.
        assertEquals(1.0, f.timeFactor(), EPS);
        assertEquals(0.0, f.distanceFactor(), EPS);
        assertEquals(0.0, f.heightFactor(), EPS);
        assertEquals(0.0, f.moonFactor(), EPS);
    }

    @Test
    void attributeFactor_globalCap_clipsAndScalesAxesProportionally() {
        TribulationConfig cfg = new TribulationConfig();
        // Lower the global cap so we can observe clipping.
        cfg.statCaps.maxFactorHealth = 1.0;
        TribulationConfig.MobScaling zombie = cfg.scaling.get("zombie");
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_HEALTH, 250, 1.5, 0.5, 0.5, zombie, cfg.statCaps
        );
        // Raw: time=2.5, dist=1.5, height=0.5, moon=0.5 sum=5.0. Clipped to 1.0. Scale=1/5.0.
        double scale = 1.0 / 5.0;
        assertEquals(2.5 * scale, f.timeFactor(), EPS);
        assertEquals(1.5 * scale, f.distanceFactor(), EPS);
        assertEquals(0.5 * scale, f.heightFactor(), EPS);
        assertEquals(0.5 * scale, f.moonFactor(), EPS);
        assertEquals(1.0, f.totalFactor(), EPS);
    }

    // ---- Per-attribute factor: ADD_VALUE (armor/toughness) ----

    @Test
    void attributeFactor_armor_addValue_timeInAbsolutePoints() {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling zombie = cfg.scaling.get("zombie");
        // Zero distance/height/moon so we isolate the time axis.
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_ARMOR, 250, 0.0, 0.0, 0.0, zombie, cfg.statCaps
        );
        // Armor rate=0.032, cap=8: level 250 → min(8, 8)=8 absolute armor.
        assertEquals(8.0, f.timeFactor(), EPS);
        assertEquals(0.0, f.distanceFactor(), EPS);
        assertEquals(0.0, f.heightFactor(), EPS);
        assertEquals(0.0, f.moonFactor(), EPS);
        assertEquals(8.0, f.totalFactor(), EPS);
    }

    @Test
    void attributeFactor_armor_addValue_distanceTranslatedViaCap() {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling zombie = cfg.scaling.get("zombie");
        // Time 0, distance factor 1.0, height 0, moon 0. Armor cap = 8 → distance contrib = 1.0 * 8 = 8.
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_ARMOR, 0, 1.0, 0.0, 0.0, zombie, cfg.statCaps
        );
        assertEquals(0.0, f.timeFactor(), EPS);
        assertEquals(8.0, f.distanceFactor(), EPS);
        assertEquals(0.0, f.heightFactor(), EPS);
        assertEquals(0.0, f.moonFactor(), EPS);
    }

    @Test
    void attributeFactor_armor_globalCap_clipsAtProtectionTimesAttributeCap() {
        TribulationConfig cfg = new TribulationConfig();
        TribulationConfig.MobScaling zombie = cfg.scaling.get("zombie");
        // maxFactorProtection=2.0, armorCap=8 → globalMax = 16.
        // Raw: time=8 + dist=1.5*8=12 + height=0.5*8=4 + moon=0.1*8=0.8 → sum=24.8, clipped to 16.
        ScalingResult.AttributeFactor f = ScalingEngine.computeAttributeFactor(
                ScalingEngine.ATTR_ARMOR, 250, 1.5, 0.5, 0.1, zombie, cfg.statCaps
        );
        assertEquals(16.0, f.totalFactor(), EPS);
        // Axes scaled proportionally so they sum to the total.
        assertEquals(16.0, f.timeFactor() + f.distanceFactor() + f.heightFactor() + f.moonFactor(), EPS);
    }

    // ---- Tier ----

    @ParameterizedTest
    @CsvSource({
            "0, 0", "49, 0",
            "50, 1", "99, 1",
            "100, 2", "150, 3",
            "200, 4", "250, 5",
            "9999, 5"
    })
    void computeTier_thresholdsProduceExpectedRanges(int level, int expectedTier) {
        assertEquals(expectedTier, ScalingEngine.computeTier(level, new TribulationConfig.Tiers()));
    }

    @Test
    void computeTier_respectsCustomThresholds() {
        TribulationConfig.Tiers t = new TribulationConfig.Tiers();
        t.tier1 = 10;
        t.tier2 = 20;
        t.tier3 = 30;
        t.tier4 = 40;
        t.tier5 = 50;
        assertEquals(0, ScalingEngine.computeTier(9, t));
        assertEquals(1, ScalingEngine.computeTier(10, t));
        assertEquals(5, ScalingEngine.computeTier(50, t));
    }

    // ---- Dimension offset ----

    @Test
    void dimensionOffset_zeroOffset_isIdentity() {
        assertEquals(30, ScalingEngine.applyDimensionOffset(30, 0, 250));
    }

    @Test
    void dimensionOffset_positiveOffset_adds() {
        assertEquals(55, ScalingEngine.applyDimensionOffset(30, 25, 250));
    }

    @Test
    void dimensionOffset_negativeOffset_clampsToZero() {
        assertEquals(30, ScalingEngine.applyDimensionOffset(30, -10, 250));
    }

    @Test
    void dimensionOffset_sumOverMaxLevel_cappedAtMaxLevel() {
        assertEquals(250, ScalingEngine.applyDimensionOffset(240, 40, 250));
    }

    // ---- Modifier IDs + classification ----

    @Test
    void modifierId_usesNamespacedPattern() {
        assertEquals("tribulation:time_health", ScalingEngine.modifierId("time", "health").toString());
        assertEquals("tribulation:distance_armor", ScalingEngine.modifierId("distance", "armor").toString());
        assertEquals("tribulation:height_damage", ScalingEngine.modifierId("height", "damage").toString());
    }

    @ParameterizedTest
    @CsvSource({
            "armor, true",
            "toughness, true",
            "health, false",
            "damage, false",
            "speed, false",
            "follow_range, false"
    })
    void classification_addValueAttributes(String attribute, boolean expected) {
        assertEquals(expected, ScalingEngine.usesAddValue(attribute));
    }

    @ParameterizedTest
    @CsvSource({
            "health, true",
            "damage, true",
            "armor, true",
            "toughness, true",
            "speed, false",
            "follow_range, false"
    })
    void classification_positionScaledSubset(String attribute, boolean expected) {
        assertEquals(expected, ScalingEngine.isPositionScaled(attribute));
    }

    // ---- Combined-attribute ceiling trim (only the tribulation buff is reduced) ----

    @Test
    void ceilingKeepRatio_underCeiling_keepsFullBuff() {
        // base+equipment 10, buff 5, total 15 <= ceiling 24 → no trim.
        assertEquals(1.0, ScalingEngine.ceilingKeepRatio(5.0, 10.0, 24.0), EPS);
    }

    @Test
    void ceilingKeepRatio_overCeiling_trimsOnlySurplusFromBuff() {
        // Mirrors the gametest: nonTrib 22 (2 base + 20 netherite), buff 10, ceiling 24.
        // total 32, surplus 8 → keep (10-8)/10 = 0.2, leaving buff 2 and final value 24.
        assertEquals(0.2, ScalingEngine.ceilingKeepRatio(10.0, 22.0, 24.0), EPS);
    }

    @Test
    void ceilingKeepRatio_equipmentAloneExceeds_removesBuffEntirely() {
        // nonTrib 30 already over ceiling 24; the 5-point buff can't save it → fully removed.
        assertEquals(0.0, ScalingEngine.ceilingKeepRatio(5.0, 30.0, 24.0), EPS);
    }

    @Test
    void ceilingKeepRatio_noBuff_isZero() {
        assertEquals(0.0, ScalingEngine.ceilingKeepRatio(0.0, 30.0, 24.0), EPS);
    }

    @Test
    void ceilingKeepRatio_exactlyAtCeiling_keepsFullBuff() {
        // nonTrib 14 + buff 10 == ceiling 24 exactly → surplus 0, no trim.
        assertEquals(1.0, ScalingEngine.ceilingKeepRatio(10.0, 14.0, 24.0), EPS);
    }

    // ---- amplifyMoonFactor (Blood Moon) ----

    @Test
    void amplifyMoonFactor_multiplies() {
        assertEquals(0.3, ScalingEngine.amplifyMoonFactor(0.1, 3.0), EPS);
    }

    @Test
    void amplifyMoonFactor_multiplierOfOne_isIdentity() {
        assertEquals(0.1, ScalingEngine.amplifyMoonFactor(0.1, 1.0), EPS);
    }

    @Test
    void amplifyMoonFactor_multiplierBelowOne_isIdentity() {
        assertEquals(0.1, ScalingEngine.amplifyMoonFactor(0.1, 0.5), EPS);
    }

    @Test
    void amplifyMoonFactor_zeroRaw_staysZero() {
        assertEquals(0.0, ScalingEngine.amplifyMoonFactor(0.0, 3.0), EPS);
    }
}
