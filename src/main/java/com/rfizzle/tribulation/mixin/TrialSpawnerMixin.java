package com.rfizzle.tribulation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.ScalingMode;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.MobScalingHandler;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Routes trial-spawner mobs through the Tribulation scaling pipeline. Natural
 * spawns resolve their effective player level by proximity ({@code
 * MobScalingHandler.onEntityLoad}), which cannot see <em>which</em> players
 * activated a trial spawner — and that proximity hook fires the instant the mob
 * is added to the world.
 *
 * <p>So this wraps the {@code tryAddFreshEntityWithPassengers} call inside
 * {@link TrialSpawner#spawnMob}: it scales the mob from the spawner's detected
 * players and stamps {@code PROCESSED_TAG} <em>before</em> the entity is added,
 * so the {@code ENTITY_LOAD} hook sees it already processed and skips it (rather
 * than re-scaling it by proximity and winning the race).
 *
 * <p>The ominous upgrade lives in {@link TrialSpawnerDataMixin}.
 */
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {

    @WrapOperation(
            method = "spawnMob",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean tribulation$scaleBeforeAdd(ServerLevel world, Entity entity, Operation<Boolean> original) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg != null && cfg.trialSpawner.enabled
                && entity instanceof Mob mob
                && !mob.getTags().contains(MobScalingHandler.PROCESSED_TAG)) {
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (!MobScalingHandler.isExcluded(typeId, cfg.general.excludedEntities)) {
                TrialSpawnerData data = ((TrialSpawner) (Object) this).getData();
                int playerLevel = tribulation$detectedPlayerLevel(world, data, cfg, mob.position());
                MobScalingHandler.applyScaling(mob, world, mob.getType(), typeId, playerLevel, cfg);
            }
        }
        return original.call(world, entity);
    }

    /**
     * Fold the levels of the spawner's detected players into a single effective
     * level using the configured scaling mode — the trial-spawner analogue of
     * {@link ScalingEngine#getEffectiveLevel}. Returns 0 when no players are
     * tracked, which scales the mob as if at level 0 (i.e. effectively vanilla).
     *
     * <p>{@code MAX} / {@code AVERAGE} are order-independent folds over the
     * detected levels. {@code NEAREST} is resolved by actual distance to {@code
     * origin} (the mob's spawn point) rather than the arbitrary iteration order
     * of the detected-player set, matching {@link ScalingEngine#getEffectiveLevel}'s
     * nearest-player semantics. Detected players that are offline or in another
     * dimension carry no position here, so they're skipped for the distance pick;
     * if none resolve we fall back to the level fold instead of scaling to 0.
     */
    private static int tribulation$detectedPlayerLevel(ServerLevel world, TrialSpawnerData data,
                                                       TribulationConfig cfg, Vec3 origin) {
        Set<UUID> detectedPlayers = ((TrialSpawnerDataAccessor) data).getDetectedPlayers();
        if (detectedPlayers.isEmpty()) return 0;

        MinecraftServer server = world.getServer();
        if (server == null) return 0;
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);

        if (cfg.general.scalingMode == ScalingMode.NEAREST) {
            ServerPlayer nearest = null;
            double best = Double.MAX_VALUE;
            for (UUID uuid : detectedPlayers) {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp == null || sp.level() != world) continue;
                double distSq = sp.distanceToSqr(origin);
                if (distSq < best) {
                    best = distSq;
                    nearest = sp;
                }
            }
            if (nearest != null) return state.getLevel(nearest.getUUID());
        }

        List<Integer> levels = new ArrayList<>(detectedPlayers.size());
        for (UUID uuid : detectedPlayers) {
            levels.add(state.getLevel(uuid));
        }
        return ScalingEngine.foldLevels(cfg.general.scalingMode, levels);
    }
}
