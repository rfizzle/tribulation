// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.concord.gametest.MockPlayers;
import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.DeathReliefHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;

import java.io.File;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end coverage of {@link DeathReliefHandler} through the config-gated
 * {@code maybeApplyPenalty} entry point. The pure floor/cooldown arithmetic is
 * unit-tested on {@code PlayerDifficultyState.applyReduce}; these tests exercise
 * the handler shell: the config gate, the level write, the cooldown suppression,
 * the minimum-level floor, and the {@link TribulationLevelCallback} fire.
 */
public class DeathReliefGameTest implements FabricGameTest {

    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_reducesLevelAndFiresCallback(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.deathRelief.enabled;
        int savedAmount = cfg.deathRelief.amount;
        int savedCooldown = cfg.deathRelief.cooldownTicks;
        int savedMin = cfg.deathRelief.minimumLevel;
        cfg.deathRelief.enabled = true;
        cfg.deathRelief.amount = 2;
        cfg.deathRelief.cooldownTicks = 6000;
        cfg.deathRelief.minimumLevel = 0;

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

            DeathReliefHandler.maybeApplyPenalty(player);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 18, "level after death relief");
            helper.assertValueEqual(callbackOld.get(), 20, "callback old level");
            helper.assertValueEqual(callbackNew.get(), 18, "callback new level");
            helper.succeed();
        } finally {
            cfg.deathRelief.enabled = savedEnabled;
            cfg.deathRelief.amount = savedAmount;
            cfg.deathRelief.cooldownTicks = savedCooldown;
            cfg.deathRelief.minimumLevel = savedMin;
        }
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_secondDeathWithinCooldownIsNoOp(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.deathRelief.enabled;
        int savedAmount = cfg.deathRelief.amount;
        int savedCooldown = cfg.deathRelief.cooldownTicks;
        int savedMin = cfg.deathRelief.minimumLevel;
        cfg.deathRelief.enabled = true;
        cfg.deathRelief.amount = 2;
        cfg.deathRelief.cooldownTicks = 6000;
        cfg.deathRelief.minimumLevel = 0;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, cfg.general.maxLevel);

            // Both deaths land on the same tick, so the second is inside the
            // 6000-tick cooldown and must not reduce the level a second time.
            DeathReliefHandler.maybeApplyPenalty(player);
            DeathReliefHandler.maybeApplyPenalty(player);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 18,
                    "level after two rapid deaths (only the first applies)");
            helper.succeed();
        } finally {
            cfg.deathRelief.enabled = savedEnabled;
            cfg.deathRelief.amount = savedAmount;
            cfg.deathRelief.cooldownTicks = savedCooldown;
            cfg.deathRelief.minimumLevel = savedMin;
        }
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_flooredAtMinimumLevel(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.deathRelief.enabled;
        int savedAmount = cfg.deathRelief.amount;
        int savedCooldown = cfg.deathRelief.cooldownTicks;
        int savedMin = cfg.deathRelief.minimumLevel;
        cfg.deathRelief.enabled = true;
        cfg.deathRelief.amount = 2;
        cfg.deathRelief.cooldownTicks = 6000;
        cfg.deathRelief.minimumLevel = 5;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 6, cfg.general.maxLevel);

            DeathReliefHandler.maybeApplyPenalty(player);

            // 6 - 2 = 4, but the minimum-level floor clamps it back up to 5.
            helper.assertValueEqual(state.getLevel(player.getUUID()), 5,
                    "level floored at deathRelief.minimumLevel");
            helper.succeed();
        } finally {
            cfg.deathRelief.enabled = savedEnabled;
            cfg.deathRelief.amount = savedAmount;
            cfg.deathRelief.cooldownTicks = savedCooldown;
            cfg.deathRelief.minimumLevel = savedMin;
        }
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_disabledLeavesLevelUntouched(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.deathRelief.enabled;
        cfg.deathRelief.enabled = false;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, cfg.general.maxLevel);

            DeathReliefHandler.maybeApplyPenalty(player);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 20,
                    "level unchanged when death relief is disabled");
            helper.succeed();
        } finally {
            cfg.deathRelief.enabled = savedEnabled;
        }
    }

    private static final long GAME_TIME = 1_000_000L;

    /**
     * Runs {@code body} with the overworld game time pinned to {@link #GAME_TIME}
     * (far above the session tick count), restoring the original afterwards.
     * The persisted cooldown must be keyed to this clock, not the per-session
     * {@code getTickCount}.
     */
    private static void withGameTime(MinecraftServer server, Runnable body) {
        ServerLevelData data = (ServerLevelData) server.overworld().getLevelData();
        long original = data.getGameTime();
        data.setGameTime(GAME_TIME);
        TribulationConfig cfg = Tribulation.getConfig();
        boolean savedEnabled = cfg.deathRelief.enabled;
        int savedAmount = cfg.deathRelief.amount;
        int savedCooldown = cfg.deathRelief.cooldownTicks;
        int savedMin = cfg.deathRelief.minimumLevel;
        cfg.deathRelief.enabled = true;
        cfg.deathRelief.amount = 2;
        cfg.deathRelief.cooldownTicks = 6000;
        cfg.deathRelief.minimumLevel = 0;
        try {
            body.run();
        } finally {
            data.setGameTime(original);
            cfg.deathRelief.enabled = savedEnabled;
            cfg.deathRelief.amount = savedAmount;
            cfg.deathRelief.cooldownTicks = savedCooldown;
            cfg.deathRelief.minimumLevel = savedMin;
        }
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_appliesAfterRestartWhenCooldownElapsedInGameTime(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        withGameTime(server, () -> {
            int max = Tribulation.getConfig().general.maxLevel;
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, max);
            // Previous death 7000 game ticks ago (cooldown 6000 has elapsed).
            state.reduceLevel(player.getUUID(), 0, 6000, 0, GAME_TIME - 7000);

            // Cold read of the saved stamp, as a restarted server would see it.
            server.overworld().getDataStorage().save();
            File dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data").toFile();
            PlayerDifficultyState reloaded = new DimensionDataStorage(
                    dataDir, server.getFixerUpper(), server.registryAccess())
                    .computeIfAbsent(PlayerDifficultyState.FACTORY, PlayerDifficultyState.STORAGE_KEY);
            helper.assertValueEqual(reloaded.getLastDeathTick(player.getUUID()), GAME_TIME - 7000,
                    "death stamp survives a storage reload");

            // The session tick count (restarted at 0) must not be the clock.
            DeathReliefHandler.maybeApplyPenalty(player);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 18,
                    "relief applies once the cooldown elapsed in persistent game time");
            helper.assertValueEqual(state.getLastDeathTick(player.getUUID()), GAME_TIME,
                    "death is stamped in overworld game time");
        });
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_secondDeathInsideWindowBlockedAtGameTime(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        withGameTime(server, () -> {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, Tribulation.getConfig().general.maxLevel);
            // Previous death 100 game ticks ago: still inside the window.
            state.reduceLevel(player.getUUID(), 0, 6000, 0, GAME_TIME - 100);

            DeathReliefHandler.maybeApplyPenalty(player);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 20,
                    "death inside the cooldown window is not relieved");
            helper.assertValueEqual(state.getLastDeathTick(player.getUUID()), GAME_TIME - 100,
                    "blocked death does not move the anchor");
        });
        helper.succeed();
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_legacyStampAboveGameTimeDoesNotBlock(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        withGameTime(server, () -> {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, Tribulation.getConfig().general.maxLevel);
            // A 1.1.0 stamp in session-tick units that exceeds the current game time.
            state.reduceLevel(player.getUUID(), 0, 6000, 0, GAME_TIME + 500_000);

            DeathReliefHandler.maybeApplyPenalty(player);

            helper.assertValueEqual(state.getLevel(player.getUUID()), 18,
                    "legacy future-dated stamp does not block relief");
            helper.assertValueEqual(state.getLastDeathTick(player.getUUID()), GAME_TIME,
                    "stamp is rewritten in game time");
        });
        helper.succeed();
    }
}
