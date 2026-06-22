package com.rfizzle.tribulation.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds extra raid wave(s) when the raid's targeted player(s) are at or above a
 * configurable tier threshold, via {@link TribulationConfig.RaidScaling}.
 *
 * <p>{@code Raid.getNumGroups(Difficulty)} drives the wave count: it is called
 * once in the live constructor (stored into the {@code final numGroups} field
 * that backs the raid bar, {@code hasMoreWaves}, and {@code isFinalWave}) and
 * twice inside {@code spawnGroup} (final-wave/captain-banner detection and
 * bonus-spawn count). The NBT-loading constructor restores {@code numGroups}
 * from disk without calling this method.
 *
 * <p>So the extra-wave count is <em>captured once and memoized</em> in a
 * {@code @Unique} field: recomputing the tier on every call would let the frozen
 * {@code numGroups} field disagree with {@code spawnGroup}'s view if the
 * targeted player's tier shifted mid-raid (death, leaving range, level-up),
 * putting the captain banner on the wrong wave. The first call is the live
 * constructor, where the instigators are present near the raid centre, so the
 * value is captured from the raid's triggering players and stays consistent
 * across the constructor, the bar, and every {@code spawnGroup} call.
 */
@Mixin(Raid.class)
public abstract class RaidScalingMixin {

    @Shadow @Final private ServerLevel level;

    @Shadow public abstract BlockPos getCenter();

    /** -1 = not yet computed; captured on the first {@code getNumGroups} call. */
    @Unique
    private int tribulation$extraWaves = -1;

    @ModifyReturnValue(method = "getNumGroups", at = @At("RETURN"))
    private int tribulation$addExtraWaves(int original) {
        if (tribulation$extraWaves < 0) {
            tribulation$extraWaves = tribulation$computeExtraWaves();
        }
        return original + tribulation$extraWaves;
    }

    @Unique
    private int tribulation$computeExtraWaves() {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || cfg.raidScaling == null || !cfg.raidScaling.enabled) return 0;
        int tier = TierManager.getTier(tribulation$detectedLevel(cfg), cfg.tiers);
        return cfg.raidScaling.extraWaves(tier);
    }

    /**
     * Fold the levels of the players near the raid centre into a single
     * effective level using the configured scaling mode — the raid-centre
     * analogue of {@link ScalingEngine#getEffectiveLevel}. A {@link Raid} is not
     * an {@link net.minecraft.world.entity.Entity}, so resolution gathers
     * players within {@code mobDetectionRange} of {@link #getCenter()} (matching
     * {@code TrialSpawnerMixin}). Spectators are excluded so an admin near the
     * village can't skew the result; creative players still count.
     */
    @Unique
    private int tribulation$detectedLevel(TribulationConfig cfg) {
        ServerLevel world = this.level;
        if (world == null) return 0;
        MinecraftServer server = world.getServer();
        if (server == null) return 0;

        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        Vec3 center = Vec3.atCenterOf(getCenter());
        double range = cfg.general.mobDetectionRange;
        double rangeSq = range * range;

        List<Integer> levels = new ArrayList<>();
        for (ServerPlayer sp : world.players()) {
            if (EntitySelector.NO_SPECTATORS.test(sp)
                    && sp.distanceToSqr(center) <= rangeSq) {
                levels.add(state.getLevel(sp.getUUID()));
            }
        }
        return ScalingEngine.foldLevels(cfg.general.scalingMode, levels);
    }
}
