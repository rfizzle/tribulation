package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.Tribulation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Upgrades the vanilla Husk Hunger I to Hunger II when the husk has been
 * tagged by {@link com.rfizzle.tribulation.ability.AbilityManager} at tier 4+.
 * Runs after vanilla's {@code doHurtTarget} has already applied Hunger I,
 * then overwrites with a higher amplifier at the same duration.
 */
@Mixin(Husk.class)
public abstract class HuskAbilityMixin {

    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void tribulation$upgradeHunger(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!(target instanceof LivingEntity living)) return;
        Mob self = (Mob) (Object) this;
        if (!self.getTags().contains("tribulation_hunger2")) return;

        try {
            MobEffectInstance existing = living.getEffect(MobEffects.HUNGER);
            if (existing != null) {
                living.addEffect(new MobEffectInstance(MobEffects.HUNGER,
                        existing.getDuration(), 1, false, existing.isVisible()), self);
            }
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Error upgrading husk hunger for {}", self, e);
        }
    }
}
