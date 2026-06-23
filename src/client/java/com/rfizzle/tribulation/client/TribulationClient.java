package com.rfizzle.tribulation.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rfizzle.tribulation.particle.TribulationParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class TribulationClient implements ClientModInitializer {
    /**
     * Hold-to-peek keybind for the tier detail panel. Unbound by default
     * ({@link GLFW#GLFW_KEY_UNKNOWN}) so it never collides with another mod's
     * binding — the player assigns it under Controls → Tribulation.
     */
    public static KeyMapping KEY_TIER_DETAIL;

    @Override
    public void onInitializeClient() {
        KEY_TIER_DETAIL = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.tribulation.tier_detail",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.tribulation"));

        ClientNetworkHandler.register();
        HudRenderCallback.EVENT.register(new TribulationHudOverlay());
        HudRenderCallback.EVENT.register(new TierDetailPanelRenderer());
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
