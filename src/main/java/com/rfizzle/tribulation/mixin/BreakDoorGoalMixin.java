package com.rfizzle.tribulation.mixin;

import com.rfizzle.tribulation.ability.AbilityManager;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Relaxes the difficulty gate on {@link BreakDoorGoal} for mobs carrying
 * {@link AbilityManager#TAG_DOOR_BREAKING}. Vanilla vindicators only break doors
 * on Hard (their goal's {@code DOOR_BREAKING_PREDICATE}); the tier-3 ability
 * reuses that same goal but makes it valid on every difficulty — mirroring the
 * zombie tier-3 door-breaking beat. Untagged mobs keep vanilla behavior.
 */
@Mixin(BreakDoorGoal.class)
public abstract class BreakDoorGoalMixin {

    @Inject(method = "isValidDifficulty", at = @At("HEAD"), cancellable = true)
    private void tribulation$alwaysBreakWhenTagged(Difficulty difficulty, CallbackInfoReturnable<Boolean> cir) {
        Mob mob = ((DoorInteractGoalAccessor) this).tribulation$getMob();
        if (mob != null && mob.getTags().contains(AbilityManager.TAG_DOOR_BREAKING)) {
            cir.setReturnValue(true);
        }
    }
}
