package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.event.TotemPenaltyHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void tribulation$onTotemProtection(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (Object) this instanceof ServerPlayer player) {
            TotemPenaltyHandler.onTotemUsed(player);
        }
    }
}
