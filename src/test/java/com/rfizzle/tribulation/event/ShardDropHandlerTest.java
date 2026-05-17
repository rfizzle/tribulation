// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.testutil.FixedRandom;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for the shard drop roll. Actually spawning an {@link
 * net.minecraft.world.entity.item.ItemEntity} needs a ServerLevel, so that
 * path is integration-tested in-game.
 */
class ShardDropHandlerTest {

    private static TribulationConfig.Shards config(boolean enabled, int startLevel, double chance, int power) {
        TribulationConfig.Shards s = new TribulationConfig.Shards();
        s.enabled = enabled;
        s.dropStartLevel = startLevel;
        s.dropChance = chance;
        s.shardPower = power;
        return s;
    }

    /** Deterministic RandomSource that returns a fixed double from nextDouble(). */
    private static RandomSource fixedDouble(double value) {
        return new FixedRandom(value);
    }

    @Test
    void shouldDrop_falseWhenDisabled() {
        TribulationConfig.Shards cfg = config(false, 0, 1.0, 5);
        assertFalse(ShardDropHandler.shouldDrop(100, cfg, fixedDouble(0.0)));
    }

    @Test
    void shouldDrop_falseBelowStartLevel() {
        TribulationConfig.Shards cfg = config(true, 25, 1.0, 5);
        assertFalse(ShardDropHandler.shouldDrop(24, cfg, fixedDouble(0.0)));
        assertFalse(ShardDropHandler.shouldDrop(0, cfg, fixedDouble(0.0)));
    }

    @Test
    void shouldDrop_trueAtOrAboveStartLevelWithFullChance() {
        TribulationConfig.Shards cfg = config(true, 25, 1.0, 5);
        assertTrue(ShardDropHandler.shouldDrop(25, cfg, fixedDouble(0.999)));
        assertTrue(ShardDropHandler.shouldDrop(250, cfg, fixedDouble(0.0)));
    }

    @Test
    void shouldDrop_rollCompareIsStrictLessThan() {
        TribulationConfig.Shards cfg = config(true, 0, 0.5, 5);
        // Roll == chance should fail (strict <).
        assertFalse(ShardDropHandler.shouldDrop(25, cfg, fixedDouble(0.5)));
        // Roll < chance should win.
        assertTrue(ShardDropHandler.shouldDrop(25, cfg, fixedDouble(0.4999)));
    }

    @Test
    void shouldDrop_falseOnZeroChance() {
        TribulationConfig.Shards cfg = config(true, 0, 0.0, 5);
        assertFalse(ShardDropHandler.shouldDrop(100, cfg, fixedDouble(0.0)));
    }

    @Test
    void shouldDrop_falseOnZeroPower() {
        // shardPower=0 means the shard does nothing; no point dropping it.
        TribulationConfig.Shards cfg = config(true, 0, 1.0, 0);
        assertFalse(ShardDropHandler.shouldDrop(100, cfg, fixedDouble(0.0)));
    }

    @Test
    void shouldDrop_falseOnNullConfig() {
        assertFalse(ShardDropHandler.shouldDrop(100, null, fixedDouble(0.0)));
    }

    @Test
    void shouldDrop_defaultChanceExactMidpoint() {
        // dropChance=0.005 — sanity check around the default.
        TribulationConfig.Shards cfg = config(true, 25, 0.005, 5);
        assertTrue(ShardDropHandler.shouldDrop(50, cfg, fixedDouble(0.004)));
        assertFalse(ShardDropHandler.shouldDrop(50, cfg, fixedDouble(0.005)));
        assertFalse(ShardDropHandler.shouldDrop(50, cfg, fixedDouble(0.5)));
    }

}
