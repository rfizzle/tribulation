package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.network.TribulationNetworking;
import com.rfizzle.tribulation.stat.TribulationStats;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Optional offline level decay (config {@code levelDecay}, off by default).
 * Each disconnect stamps a wall-clock anchor in {@link PlayerDifficultyState};
 * on the next login the elapsed absence beyond {@code graceDays} sheds
 * {@code levelsPerDay} levels per day, floored at {@code floor}. The anchor
 * is re-stamped on login too, so a crashed server (whose DISCONNECT never
 * fired) can at worst decay from the previous login, never from an ancient
 * stamp — and so mid-session level changes are never decayed retroactively.
 *
 * <p>When the feature is disabled nothing is stamped or decayed, so the
 * persisted state stays byte-identical to pre-feature saves. A player whose
 * anchor is {@link PlayerDifficultyState#NEVER_SEEN} (fresh player, old save,
 * or feature just enabled) never decays on that login; the stamp cycle
 * begins from it instead.
 */
public final class LevelDecayHandler {
    /** One real-time day in milliseconds — the unit of {@code graceDays} and {@code levelsPerDay}. */
    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private LevelDecayHandler() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                applyOfflineDecay(handler.getPlayer(), System.currentTimeMillis()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                stampLastSeen(handler.getPlayer(), System.currentTimeMillis()));
    }

    /**
     * Record the decay anchor on disconnect. Gated on the config toggle so a
     * server that never enables the feature never grows the new NBT field.
     */
    public static void stampLastSeen(ServerPlayer player, long nowEpochMs) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.levelDecay.enabled) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        try {
            PlayerDifficultyState.getOrCreate(server).setLastSeen(player.getUUID(), nowEpochMs);
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to stamp level-decay anchor for {}", player, e);
        }
    }

    /**
     * Compute and apply the offline decay for a joining player, then re-anchor
     * the timestamp to now (consuming the absence — a re-login a minute later
     * decays nothing extra). Notifies the player and fires
     * {@link TribulationLevelCallback} when the level actually dropped.
     */
    public static void applyOfflineDecay(ServerPlayer player, long nowEpochMs) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.levelDecay.enabled) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            long lastSeen = state.getLastSeen(player.getUUID());
            state.setLastSeen(player.getUUID(), nowEpochMs);
            if (lastSeen == PlayerDifficultyState.NEVER_SEEN) {
                return;
            }

            // A negative elapsed time (host clock stepped backwards while the
            // player was offline) decays nothing; the anchor re-stamp above
            // already bounds the skew to this one absence.
            long elapsedMs = nowEpochMs - lastSeen;
            int decay = computeDecayLevels(elapsedMs, cfg.levelDecay.graceDays, cfg.levelDecay.levelsPerDay);
            if (decay <= 0) {
                return;
            }

            // Decay only ever pulls a level DOWN. A player already at or below
            // the floor (fresh player, or lowered via /tribulation set) must
            // not be lifted up to it by the floor clamp inside reducePlayerLevel.
            int before = state.getLevel(player.getUUID());
            if (before <= cfg.levelDecay.floor) {
                return;
            }
            int after = state.reducePlayerLevel(player.getUUID(), decay, cfg.levelDecay.floor);
            TribulationNetworking.syncLevel(player);
            if (before != after) {
                player.awardStat(TribulationStats.LEVELS_LOST_TO_DECAY, before - after);
                TribulationLevelCallback.EVENT.invoker().onLevelChanged(player, before, after);
                String daysAway = String.format(Locale.ROOT, "%.1f", elapsedMs / (double) DAY_MS);
                player.sendSystemMessage(Component.translatable(
                                "message.tribulation.level_decay", daysAway, before, after)
                        .withStyle(ChatFormatting.GREEN));
                Tribulation.LOGGER.debug(
                        "Offline decay: {} away {} days, reduced from level {} to {}",
                        player.getGameProfile().getName(), daysAway, before, after);
            }
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply offline level decay for {}", player, e);
        }
    }

    /**
     * Levels shed for an absence of {@code elapsedMs}: zero within the grace
     * window, then {@code levelsPerDay} per day beyond it, floored to whole
     * levels (a partial day's remainder is dropped, not banked). Pure math —
     * covered by unit tests. Non-positive elapsed time (clock skew) and
     * non-positive rates decay nothing; a pathological product saturates at
     * {@link Integer#MAX_VALUE} instead of overflowing.
     */
    public static int computeDecayLevels(long elapsedMs, double graceDays, double levelsPerDay) {
        if (elapsedMs <= 0 || levelsPerDay <= 0) return 0;
        double decayableDays = elapsedMs / (double) DAY_MS - Math.max(0.0, graceDays);
        if (decayableDays <= 0) return 0;
        double levels = Math.floor(levelsPerDay * decayableDays);
        return levels >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) levels;
    }
}
