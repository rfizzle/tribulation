package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Optional ominous upgrade for trial spawners (off by default). Vanilla makes a
 * spawner ominous only when a detected player carries Bad Omen; this lets a
 * high-tier world roll a configurable chance to become ominous with no omen
 * effect, hooked at the spawner's own player-detection step
 * ({@link TrialSpawnerData#tryDetectPlayers}), the same step where vanilla runs
 * its Bad-Omen-driven upgrade.
 *
 * <p>The hook fires at {@code HEAD}, so it reads the {@code detectedPlayers}
 * accumulated by <em>previous</em> detection scans — the very first scan that
 * finds a player rolls on the next scan rather than the same one. That one-scan
 * lag is harmless for an optional flavour feature and keeps the read off the
 * fragile tail of a long, branch-heavy method.
 *
 * <p>The roll is single-shot per activation: {@link #tribulation$ominousRolled}
 * gates re-rolls while players stay detected, and {@code reset()} (fired when
 * the trial finishes its cooldown) clears it for the next activation. Already-
 * ominous spawners (vanilla or via this roll) are left untouched, so the
 * player's omen state is never double-triggered.
 */
@Mixin(TrialSpawnerData.class)
public abstract class TrialSpawnerDataMixin {

    @Unique
    private boolean tribulation$ominousRolled = false;

    @Inject(method = "tryDetectPlayers", at = @At("HEAD"))
    private void tribulation$rollOminousUpgrade(ServerLevel world, BlockPos pos, TrialSpawner spawner, CallbackInfo ci) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.trialSpawner.enabled || !cfg.trialSpawner.ominousUpgrade.enabled) return;
        if (tribulation$ominousRolled || spawner.isOminous()) return;

        Set<UUID> players = ((TrialSpawnerDataAccessor) this).getDetectedPlayers();
        if (players.isEmpty()) return;

        // Players are now detected for this activation — take the single roll.
        tribulation$ominousRolled = true;

        MinecraftServer server = world.getServer();
        if (server == null) return;
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        List<Integer> levels = new ArrayList<>(players.size());
        for (UUID uuid : players) {
            levels.add(state.getLevel(uuid));
        }

        int effectiveLevel = ScalingEngine.foldLevels(cfg.general.scalingMode, levels);
        int tier = TierManager.getTier(effectiveLevel, cfg.tiers);
        if (tier < cfg.trialSpawner.ominousUpgrade.minimumTier) return;

        if (world.getRandom().nextFloat() < cfg.trialSpawner.ominousUpgrade.chance) {
            spawner.applyOminous(world, pos);
        }
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void tribulation$clearOminousRoll(CallbackInfo ci) {
        tribulation$ominousRolled = false;
    }
}
