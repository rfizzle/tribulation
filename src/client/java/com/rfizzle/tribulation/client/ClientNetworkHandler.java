package com.rfizzle.tribulation.client;

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
    }
}
