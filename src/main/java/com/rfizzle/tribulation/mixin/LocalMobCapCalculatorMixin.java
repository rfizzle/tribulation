package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.event.BloodMoonHandler;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Raises the per-player local MONSTER cap during an active Blood Moon,
 * mirroring {@link NaturalSpawnerSpawnStateMixin}'s global-cap boost — both
 * caps must move together or the tighter one silently wins. The redirected
 * call is re-implemented with the scaled cap; with no event active the math
 * reduces to vanilla's {@code count < getMaxInstancesPerChunk()}.
 */
@Mixin(LocalMobCapCalculator.class)
public abstract class LocalMobCapCalculatorMixin {

    @Shadow @Final private ChunkMap chunkMap;

    @Redirect(
            method = "canSpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LocalMobCapCalculator$MobCounts;canSpawn(Lnet/minecraft/world/entity/MobCategory;)Z"))
    private boolean tribulation$boostLocalMobCap(@Coerce Object mobCounts, MobCategory category) {
        int count = ((MobCountsAccessor) mobCounts).tribulation$getCounts().getOrDefault(category, 0);
        int cap = category.getMaxInstancesPerChunk();
        if (category == MobCategory.MONSTER) {
            ServerLevel level = ((ChunkMapAccessor) this.chunkMap).tribulation$getLevel();
            cap = BloodMoonHandler.scaledMobCap(
                    cap,
                    BloodMoonHandler.isSpawnBoostActive(level),
                    BloodMoonHandler.spawnCapMultiplier());
        }
        return count < cap;
    }
}
