// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.XpAndLoot;
import com.rfizzle.tribulation.testutil.FixedRandom;
import com.rfizzle.tribulation.testutil.ThrowingRandom;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math tests for the XP multiplier and extra-loot roll. The
 * ItemEntity scan path uses a ServerLevel, so it's exercised integration-style
 * in-game; only the math is unit-tested here.
 */
class XpLootHandlerTest {

    private static XpAndLoot cfg(boolean extraXp, double maxXpFactor, boolean dropMoreLoot, double moreLootChance, double maxLootChance) {
        XpAndLoot c = new XpAndLoot();
        c.extraXp = extraXp;
        c.maxXpFactor = maxXpFactor;
        c.dropMoreLoot = dropMoreLoot;
        c.moreLootChance = moreLootChance;
        c.maxLootChance = maxLootChance;
        return c;
    }

    // ---- XP multiplier ----

    @Test
    void xpMultiplier_oneWhenDisabled() {
        XpAndLoot c = cfg(false, 2.0, false, 0.02, 0.7);
        assertEquals(1.0, XpLootHandler.computeXpMultiplier(1.5, c), 1e-9);
    }

    @Test
    void xpMultiplier_oneWhenFactorIsZero() {
        XpAndLoot c = cfg(true, 2.0, false, 0.02, 0.7);
        assertEquals(1.0, XpLootHandler.computeXpMultiplier(0.0, c), 1e-9);
        assertEquals(1.0, XpLootHandler.computeXpMultiplier(-0.3, c), 1e-9);
    }

    @Test
    void xpMultiplier_linearBelowCap() {
        XpAndLoot c = cfg(true, 3.0, false, 0.02, 0.7);
        // cap addend = 2.0. factor=0.5 → mult = 1.5
        assertEquals(1.5, XpLootHandler.computeXpMultiplier(0.5, c), 1e-9);
        // factor=1.5 → mult = 2.5
        assertEquals(2.5, XpLootHandler.computeXpMultiplier(1.5, c), 1e-9);
    }

    @Test
    void xpMultiplier_cappedAtMaxXpFactor() {
        XpAndLoot c = cfg(true, 2.0, false, 0.02, 0.7);
        // cap addend = 1.0. factor=1.5 → capped at 2.0 total.
        assertEquals(2.0, XpLootHandler.computeXpMultiplier(1.5, c), 1e-9);
        assertEquals(2.0, XpLootHandler.computeXpMultiplier(5.0, c), 1e-9);
    }

    @Test
    void xpMultiplier_oneWhenMaxFactorLtOne() {
        // maxXpFactor <= 1 means no bonus room; scaler returns 1.0 regardless.
        XpAndLoot c = cfg(true, 1.0, false, 0.02, 0.7);
        assertEquals(1.0, XpLootHandler.computeXpMultiplier(2.0, c), 1e-9);
        XpAndLoot c2 = cfg(true, 0.5, false, 0.02, 0.7);
        assertEquals(1.0, XpLootHandler.computeXpMultiplier(2.0, c2), 1e-9);
    }

    @Test
    void applyXpMultiplier_roundsInsteadOfFloors() {
        XpAndLoot c = cfg(true, 2.0, false, 0.02, 0.7);
        // 1 XP * 1.5 = 1.5 → rounds to 2.
        assertEquals(2, XpLootHandler.applyXpMultiplier(1, 0.5, c));
        // 3 XP * 2.0 = 6.
        assertEquals(6, XpLootHandler.applyXpMultiplier(3, 1.5, c));
    }

    @Test
    void applyXpMultiplier_passThroughWhenZeroOrNegativeBase() {
        XpAndLoot c = cfg(true, 2.0, false, 0.02, 0.7);
        assertEquals(0, XpLootHandler.applyXpMultiplier(0, 2.0, c));
        assertEquals(-5, XpLootHandler.applyXpMultiplier(-5, 2.0, c));
    }

