package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.MobScalingSummary;
import com.rfizzle.tribulation.api.TribulationAPI;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.command.TribulationCommand;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.scaling.BossScalingEngine;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;

import java.util.Optional;
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
            // Pass levelUpTicks as ticksToAdd to cross a level boundary in one call,
            // exercising the same production path the server tick handler uses.
            Tribulation.applyLevelTick(player, state, cfg, cfg.general.levelUpTicks);

            helper.succeedWhen(() -> {
                helper.assertValueEqual(eventFiredCount.get(), 1, "Event should fire on level up");
                helper.assertValueEqual(lastOldLevel.get(), 0, "Old level should be 0");
                helper.assertValueEqual(lastNewLevel.get(), 1, "New level should be 1");
            });
        } finally {
            active.set(false);
        }
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void api_getTierThresholds_matchesConfiguredTiers(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();
        int[] thresholds = TribulationAPI.getTierThresholds();

        helper.assertValueEqual(thresholds.length, 5, "threshold count");
        helper.assertValueEqual(thresholds[0], cfg.tiers.tier1, "tier1 threshold");
        helper.assertValueEqual(thresholds[1], cfg.tiers.tier2, "tier2 threshold");
        helper.assertValueEqual(thresholds[2], cfg.tiers.tier3, "tier3 threshold");
        helper.assertValueEqual(thresholds[3], cfg.tiers.tier4, "tier4 threshold");
        helper.assertValueEqual(thresholds[4], cfg.tiers.tier5, "tier5 threshold");
        helper.succeed();
    }

    /**
     * A zombie scaled by the normal per-mob formula at player level 100 must
     * report a summary with the frozen tier (level 100 = tier 2), the time-axis
     * health/damage factors from DESIGN.md's zombie rates (0.01 and 0.015 per
     * level), and no boss flag. Distance/height/variants are disabled for the
     * spawn so the assertion reflects the time axis alone, same recipe as
     * {@link MobScalingGameTest}.
     */
    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void api_mobScalingSummary_describesScaledZombie(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(new BlockPos(0, 2, 1));
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);

        MinecraftServer server = helper.getLevel().getServer();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        TribulationConfig cfg = Tribulation.getConfig();
        state.setLevel(player.getUUID(), 100, cfg.general.maxLevel);

        boolean savedDist = cfg.distanceScaling.enabled;
        boolean savedHeight = cfg.heightScaling.enabled;
        boolean savedSpecial = cfg.specialZombies.enabled;
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.specialZombies.enabled = false;

        Zombie zombie;
        try {
            zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        } finally {
            cfg.distanceScaling.enabled = savedDist;
            cfg.heightScaling.enabled = savedHeight;
            cfg.specialZombies.enabled = savedSpecial;
        }

        helper.succeedWhen(() -> {
            Optional<MobScalingSummary> summary = TribulationAPI.getMobScalingSummary(zombie);
            helper.assertTrue(summary.isPresent(), "scaled zombie must have a scaling summary");
            MobScalingSummary s = summary.orElseThrow();
            helper.assertValueEqual(s.tier(), 2, "tier @ level 100");
            helper.assertFalse(s.bossScaled(), "normal scaling must not be flagged as boss");
            // Zombie time-axis rates: health 0.01/level, damage 0.015/level.
            helper.assertTrue(Math.abs(s.healthFactor() - 1.0) < 1e-9,
                    "healthFactor @ level 100 should be 1.0, got " + s.healthFactor());
            helper.assertTrue(Math.abs(s.damageFactor() - 1.5) < 1e-9,
                    "damageFactor @ level 100 should be 1.5, got " + s.damageFactor());
        });
    }

    /** An entity that never went through scaling reports no summary. */
    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void api_mobScalingSummary_emptyForUnscaledMob(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
        helper.succeedWhen(() -> {
            helper.assertTrue(TribulationAPI.getMobScalingSummary(pig).isEmpty(),
                    "unscaled pig must have no scaling summary");
            helper.assertFalse(TribulationAPI.isBossScaled(pig),
                    "unscaled pig must not be boss-scaled");
        });
    }

    /**
     * isBossScaled keys off the boss-axis modifier IDs, so a normally scaled
     * mob reports false and the same mob reports true once boss-formula
     * modifiers are applied. The zombie spawns with no nearby player and the
     * position axes disabled so it starts with zero modifiers, then boss
     * scaling is applied directly at level 100 (bossTimeFactor 0.3 -> capped
     * at bossMaxFactor, comfortably non-zero).
     */
    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void api_isBossScaled_distinguishesBossFormulaScaling(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedDist = cfg.distanceScaling.enabled;
        boolean savedHeight = cfg.heightScaling.enabled;
        boolean savedSpecial = cfg.specialZombies.enabled;
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.specialZombies.enabled = false;

        Zombie zombie;
        try {
            zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        } finally {
            cfg.distanceScaling.enabled = savedDist;
            cfg.heightScaling.enabled = savedHeight;
            cfg.specialZombies.enabled = savedSpecial;
        }

        helper.assertFalse(TribulationAPI.isBossScaled(zombie),
                "normally processed zombie must not report boss scaling");

        BossScalingEngine.applyModifiers(zombie, helper.getLevel(), 100, cfg);

        helper.succeedWhen(() -> helper.assertTrue(TribulationAPI.isBossScaled(zombie),
                "zombie with boss-axis modifiers must report boss scaling"));
    }
}
