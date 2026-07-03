// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math coverage for the Blood Moon trigger conditions and the spawn-cap
 * arithmetic. World-driven behavior (nightfall detection, SavedData
 * persistence, client sync) is covered by {@code BloodMoonGameTest}.
 */
class BloodMoonHandlerTest {

    // ---- rollDue ----

    @Test
    void rollDue_fullMoonNight_firstCheck_isDue() {
        assertTrue(BloodMoonHandler.rollDue(false, true, 0, 4, Long.MIN_VALUE));
    }

    @Test
    void rollDue_alreadyActive_isNotDue() {
        assertFalse(BloodMoonHandler.rollDue(true, true, 0, 4, Long.MIN_VALUE));
    }

    @Test
    void rollDue_daytime_isNotDue() {
        assertFalse(BloodMoonHandler.rollDue(false, false, 0, 4, Long.MIN_VALUE));
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3", "4", "5", "6", "7"})
    void rollDue_nonFullMoonPhase_isNotDue(int phase) {
        assertFalse(BloodMoonHandler.rollDue(false, true, phase, 4, Long.MIN_VALUE));
    }

    @Test
    void rollDue_sameNightAlreadyRolled_isNotDue() {
        assertFalse(BloodMoonHandler.rollDue(false, true, 0, 4, 4));
    }

    @Test
    void rollDue_nextFullMoonAfterFailedRoll_isDueAgain() {
        // Rolled (and failed) on day 4; eight days later the moon is full again.
        assertTrue(BloodMoonHandler.rollDue(false, true, 0, 12, 4));
    }

    // ---- shouldEnd ----

    @Test
    void shouldEnd_activeAtDaybreak_ends() {
        assertTrue(BloodMoonHandler.shouldEnd(true, false));
    }

    @Test
    void shouldEnd_activeAtNight_continues() {
        assertFalse(BloodMoonHandler.shouldEnd(true, true));
    }

    @Test
    void shouldEnd_inactive_neverEnds() {
        assertFalse(BloodMoonHandler.shouldEnd(false, false));
        assertFalse(BloodMoonHandler.shouldEnd(false, true));
    }

    // ---- scaledMobCap ----

    @Test
    void scaledMobCap_inactive_returnsBase() {
        assertEquals(70, BloodMoonHandler.scaledMobCap(70, false, 2.0));
    }

    @Test
    void scaledMobCap_active_scalesAndRounds() {
        assertEquals(140, BloodMoonHandler.scaledMobCap(70, true, 2.0));
        assertEquals(105, BloodMoonHandler.scaledMobCap(70, true, 1.5));
    }

    @Test
    void scaledMobCap_multiplierAtOrBelowOne_returnsBase() {
        assertEquals(70, BloodMoonHandler.scaledMobCap(70, true, 1.0));
        assertEquals(70, BloodMoonHandler.scaledMobCap(70, true, 0.5));
    }

    @Test
    void scaledMobCap_neverBelowBase() {
        assertEquals(0, BloodMoonHandler.scaledMobCap(0, true, 2.0));
        assertEquals(1, BloodMoonHandler.scaledMobCap(1, true, 1.2));
    }
}
