// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.LevelDecayHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end coverage of {@link LevelDecayHandler} through the real login
 * path (with an explicit "now" so tests don't sleep for a week). The pure
 * grace/rate arithmetic is unit-tested on {@code computeDecayLevels}; these
 * tests exercise the handler shell: the config gate, the anchor consumption
 * and re-stamp, the floor, and the {@link TribulationLevelCallback} fire.
 */
public class LevelDecayGameTest implements FabricGameTest {

    private static final long DAY_MS = LevelDecayHandler.DAY_MS;

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void levelDecay_reducesLevelAfterGraceAndFiresCallback(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.levelDecay.enabled;
        double savedGrace = cfg.levelDecay.graceDays;
        double savedRate = cfg.levelDecay.levelsPerDay;
        int savedFloor = cfg.levelDecay.floor;
        cfg.levelDecay.enabled = true;
        cfg.levelDecay.graceDays = 7.0;
        cfg.levelDecay.levelsPerDay = 2.0;
        cfg.levelDecay.floor = 0;

        AtomicInteger callbackOld = new AtomicInteger(-1);
        AtomicInteger callbackNew = new AtomicInteger(-1);
        // Array-backed events cannot unregister; scope this listener to our
        // mock player so it never reacts to another test's player.
        TribulationLevelCallback.EVENT.register((changed, oldLevel, newLevel) -> {
            if (changed == player) {
                callbackOld.set(oldLevel);
                callbackNew.set(newLevel);
            }
        });

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, cfg.general.maxLevel);

            // Away 10 days with a 7-day grace at 2 levels/day → -6 levels.
            long now = 1_700_000_000_000L;
            state.setLastSeen(player.getUUID(), now - 10 * DAY_MS);
            LevelDecayHandler.applyOfflineDecay(player, now);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 14, "level after offline decay");
            helper.assertValueEqual(callbackOld.get(), 20, "callback old level");
            helper.assertValueEqual(callbackNew.get(), 14, "callback new level");
            helper.assertValueEqual(state.getLastSeen(player.getUUID()), now,
                    "anchor re-stamped to login time");
            player.discard();
            helper.succeed();
        } finally {
            cfg.levelDecay.enabled = savedEnabled;
            cfg.levelDecay.graceDays = savedGrace;
            cfg.levelDecay.levelsPerDay = savedRate;
            cfg.levelDecay.floor = savedFloor;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void levelDecay_withinGraceLeavesLevelUntouched(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.levelDecay.enabled;
        double savedGrace = cfg.levelDecay.graceDays;
        double savedRate = cfg.levelDecay.levelsPerDay;
        cfg.levelDecay.enabled = true;
        cfg.levelDecay.graceDays = 7.0;
        cfg.levelDecay.levelsPerDay = 2.0;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, cfg.general.maxLevel);

            long now = 1_700_000_000_000L;
            state.setLastSeen(player.getUUID(), now - 3 * DAY_MS);
            LevelDecayHandler.applyOfflineDecay(player, now);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 20,
                    "level unchanged inside the grace window");
            player.discard();
            helper.succeed();
        } finally {
            cfg.levelDecay.enabled = savedEnabled;
            cfg.levelDecay.graceDays = savedGrace;
            cfg.levelDecay.levelsPerDay = savedRate;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void levelDecay_flooredAtConfiguredMinimum(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.levelDecay.enabled;
        double savedGrace = cfg.levelDecay.graceDays;
        double savedRate = cfg.levelDecay.levelsPerDay;
        int savedFloor = cfg.levelDecay.floor;
        cfg.levelDecay.enabled = true;
        cfg.levelDecay.graceDays = 0.0;
        cfg.levelDecay.levelsPerDay = 2.0;
        cfg.levelDecay.floor = 15;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, cfg.general.maxLevel);

            // 100 days away would shed 200 levels; the floor stops it at 15.
            long now = 1_700_000_000_000L;
            state.setLastSeen(player.getUUID(), now - 100 * DAY_MS);
            LevelDecayHandler.applyOfflineDecay(player, now);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 15,
                    "level floored at levelDecay.floor");
            player.discard();
            helper.succeed();
        } finally {
            cfg.levelDecay.enabled = savedEnabled;
            cfg.levelDecay.graceDays = savedGrace;
            cfg.levelDecay.levelsPerDay = savedRate;
            cfg.levelDecay.floor = savedFloor;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void levelDecay_belowFloorIsNeverRaisedToIt(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.levelDecay.enabled;
        double savedGrace = cfg.levelDecay.graceDays;
        double savedRate = cfg.levelDecay.levelsPerDay;
        int savedFloor = cfg.levelDecay.floor;
        cfg.levelDecay.enabled = true;
        cfg.levelDecay.graceDays = 0.0;
        cfg.levelDecay.levelsPerDay = 2.0;
        cfg.levelDecay.floor = 15;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            // Player already below the floor (e.g. lowered via /tribulation set):
            // decay must be a no-op, never a lift up to the floor.
            state.setLevel(player.getUUID(), 5, cfg.general.maxLevel);

            long now = 1_700_000_000_000L;
            state.setLastSeen(player.getUUID(), now - 10 * DAY_MS);
            LevelDecayHandler.applyOfflineDecay(player, now);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 5,
                    "level below the floor stays untouched");
            player.discard();
            helper.succeed();
        } finally {
            cfg.levelDecay.enabled = savedEnabled;
            cfg.levelDecay.graceDays = savedGrace;
            cfg.levelDecay.levelsPerDay = savedRate;
            cfg.levelDecay.floor = savedFloor;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void levelDecay_disabledLeavesLevelAndAnchorUntouched(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.levelDecay.enabled;
        cfg.levelDecay.enabled = false;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, cfg.general.maxLevel);

            long now = 1_700_000_000_000L;
            LevelDecayHandler.applyOfflineDecay(player, now);
            LevelDecayHandler.stampLastSeen(player, now);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 20,
                    "level unchanged when level decay is disabled");
            helper.assertValueEqual(state.getLastSeen(player.getUUID()),
                    PlayerDifficultyState.NEVER_SEEN,
                    "no anchor stamped when level decay is disabled");
            player.discard();
            helper.succeed();
        } finally {
            cfg.levelDecay.enabled = savedEnabled;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void levelDecay_firstLoginWithoutAnchorOnlyStamps(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.levelDecay.enabled;
        double savedGrace = cfg.levelDecay.graceDays;
        cfg.levelDecay.enabled = true;
        cfg.levelDecay.graceDays = 0.0;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, cfg.general.maxLevel);

            // No anchor (pre-feature save / fresh player): the first login must
            // never decay, only begin the stamp cycle.
            long now = 1_700_000_000_000L;
            LevelDecayHandler.applyOfflineDecay(player, now);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 20,
                    "level unchanged on first login without an anchor");
            helper.assertValueEqual(state.getLastSeen(player.getUUID()), now,
                    "anchor stamped so the next absence is measured");
            player.discard();
            helper.succeed();
        } finally {
            cfg.levelDecay.enabled = savedEnabled;
            cfg.levelDecay.graceDays = savedGrace;
        }
    }
}
