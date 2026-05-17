package com.rfizzle.tribulation.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class TribulationNetworking {
    private TribulationNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TribulationLevelPayload.TYPE, TribulationLevelPayload.STREAM_CODEC);
    }

    public static void syncLevel(ServerPlayer player, int level) {
        ServerPlayNetworking.send(player, new TribulationLevelPayload(level));
    }
}
