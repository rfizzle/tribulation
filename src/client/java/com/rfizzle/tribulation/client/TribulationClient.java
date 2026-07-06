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
     * Hold-to-peek keybind for the tier detail panel. Bound to Tab by default —
     * the panel behaves like vanilla's hold-Tab player list (it never captures
     * the mouse or opens a {@link net.minecraft.client.gui.screens.Screen}), so
     * a shared default makes the mod's richest explainer discoverable without a
     * Controls-menu visit. A player who has already assigned their own key keeps
     * it: Fabric only applies the registered default for a binding absent from
     * {@code options.txt}.
     */
    public static KeyMapping KEY_TIER_DETAIL;

    @Override
    public void onInitializeClient() {
        KEY_TIER_DETAIL = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.tribulation.tier_detail",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_TAB,
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
