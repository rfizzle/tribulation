package com.rfizzle.tribulation.network;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.event.EnvironmentalPressureHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class TribulationNetworking {
    private TribulationNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TribulationLevelPayload.TYPE, TribulationLevelPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BloodMoonPayload.TYPE, BloodMoonPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(EnvironmentalPressurePayload.TYPE, EnvironmentalPressurePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC);
    }

    /**
     * Sends the server's active config to one player. Called on join and after a
     * config reload so client-side readers resolve server-accurate values.
     */
    public static void syncConfig(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) {
            return;
        }
        ServerPlayNetworking.send(player, new ConfigSyncPayload(cfg.toJson()));
    }

    /** Re-broadcasts the active config to every connected player after a reload. */
    public static void broadcastConfig(MinecraftServer server) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) {
            return;
        }
        ConfigSyncPayload payload = new ConfigSyncPayload(cfg.toJson());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void syncBloodMoon(ServerPlayer player, boolean active) {
        ServerPlayNetworking.send(player, new BloodMoonPayload(active));
    }

    public static void syncLevel(ServerPlayer player) {
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(player.server);
        TribulationConfig cfg = Tribulation.getConfig();
        int level = state.getLevel(player.getUUID());
        int progressTicks = state.getTickCounter(player.getUUID());
        int goalTicks = cfg != null ? Math.max(1, cfg.general.levelUpTicks) : 1;
        ServerPlayNetworking.send(player, new TribulationLevelPayload(level, progressTicks, goalTicks));
        // Environmental pressure tracks the player's tier, so every path that
        // syncs the level also re-evaluates it; sends only on change.
        EnvironmentalPressureHandler.syncNightPressure(player, cfg, level);
    }
}
