// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.config;

import com.rfizzle.tribulation.config.TribulationConfig.RaidScaling;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-arithmetic coverage of the {@link RaidScaling} helpers. No Minecraft
 * imports — the formulas are deliberately decoupled from the spawn pipeline so
 * they can be exercised here.
 */
class RaidScalingTest {

    private static RaidScaling cfg(int patrolBonusRate, int threshold, int waveCount) {
        RaidScaling rs = new RaidScaling();
        rs.patrolBonusRate = patrolBonusRate;
        rs.extraWaveTierThreshold = threshold;
        rs.extraWaveCount = waveCount;
        return rs;
    }

    // ---- extraPatrolMembers ----

    @Test
    void extraPatrolMembers_floorsTierOverRate() {
        RaidScaling rs = cfg(2, 4, 1); // +1 per 2 tiers (default rate)
        assertEquals(0, rs.extraPatrolMembers(0));
        assertEquals(0, rs.extraPatrolMembers(1));
        assertEquals(1, rs.extraPatrolMembers(2));
        assertEquals(1, rs.extraPatrolMembers(3));
        assertEquals(2, rs.extraPatrolMembers(4));
        assertEquals(2, rs.extraPatrolMembers(5));
    }

    @Test
    void extraPatrolMembers_rateOne_addsOnePerTier() {
        RaidScaling rs = cfg(1, 4, 1);
        assertEquals(0, rs.extraPatrolMembers(0));
        assertEquals(3, rs.extraPatrolMembers(3));
        assertEquals(5, rs.extraPatrolMembers(5));
    }

    @Test
    void extraPatrolMembers_rateZero_noDivideByZero() {
        RaidScaling rs = cfg(0, 4, 1);
        assertEquals(0, rs.extraPatrolMembers(5));
    }

    @Test
    void extraPatrolMembers_negativeTier_isZero() {
        RaidScaling rs = cfg(2, 4, 1);
        assertEquals(0, rs.extraPatrolMembers(-3));
    }

    @Test
    void extraPatrolMembers_disabled_isZero() {
        RaidScaling rs = cfg(1, 4, 1);
        rs.enabled = false;
        assertEquals(0, rs.extraPatrolMembers(5));
    }

    // ---- extraWaves ----

    @Test
    void extraWaves_belowThreshold_isZero() {
        RaidScaling rs = cfg(2, 4, 1);
        assertEquals(0, rs.extraWaves(0));
        assertEquals(0, rs.extraWaves(3));
    }

    @Test
    void extraWaves_atOrAboveThreshold_isWaveCount() {
        RaidScaling rs = cfg(2, 4, 2);
        assertEquals(2, rs.extraWaves(4));
        assertEquals(2, rs.extraWaves(5));
    }

    @Test
    void extraWaves_disabled_isZero() {
        RaidScaling rs = cfg(2, 4, 1);
        rs.enabled = false;
        assertEquals(0, rs.extraWaves(5));
    }

    @Test
    void extraWaves_zeroThreshold_alwaysApplies() {
        RaidScaling rs = cfg(2, 0, 1);
        assertEquals(1, rs.extraWaves(0));
    }
}
