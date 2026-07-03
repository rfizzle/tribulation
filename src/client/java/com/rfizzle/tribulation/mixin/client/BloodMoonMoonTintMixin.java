package com.rfizzle.tribulation.mixin.client;

import com.rfizzle.tribulation.client.BloodMoonClientEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Tints the celestial bodies red while a Blood Moon is active. Targets the
 * shader color set right before the sun/moon quads are drawn — the first
 * {@code setShaderColor} after the rain-alpha lookup — so the sunrise glow
 * and star passes keep their vanilla colors. The alpha (rain fade) is left
 * untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class BloodMoonMoonTintMixin {

    @Shadow private ClientLevel level;

    @ModifyArgs(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",
                    ordinal = 0),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F")))
    private void tribulation$tintMoon(Args args) {
        if (!BloodMoonClientEffects.isTintActive(this.level)) return;
        args.set(1, BloodMoonClientEffects.tintMoonGreen(args.get(1)));
        args.set(2, BloodMoonClientEffects.tintMoonBlue(args.get(2)));
    }
}
