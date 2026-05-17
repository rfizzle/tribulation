package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies death relief on a qualifying player death: subtracts
 * {@link TribulationConfig.DeathRelief#amount} levels from the dying
 * player's difficulty level, floored at
 * {@link TribulationConfig.DeathRelief#minimumLevel}. A cooldown keyed on
 * {@code cooldownTicks} suppresses rapid-suicide exploits. All death causes
 * count; the cooldown is the sole gate.
 */
public final class DeathReliefHandler {

    private DeathReliefHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(DeathReliefHandler::onAfterDeath);
    }

    static void onAfterDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof ServerPlayer player)) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.deathRelief.enabled) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int before = state.getLevel(player.getUUID());
            boolean applied = state.reduceLevel(
                    player.getUUID(),
                    cfg.deathRelief.amount,
                    cfg.deathRelief.cooldownTicks,
                    cfg.deathRelief.minimumLevel,
                    server.getTickCount()
            );
            if (!applied) {
                Tribulation.LOGGER.debug(
                        "Death relief skipped for {} — within cooldown ({} ticks)",
                        player.getGameProfile().getName(),
                        cfg.deathRelief.cooldownTicks
                );
                return;
            }
            int after = state.getLevel(player.getUUID());
            if (before != after) {
                Tribulation.LOGGER.debug(
                        "Death relief: {} reduced from level {} to {}",
                        player.getGameProfile().getName(),
                        before,
                        after
                );
            }
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply death relief for {}", player, e);
        }
    }
}
