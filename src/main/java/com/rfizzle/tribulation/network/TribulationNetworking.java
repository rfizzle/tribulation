package com.rfizzle.tribulation.network;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class TribulationNetworking {
    private TribulationNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TribulationLevelPayload.TYPE, TribulationLevelPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BloodMoonPayload.TYPE, BloodMoonPayload.STREAM_CODEC);
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
    }
}
