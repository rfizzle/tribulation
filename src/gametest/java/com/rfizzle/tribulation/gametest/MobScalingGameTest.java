// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.MobScalingHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

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
        cfg.distanceScaling.enabled = false;
        cfg.heightScaling.enabled = false;
        cfg.specialZombies.enabled = false;

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
        }

        Zombie z = zombie;
        helper.succeedWhen(() -> {
            helper.assertTrue(z.getTags().contains(MobScalingHandler.PROCESSED_TAG),
                    "scaling handler must have tagged the zombie");
            helper.assertValueEqual(z.getMaxHealth(), expectedMaxHp,
                    "maxHealth @ level " + playerLevel);
        });
    }
}
