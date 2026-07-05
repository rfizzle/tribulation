package com.rfizzle.tribulation.mixin.client;

import com.rfizzle.tribulation.client.EnvironmentalPressureClientEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the Oppressive Nights ambient darkening to the lightmap. Raising the
 * return value of the vanilla darkness-effect scale subtracts the extra
 * darkness from every lightmap texel exactly like the Darkness effect does,
 * so it composes with sky/block light, night vision, and gamma. The call site
 * multiplies the result by the vanilla "Darkness Pulsing" accessibility
 * slider, so that existing accessibility control bounds this effect too.
 */
@Mixin(LightTexture.class)
public abstract class OppressiveNightLightMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "calculateDarknessScale", at = @At("RETURN"), cancellable = true)
    private void tribulation$addNightPressure(LivingEntity entity, float gamma, float partialTick,
                                              CallbackInfoReturnable<Float> cir) {
        float extra = EnvironmentalPressureClientEffects.nightDarkness(this.minecraft.level, partialTick);
        if (extra > 0f) {
            cir.setReturnValue(cir.getReturnValue() + extra);
        }
    }
}
