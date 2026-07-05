package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.event.SoulInventoryHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Voids non-soulbound items on player death. Targets {@link LivingEntity} — not
 * {@link ServerPlayer} — because {@code dropAllDeathLoot} is declared only on
 * {@code LivingEntity} and never overridden by {@code Player}/{@code ServerPlayer};
 * a Mixin {@code @Inject} can only bind a method the target class itself declares,
 * so the {@code instanceof ServerPlayer} guard is required, not the broad target
 * a naive reading of the mc-mixin-craft narrowing rule might suggest.
 */
@Mixin(LivingEntity.class)
public abstract class SoulInventoryMixin {

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void tribulation$voidNonSoulbound(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        SoulInventoryHandler.processDeathInventory(player);
    }
}
