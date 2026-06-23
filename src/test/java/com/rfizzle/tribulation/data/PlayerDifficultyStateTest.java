// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDifficultyStateTest {

    @Test
    void newPlayerData_hasZeroLevelAndNoDeathTimestamp() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        assertEquals(0, pd.level);
        assertEquals(0, pd.tickCounter);
        assertEquals(PlayerDifficultyState.NEVER_DIED, pd.lastDeathTick);
    }

    @Test
    void getPlayerData_returnsSameInstanceForSameUuid() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData first = state.getPlayerData(uuid);
        PlayerDifficultyState.PlayerData second = state.getPlayerData(uuid);
        assertSame(first, second);
    }

    @Test
    void getPlayerData_distinctInstancesPerUuid() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        PlayerDifficultyState.PlayerData a = state.getPlayerData(UUID.randomUUID());
        PlayerDifficultyState.PlayerData b = state.getPlayerData(UUID.randomUUID());
        assertNotSame(a, b);
    }

    @Test
    void applyTicks_belowLevelUp_justAccumulates() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        int levelsGained = PlayerDifficultyState.applyTicks(pd, 100, 72000, 250);
        assertEquals(0, levelsGained);
        assertEquals(0, pd.level);
        assertEquals(100, pd.tickCounter);
    }

    @Test
    void applyTicks_rollsOverIntoNextLevel() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.tickCounter = 71990;
        int levelsGained = PlayerDifficultyState.applyTicks(pd, 20, 72000, 250);
        assertEquals(1, levelsGained);
        assertEquals(1, pd.level);
        assertEquals(10, pd.tickCounter);
    }

    @Test
    void applyTicks_canRollOverMultipleLevelsAtOnce() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        int levelsGained = PlayerDifficultyState.applyTicks(pd, 144_000, 72_000, 250);
        assertEquals(2, levelsGained);
        assertEquals(2, pd.level);
        assertEquals(0, pd.tickCounter);
    }

    @Test
    void applyTicks_clampsToMaxLevel() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 249;
        pd.tickCounter = 71_000;
        int levelsGained = PlayerDifficultyState.applyTicks(pd, 200_000, 72_000, 250);
        assertEquals(1, levelsGained);
        assertEquals(250, pd.level);
        assertEquals(0, pd.tickCounter);
    }

    @Test
    void applyTicks_atMaxLevelZeroesStaleCounter() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 250;
        pd.tickCounter = 100;
        int levelsGained = PlayerDifficultyState.applyTicks(pd, 50_000, 72_000, 250);
        // The counter was mutated (stale counter zeroed) but the player did
        // not cross a level boundary — that's what incrementTick dirties on.
        assertEquals(0, levelsGained);
        assertEquals(250, pd.level);
        assertEquals(0, pd.tickCounter);
    }

    @Test
    void applyTicks_atMaxLevelWithCleanCounter_isNoop() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 250;
        int levelsGained = PlayerDifficultyState.applyTicks(pd, 50_000, 72_000, 250);
        assertEquals(0, levelsGained);
        assertEquals(250, pd.level);
        assertEquals(0, pd.tickCounter);
    }

    @Test
    void applyTicks_reachesMaxLevelAfterFullProgressionCadence() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        // Default cadence: handler fires every 20 game ticks, increments by 20.
        // Per level: 72,000 ticks => 3,600 handler fires. 250 levels => 900,000 fires.
        for (int i = 0; i < 3_600 * 250; i++) {
            PlayerDifficultyState.applyTicks(pd, 20, 72_000, 250);
        }
        assertEquals(250, pd.level);
        assertEquals(0, pd.tickCounter);
    }

    @Test
    void incrementTick_marksDirtyOnChange() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        int levelsGained = state.incrementTick(uuid, 100, 72_000, 250);
        assertEquals(0, levelsGained);
        assertTrue(state.isDirty());
        assertEquals(100, state.getPlayerData(uuid).tickCounter);
    }

    @Test
    void incrementTick_invalidArgumentsAreIgnored() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        assertEquals(0, state.incrementTick(uuid, 0, 72_000, 250));
        assertEquals(0, state.incrementTick(uuid, 20, 0, 250));
        assertEquals(0, state.incrementTick(uuid, 20, 72_000, 0));
        assertFalse(state.isDirty());
        assertEquals(0, state.getPlayerData(uuid).tickCounter);
    }

    // ---- level-up notifications (Task 17) ----

    @Test
    void applyTicks_zeroLevelsGainedWhenBelowBoundary() {
        // Sub-boundary tick increments must not return a level-up count — the
        // tick handler uses a zero return as "stay quiet", so cold-start ticks
        // and mid-interval ticks must not spam chat.
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        for (int i = 0; i < 100; i++) {
            int levelsGained = PlayerDifficultyState.applyTicks(pd, 20, 72_000, 250);
            assertEquals(0, levelsGained);
        }
        assertEquals(0, pd.level);
        assertEquals(2_000, pd.tickCounter);
    }

    @Test
    void incrementTick_returnsSingleLevelGainedOnBoundaryCross() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).tickCounter = 71_999;
        int levelsGained = state.incrementTick(uuid, 1, 72_000, 250);
        assertEquals(1, levelsGained);
        assertEquals(1, state.getLevel(uuid));
        assertEquals(0, state.getTickCounter(uuid));
    }

    @Test
    void incrementTick_returnsMultipleLevelsOnLaggySingleBatch() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        // A long stall can deliver several level_up-ticks' worth in a single
        // handler call — the caller must still send ONE message for the final
        // level, which only works if we report the true count back.
        int levelsGained = state.incrementTick(uuid, 72_000 * 3, 72_000, 250);
        assertEquals(3, levelsGained);
        assertEquals(3, state.getLevel(uuid));
    }

    @Test
    void incrementTick_returnsLevelsGainedUpToCap() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 248;
        int levelsGained = state.incrementTick(uuid, 72_000 * 10, 72_000, 250);
        // Can only gain 2 more before hitting the cap, regardless of how
        // many "would-have-been" rollovers the tick batch supplied.
        assertEquals(2, levelsGained);
        assertEquals(250, state.getLevel(uuid));
    }

    @Test
    void incrementTick_zeroesStaleCounterAtMaxLevelMarksDirtyButReportsNoLevels() {
        // Counter-zero at cap is a mutation (dirty), but not a level-up.
        // The tick handler must treat this as silent — chat only fires on
        // a positive return.
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData pd = state.getPlayerData(uuid);
        pd.level = 250;
        pd.tickCounter = 100;
        int levelsGained = state.incrementTick(uuid, 20, 72_000, 250);
        assertEquals(0, levelsGained);
        assertEquals(0, pd.tickCounter);
        assertTrue(state.isDirty());
    }

    @Test
    void incrementTick_atCapWithCleanCounterReportsZeroAndStaysClean() {
        // A player already capped with a zero counter receives no dirty flag
        // on subsequent ticks — otherwise every server tick would flush.
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 250;
        int levelsGained = state.incrementTick(uuid, 20, 72_000, 250);
        assertEquals(0, levelsGained);
        assertFalse(state.isDirty());
    }

    @Test
    void getLevel_returnsLevelAndCreatesEntryIfMissing() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        assertEquals(0, state.getLevel(uuid));
        assertTrue(state.trackedPlayers().contains(uuid));
    }

    @Test
    void applyReduce_firstDeathIsAlwaysOffCooldown() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 10;
        PlayerDifficultyState.Reduction r =
                PlayerDifficultyState.applyReduce(pd, 2, 6000, 0, 0L);
        assertEquals(PlayerDifficultyState.Reduction.APPLIED, r);
        assertEquals(8, pd.level);
        assertEquals(0L, pd.lastDeathTick);
    }

    @Test
    void applyReduce_floorsAtMinimumLevel() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 3;
        PlayerDifficultyState.Reduction r =
                PlayerDifficultyState.applyReduce(pd, 10, 0, 5, 12345L);
        assertEquals(PlayerDifficultyState.Reduction.APPLIED, r);
        assertEquals(5, pd.level);
        assertEquals(12345L, pd.lastDeathTick);
    }

    @Test
    void applyReduce_alreadyAtFloorStillUpdatesCooldown() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 0;
        PlayerDifficultyState.Reduction r =
                PlayerDifficultyState.applyReduce(pd, 2, 6000, 0, 500L);
        assertEquals(PlayerDifficultyState.Reduction.APPLIED_AT_FLOOR, r);
        assertEquals(0, pd.level);
        assertEquals(500L, pd.lastDeathTick);
    }

    @Test
    void applyReduce_skipsWhenWithinCooldown() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 10;
        pd.lastDeathTick = 1_000L;
        PlayerDifficultyState.Reduction r =
                PlayerDifficultyState.applyReduce(pd, 2, 6000, 0, 1_500L);
        assertEquals(PlayerDifficultyState.Reduction.ON_COOLDOWN, r);
        assertEquals(10, pd.level);
        assertEquals(1_000L, pd.lastDeathTick);
    }

    @Test
    void applyReduce_appliesAfterCooldownExpires() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 10;
        pd.lastDeathTick = 0L;
        PlayerDifficultyState.Reduction r =
                PlayerDifficultyState.applyReduce(pd, 2, 6000, 0, 6000L);
        assertEquals(PlayerDifficultyState.Reduction.APPLIED, r);
        assertEquals(8, pd.level);
        assertEquals(6000L, pd.lastDeathTick);
    }

    @Test
    void applyReduce_zeroCooldownAlwaysApplies() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 10;
        pd.lastDeathTick = 1_000L;
        PlayerDifficultyState.Reduction r =
                PlayerDifficultyState.applyReduce(pd, 2, 0, 0, 1_001L);
        assertEquals(PlayerDifficultyState.Reduction.APPLIED, r);
        assertEquals(8, pd.level);
        assertEquals(1_001L, pd.lastDeathTick);
    }

    @Test
    void applyReduce_zeroAmountLeavesLevelButStartsCooldown() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 10;
        PlayerDifficultyState.Reduction r =
                PlayerDifficultyState.applyReduce(pd, 0, 6000, 0, 42L);
        assertEquals(PlayerDifficultyState.Reduction.APPLIED_AT_FLOOR, r);
        assertEquals(10, pd.level);
        assertEquals(42L, pd.lastDeathTick);
    }

    @Test
    void applyReduce_preservesTickCounterAcrossDeath() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        pd.level = 5;
        pd.tickCounter = 36_000;
        PlayerDifficultyState.applyReduce(pd, 2, 0, 0, 1L);
        assertEquals(3, pd.level);
        assertEquals(36_000, pd.tickCounter);
    }

    @Test
    void reduceLevel_marksDirtyWhenReliefApplied() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 10;
        assertTrue(state.reduceLevel(uuid, 2, 6000, 0, 0L));
        assertTrue(state.isDirty());
        assertEquals(8, state.getLevel(uuid));
    }

    @Test
    void reduceLevel_returnsFalseAndDoesNotDirtyWhenOnCooldown() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData pd = state.getPlayerData(uuid);
        pd.level = 10;
        pd.lastDeathTick = 1_000L;
        assertFalse(state.reduceLevel(uuid, 2, 6000, 0, 1_500L));
        assertFalse(state.isDirty());
        assertEquals(10, state.getLevel(uuid));
    }

    @Test
    void reducePlayerLevel_dropsByAmountAndMarksDirty() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 12;
        int after = state.reducePlayerLevel(uuid, 5, 0);
        assertEquals(7, after);
        assertEquals(7, state.getLevel(uuid));
        assertTrue(state.isDirty());
    }

    @Test
    void reducePlayerLevel_floorsAtMinimum() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 3;
        int after = state.reducePlayerLevel(uuid, 10, 2);
        assertEquals(2, after);
        assertEquals(2, state.getLevel(uuid));
    }

    @Test
    void reducePlayerLevel_noopAtFloorDoesNotDirty() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 0;
        int after = state.reducePlayerLevel(uuid, 5, 0);
        assertEquals(0, after);
        assertFalse(state.isDirty());
    }

    @Test
    void reducePlayerLevel_ignoresDeathReliefCooldownState() {
        // Shard use should be orthogonal to death relief — it does not touch
        // lastDeathTick, and it does not honor the death-relief cooldown.
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData pd = state.getPlayerData(uuid);
        pd.level = 10;
        pd.lastDeathTick = 500L;
        int after = state.reducePlayerLevel(uuid, 3, 0);
        assertEquals(7, after);
        assertEquals(500L, pd.lastDeathTick);
    }

    @Test
    void reducePlayerLevel_negativeAmountClampedToZero() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 10;
        int after = state.reducePlayerLevel(uuid, -5, 0);
        assertEquals(10, after);
        assertFalse(state.isDirty());
    }

    // ---- setLevel (for /tribulation set) ----

    @Test
    void setLevel_storesRequestedLevelAndClearsTickCounter() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData pd = state.getPlayerData(uuid);
        pd.tickCounter = 50_000;
        int after = state.setLevel(uuid, 42, 250);
        assertEquals(42, after);
        assertEquals(42, state.getLevel(uuid));
        assertEquals(0, pd.tickCounter);
        assertTrue(state.isDirty());
    }

    @Test
    void setLevel_clampsToMaxLevel() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        int after = state.setLevel(uuid, 9999, 250);
        assertEquals(250, after);
        assertEquals(250, state.getLevel(uuid));
    }

    @Test
    void setLevel_clampsNegativeToZero() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 100;
        int after = state.setLevel(uuid, -10, 250);
        assertEquals(0, after);
        assertEquals(0, state.getLevel(uuid));
    }

    @Test
    void setLevel_noopWhenAlreadyAtTargetDoesNotDirty() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData pd = state.getPlayerData(uuid);
        pd.level = 42;
        pd.tickCounter = 0;
        // Touch getPlayerData so it exists; isDirty() should still be false.
        assertFalse(state.isDirty());
        int after = state.setLevel(uuid, 42, 250);
        assertEquals(42, after);
        assertFalse(state.isDirty(), "no change → no dirty flag");
    }

    @Test
    void setLevel_leavesDeathTickUntouched() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData pd = state.getPlayerData(uuid);
        pd.level = 10;
        pd.lastDeathTick = 4_200L;
        state.setLevel(uuid, 100, 250);
        assertEquals(4_200L, pd.lastDeathTick);
    }

    // ---- reset (for /tribulation reset) ----

    @Test
    void reset_zeroesLevelAndTickCounter() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData pd = state.getPlayerData(uuid);
        pd.level = 100;
        pd.tickCounter = 50_000;
        state.reset(uuid);
        assertEquals(0, pd.level);
        assertEquals(0, pd.tickCounter);
        assertTrue(state.isDirty());
    }

    @Test
    void reset_noopWhenAlreadyZero() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid); // ensure entry exists
        assertFalse(state.isDirty());
        state.reset(uuid);
        assertFalse(state.isDirty());
    }

    @Test
    void reset_leavesDeathTickUntouched() {
        // Resetting the level must NOT clear the death-relief cooldown — otherwise
        // an admin reset would open a fresh exploit window immediately.
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        PlayerDifficultyState.PlayerData pd = state.getPlayerData(uuid);
        pd.level = 100;
        pd.lastDeathTick = 12_345L;
        state.reset(uuid);
        assertEquals(12_345L, pd.lastDeathTick);
    }

    @Test
    void getTickCounter_returnsTick() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).tickCounter = 1234;
        assertEquals(1234, state.getTickCounter(uuid));
    }

    // ---- heartsLost (Hardcore Hearts persistence) ----

    @Test
    void newPlayerData_hasZeroHeartsLost() {
        PlayerDifficultyState.PlayerData pd = new PlayerDifficultyState.PlayerData();
        assertEquals(0, pd.heartsLost);
    }

    @Test
    void getHeartsLost_defaultsToZero() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        assertEquals(0, state.getHeartsLost(uuid));
    }

    @Test
    void addHeartsLost_incrementsAndMarksDirty() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        int result = state.addHeartsLost(uuid, 2, 2);
        assertEquals(2, result);
        assertEquals(2, state.getHeartsLost(uuid));
        assertTrue(state.isDirty());
    }

    @Test
    void addHeartsLost_floorsAtMinimumHearts() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        // minimumHearts=2 means maxPenalty = 20 - 2 = 18
        int result = state.addHeartsLost(uuid, 50, 2);
        assertEquals(18, result);
        assertEquals(18, state.getHeartsLost(uuid));
    }

    @Test
    void addHeartsLost_accumulatesAcrossCalls() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.addHeartsLost(uuid, 4, 2);
        state.addHeartsLost(uuid, 4, 2);
        assertEquals(8, state.getHeartsLost(uuid));
    }

    @Test
    void addHeartsLost_zeroAmount_isNoop() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).heartsLost = 4;
        int result = state.addHeartsLost(uuid, 0, 2);
        assertEquals(4, result);
        assertFalse(state.isDirty());
    }

    @Test
    void addHeartsLost_negativeAmount_isNoop() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).heartsLost = 4;
        int result = state.addHeartsLost(uuid, -3, 2);
        assertEquals(4, result);
        assertFalse(state.isDirty());
    }

    @Test
    void restoreHearts_reducesHeartsLostAndMarksDirty() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).heartsLost = 10;
        int result = state.restoreHearts(uuid, 4);
        assertEquals(6, result);
        assertEquals(6, state.getHeartsLost(uuid));
        assertTrue(state.isDirty());
    }

    @Test
    void restoreHearts_floorsAtZero() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).heartsLost = 3;
        int result = state.restoreHearts(uuid, 10);
        assertEquals(0, result);
        assertEquals(0, state.getHeartsLost(uuid));
    }

    @Test
    void restoreHearts_zeroAmount_isNoop() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).heartsLost = 5;
        int result = state.restoreHearts(uuid, 0);
        assertEquals(5, result);
        assertFalse(state.isDirty());
    }

    @Test
    void restoreHearts_whenAlreadyZero_isNoop() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        int result = state.restoreHearts(uuid, 5);
        assertEquals(0, result);
        assertFalse(state.isDirty());
    }

    @Test
    void resetHearts_clearsPenaltyAndMarksDirty() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).heartsLost = 8;
        int prev = state.resetHearts(uuid);
        assertEquals(8, prev);
        assertEquals(0, state.getHeartsLost(uuid));
        assertTrue(state.isDirty());
    }

    @Test
    void resetHearts_noopWhenAlreadyZero() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid); // ensure entry
        int prev = state.resetHearts(uuid);
        assertEquals(0, prev);
        assertFalse(state.isDirty());
    }

    @Test
    void heartsLost_independentOfLevelAndTickCounter() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 100;
        state.getPlayerData(uuid).tickCounter = 50_000;
        state.addHeartsLost(uuid, 6, 2);
        assertEquals(100, state.getLevel(uuid));
        assertEquals(50_000, state.getTickCounter(uuid));
        assertEquals(6, state.getHeartsLost(uuid));
    }

    // ---- NBT serialization round-trip ----

    @Test
    void nbt_roundTrip_preservesHeartsLost() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        UUID uuid = UUID.randomUUID();
        state.getPlayerData(uuid).level = 42;
        state.getPlayerData(uuid).heartsLost = 8;
        state.getPlayerData(uuid).tickCounter = 1000;

        CompoundTag tag = state.save(new CompoundTag(), null);
        PlayerDifficultyState loaded = PlayerDifficultyState.load(tag, null);

        assertEquals(42, loaded.getLevel(uuid));
        assertEquals(8, loaded.getHeartsLost(uuid));
        assertEquals(1000, loaded.getTickCounter(uuid));
    }

    @Test
    void save_serializesPlayersSortedByUuid() {
        PlayerDifficultyState state = new PlayerDifficultyState();
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID uuid = UUID.randomUUID();
            uuids.add(uuid);
            state.getPlayerData(uuid).level = i;
        }
        // Serialization sorts by UUID string for a deterministic, diff-friendly
        // on-disk order independent of the (unordered) backing map.
        List<UUID> expectedOrder = new ArrayList<>(uuids);
        expectedOrder.sort(java.util.Comparator.comparing(UUID::toString));

        CompoundTag tag = state.save(new CompoundTag(), null);
        ListTag list = tag.getList("Players", 10); // 10 is TAG_COMPOUND

        assertEquals(expectedOrder.size(), list.size());
        for (int i = 0; i < expectedOrder.size(); i++) {
            assertEquals(expectedOrder.get(i), list.getCompound(i).getUUID("UUID"));
        }
    }

    @Test
    void nbt_backwardCompat_missingHeartsLostDefaultsToZero() {
        // Simulate an old save file that doesn't have the HeartsLost key
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        CompoundTag playerTag = new CompoundTag();
        UUID uuid = UUID.randomUUID();
        playerTag.putUUID("UUID", uuid);
        playerTag.putInt("Level", 10);
        playerTag.putInt("Tick", 500);
        playerTag.putLong("LastDeathTick", 12345L);
        // No HeartsLost key
        list.add(playerTag);
        tag.put("Players", list);

        PlayerDifficultyState loaded = PlayerDifficultyState.load(tag, null);

        assertEquals(10, loaded.getLevel(uuid));
        assertEquals(500, loaded.getTickCounter(uuid));
        assertEquals(0, loaded.getHeartsLost(uuid));
    }
}
