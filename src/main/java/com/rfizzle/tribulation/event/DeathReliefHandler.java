package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.stat.TribulationStats;
import com.rfizzle.tribulation.network.TribulationNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Applies death relief when a player respawns after dying: subtracts
 * {@link TribulationConfig.DeathRelief#amount} levels from the player's
 * difficulty level, floored at
 * {@link TribulationConfig.DeathRelief#minimumLevel}. A cooldown keyed on
 * {@code cooldownTicks} suppresses rapid-suicide exploits. All death causes
 * count; the cooldown is the sole gate. Running on respawn (rather than at
 * death) means the actionbar message and HUD cooling flash land once the
 * death screen has closed and the player can see them.
 */
public final class DeathReliefHandler {

    private DeathReliefHandler() {}

    public static void register() {
        // Apply on respawn, not at AFTER_DEATH: the actionbar message and the
        // HUD cooling flash are only drawn once the death screen closes and the
        // in-game HUD returns, so both player-facing signals must land on the
        // freshly respawned player to be seen.
        ServerPlayerEvents.AFTER_RESPAWN.register(DeathReliefHandler::onAfterRespawn);
    }

    public static void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        // {@code alive} is true when the player conquered the End and returned
        // without dying — no death, so no relief.
        if (alive) return;
        maybeApplyPenalty(newPlayer);
    }

    /**
     * Applies death relief to the given player if the mechanic is enabled.
     * The config gate lives here so both the respawn event and direct callers
     * (gametests, totem integration entry point) share it.
     */
    public static void maybeApplyPenalty(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.deathRelief.enabled) return;
        applyPenalty(player);
    }

    public static void applyPenalty(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) return;

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
            TribulationNetworking.syncLevel(player);
            if (before != after) {
                player.awardStat(TribulationStats.LEVELS_LOST_TO_DEATH_RELIEF, before - after);
                TribulationLevelCallback.EVENT.invoker().onLevelChanged(player, before, after);
                player.displayClientMessage(
                        Component.translatable("message.tribulation.death_relief", before, after)
                                .withStyle(ChatFormatting.AQUA),
                        true);
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
