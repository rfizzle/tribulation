// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.Bosses;
import com.rfizzle.tribulation.config.TribulationConfig.DistanceScaling;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math tests for {@link BossScalingEngine}. Mirrors the style of
 * {@link ScalingEngineTest} — no Minecraft bootstrap required.
 */
class BossScalingEngineTest {

    private static final double EPS = 1e-9;

    // ---- Time factor ----

    @Test
    void timeFactor_scalesLinearlyWithLevel() {
        Bosses bossCfg = new Bosses();
        // bossTimeFactor = 0.3 per level by default.
        assertEquals(0.0, BossScalingEngine.computeTimeFactor(0, bossCfg), EPS);
        assertEquals(0.3, BossScalingEngine.computeTimeFactor(1, bossCfg), EPS);
        assertEquals(3.0, BossScalingEngine.computeTimeFactor(10, bossCfg), EPS);
        assertEquals(75.0, BossScalingEngine.computeTimeFactor(250, bossCfg), EPS);
    }

    @Test
    void timeFactor_zeroRateOrNegativeLevel_returnsZero() {
        Bosses bossCfg = new Bosses();
        bossCfg.bossTimeFactor = 0.0;
        assertEquals(0.0, BossScalingEngine.computeTimeFactor(250, bossCfg), EPS);

        Bosses bossCfg2 = new Bosses();
        assertEquals(0.0, BossScalingEngine.computeTimeFactor(-5, bossCfg2), EPS);
    }

    @Test
    void timeFactor_nullConfig_returnsZero() {
        assertEquals(0.0, BossScalingEngine.computeTimeFactor(250, null), EPS);
    }

    // ---- Distance factor ----

    @Test
    void distanceFactor_usesBossRateButNormalThresholds() {
        TribulationConfig cfg = new TribulationConfig();
        // startingDistance=1000, increasingDistance=300, bossDistanceFactor=0.1.
        // 4000 blocks → levels=10, bossFactor=10*0.1=1.0.
        assertEquals(0.0, BossScalingEngine.computeDistanceFactor(500, cfg.distanceScaling, cfg.bosses), EPS);
        assertEquals(0.0, BossScalingEngine.computeDistanceFactor(1000, cfg.distanceScaling, cfg.bosses), EPS);
        assertEquals(1.0, BossScalingEngine.computeDistanceFactor(4000, cfg.distanceScaling, cfg.bosses), EPS);
        // 10000 blocks → levels=30, bossFactor=30*0.1=3.0. No per-axis cap here —
        // capping happens in combineFactor.
        assertEquals(3.0, BossScalingEngine.computeDistanceFactor(10000, cfg.distanceScaling, cfg.bosses), EPS);
    }

