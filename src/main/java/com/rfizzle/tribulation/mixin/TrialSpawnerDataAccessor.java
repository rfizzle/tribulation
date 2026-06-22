package com.rfizzle.tribulation.mixin;

import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mixin-local access to {@link TrialSpawnerData}'s {@code protected} internals.
 *
 * <p>{@link #getDetectedPlayers()} backs the scaling path in
 * {@code TrialSpawnerMixin}: trial-spawned mobs are scaled from the levels of
 * the players the spawner has already detected, rather than the proximity scan
 * used for natural spawns.
 *
 * <p>{@link #setNextSpawnData(Optional)} exists so gametests can seed a
 * deterministic spawn (entity id + explicit position) without relying on
 * structure NBT, keeping the trial-spawner spawn assertions reproducible.
 */
@Mixin(TrialSpawnerData.class)
public interface TrialSpawnerDataAccessor {
    @Accessor("detectedPlayers")
    Set<UUID> getDetectedPlayers();

    @Accessor("nextSpawnData")
    void setNextSpawnData(Optional<SpawnData> nextSpawnData);
}
