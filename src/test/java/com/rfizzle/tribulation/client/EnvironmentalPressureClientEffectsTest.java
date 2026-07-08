// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentalPressureClientEffectsTest {

    /** Mth.cos is table-based, so compare with a small tolerance. */
    private static final float EPSILON = 0.01f;

    @Test
    void nightFactor_isZeroAtNoon() {
        // timeOfDay 0.0 = noon (celestial angle origin)
        assertEquals(0.0f, EnvironmentalPressureClientEffects.nightFactor(0.0f), EPSILON);
    }

    @Test
    void nightFactor_isFullAtMidnight() {
        // timeOfDay 0.5 = midnight
        assertEquals(1.0f, EnvironmentalPressureClientEffects.nightFactor(0.5f), EPSILON);
    }

    @Test
    void nightFactor_rampsSmoothlyThroughDusk() {
        // timeOfDay 0.25 = the dusk transition midpoint
        assertEquals(0.5f, EnvironmentalPressureClientEffects.nightFactor(0.25f), EPSILON);
    }

    @Test
    void nightFactor_staysInUnitRange() {
        for (float t = 0.0f; t < 1.0f; t += 0.01f) {
            float factor = EnvironmentalPressureClientEffects.nightFactor(t);
            assertTrue(factor >= 0.0f && factor <= 1.0f,
                    "nightFactor(" + t + ") = " + factor + " must be in [0,1]");
        }
    }

    @Test
    void computeDarkness_scalesByNightFactor() {
        assertEquals(0.25f, EnvironmentalPressureClientEffects.computeDarkness(0.25f, 0.5f), EPSILON);
        assertEquals(0.0f, EnvironmentalPressureClientEffects.computeDarkness(0.25f, 0.0f), EPSILON);
    }

    @Test
    void computeDarkness_capsOversizedServerValue() {
        // A misconfigured/malicious server may send anything; the client caps it.
        assertEquals(EnvironmentalPressureClientEffects.MAX_DARKNESS,
                EnvironmentalPressureClientEffects.computeDarkness(50.0f, 0.5f), EPSILON);
    }

    @Test
    void computeDarkness_rejectsNaN() {
        // NaN cannot be tamed by the MAX_DARKNESS clamp, so it reads as no darkness.
        assertEquals(0.0f, EnvironmentalPressureClientEffects.computeDarkness(Float.NaN, 0.5f), EPSILON);
    }

    @Test
    void computeDarkness_rejectsInfinity() {
        assertEquals(0.0f,
                EnvironmentalPressureClientEffects.computeDarkness(Float.POSITIVE_INFINITY, 0.5f), EPSILON);
        assertEquals(0.0f,
                EnvironmentalPressureClientEffects.computeDarkness(Float.NEGATIVE_INFINITY, 0.5f), EPSILON);
    }
}
