// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.BloodMoonState;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.BloodMoonHandler;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * End-to-end coverage for Blood Moon nights: the amplified moon axis on a real
 * spawn, the sleep block through the actual {@link EntitySleepEvents} hook the
 * handler registers, and the nightfall/dawn transitions driven through
 * {@link BloodMoonHandler#tick}. Pure trigger/cap math is covered by
 * {@code BloodMoonHandlerTest}; SavedData round-trips by {@code BloodMoonStateTest}.
 */
public class BloodMoonGameTest implements FabricGameTest {

    /**
     * Full moon (day 0, midnight) with maxBonus 0.2 gives +20% health; with an
     * active Blood Moon at ×3 the zombie lands at 20 * (1 + 0.6) = 32 HP. The
     * other axes and variants are disabled and the detection range is shrunk
     * to this test's level-0 player, mirroring the full-moon test in
     * {@code MobScalingGameTest} — the only delta is the multiplier.
     */
    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void bloodMoon_amplifiesMoonBonusOnSpawn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        BloodMoonState state = BloodMoonState.getOrCreate(server);

        boolean savedMoonEnabled = cfg.moonPhaseScaling.enabled;
        double savedMoonBonus = cfg.moonPhaseScaling.maxBonus;
        boolean savedDist = cfg.distanceScaling.enabled;
        boolean savedHeight = cfg.heightScaling.enabled;
        boolean savedSpecial = cfg.specialZombies.enabled;
        double savedRange = cfg.general.mobDetectionRange;
        boolean savedBmEnabled = cfg.bloodMoon.enabled;
        double savedBmMult = cfg.bloodMoon.moonBonusMultiplier;
        long savedTime = helper.getLevel().getDayTime();

        // setDayTime alone only moves the clock — isDay() reads skyDarken,
        // which needs the explicit updateSkyBrightness() in a gametest.
        helper.getLevel().setDayTime(18000);
        helper.getLevel().updateSkyBrightness();

        cfg.moonPhaseScaling.enabled = true;
        cfg.moonPhaseScaling.maxBonus = 0.2;
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.specialZombies.enabled = false;
        cfg.general.mobDetectionRange = 2.0;
        cfg.bloodMoon.enabled = true;
        cfg.bloodMoon.moonBonusMultiplier = 3.0;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
        PlayerDifficultyState levels = PlayerDifficultyState.getOrCreate(server);
        levels.setLevel(player.getUUID(), 0, cfg.general.maxLevel);

        BloodMoonHandler.start(server, state, cfg);
        try {
            Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
            helper.assertValueEqual(zombie.getMaxHealth(), 32.0f,
                    "blood moon amplified moon bonus (0.2 × 3)");
        } finally {
            BloodMoonHandler.end(server, state, cfg);
            cfg.moonPhaseScaling.enabled = savedMoonEnabled;
            cfg.moonPhaseScaling.maxBonus = savedMoonBonus;
            cfg.distanceScaling.enabled = savedDist;
            cfg.heightScaling.enabled = savedHeight;
            cfg.specialZombies.enabled = savedSpecial;
            cfg.general.mobDetectionRange = savedRange;
            cfg.bloodMoon.enabled = savedBmEnabled;
            cfg.bloodMoon.moonBonusMultiplier = savedBmMult;
            helper.getLevel().setDayTime(savedTime);
            helper.getLevel().updateSkyBrightness();
            player.discard();
        }
        helper.succeed();
    }

    /**
     * Drives the real {@link EntitySleepEvents#ALLOW_SLEEPING} invoker — the
     * exact hook vanilla fires from {@code ServerPlayer.startSleepInBed} — so
     * the handler's registered listener is exercised, not a reimplementation.
     */
    @GameTest(template = "tribulation:empty_3x3")
    public void bloodMoon_blocksSleepWhileActive(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        BloodMoonState state = BloodMoonState.getOrCreate(server);
        boolean savedBlockSleep = cfg.bloodMoon.blockSleep;
        cfg.bloodMoon.blockSleep = true;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos bedPos = helper.absolutePos(new BlockPos(1, 2, 1));

        BloodMoonHandler.start(server, state, cfg);
        try {
            Player.BedSleepingProblem during =
                    EntitySleepEvents.ALLOW_SLEEPING.invoker().allowSleep(player, bedPos);
            helper.assertTrue(during == Player.BedSleepingProblem.OTHER_PROBLEM,
                    "sleeping must be blocked during a blood moon, got " + during);

            BloodMoonHandler.end(server, state, cfg);
            Player.BedSleepingProblem after =
                    EntitySleepEvents.ALLOW_SLEEPING.invoker().allowSleep(player, bedPos);
            helper.assertTrue(after == null,
                    "sleeping must be allowed again after the event, got " + after);
        } finally {
            BloodMoonHandler.end(server, state, cfg);
            cfg.bloodMoon.blockSleep = savedBlockSleep;
            player.discard();
        }
        helper.succeed();
    }

    /**
     * A guaranteed roll (chance 1.0) on a full-moon night starts the event via
     * the scheduler pass, and the same pass ends it once day breaks — the
     * whole nightfall→dawn lifecycle without waiting out a real night.
     */
    @GameTest(template = "tribulation:empty_3x3")
    public void bloodMoon_startsAtNightfallAndEndsAtDawn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TribulationConfig cfg = Tribulation.getConfig();
        BloodMoonState state = BloodMoonState.getOrCreate(server);

        boolean savedEnabled = cfg.bloodMoon.enabled;
        double savedChance = cfg.bloodMoon.chance;
        long savedTime = helper.getLevel().getDayTime();
        long savedRolledDay = state.getLastRolledDay();

        cfg.bloodMoon.enabled = true;
        cfg.bloodMoon.chance = 1.0;

        try {
            // Day 0 midnight — full moon night, roll not yet spent.
            helper.getLevel().setDayTime(18000);
            helper.getLevel().updateSkyBrightness();
            state.setActive(false);
            state.markRolled(BloodMoonState.NEVER_ROLLED);

            BloodMoonHandler.tick(server, cfg);
            helper.assertTrue(state.isActive(), "chance=1.0 full-moon nightfall must start a blood moon");
            helper.assertTrue(state.getLastRolledDay() == 0, "the night's roll must be recorded");

            // Dawn of the next day ends it.
            helper.getLevel().setDayTime(24000 + 1000);
            helper.getLevel().updateSkyBrightness();
            BloodMoonHandler.tick(server, cfg);
            helper.assertFalse(state.isActive(), "the event must end at dawn");
        } finally {
            BloodMoonHandler.end(server, state, cfg);
            state.markRolled(savedRolledDay);
            cfg.bloodMoon.enabled = savedEnabled;
            cfg.bloodMoon.chance = savedChance;
            helper.getLevel().setDayTime(savedTime);
            helper.getLevel().updateSkyBrightness();
        }
        helper.succeed();
    }
}
