package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.ability.AbilityManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Widens the {@link Ravager} roar's knockback radius when the ravager carries
 * {@link AbilityManager#TAG_RAVAGER_ROAR} (tier 3+). Vanilla {@code roar()}
 * gathers victims in an AABB inflated by {@code 4.0}; the tagged roar inflates
 * by an extra {@code 2.0}, sweeping a noticeably larger area.
 */
@Mixin(Ravager.class)
public abstract class RavagerRoarMixin {

    private static final double ROAR_RADIUS_BONUS = 2.0;

    @ModifyConstant(method = "roar", constant = @Constant(doubleValue = 4.0))
    private double tribulation$expandRoar(double original) {
        Mob self = (Mob) (Object) this;
        return self.getTags().contains(AbilityManager.TAG_RAVAGER_ROAR)
                ? original + ROAR_RADIUS_BONUS
                : original;
    }
}
