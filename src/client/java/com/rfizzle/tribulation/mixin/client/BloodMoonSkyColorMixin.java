package com.rfizzle.tribulation.mixin.client;

import com.rfizzle.tribulation.client.BloodMoonClientEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pulls the computed sky color toward blood red while a Blood Moon is active. */
@Mixin(ClientLevel.class)
public abstract class BloodMoonSkyColorMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void tribulation$tintSkyColor(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (!BloodMoonClientEffects.isTintActive(self)) return;
        cir.setReturnValue(BloodMoonClientEffects.tintSky(cir.getReturnValue()));
    }
}