    @Test
    void distanceFactor_zeroRate_returnsZero() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.bosses.bossDistanceFactor = 0.0;
        assertEquals(0.0, BossScalingEngine.computeDistanceFactor(10000, cfg.distanceScaling, cfg.bosses), EPS);
    }

    // ---- Combine / cap ----

    @Test
    void combineFactor_clipsAtBossMaxFactor() {
        Bosses bossCfg = new Bosses();
        // bossMaxFactor = 3.0 by default.
        assertEquals(1.5, BossScalingEngine.combineFactor(0.5, 1.0, bossCfg), EPS);
        assertEquals(3.0, BossScalingEngine.combineFactor(2.0, 2.0, bossCfg), EPS);
        assertEquals(3.0, BossScalingEngine.combineFactor(10.0, 0.0, bossCfg), EPS);
    }

    @Test
    void combineFactor_zeroCap_returnsRawSum() {
        Bosses bossCfg = new Bosses();
        bossCfg.bossMaxFactor = 0.0;
        assertEquals(5.5, BossScalingEngine.combineFactor(3.0, 2.5, bossCfg), EPS);
    }

    // ---- Per-attribute factor (the output type used by ScalingResult) ----

    @Test
    void attributeFactor_underCap_returnsExactContributions() {
        TribulationConfig cfg = new TribulationConfig();
        // playerLevel=5, distance=2000 (below startingDistance? no: 2000>1000).
        // time = 5 * 0.3 = 1.5. distance = (2000-1000)/300 * 0.1 = 0.333...
        ScalingResult.AttributeFactor f = BossScalingEngine.computeAttributeFactor(5, 2000, cfg);
        assertEquals(1.5, f.timeFactor(), EPS);
        assertEquals((1000.0 / 300.0) * 0.1, f.distanceFactor(), EPS);
        assertEquals(0.0, f.heightFactor(), EPS); // bosses skip height
        assertEquals(1.5 + (1000.0 / 300.0) * 0.1, f.totalFactor(), EPS);
    }

    @Test
    void attributeFactor_overCap_scalesAxesProportionally() {
        TribulationConfig cfg = new TribulationConfig();
        // playerLevel=250 → time=75, distance=4000 blocks=1.0, sum=76, capped to 3.0.
        ScalingResult.AttributeFactor f = BossScalingEngine.computeAttributeFactor(250, 4000, cfg);
        assertEquals(3.0, f.totalFactor(), EPS);
        // Axes scale proportionally so time + dist == totalFactor.
        assertEquals(3.0, f.timeFactor() + f.distanceFactor(), EPS);
        // time >> dist originally, so time still dominates after scaling.
        assertTrue(f.timeFactor() > f.distanceFactor());
        assertEquals(0.0, f.heightFactor(), EPS);
    }

    @Test
    void attributeFactor_heightAxisAlwaysZero() {
        TribulationConfig cfg = new TribulationConfig();
        // Even at level 250 and far from spawn, height contribution stays zero.
        ScalingResult.AttributeFactor f1 = BossScalingEngine.computeAttributeFactor(250, 10000, cfg);
        assertEquals(0.0, f1.heightFactor(), EPS);

        // And at zero inputs too.
        ScalingResult.AttributeFactor f2 = BossScalingEngine.computeAttributeFactor(0, 0, cfg);
        assertEquals(0.0, f2.heightFactor(), EPS);
        assertEquals(0.0, f2.totalFactor(), EPS);
    }

    @Test
    void attributeFactor_distanceDisabled_zerosDistanceAxis() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.distanceScaling.enabled = false;
        ScalingResult.AttributeFactor f = BossScalingEngine.computeAttributeFactor(10, 10000, cfg);
        // Only time contributes now.
        assertEquals(10 * 0.3, f.timeFactor(), EPS);
        assertEquals(0.0, f.distanceFactor(), EPS);
    }

    @Test
    void attributeFactor_levelZero_returnsAllZero() {
        TribulationConfig cfg = new TribulationConfig();
        ScalingResult.AttributeFactor f = BossScalingEngine.computeAttributeFactor(0, 500, cfg);
        assertEquals(0.0, f.totalFactor(), EPS);
        assertEquals(0.0, f.timeFactor(), EPS);
        assertEquals(0.0, f.distanceFactor(), EPS);
    }

    @Test
    void attributeFactor_ignoresExcludeInOtherDimensions() {
        // Even with excludeInOtherDimensions=true, the boss path must still
        // compute a distance factor from raw horizontal distance — the caller
        // (BossScalingEngine.compute) is responsible for feeding the right
        // coords and the engine itself must NOT consult the flag. Verify by
        // toggling the flag and confirming the factor is unchanged.
        TribulationConfig cfg = new TribulationConfig();
        cfg.distanceScaling.excludeInOtherDimensions = true;
        double withFlag = BossScalingEngine.computeDistanceFactor(4000, cfg.distanceScaling, cfg.bosses);
        cfg.distanceScaling.excludeInOtherDimensions = false;
        double withoutFlag = BossScalingEngine.computeDistanceFactor(4000, cfg.distanceScaling, cfg.bosses);
        assertEquals(withFlag, withoutFlag, EPS);
        assertEquals(1.0, withFlag, EPS);
    }

    // ---- Modifier IDs ----

    @Test
    void modifierId_usesBossNamespacePrefix() {
        assertEquals("tribulation:boss_time_health",
                BossScalingEngine.modifierId("boss_time", "health").toString());
        assertEquals("tribulation:boss_distance_damage",
                BossScalingEngine.modifierId("boss_distance", "damage").toString());
    }

    @Test
    void bossAttributes_coversHealthAndDamageOnly() {
        // The boss path intentionally skips speed, follow range, armor, and
        // toughness — guard against accidental expansion.
        assertEquals(2, BossScalingEngine.BOSS_ATTRIBUTES.size());
        assertTrue(BossScalingEngine.BOSS_ATTRIBUTES.contains(ScalingEngine.ATTR_HEALTH));
        assertTrue(BossScalingEngine.BOSS_ATTRIBUTES.contains(ScalingEngine.ATTR_DAMAGE));
    }
}
