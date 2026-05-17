// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.config.TribulationConfig.Tiers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-math tests for {@link TierManager}. No Minecraft bootstrap needed.
 */
class TierManagerTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0", "49, 0",
            "50, 1", "99, 1",
            "100, 2", "149, 2",
            "150, 3", "199, 3",
            "200, 4", "249, 4",
            "250, 5"
    })
    void defaultTiers_thresholdsMatchDesign(int level, int expectedTier) {
        assertEquals(expectedTier, TierManager.getTier(level, new Tiers()));
    }

    @Test
    void levelAboveMax_returnsTopTier() {
        Tiers t = new Tiers();
        assertEquals(5, TierManager.getTier(1000, t));
        assertEquals(5, TierManager.getTier(Integer.MAX_VALUE, t));
    }

    @Test
    void negativeLevel_returnsMinTier() {
        Tiers t = new Tiers();
        assertEquals(0, TierManager.getTier(-1, t));
        assertEquals(0, TierManager.getTier(Integer.MIN_VALUE, t));
    }

    @Test
    void nullConfig_returnsMinTier() {
        assertEquals(0, TierManager.getTier(250, null));
        assertEquals(0, TierManager.getTier(0, null));
    }

    @Test
    void customThresholds_respected() {
        Tiers t = new Tiers();
        t.tier1 = 5;
        t.tier2 = 10;
        t.tier3 = 15;
        t.tier4 = 20;
        t.tier5 = 25;
        assertEquals(0, TierManager.getTier(4, t));
        assertEquals(1, TierManager.getTier(5, t));
        assertEquals(2, TierManager.getTier(10, t));
        assertEquals(3, TierManager.getTier(17, t));
        assertEquals(4, TierManager.getTier(20, t));
        assertEquals(5, TierManager.getTier(25, t));
        assertEquals(5, TierManager.getTier(9999, t));
    }

    @ParameterizedTest
    @CsvSource({
            "49, 0",
            "50, 1",
            "99, 1",
            "100, 2",
            "249, 4",
            "250, 5"
    })
    void boundaryConditions_respectsInclusiveThreshold(int level, int expectedTier) {
        assertEquals(expectedTier, TierManager.getTier(level, new Tiers()));
    }

    @ParameterizedTest
    @CsvSource({
            "0", "25", "50", "75", "100", "125", "150",
            "175", "200", "225", "250", "275", "300"
    })
    void scalingEngineComputeTier_delegates(int level) {
        Tiers t = new Tiers();
        assertEquals(TierManager.getTier(level, t), ScalingEngine.computeTier(level, t));
    }
}
