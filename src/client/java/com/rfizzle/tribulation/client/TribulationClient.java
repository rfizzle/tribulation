package com.rfizzle.tribulation.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rfizzle.tribulation.compat.common.TooltipDetailProvider;
import com.rfizzle.tribulation.particle.TribulationParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public class TribulationClient implements ClientModInitializer {
    /**
     * Hold-to-peek keybind for the tier detail panel. Defaults to Left Alt per
     * the Concord HUD Standard §8 — unused by vanilla and ergonomic to hold. It
     * is deliberately not Tab: Tab holds the vanilla player list, the exact
     * interaction this panel imitates, so the two would conflict. A player who
     * has already assigned their own key keeps it: Fabric only applies the
     * registered default for a binding absent from {@code options.txt}.
     */
    public static KeyMapping KEY_PEEK_DETAIL;

    @Override
    public void onInitializeClient() {
        KEY_PEEK_DETAIL = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.tribulation.peek_detail",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                "key.categories.tribulation"));

        ClientNetworkHandler.register();

        // Feed the client-only tooltip inputs into the shared item classes.
        TooltipDetailProvider.setShiftHeld(Screen::hasShiftDown);
        TooltipDetailProvider.setEffectiveConfig(ClientConfigState::effective);

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
