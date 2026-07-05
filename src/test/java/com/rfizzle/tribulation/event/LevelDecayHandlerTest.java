// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import org.junit.jupiter.api.Test;

import static com.rfizzle.tribulation.event.LevelDecayHandler.DAY_MS;
import static com.rfizzle.tribulation.event.LevelDecayHandler.computeDecayLevels;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-math coverage of {@link LevelDecayHandler#computeDecayLevels}. The
 * handler shell (config gate, anchor consumption, floor, notification,
 * callback) is exercised end-to-end in {@code LevelDecayGameTest}.
 */
class LevelDecayHandlerTest {

    @Test
    void withinGraceWindow_decaysNothing() {
        // 6 days away, 7-day grace.
        assertEquals(0, computeDecayLevels(6 * DAY_MS, 7.0, 2.0));
    }

    @Test
    void exactlyAtGraceBoundary_decaysNothing() {
        assertEquals(0, computeDecayLevels(7 * DAY_MS, 7.0, 2.0));
    }

    @Test
    void oneDayBeyondGrace_decaysOneDaysWorth() {
        assertEquals(2, computeDecayLevels(8 * DAY_MS, 7.0, 2.0));
    }

    @Test
    void threeDaysBeyondGrace_decaysLinearly() {
        assertEquals(6, computeDecayLevels(10 * DAY_MS, 7.0, 2.0));
    }

    @Test
    void partialDayBeyondGrace_floorsToWholeLevels() {
        // 1.5 days past grace at 2/day = 3.0 levels; 1.4 days = 2.8 → 2.
        assertEquals(3, computeDecayLevels(8 * DAY_MS + DAY_MS / 2, 7.0, 2.0));
        assertEquals(2, computeDecayLevels(8 * DAY_MS + (long) (0.4 * DAY_MS), 7.0, 2.0));
    }

    @Test
    void fractionalRate_accumulatesAcrossDays() {
        // 0.5 levels/day: 1 day past grace floors to 0, 4 days past grace = 2.
        assertEquals(0, computeDecayLevels(8 * DAY_MS, 7.0, 0.5));
        assertEquals(2, computeDecayLevels(11 * DAY_MS, 7.0, 0.5));
    }

    @Test
    void zeroGrace_decaysFromDayOne() {
        assertEquals(2, computeDecayLevels(DAY_MS, 0.0, 2.0));
    }

    @Test
    void negativeGrace_isTreatedAsZero() {
        assertEquals(2, computeDecayLevels(DAY_MS, -5.0, 2.0));
    }

    @Test
    void zeroOrNegativeRate_decaysNothing() {
        assertEquals(0, computeDecayLevels(100 * DAY_MS, 7.0, 0.0));
        assertEquals(0, computeDecayLevels(100 * DAY_MS, 7.0, -1.0));
    }

    @Test
    void zeroOrNegativeElapsed_decaysNothing() {
        // Clock skew (login timestamp before the recorded logout) must never decay.
        assertEquals(0, computeDecayLevels(0, 0.0, 2.0));
        assertEquals(0, computeDecayLevels(-DAY_MS, 0.0, 2.0));
    }

    @Test
    void pathologicalProduct_saturatesInsteadOfOverflowing() {
        int decay = computeDecayLevels(Long.MAX_VALUE, 0.0, Double.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, decay);
    }
}
