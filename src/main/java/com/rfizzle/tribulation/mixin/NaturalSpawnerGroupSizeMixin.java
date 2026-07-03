package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.event.PackTacticsHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enlarges natural spawn groups of pack-tactics-eligible mob types above the
 * configured tier threshold. Vanilla picks the group size as
 * {@code minCount + random.nextInt(1 + maxCount - minCount)}; boosting every
 * {@code minCount}/{@code maxCount} read in that one expression by the same
 * bonus shifts the whole group size by exactly the bonus while leaving the
 * random bound — and therefore its positivity — untouched. Those three field
 * reads are the only ones in the method, so no other logic is affected.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerGroupSizeMixin {

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/biome/MobSpawnSettings$SpawnerData;minCount:I",
                    opcode = Opcodes.GETFIELD))
    private static int tribulation$boostGroupMinCount(MobSpawnSettings.SpawnerData data,
                                                      MobCategory category, ServerLevel level, ChunkAccess chunk, BlockPos pos,
                                                      NaturalSpawner.SpawnPredicate filter, NaturalSpawner.AfterSpawnCallback callback) {
        return data.minCount + PackTacticsHandler.spawnGroupBonus(data.type, level, pos);
    }

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/biome/MobSpawnSettings$SpawnerData;maxCount:I",
                    opcode = Opcodes.GETFIELD))
    private static int tribulation$boostGroupMaxCount(MobSpawnSettings.SpawnerData data,
                                                      MobCategory category, ServerLevel level, ChunkAccess chunk, BlockPos pos,
                                                      NaturalSpawner.SpawnPredicate filter, NaturalSpawner.AfterSpawnCallback callback) {
        return data.maxCount + PackTacticsHandler.spawnGroupBonus(data.type, level, pos);
    }
}
