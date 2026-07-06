package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.compat.common.ShardViewerRefresh;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.network.BloodMoonPayload;
import com.rfizzle.tribulation.network.ConfigSyncPayload;
import com.rfizzle.tribulation.network.EnvironmentalPressurePayload;
import com.rfizzle.tribulation.network.TribulationLevelPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TribulationLevelPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientTribulationState.setLevel(payload.level());
                ClientTribulationState.setProgress(payload.progressTicks(), payload.goalTicks());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BloodMoonPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientTribulationState.setBloodMoonActive(payload.active()));
        });
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) -> {
            TribulationConfig synced = TribulationConfig.fromJson(payload.json());
            context.client().execute(() -> {
                ClientConfigState.setServerConfig(synced);
                // Re-drive any viewer that can refresh live so an already-open
                // Shatter Shard info panel reflects the new tuning after a
                // /tribulation reload (EMI); JEI/REI pick it up on the next
                // (re)build. Safe on the client thread — we are inside execute.
                ShardViewerRefresh.refreshViewers();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(EnvironmentalPressurePayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientTribulationState.setOppressiveNightDarkness(payload.nightDarkness()));
        });
    }
}
