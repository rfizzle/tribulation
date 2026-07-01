// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end coverage of {@link DeathReliefHandler} through the real
 * {@code onAfterDeath} event path. The pure floor/cooldown arithmetic is
 * unit-tested on {@code PlayerDifficultyState.applyReduce}; these tests exercise
 * the handler shell: the config gate, the level write, the cooldown suppression,
 * the minimum-level floor, and the {@link TribulationLevelCallback} fire.
 */
public class DeathReliefGameTest implements FabricGameTest {

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_reducesLevelAndFiresCallback(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
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

            DeathReliefHandler.onAfterDeath(player, player.damageSources().generic());

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

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_secondDeathWithinCooldownIsNoOp(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
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
            DeathReliefHandler.onAfterDeath(player, player.damageSources().generic());
            DeathReliefHandler.onAfterDeath(player, player.damageSources().generic());

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

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_flooredAtMinimumLevel(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
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

            DeathReliefHandler.onAfterDeath(player, player.damageSources().generic());

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

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void deathRelief_disabledLeavesLevelUntouched(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedEnabled = cfg.deathRelief.enabled;
        cfg.deathRelief.enabled = false;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            state.setLevel(player.getUUID(), 20, cfg.general.maxLevel);

            DeathReliefHandler.onAfterDeath(player, player.damageSources().generic());

            helper.assertValueEqual(state.getLevel(player.getUUID()), 20,
                    "level unchanged when death relief is disabled");
            helper.succeed();
        } finally {
            cfg.deathRelief.enabled = savedEnabled;
        }
    }
}
