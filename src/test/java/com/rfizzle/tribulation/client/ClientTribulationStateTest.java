// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTribulationStateTest {

    @BeforeEach
    void setUp() {
        ClientTribulationState.reset();
    }

    @Test
    void initialState_isSentinel() {
        assertEquals(-1, ClientTribulationState.getLevel());
        assertEquals(-1, ClientTribulationState.getLevelUpTimestamp());
    }

    @Test
    void setLevel_updatesLevel() {
        ClientTribulationState.setLevel(42);
        assertEquals(42, ClientTribulationState.getLevel());
    }

    @Test
    void setLevel_increase_setsTimestamp() {
        ClientTribulationState.setLevel(5);
        long ts = ClientTribulationState.getLevelUpTimestamp();
        assertTrue(ts > 0, "timestamp should be set on level increase");
    }

    @Test
    void setLevel_decrease_doesNotSetTimestamp() {
        ClientTribulationState.setLevel(10);
        ClientTribulationState.reset();
        ClientTribulationState.setLevel(10);
        long afterIncrease = ClientTribulationState.getLevelUpTimestamp();

        ClientTribulationState.setLevel(5);
        long afterDecrease = ClientTribulationState.getLevelUpTimestamp();
        assertEquals(afterIncrease, afterDecrease, "timestamp should not change on decrease");
    }

    @Test
    void setLevel_decrease_setsDropTimestamp() {
        ClientTribulationState.setLevel(10);
        ClientTribulationState.setLevel(5);
        assertTrue(ClientTribulationState.getLevelDropTimestamp() > 0,
                "drop timestamp should be set on a real level decrease");
    }

    @Test
    void setLevel_initialSyncFromSentinel_noDropTimestamp() {
        // First real sync moves up from the -1 sentinel to a positive level;
        // that is initial state, not a decrease, so no cooling flash triggers.
        ClientTribulationState.setLevel(50);
        assertEquals(-1, ClientTribulationState.getLevelDropTimestamp());
    }

    @Test
    void setLevel_increase_doesNotSetDropTimestamp() {
        ClientTribulationState.setLevel(3);
        ClientTribulationState.setLevel(8);
        assertEquals(-1, ClientTribulationState.getLevelDropTimestamp());
    }

    @Test
    void setLevel_sameValue_noChange() {
        ClientTribulationState.setLevel(7);
        long ts = ClientTribulationState.getLevelUpTimestamp();
        ClientTribulationState.setLevel(7);
        assertEquals(ts, ClientTribulationState.getLevelUpTimestamp());
        assertEquals(7, ClientTribulationState.getLevel());
    }

    @Test
    void reset_clearsState() {
        ClientTribulationState.setLevel(100);
        ClientTribulationState.setProgress(50000, 72000);
        ClientTribulationState.reset();
        assertEquals(-1, ClientTribulationState.getLevel());
        assertEquals(-1, ClientTribulationState.getLevelUpTimestamp());
        assertEquals(0, ClientTribulationState.getProgressTicks());
        assertEquals(1, ClientTribulationState.getGoalTicks());
    }

    @Test
    void getProgressFraction_atZero_returnsZero() {
        ClientTribulationState.setProgress(0, 72000);
        assertEquals(0f, ClientTribulationState.getProgressFraction(), 0.0001f);
    }

    @Test
    void getProgressFraction_atHalfway_returnsHalf() {
        ClientTribulationState.setProgress(36000, 72000);
        assertEquals(0.5f, ClientTribulationState.getProgressFraction(), 0.0001f);
    }

    @Test
    void getProgressFraction_atFull_returnsOne() {
        ClientTribulationState.setProgress(72000, 72000);
        assertEquals(1f, ClientTribulationState.getProgressFraction(), 0.0001f);
    }

    @Test
    void getProgressFraction_overflow_clampsToOne() {
        ClientTribulationState.setProgress(99999999, 72000);
        assertEquals(1f, ClientTribulationState.getProgressFraction(), 0.0001f);
    }

    @Test
    void setProgress_clampsNegativeProgressToZero() {
        ClientTribulationState.setProgress(-100, 72000);
        assertEquals(0, ClientTribulationState.getProgressTicks());
    }

    @Test
    void setProgress_clampsNonPositiveGoalToOne() {
        ClientTribulationState.setProgress(50, 0);
        assertEquals(1, ClientTribulationState.getGoalTicks());
    }

    @Test
    void setOppressiveNightDarkness_keepsFinitePositive() {
        ClientTribulationState.setOppressiveNightDarkness(0.3f);
        assertEquals(0.3f, ClientTribulationState.getOppressiveNightDarkness(), 0.0001f);
    }

    @Test
    void setOppressiveNightDarkness_clampsNegativeToZero() {
        ClientTribulationState.setOppressiveNightDarkness(-1.0f);
        assertEquals(0f, ClientTribulationState.getOppressiveNightDarkness(), 0.0001f);
    }

    @Test
    void setOppressiveNightDarkness_rejectsNaN() {
        // A non-finite synced value must never reach the lightmap; it reads as off.
        ClientTribulationState.setOppressiveNightDarkness(Float.NaN);
        assertEquals(0f, ClientTribulationState.getOppressiveNightDarkness(), 0.0001f);
    }

    @Test
    void setOppressiveNightDarkness_rejectsInfinity() {
        ClientTribulationState.setOppressiveNightDarkness(Float.POSITIVE_INFINITY);
        assertEquals(0f, ClientTribulationState.getOppressiveNightDarkness(), 0.0001f);
        ClientTribulationState.setOppressiveNightDarkness(Float.NEGATIVE_INFINITY);
        assertEquals(0f, ClientTribulationState.getOppressiveNightDarkness(), 0.0001f);
    }
}
