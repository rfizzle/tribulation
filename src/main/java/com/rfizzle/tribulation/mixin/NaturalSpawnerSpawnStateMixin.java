package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.event.BloodMoonHandler;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Raises the global per-category spawn cap for MONSTER during an active Blood
 * Moon. The spawning level is resolved through the state's
 * {@link LocalMobCapCalculator} so the boost applies only to the Overworld —
 * Nether/End caps stay vanilla even while the event runs.
 */
@Mixin(NaturalSpawner.SpawnState.class)
public abstract class NaturalSpawnerSpawnStateMixin {

    @Shadow @Final private LocalMobCapCalculator localMobCapCalculator;

    @Redirect(
            method = "canSpawnForCategory",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int tribulation$boostGlobalMobCap(MobCategory category) {
        int base = category.getMaxInstancesPerChunk();
        if (category != MobCategory.MONSTER) return base;
        ChunkMap chunkMap = ((LocalMobCapCalculatorAccessor) this.localMobCapCalculator).tribulation$getChunkMap();
        ServerLevel level = ((ChunkMapAccessor) chunkMap).tribulation$getLevel();
        return BloodMoonHandler.scaledMobCap(
                base,
                BloodMoonHandler.isSpawnBoostActive(level),
                BloodMoonHandler.spawnCapMultiplier());
    }
}
