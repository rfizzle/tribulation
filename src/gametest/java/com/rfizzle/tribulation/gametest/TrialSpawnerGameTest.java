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

        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        // Two players with distinct levels prove the scaling *source*, not just
        // that scaling happened. The detected player (level 150 -> tier 3) is
        // teleported well outside the proximity scan range, and a decoy player
        // (level 50 -> tier 1) sits in proximity but is NOT detected. The natural
        // ENTITY_LOAD hook resolves by proximity, so if the mixin's pre-add scaling
        // ever regressed the mob would scale to tier 1 from the decoy. A tier-3
        // result can therefore only come from the spawner's detected-player list.
        ServerPlayer detected = helper.makeMockServerPlayerInLevel();
        state.setLevel(detected.getUUID(), 150, cfg.general.maxLevel);
        detected.setPos(absSpawn.getX() + 1000.0, absSpawn.getY(), absSpawn.getZ());
        dataAccessor.getDetectedPlayers().add(detected.getUUID());

        // Decoy: in proximity, not detected. setLevel before spawn so a proximity
        // scan would deterministically yield tier 1.
        ServerPlayer decoy = helper.makeMockServerPlayerInLevel();
        state.setLevel(decoy.getUUID(), 50, cfg.general.maxLevel);

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
            helper.assertTrue(mob.getAttachedOrThrow(TribulationAttachments.SCALED_TIER) == 3,
                    "Trial-spawned mob must scale from the detected player (tier 3), not the proximity decoy (tier 1)");
            mob.discard();
        } finally {
            detected.discard();
            decoy.discard();
            server.setDifficulty(originalDifficulty, true);
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void trialSpawner_nearestMode_picksClosestDetectedPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Difficulty originalDifficulty = level.getDifficulty();
        server.setDifficulty(Difficulty.NORMAL, true);

        helper.setBlock(SPAWNER_POS, Blocks.TRIAL_SPAWNER);
        TrialSpawnerBlockEntity be = helper.getBlockEntity(SPAWNER_POS);
        TrialSpawner spawner = be.getTrialSpawner();
        TrialSpawnerDataAccessor dataAccessor = (TrialSpawnerDataAccessor) spawner.getData();

        BlockPos absSpawn = helper.absolutePos(SPAWN_POS);
        CompoundTag entityTag = new CompoundTag();
        entityTag.putString("id", "minecraft:zombie");
        ListTag posTag = new ListTag();
        posTag.add(0, DoubleTag.valueOf(absSpawn.getX() + 0.5));
        posTag.add(1, DoubleTag.valueOf(absSpawn.getY() + 0.2));
        posTag.add(2, DoubleTag.valueOf(absSpawn.getZ() + 0.5));
        entityTag.put("Pos", posTag);
        dataAccessor.setNextSpawnData(Optional.of(new SpawnData(entityTag, Optional.empty(), Optional.empty())));

        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        // Default scaling mode is NEAREST. Two *detected* players at different
        // levels and distances: the closer one (tier 1) must win over the farther,
        // higher-level one (tier 4). Under the arbitrary set-iteration fold this
        // pick would be non-deterministic; distance-based resolution makes it stable.
        // Mock players spawn near world origin, but the gametest structure sits
        // millions of blocks out, so both players are positioned explicitly
        // relative to the spawn. 'near' sits two blocks from the spawn point (close
        // but not blocking it); 'far' is teleported 1000 blocks out.
        BlockPos nearPos = helper.absolutePos(new BlockPos(2, 0, 1));
        ServerPlayer near = helper.makeMockServerPlayerInLevel();
        state.setLevel(near.getUUID(), 60, cfg.general.maxLevel); // tier 1
        near.setPos(nearPos.getX() + 0.5, nearPos.getY(), nearPos.getZ() + 0.5);
        dataAccessor.getDetectedPlayers().add(near.getUUID());

        ServerPlayer far = helper.makeMockServerPlayerInLevel();
        state.setLevel(far.getUUID(), 200, cfg.general.maxLevel); // tier 4
        far.setPos(absSpawn.getX() + 1000.0, absSpawn.getY(), absSpawn.getZ());
        dataAccessor.getDetectedPlayers().add(far.getUUID());

        try {
            Optional<UUID> mobUuid = spawner.spawnMob(level, helper.absolutePos(SPAWNER_POS));
            helper.assertTrue(mobUuid.isPresent(), "Trial spawner failed to spawn the seeded mob");
            Mob mob = (Mob) level.getEntity(mobUuid.get());
            helper.assertTrue(mob.getAttachedOrThrow(TribulationAttachments.SCALED_TIER) == 1,
                    "NEAREST mode must scale from the closest detected player (tier 1), not the farthest (tier 4)");
            mob.discard();
        } finally {
            near.discard();
            far.discard();
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

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void trialSpawner_ominousUpgrade_skipsBelowMinimumTier(GameTestHelper helper) {
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
        cfg.trialSpawner.ominousUpgrade.chance = 1.0f; // would always roll if the gate passed
        cfg.trialSpawner.ominousUpgrade.minimumTier = 3;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        // Level 100 -> tier 2, below the configured minimum tier 3.
        state.setLevel(player.getUUID(), 100, cfg.general.maxLevel);
        dataAccessor.getDetectedPlayers().add(player.getUUID());

        try {
            spawner.getData().tryDetectPlayers(level, helper.absolutePos(SPAWNER_POS), spawner);
            helper.assertFalse(spawner.isOminous(),
                    "Trial spawner should stay non-ominous below the minimum tier even with chance 1.0");
        } finally {
            cfg.trialSpawner.enabled = savedEnabled;
            cfg.trialSpawner.ominousUpgrade.enabled = savedOminousEnabled;
            cfg.trialSpawner.ominousUpgrade.chance = savedChance;
            cfg.trialSpawner.ominousUpgrade.minimumTier = savedMinTier;
            player.discard();
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "tribulation:empty_3x3")
    public void trialSpawner_ominousUpgrade_respectsMasterToggle(GameTestHelper helper) {
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
        // Master toggle off must gate the ominous upgrade even with a high tier
        // and a guaranteed roll otherwise configured.
        cfg.trialSpawner.enabled = false;
        cfg.trialSpawner.ominousUpgrade.enabled = true;
        cfg.trialSpawner.ominousUpgrade.chance = 1.0f;
        cfg.trialSpawner.ominousUpgrade.minimumTier = 3;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        // Level 150 -> tier 3, clearing the minimum; only the master toggle blocks it.
        state.setLevel(player.getUUID(), 150, cfg.general.maxLevel);
        dataAccessor.getDetectedPlayers().add(player.getUUID());

        try {
            spawner.getData().tryDetectPlayers(level, helper.absolutePos(SPAWNER_POS), spawner);
            helper.assertFalse(spawner.isOminous(),
                    "Trial spawner should stay non-ominous when trialSpawner.enabled is false");
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
