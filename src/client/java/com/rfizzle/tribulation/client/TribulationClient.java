package com.rfizzle.tribulation.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class TribulationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.register();
        HudRenderCallback.EVENT.register(new TribulationHudOverlay());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientTribulationState.reset());
    }
}
