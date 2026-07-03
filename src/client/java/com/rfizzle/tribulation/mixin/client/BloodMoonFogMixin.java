package com.rfizzle.tribulation.mixin.client;

import com.rfizzle.tribulation.client.BloodMoonClientEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tints the atmosphere fog toward blood red while a Blood Moon is active.
 * The injection point is the final {@code clearColor} call (ordinal 1 — the
 * unconditional method tail; water/lava branches fall through to it), so the
 * handler itself checks the camera fluid and leaves submerged fog vanilla.
 */
@Mixin(FogRenderer.class)
public abstract class BloodMoonFogMixin {

    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(
            method = "setupColor",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V",
                    ordinal = 1))
    private static void tribulation$tintFog(Camera camera, float partialTick, ClientLevel level,
                                            int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
        if (camera.getFluidInCamera() != FogType.NONE) return;
        if (!BloodMoonClientEffects.isTintActive(level)) return;
        float[] tinted = BloodMoonClientEffects.tintFog(fogRed, fogGreen, fogBlue);
        fogRed = tinted[0];
        fogGreen = tinted[1];
        fogBlue = tinted[2];
    }
}
