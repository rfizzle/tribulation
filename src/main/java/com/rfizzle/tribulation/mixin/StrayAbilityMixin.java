package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.ability.AbilityManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Upgrades a {@link Stray}'s arrow from vanilla Slowness I to Slowness II when
 * the stray carries {@link AbilityManager#TAG_SLOWNESS_ARROWS} (tier 2+). Runs
 * after vanilla's {@code getArrow} has already added Slowness I at the same
 * 600-tick duration, then re-adds the effect at amplifier 1 — the stronger
 * amplifier wins when the arrow applies its effects on hit.
 */
@Mixin(Stray.class)
public abstract class StrayAbilityMixin {

    @Inject(method = "getArrow", at = @At("RETURN"))
    private void tribulation$upgradeSlowness(ItemStack weapon, float velocity, ItemStack ammo,
                                             CallbackInfoReturnable<AbstractArrow> cir) {
        Mob self = (Mob) (Object) this;
        if (!self.getTags().contains(AbilityManager.TAG_SLOWNESS_ARROWS)) return;
        if (!(cir.getReturnValue() instanceof Arrow arrow)) return;

        try {
            arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1));
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Error upgrading stray slowness arrow for {}", self, e);
        }
    }
}
