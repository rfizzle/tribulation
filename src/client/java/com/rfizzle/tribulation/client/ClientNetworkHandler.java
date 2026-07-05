package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.network.BloodMoonPayload;
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
        ClientPlayNetworking.registerGlobalReceiver(EnvironmentalPressurePayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ClientTribulationState.setOppressiveNightDarkness(payload.nightDarkness()));
        });
    }
}
