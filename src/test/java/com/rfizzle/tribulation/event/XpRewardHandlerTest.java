// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig.Xp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-math tests for the bonus-XP multiplier applied to scaled mobs.
 */
class XpRewardHandlerTest {

    private static Xp cfg(double xpMultiplier) {
        Xp c = new Xp();
        c.xpMultiplier = xpMultiplier;
        return c;
    }

    @Test
    void xpMultiplier_oneWhenDisabled() {
        // xpMultiplier <= 0 means the bonus is off; vanilla XP regardless of factor.
        assertEquals(1.0, XpRewardHandler.computeXpMultiplier(1.5, cfg(0.0)), 1e-9);
        assertEquals(1.0, XpRewardHandler.computeXpMultiplier(1.5, cfg(-0.5)), 1e-9);
    }

    @Test
    void xpMultiplier_oneWhenFactorIsZero() {
        Xp c = cfg(1.0);
        assertEquals(1.0, XpRewardHandler.computeXpMultiplier(0.0, c), 1e-9);
        assertEquals(1.0, XpRewardHandler.computeXpMultiplier(-0.3, c), 1e-9);
    }

    @Test
    void xpMultiplier_oneWhenNullConfig() {
        assertEquals(1.0, XpRewardHandler.computeXpMultiplier(1.5, null), 1e-9);
    }

    @Test
    void xpMultiplier_linearInFactor() {
        Xp c = cfg(2.0);
        // mult = 1 + factor * xpMultiplier. factor=0.5 → 1 + 1.0 = 2.0
        assertEquals(2.0, XpRewardHandler.computeXpMultiplier(0.5, c), 1e-9);
        // factor=1.5 → 1 + 3.0 = 4.0
        assertEquals(4.0, XpRewardHandler.computeXpMultiplier(1.5, c), 1e-9);
    }

    @Test
    void xpMultiplier_hasNoOwnCeiling() {
        // No separate cap — healthFactor is bounded upstream, so a large factor
        // produces a large multiplier with no clamp here.
        assertEquals(6.0, XpRewardHandler.computeXpMultiplier(5.0, cfg(1.0)), 1e-9);
    }

    @Test
    void applyXpMultiplier_roundsInsteadOfFloors() {
        Xp c = cfg(1.0);
        // 1 XP * 1.5 = 1.5 → rounds to 2.
        assertEquals(2, XpRewardHandler.applyXpMultiplier(1, 0.5, c));
        // 3 XP * 2.0 = 6.
        assertEquals(6, XpRewardHandler.applyXpMultiplier(3, 1.0, c));
    }

    @Test
    void applyXpMultiplier_passThroughWhenZeroOrNegativeBase() {
        Xp c = cfg(1.0);
        assertEquals(0, XpRewardHandler.applyXpMultiplier(0, 2.0, c));
        assertEquals(-5, XpRewardHandler.applyXpMultiplier(-5, 2.0, c));
    }

    @Test
    void applyXpMultiplier_noChangeWhenMultiplierIsOne() {
        assertEquals(7, XpRewardHandler.applyXpMultiplier(7, 0.0, cfg(1.0)));
    }

    @Test
    void applyXpMultiplier_clampsToIntMaxOnOverflow() {
        // base * (1 + 9*10) would overflow int. Should clamp.
        int result = XpRewardHandler.applyXpMultiplier(Integer.MAX_VALUE, 9.0, cfg(10.0));
        assertEquals(Integer.MAX_VALUE, result);
    }

    @Test
    void xpMultiplier_defaultsMatchReferenceZombie() {
        // level-250 zombie health factor is 2.5 (per-mob scaling table).
        // Default Xp: xpMultiplier=1.0. So mult = 1 + 2.5 = 3.5.
        assertEquals(3.5, XpRewardHandler.computeXpMultiplier(2.5, new Xp()), 1e-9);
    }
}
