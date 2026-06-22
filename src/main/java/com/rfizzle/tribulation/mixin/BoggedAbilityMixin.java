package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.ability.AbilityManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Upgrades a {@link Bogged}'s arrow from vanilla Poison I to Poison II when the
 * bogged carries {@link AbilityManager#TAG_POISON_ARROWS} (tier 2+). Mirrors
 * {@link StrayAbilityMixin}: re-adds the effect at amplifier 1 with vanilla's
 * 100-tick duration, so the stronger amplifier wins on hit.
 */
@Mixin(Bogged.class)
public abstract class BoggedAbilityMixin {

    @Inject(method = "getArrow", at = @At("RETURN"))
    private void tribulation$upgradePoison(ItemStack weapon, float velocity, ItemStack ammo,
                                           CallbackInfoReturnable<AbstractArrow> cir) {
        Mob self = (Mob) (Object) this;
        if (!self.getTags().contains(AbilityManager.TAG_POISON_ARROWS)) return;
        if (!(cir.getReturnValue() instanceof Arrow arrow)) return;

        try {
            arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Error upgrading bogged poison arrow for {}", self, e);
        }
    }
}