    @Test
    void applyXpMultiplier_noChangeWhenMultiplierIsOne() {
        XpAndLoot c = cfg(true, 2.0, false, 0.02, 0.7);
        assertEquals(7, XpLootHandler.applyXpMultiplier(7, 0.0, c));
    }

    @Test
    void applyXpMultiplier_clampsToIntMaxOnOverflow() {
        XpAndLoot c = cfg(true, 10.0, false, 0.02, 0.7);
        // base * 10 would overflow int. Should clamp.
        int result = XpLootHandler.applyXpMultiplier(Integer.MAX_VALUE, 9.0, c);
        assertEquals(Integer.MAX_VALUE, result);
    }

    // ---- Extra loot chance ----

    @Test
    void extraLoot_zeroWhenDisabled() {
        XpAndLoot c = cfg(true, 2.0, false, 0.02, 0.7);
        assertEquals(0.0, XpLootHandler.computeExtraLootChance(1.5, c), 1e-9);
    }

    @Test
    void extraLoot_zeroWhenFactorOrRateIsZero() {
        XpAndLoot c = cfg(true, 2.0, true, 0.02, 0.7);
        assertEquals(0.0, XpLootHandler.computeExtraLootChance(0.0, c), 1e-9);
        XpAndLoot c2 = cfg(true, 2.0, true, 0.0, 0.7);
        assertEquals(0.0, XpLootHandler.computeExtraLootChance(1.5, c2), 1e-9);
    }

    @Test
    void extraLoot_linearWithFactor() {
        XpAndLoot c = cfg(true, 2.0, true, 0.1, 1.0);
        assertEquals(0.1, XpLootHandler.computeExtraLootChance(1.0, c), 1e-9);
        assertEquals(0.3, XpLootHandler.computeExtraLootChance(3.0, c), 1e-9);
    }

    @Test
    void extraLoot_cappedAtMaxLootChance() {
        XpAndLoot c = cfg(true, 2.0, true, 0.5, 0.7);
        // 2 * 0.5 = 1.0 → capped at 0.7
        assertEquals(0.7, XpLootHandler.computeExtraLootChance(2.0, c), 1e-9);
    }

    @Test
    void shouldDropExtraLoot_rollCompareIsStrictLessThan() {
        XpAndLoot c = cfg(true, 2.0, true, 0.5, 1.0);
        // factor=1.0, chance=0.5. Strict <.
        assertTrue(XpLootHandler.shouldDropExtraLoot(1.0, c, fixedDouble(0.4999)));
        assertFalse(XpLootHandler.shouldDropExtraLoot(1.0, c, fixedDouble(0.5)));
    }

    @Test
    void shouldDropExtraLoot_alwaysWhenChanceAtLeastOne() {
        // chance = 1.0 short-circuits past nextDouble entirely.
        XpAndLoot c = cfg(true, 2.0, true, 1.0, 1.0);
        assertTrue(XpLootHandler.shouldDropExtraLoot(1.0, c, new ThrowingRandom()));
    }

    @Test
    void shouldDropExtraLoot_falseWhenChanceZero() {
        XpAndLoot c = cfg(true, 2.0, false, 0.02, 0.7);
        assertFalse(XpLootHandler.shouldDropExtraLoot(1.5, c, fixedDouble(0.0)));
    }

    @Test
    void shouldDropExtraLoot_falseWhenNullConfig() {
        assertFalse(XpLootHandler.shouldDropExtraLoot(1.5, null, fixedDouble(0.0)));
    }

    // ---- Defaults from DESIGN.md ----

    @Test
    void xpMultiplier_defaultsMatchDesignReferenceZombie() {
        // level-250 zombie health factor is 2.5 (DESIGN.md zombie table).
        // Default XpAndLoot: extraXp=true, maxXpFactor=2.0. So expected mult=2.0.
        XpAndLoot c = new XpAndLoot();
        assertEquals(2.0, XpLootHandler.computeXpMultiplier(2.5, c), 1e-9);
    }

    // ---- Test fixtures ----

    private static RandomSource fixedDouble(double value) {
        return new FixedRandom(value);
    }

}
