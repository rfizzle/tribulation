package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.event.XpLootHandler;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplies dropped XP for fizzle-scaled mobs. Hooks
 * {@code LivingEntity#getExperienceReward} rather than {@code dropExperience}
 * so the scaling survives any downstream looting-enchant modifier that reads
 * the returned value — and so {@code /xp query} on a killed mob shows the
 * scaled total rather than the vanilla base.
 *
 * <p>Guard is strict: only {@link Mob} instances are multiplied (players drop
 * via the same method but must not be scaled), and mobs without any
 * tribulation health modifier fall through unchanged.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityExperienceMixin {

    @Inject(
            method = "getExperienceReward(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private void tribulation$scaleExperienceReward(
            ServerLevel world,
            Entity attacker,
            CallbackInfoReturnable<Integer> cir
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Mob mob)) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || cfg.xpAndLoot == null || !cfg.xpAndLoot.extraXp) return;

        int base = cir.getReturnValueI();
        if (base <= 0) return;

        double factor = ScalingEngine.readHealthScalingFactor(mob);
        if (factor <= 0) return;

        int scaled = XpLootHandler.applyXpMultiplier(base, factor, cfg.xpAndLoot);
        if (scaled != base) {
            cir.setReturnValue(scaled);
        }
    }
}
