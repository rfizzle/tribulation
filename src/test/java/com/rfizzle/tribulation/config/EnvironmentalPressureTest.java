// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.config;

import com.rfizzle.tribulation.config.TribulationConfig.EnvironmentalPressure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentalPressureTest {

    private static EnvironmentalPressure enabled() {
        EnvironmentalPressure ep = new EnvironmentalPressure();
        ep.enabled = true;
        return ep;
    }

    // ---- strikesActiveAtTier ----

    @ParameterizedTest
    @CsvSource({
            "0, false",
            "2, false",
            "3, true",
            "4, true",
            "5, true"
    })
    void strikesActiveAtTier_gatesOnDefaultThreshold(int tier, boolean expected) {
        assertEquals(expected, enabled().strikesActiveAtTier(tier));
    }

    @Test
    void strikesActiveAtTier_masterToggleOff_isAlwaysInactive() {
        EnvironmentalPressure ep = new EnvironmentalPressure();
        assertFalse(ep.strikesActiveAtTier(5));
    }

    @Test
    void strikesActiveAtTier_strikesToggleOff_isInactive() {
        EnvironmentalPressure ep = enabled();
        ep.debilitatingStrikes.enabled = false;
        assertFalse(ep.strikesActiveAtTier(5));
    }

    @Test
    void strikesActiveAtTier_thresholdZero_isActiveAtTierZero() {
        EnvironmentalPressure ep = enabled();
        ep.debilitatingStrikes.tierThreshold = 0;
        assertTrue(ep.strikesActiveAtTier(0));
    }

    // ---- nightDarknessAtTier ----

    @ParameterizedTest
    @CsvSource({
            "0, 0.0",
            "3, 0.0",
            "4, 0.25",
            "5, 0.25"
    })
    void nightDarknessAtTier_gatesOnDefaultThreshold(int tier, double expected) {
        assertEquals(expected, enabled().nightDarknessAtTier(tier));
    }

    @Test
    void nightDarknessAtTier_masterToggleOff_isZero() {
        EnvironmentalPressure ep = new EnvironmentalPressure();
        assertEquals(0.0, ep.nightDarknessAtTier(5));
    }

    @Test
    void nightDarknessAtTier_nightsToggleOff_isZero() {
        EnvironmentalPressure ep = enabled();
        ep.oppressiveNights.enabled = false;
        assertEquals(0.0, ep.nightDarknessAtTier(5));
    }

    @Test
    void nightDarknessAtTier_returnsConfiguredStrength() {
        EnvironmentalPressure ep = enabled();
        ep.oppressiveNights.maxDarkness = 0.4;
        assertEquals(0.4, ep.nightDarknessAtTier(5));
    }

    // ---- nightFollowRangeMultiplierAtTier ----

    @ParameterizedTest
    @CsvSource({
            "0, 1.0",
            "3, 1.0",
            "4, 1.5",
            "5, 1.5"
    })
    void nightFollowRangeMultiplierAtTier_gatesOnDefaultThreshold(int tier, double expected) {
        assertEquals(expected, enabled().nightFollowRangeMultiplierAtTier(tier));
    }

    @Test
    void nightFollowRangeMultiplierAtTier_masterToggleOff_isNeutral() {
        EnvironmentalPressure ep = new EnvironmentalPressure();
        assertEquals(1.0, ep.nightFollowRangeMultiplierAtTier(5));
    }

    @Test
    void nightFollowRangeMultiplierAtTier_nightsToggleOff_isNeutral() {
        EnvironmentalPressure ep = enabled();
        ep.oppressiveNights.enabled = false;
        assertEquals(1.0, ep.nightFollowRangeMultiplierAtTier(5));
    }

    @Test
    void nightFollowRangeMultiplierAtTier_returnsConfiguredMultiplier() {
        EnvironmentalPressure ep = enabled();
        ep.oppressiveNights.followRangeMultiplier = 2.0;
        assertEquals(2.0, ep.nightFollowRangeMultiplierAtTier(5));
    }
}
