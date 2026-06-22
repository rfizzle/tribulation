package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.ability.AbilityManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Speeds up a {@link Guardian}'s beam attack when it carries
 * {@link AbilityManager#TAG_GUARDIAN_BEAM} (tier 3+). {@code getAttackDuration}
 * is the tick threshold the attack goal counts up to before firing; halving it
 * (floored at 20 ticks) makes the beam charge and fire in roughly half the time
 * while leaving the charge-up visuals intact.
 */
@Mixin(Guardian.class)
public abstract class GuardianBeamMixin {

    private static final int MIN_ATTACK_DURATION = 20;

    @Inject(method = "getAttackDuration", at = @At("RETURN"), cancellable = true)
    private void tribulation$fasterBeam(CallbackInfoReturnable<Integer> cir) {
        Mob self = (Mob) (Object) this;
        if (!self.getTags().contains(AbilityManager.TAG_GUARDIAN_BEAM)) return;
        cir.setReturnValue(Math.max(MIN_ATTACK_DURATION, cir.getReturnValue() / 2));
    }
}
