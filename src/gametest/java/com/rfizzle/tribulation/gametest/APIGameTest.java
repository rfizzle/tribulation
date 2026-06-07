package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.command.TribulationCommand;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class APIGameTest implements FabricGameTest {

    @GameTest(template = "tribulation:empty_3x3")
    public void api_levelChangeEventFiresOnSet(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        TribulationConfig cfg = Tribulation.getConfig();

        AtomicInteger eventFiredCount = new AtomicInteger(0);
        AtomicInteger lastOldLevel = new AtomicInteger(-1);
        AtomicInteger lastNewLevel = new AtomicInteger(-1);
        AtomicBoolean active = new AtomicBoolean(true);

        TribulationLevelCallback listener = (p, oldLvl, newLvl) -> {
            if (!active.get()) return;
            if (p.getUUID().equals(player.getUUID())) {
                eventFiredCount.incrementAndGet();
                lastOldLevel.set(oldLvl);
                lastNewLevel.set(newLvl);
            }
        };

        TribulationLevelCallback.EVENT.register(listener);

        try {
            state.reset(player.getUUID());
            TribulationCommand.applySetLevel(player, 5, state, cfg);

            helper.succeedWhen(() -> {
                helper.assertValueEqual(eventFiredCount.get(), 1, "Event should fire once");
                helper.assertValueEqual(lastOldLevel.get(), 0, "Old level should be 0");
                helper.assertValueEqual(lastNewLevel.get(), 5, "New level should be 5");
            });
        } finally {
            active.set(false);
        }
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void api_levelChangeEventFiresOnIncrementTick(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        TribulationConfig cfg = Tribulation.getConfig();

        AtomicInteger eventFiredCount = new AtomicInteger(0);
        AtomicBoolean active = new AtomicBoolean(true);

        TribulationLevelCallback listener = (p, oldLvl, newLvl) -> {
            if (!active.get()) return;
            if (p.getUUID().equals(player.getUUID())) {
                eventFiredCount.incrementAndGet();
            }
        };
        TribulationLevelCallback.EVENT.register(listener);

        try {
            state.reset(player.getUUID());
            // Pass levelUpTicks as ticksToAdd to cross a level boundary in one call,
            // exercising the same production path the server tick handler uses.
            Tribulation.applyLevelTick(player, state, cfg, cfg.general.levelUpTicks);

            helper.succeedWhen(() -> {
                helper.assertValueEqual(eventFiredCount.get(), 1, "Event should fire on level up");
            });
        } finally {
            active.set(false);
        }
    }
}
