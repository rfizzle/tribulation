package com.rfizzle.tribulation.gametest;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.mixin.TrialSpawnerAccessor;
import com.rfizzle.tribulation.mixin.TrialSpawnerDataAccessor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;

import java.util.Optional;
import java.util.UUID;

public class TrialSpawnerGameTest implements FabricGameTest {

    @GameTest(template = "tribulation:empty_3x3")
    public void trialSpawner_scalesSpawnedMob(GameTestHelper helper) {
        BlockPos spawnerPos = new BlockPos(1, 2, 1);
        helper.setBlock(spawnerPos, Blocks.TRIAL_SPAWNER);

        TrialSpawnerBlockEntity be = (TrialSpawnerBlockEntity) helper.getBlockEntity(spawnerPos);
        TrialSpawner spawner = be.getTrialSpawner();
        TrialSpawnerDataAccessor data = (TrialSpawnerDataAccessor) ((TrialSpawnerAccessor) (Object) spawner).getData();

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(spawnerPos);
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);

        TribulationConfig cfg = Tribulation.getConfig();
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
        // Level 100 -> Tier 2
        state.setLevel(player.getUUID(), 100, cfg.general.maxLevel);

        // Manually add player to detected list to simulate proximity detection
        data.getDetectedPlayers().add(player.getUUID());

        try {
            // Force a spawn
            Optional<UUID> mobUuid = spawner.spawnMob(helper.getLevel(), helper.absolutePos(spawnerPos));

            helper.assertTrue(mobUuid.isPresent(), "Trial spawner failed to spawn a mob");

            Mob mob = (Mob) helper.getLevel().getEntity(mobUuid.get());
            helper.assertTrue(mob != null, "Spawned mob not found in world");

            helper.succeedWhen(() -> {
                helper.assertTrue(mob.getTags().contains(MobScalingHandler.PROCESSED_TAG), "Trial-spawned mob missing PROCESSED_TAG");
                helper.assertTrue(mob.hasAttached(TribulationAttachments.SCALED_TIER), "Trial-spawned mob missing tier attachment");
                helper.assertTrue(mob.getAttachedOrThrow(TribulationAttachments.SCALED_TIER) == 2, "Trial-spawned mob has wrong tier");
            });
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "tribulation:empty_3x3")
    public void trialSpawner_ominousUpgrade_triggersAtHighTier(GameTestHelper helper) {
        BlockPos spawnerPos = new BlockPos(1, 2, 1);
        helper.setBlock(spawnerPos, Blocks.TRIAL_SPAWNER);

        TrialSpawnerBlockEntity be = (TrialSpawnerBlockEntity) helper.getBlockEntity(spawnerPos);
        TrialSpawner spawner = be.getTrialSpawner();
        TrialSpawnerDataAccessor data = (TrialSpawnerDataAccessor) ((TrialSpawnerAccessor) (Object) spawner).getData();

        TribulationConfig cfg = Tribulation.getConfig();
        boolean originalOminousEnabled = cfg.trialSpawner.ominousUpgrade.enabled;
        float originalChance = cfg.trialSpawner.ominousUpgrade.chance;

        cfg.trialSpawner.ominousUpgrade.enabled = true;
        cfg.trialSpawner.ominousUpgrade.chance = 1.0f; // Guaranteed
        cfg.trialSpawner.ominousUpgrade.minimumTier = 3;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAbs = helper.absolutePos(spawnerPos);
        player.teleportTo(playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(helper.getLevel().getServer());
            // Level 150 -> Tier 3
            state.setLevel(player.getUUID(), 150, cfg.general.maxLevel);

            data.getDetectedPlayers().add(player.getUUID());

            // We let the world tick naturally to trigger the upgrade check in TrialSpawnerMixin.tick
            helper.succeedWhen(() -> {
                helper.assertTrue(spawner.isOminous(), "Trial spawner should have become ominous");
            });
        } finally {
            cfg.trialSpawner.ominousUpgrade.enabled = originalOminousEnabled;
            cfg.trialSpawner.ominousUpgrade.chance = originalChance;
            player.discard();
        }
    }
}
