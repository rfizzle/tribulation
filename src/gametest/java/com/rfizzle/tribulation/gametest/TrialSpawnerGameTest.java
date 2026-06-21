// Tier: 3 (Fabric Gametest)
package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.mixin.TrialSpawnerDataAccessor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;

import java.util.Optional;
import java.util.UUID;

/**
 * End-to-end coverage of {@code TrialSpawnerMixin} (spawn scaling) and
 * {@code TrialSpawnerDataMixin} (ominous upgrade).
 *
 * <p>Both tests run synchronously against the spawner's public API so the
 * assertions never depend on the spawner's RNG placement or tick cadence: the
 * scaling test seeds a deterministic spawn (explicit entity + position) and
 * drives {@link TrialSpawner#spawnMob}; the ominous test seeds detected players
 * and drives {@link TrialSpawnerData#tryDetectPlayers}, which is exactly where
 * the mixin rolls the upgrade.
 */
public class TrialSpawnerGameTest implements FabricGameTest {

    // Both rest on the structure's stone floor (local y=0). The spawn block is
    // adjacent to the spawner with clear line of sight and stone directly beneath,
    // so spawnMob's collision / line-of-sight / ground-rule checks all pass.
    private static final BlockPos SPAWNER_POS = new BlockPos(1, 0, 1);
    private static final BlockPos SPAWN_POS = new BlockPos(0, 0, 1);

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void trialSpawner_scalesSpawnedMob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Difficulty originalDifficulty = level.getDifficulty();
        // Trial-spawner spawns still run vanilla monster spawn rules, which reject
        // PEACEFUL — force NORMAL so the seeded zombie actually spawns.
        server.setDifficulty(Difficulty.NORMAL, true);

        helper.setBlock(SPAWNER_POS, Blocks.TRIAL_SPAWNER);
        TrialSpawnerBlockEntity be = helper.getBlockEntity(SPAWNER_POS);
        TrialSpawner spawner = be.getTrialSpawner();
        TrialSpawnerDataAccessor dataAccessor = (TrialSpawnerDataAccessor) spawner.getData();

        // Seed a deterministic spawn: a zombie at a known in-template air block.
        BlockPos absSpawn = helper.absolutePos(SPAWN_POS);
        CompoundTag entityTag = new CompoundTag();
        entityTag.putString("id", "minecraft:zombie");
        ListTag posTag = new ListTag();
        // Centre of the block, lifted slightly off the floor surface so the
        // spawner's line-of-sight check rides above it rather than grazing it.
        posTag.add(0, DoubleTag.valueOf(absSpawn.getX() + 0.5));
        posTag.add(1, DoubleTag.valueOf(absSpawn.getY() + 0.2));
        posTag.add(2, DoubleTag.valueOf(absSpawn.getZ() + 0.5));
        entityTag.put("Pos", posTag);
        dataAccessor.setNextSpawnData(Optional.of(new SpawnData(entityTag, Optional.empty(), Optional.empty())));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        // Level 100 -> tier 2 with default breakpoints.
        state.setLevel(player.getUUID(), 100, cfg.general.maxLevel);
        dataAccessor.getDetectedPlayers().add(player.getUUID());

        try {
            Optional<UUID> mobUuid = spawner.spawnMob(level, helper.absolutePos(SPAWNER_POS));
            helper.assertTrue(mobUuid.isPresent(), "Trial spawner failed to spawn the seeded mob");

            Entity entity = level.getEntity(mobUuid.get());
            helper.assertTrue(entity instanceof Mob, "Spawned entity missing or not a mob");
            Mob mob = (Mob) entity;

            helper.assertTrue(mob.getTags().contains(MobScalingHandler.PROCESSED_TAG),
                    "Trial-spawned mob missing PROCESSED_TAG");
            helper.assertTrue(mob.hasAttached(TribulationAttachments.SCALED_TIER),
                    "Trial-spawned mob missing tier attachment");
            helper.assertTrue(mob.getAttachedOrThrow(TribulationAttachments.SCALED_TIER) == 2,
                    "Trial-spawned mob scaled to wrong tier");
            mob.discard();
        } finally {
            player.discard();
            server.setDifficulty(originalDifficulty, true);
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void trialSpawner_ominousUpgrade_triggersAtHighTier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();

        helper.setBlock(SPAWNER_POS, Blocks.TRIAL_SPAWNER);
        TrialSpawnerBlockEntity be = helper.getBlockEntity(SPAWNER_POS);
        TrialSpawner spawner = be.getTrialSpawner();
        TrialSpawnerDataAccessor dataAccessor = (TrialSpawnerDataAccessor) spawner.getData();

        TribulationConfig cfg = Tribulation.getConfig();
        boolean savedEnabled = cfg.trialSpawner.enabled;
        boolean savedOminousEnabled = cfg.trialSpawner.ominousUpgrade.enabled;
        float savedChance = cfg.trialSpawner.ominousUpgrade.chance;
        int savedMinTier = cfg.trialSpawner.ominousUpgrade.minimumTier;
        cfg.trialSpawner.enabled = true;
        cfg.trialSpawner.ominousUpgrade.enabled = true;
        cfg.trialSpawner.ominousUpgrade.chance = 1.0f; // guaranteed roll
        cfg.trialSpawner.ominousUpgrade.minimumTier = 3;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        // Level 150 -> tier 3, clearing the configured minimum.
        state.setLevel(player.getUUID(), 150, cfg.general.maxLevel);
        dataAccessor.getDetectedPlayers().add(player.getUUID());

        try {
            helper.assertFalse(spawner.isOminous(), "Spawner should start non-ominous");
            // Drives the same detection step the tick loop calls; the mixin rolls
            // the ominous upgrade at its tail when players are tracked.
            spawner.getData().tryDetectPlayers(level, helper.absolutePos(SPAWNER_POS), spawner);
            helper.assertTrue(spawner.isOminous(),
                    "Trial spawner should have become ominous at tier 3 with chance 1.0");
        } finally {
            cfg.trialSpawner.enabled = savedEnabled;
            cfg.trialSpawner.ominousUpgrade.enabled = savedOminousEnabled;
            cfg.trialSpawner.ominousUpgrade.chance = savedChance;
            cfg.trialSpawner.ominousUpgrade.minimumTier = savedMinTier;
            player.discard();
        }
        helper.succeed();
    }
}
