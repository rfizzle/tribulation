// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.item;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the Heart Fragment restore logic through PlayerDifficultyState.
 * The item's use() method delegates to restoreHearts() + applyModifier() —
 * the interaction with ServerPlayer is integration-tested in-game.
 */
class HeartFragmentItemTest {

    @Test
    void restore_reducesHeartsLost() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.addHeartsLost(uuid, 6, 2);

        TribulationConfig.HardcoreHearts cfg = new TribulationConfig.HardcoreHearts();
        cfg.heartsRestoredPerFragment = 2;

        int after = state.restoreHearts(uuid, cfg.heartsRestoredPerFragment);
        assertEquals(4, after);
    }

    @Test
    void restore_floorsAtZero() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.addHeartsLost(uuid, 1, 2);

        TribulationConfig.HardcoreHearts cfg = new TribulationConfig.HardcoreHearts();
        cfg.heartsRestoredPerFragment = 4;

        int after = state.restoreHearts(uuid, cfg.heartsRestoredPerFragment);
        assertEquals(0, after);
    }

    @Test
    void restore_noOpWhenAlreadyFull() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        // heartsLost is 0 by default

        int after = state.restoreHearts(uuid, 2);
        assertEquals(0, after);
    }

    @Test
    void restore_multipleFragments_accumulateCorrectly() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.addHeartsLost(uuid, 10, 2);

        state.restoreHearts(uuid, 2);
        state.restoreHearts(uuid, 2);
        int after = state.restoreHearts(uuid, 2);
        assertEquals(4, after);
    }

    @Test
    void restore_cannotExceedBaseline() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.addHeartsLost(uuid, 4, 2);

        // Restore more than was lost
        int after = state.restoreHearts(uuid, 20);
        assertEquals(0, after);
        // Max HP is back at 20 (baseline)
        assertEquals(20, 20 - after);
    }
}
