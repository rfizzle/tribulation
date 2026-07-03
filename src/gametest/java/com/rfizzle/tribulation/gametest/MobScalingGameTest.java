// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end coverage of the spawn-tag dedup contract enforced by
 * {@link MobScalingHandler#onEntityLoad}. Pure-JUnit tests verify the toggle
 * key / exclusion helpers; the Tier 2 bridge test verifies the modifier math
 * against a real AttributeMap; this test closes the loop by spawning a real
 * Zombie in a real ServerLevel and asserting the handler tags it.
 *
 * <p>Structure template ({@code tribulation:empty_3x3}) is a 3x3 stone
 * floor under 3 blocks of air — large enough for zombie AABB, small enough
 * that test setup/teardown is cheap.
 */
public class MobScalingGameTest implements FabricGameTest {

    /**
     * Spawning a zombie fires {@code ServerEntityEvents.ENTITY_LOAD} which is
     * what {@link MobScalingHandler#register()} hooks — so the handler is
     * invoked exactly the way it would be for a naturally spawning mob. The
     * PROCESSED_TAG is the canonical "this mob has been through scaling"
     * signal used to prevent re-processing on chunk reload, so asserting it
     * lands proves the whole spawn-hook pipeline wired up correctly.
     */
    @GameTest(template = "tribulation:empty_3x3")
    public void zombieSpawn_getsProcessedTag(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        helper.succeedWhen(() -> helper.assertTrue(
                zombie.getTags().contains(MobScalingHandler.PROCESSED_TAG),
                "zombie missing " + MobScalingHandler.PROCESSED_TAG + " tag after spawn"));
    }

    // DESIGN.md zombie health breakpoints: rate=0.01, cap=2.5 → timeFactor = min(level*0.01, 2.5).
    // Final maxHealth = 20 * (1 + timeFactor). Each tier below locks in one row of that table
    // end-to-end: PlayerDifficultyState → resolveNearestPlayerLevel → ScalingEngine.applyModifiers
    // → AttributeMap.getValue() → setHealth(getMaxHealth()).

    @GameTest(template = "tribulation:empty_3x3")
    public void zombieSpawn_atPlayerLevel50_reaches30Hp(GameTestHelper helper) {
        assertTimeAxisHp(helper, 50, 30.0f);
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void zombieSpawn_atPlayerLevel100_reaches40Hp(GameTestHelper helper) {
        assertTimeAxisHp(helper, 100, 40.0f);
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void zombieSpawn_atPlayerLevel150_reaches50Hp(GameTestHelper helper) {
        assertTimeAxisHp(helper, 150, 50.0f);
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void zombieSpawn_atPlayerLevel200_reaches60Hp(GameTestHelper helper) {
        assertTimeAxisHp(helper, 200, 60.0f);
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void zombieSpawn_atPlayerLevel250_reachesDesignMaxHp(GameTestHelper helper) {
        assertTimeAxisHp(helper, 250, 70.0f);
    }

    // Multiplayer resolution: with two players in range, MAX folds to the highest
    // level (200 → 60 HP) and AVERAGE to the floored mean ((50+200)/2 = 125 → 45 HP).
    // Both exercise the real getEffectiveLevel world.players() loop, not just the
    // foldLevels math helper.
    //
    // These also guard the player-mode filter. getEffectiveLevel uses
    // EntitySelector.NO_SPECTATORS — the same predicate the NEAREST path applies via
    // world.getNearestPlayer(entity, range) — so creative players still count and
    // only spectators are dropped. makeMockServerPlayerInLevel hardcodes
    // isCreative()=true, so both mock players here are creative: if the filter ever
    // regressed to NO_CREATIVE_OR_SPECTATOR, both would be excluded, the zombie would
    // fall back to base 20 HP, and these assertions would fail. (Spectator exclusion
    // can't be gametested here because the same mock hardcodes isSpectator()=false;
    // it relies on the shared, vanilla-tested NO_SPECTATORS predicate.)

    @GameTest(template = "tribulation:empty_3x3")
    public void multiplayerScaling_MAX_reachesHighestLevel(GameTestHelper helper) {
        assertMultiplayerHp(helper, TribulationConfig.ScalingMode.MAX, 60.0f, 50, 200);
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void multiplayerScaling_AVERAGE_reachesMeanLevel(GameTestHelper helper) {
        assertMultiplayerHp(helper, TribulationConfig.ScalingMode.AVERAGE, 45.0f, 50, 200);
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void zombieSpawn_fullMoon_reachesExtraHp(GameTestHelper helper) {
        TribulationConfig cfg = Tribulation.getConfig();
        boolean savedMoonEnabled = cfg.moonPhaseScaling.enabled;
        double savedMoonBonus = cfg.moonPhaseScaling.maxBonus;
        boolean savedDist = cfg.distanceScaling.enabled;
        boolean savedHeight = cfg.heightScaling.enabled;
        boolean savedSpecial = cfg.specialZombies.enabled;
        boolean savedChampions = cfg.champions.enabled;
        double savedRange = cfg.general.mobDetectionRange;

        // Night at day 0 is phase 0 (Full Moon). 18000 is midnight. setDayTime
        // alone only moves the clock — skyDarken (what isDay() reads) is recomputed
        // by the per-tick world loop, which the GameTestServer hasn't run for this
        // synchronous spawn yet, so without the explicit updateSkyBrightness() the
        // world still reports day and the moon axis stays inactive.
        long savedTime = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(18000);
        helper.getLevel().updateSkyBrightness();

        cfg.moonPhaseScaling.enabled = true;
        cfg.moonPhaseScaling.maxBonus = 0.5; // +50% health
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.specialZombies.enabled = false;
        cfg.champions.enabled = false;
        // Seat the player on the zombie's block and shrink the range so the moon
        // bonus is measured against a known level-0 player, not a persistent
        // player leaking in from an adjacent test's structure (~8 blocks away,
        // inside the default 32-block range) whose time factor would otherwise
        // stack on top of the moon bonus.
        cfg.general.mobDetectionRange = 2.0;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        state.setLevel(player.getUUID(), 0, cfg.general.maxLevel);

        try {
            // Level-0 player: 20 HP. Full moon +0.5: 20 * (1 + 0.5) = 30 HP.
            Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
            helper.assertValueEqual(zombie.getMaxHealth(), 30.0f, "full moon health bonus");
        } finally {
            cfg.moonPhaseScaling.enabled = savedMoonEnabled;
            cfg.moonPhaseScaling.maxBonus = savedMoonBonus;
            cfg.distanceScaling.enabled = savedDist;
            cfg.heightScaling.enabled = savedHeight;
            cfg.specialZombies.enabled = savedSpecial;
            cfg.champions.enabled = savedChampions;
            cfg.general.mobDetectionRange = savedRange;
            helper.getLevel().setDayTime(savedTime);
            helper.getLevel().updateSkyBrightness();
            player.discard();
        }
        helper.succeed();
    }

    /**
     * Proves the per-dimension offset feeds the whole scaling pipeline end-to-end.
     * Gametests run in the Overworld, so rather than teleport to the Nether the
     * test injects a +50 offset on {@code minecraft:overworld}: a level-0 player
     * then scales mobs as if they were level 50. Zombie health: timeFactor =
     * min(50 * 0.01, 2.5) = 0.5 → 20 * (1 + 0.5) = 30 HP. The other axes (and
     * special variants) are disabled so the assertion reflects the offset alone.
     */
    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void zombieSpawn_dimensionOffset_scalesAsHigherLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        TribulationConfig cfg = Tribulation.getConfig();

        boolean savedDist = cfg.distanceScaling.enabled;
        boolean savedHeight = cfg.heightScaling.enabled;
        boolean savedSpecial = cfg.specialZombies.enabled;
        boolean savedChampions = cfg.champions.enabled;
        boolean savedMoon = cfg.moonPhaseScaling.enabled;
        double savedRange = cfg.general.mobDetectionRange;
        Integer savedOffset = cfg.dimensionOffsets.get("minecraft:overworld");
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.specialZombies.enabled = false;
        cfg.champions.enabled = false;
        cfg.moonPhaseScaling.enabled = false;
        // Seat the player on the zombie's block and shrink the range so the fold
        // sees only this test's player, not one leaking in from an adjacent
        // structure (see assertMultiplayerHp for the same isolation).
        cfg.general.mobDetectionRange = 2.0;
        cfg.dimensionOffsets.put("minecraft:overworld", 50);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
        state.setLevel(player.getUUID(), 0, cfg.general.maxLevel);

        Zombie zombie;
        try {
            zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        } finally {
            cfg.distanceScaling.enabled = savedDist;
            cfg.heightScaling.enabled = savedHeight;
            cfg.specialZombies.enabled = savedSpecial;
            cfg.champions.enabled = savedChampions;
            cfg.moonPhaseScaling.enabled = savedMoon;
            cfg.general.mobDetectionRange = savedRange;
            if (savedOffset == null) {
                cfg.dimensionOffsets.remove("minecraft:overworld");
            } else {
                cfg.dimensionOffsets.put("minecraft:overworld", savedOffset);
            }
            player.discard();
        }

        Zombie z = zombie;
        helper.succeedWhen(() -> {
            helper.assertTrue(z.getTags().contains(MobScalingHandler.PROCESSED_TAG),
                    "scaling handler must have tagged the zombie");
            helper.assertValueEqual(z.getMaxHealth(), 30.0f,
                    "maxHealth with +50 dimension offset at player level 0");
        });
    }

    /**
     * Shared recipe for a time-axis-only scaling gametest. Seats a ServerPlayer at
     * {@code playerLevel}, spawns a zombie within {@code mobDetectionRange}, and
     * asserts the zombie's max HP lands on the expected DESIGN.md breakpoint.
     *
     * <p>Isolates from distance/height scaling and special-zombie variants so the
     * assertion reflects the time axis alone. Gametests run in a world spawned far
     * from (0,0,0) and below Y=0, which would otherwise contribute cap-hitting
     * distance+height factors and a random variant bonus on top of the time axis,
     * making the final HP position-dependent.
     *
     * <p>Safe against concurrent gametests: Minecraft's GameTestServer runs tests
     * on the main server thread, so the enable/spawn/restore sequence is atomic
     * with respect to any other test's spawn call.
     */
    @SuppressWarnings("removal")
    private void assertTimeAxisHp(GameTestHelper helper, int playerLevel, float expectedMaxHp) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // makeMockServerPlayerInLevel places the player near (0,0,0) of the level.
        // Each gametest runs at a randomized far-out position, so we must teleport
        // the player into the test region for world.getNearestPlayer(mob, range)
        // to reach them. One block away from the zombie spawn is well inside the
        // default 32-block mobDetectionRange.
        BlockPos playerAbs = helper.absolutePos(new BlockPos(0, 2, 1));
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);

        MinecraftServer server = helper.getLevel().getServer();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        TribulationConfig cfg = Tribulation.getConfig();

        state.setLevel(player.getUUID(), playerLevel, cfg.general.maxLevel);

        boolean savedDist = cfg.distanceScaling.enabled;
        boolean savedHeight = cfg.heightScaling.enabled;
        boolean savedSpecial = cfg.specialZombies.enabled;
        boolean savedChampions = cfg.champions.enabled;
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.specialZombies.enabled = false;
        cfg.champions.enabled = false;

        Zombie zombie;
        try {
            // MobScalingHandler.onEntityLoad fires synchronously during addFreshEntity,
            // so all modifiers are frozen on the returned entity by the time we restore
            // config below.
            zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        } finally {
            cfg.distanceScaling.enabled = savedDist;
            cfg.heightScaling.enabled = savedHeight;
            cfg.specialZombies.enabled = savedSpecial;
            cfg.champions.enabled = savedChampions;
        }

        Zombie z = zombie;
        helper.succeedWhen(() -> {
            helper.assertTrue(z.getTags().contains(MobScalingHandler.PROCESSED_TAG),
                    "scaling handler must have tagged the zombie");
            helper.assertValueEqual(z.getMaxHealth(), expectedMaxHp,
                    "maxHealth @ level " + playerLevel);
        });
    }

    /**
     * Shared recipe for the AVERAGE/MAX multiplayer-resolution gametests. Seats one
     * mock player per entry in {@code levels} at the test region, spawns a zombie
     * within {@code mobDetectionRange}, and asserts its max HP lands on the expected
     * breakpoint. Like {@link #assertTimeAxisHp}, it isolates the time axis by
     * disabling distance/height scaling and special-zombie variants, restoring all
     * touched config in a {@code finally} block.
     *
     * <p>The players exercise the real {@link ScalingEngine#getEffectiveLevel}
     * {@code world.players()} loop and its NO_SPECTATORS filter end-to-end. Game mode
     * is not configurable: {@code makeMockServerPlayerInLevel} hardcodes
     * isCreative()=true / isSpectator()=false, so every mock is a counted (creative)
     * player. That is enough to guard the filter — see the call-site comment.
     */
    @SuppressWarnings("removal")
    private void assertMultiplayerHp(GameTestHelper helper,
            TribulationConfig.ScalingMode mode, float expectedMaxHp, int... levels) {
        TribulationConfig cfg = Tribulation.getConfig();
        TribulationConfig.ScalingMode savedMode = cfg.general.scalingMode;
        boolean savedDist = cfg.distanceScaling.enabled;
        boolean savedHeight = cfg.heightScaling.enabled;
        boolean savedSpecial = cfg.specialZombies.enabled;
        boolean savedChampions = cfg.champions.enabled;
        double savedRange = cfg.general.mobDetectionRange;

        cfg.general.scalingMode = mode;
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.specialZombies.enabled = false;
        cfg.champions.enabled = false;
        // AVERAGE/MAX fold over every player in range, and gametests run concurrently
        // in one shared level with structures only ~8 blocks apart. Shrink the range
        // so the fold sees only this test's own players (seated on the spawn, distSq≈0)
        // and not mock players leaking in from adjacent test structures.
        cfg.general.mobDetectionRange = 2.0;

        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        BlockPos playerAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        List<ServerPlayer> players = new ArrayList<>();

        try {
            for (int level : levels) {
                ServerPlayer p = helper.makeMockServerPlayerInLevel();
                p.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
                state.setLevel(p.getUUID(), level, cfg.general.maxLevel);
                players.add(p);
            }
            Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
            helper.assertValueEqual(zombie.getMaxHealth(), expectedMaxHp, mode + " mode health");
        } finally {
            cfg.general.scalingMode = savedMode;
            cfg.distanceScaling.enabled = savedDist;
            cfg.heightScaling.enabled = savedHeight;
            cfg.specialZombies.enabled = savedSpecial;
            cfg.champions.enabled = savedChampions;
            cfg.general.mobDetectionRange = savedRange;
            for (ServerPlayer p : players) {
                p.discard();
            }
        }
        helper.succeed();
    }
}
