package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.particle.TribulationParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class TribulationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.register();
        HudRenderCallback.EVENT.register(new TribulationHudOverlay());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientTribulationState.reset());

        registerThreatParticles();
        TribulationParticleEmitter.register();
    }

    private static void registerThreatParticles() {
        ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
        registry.register(TribulationParticles.THREAT_TIER,
                sprites -> new ThreatParticle.Provider(sprites, ThreatParticle.Style.TIER));
        registry.register(TribulationParticles.THREAT_BIG,
                sprites -> new ThreatParticle.Provider(sprites, ThreatParticle.Style.BIG));
        registry.register(TribulationParticles.THREAT_SPEED,
                sprites -> new ThreatParticle.Provider(sprites, ThreatParticle.Style.SPEED));
    }
}
