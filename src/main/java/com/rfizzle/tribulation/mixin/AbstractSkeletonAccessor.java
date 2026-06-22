package com.rfizzle.tribulation.mixin;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to the private {@code bowGoal} field on {@link AbstractSkeleton}
 * so {@link AbstractSkeletonMixin} can override its attack interval. Follows the
 * {@code CreeperAccessor} precedent (accessor interface, no {@code @Shadow}).
 */
@Mixin(AbstractSkeleton.class)
public interface AbstractSkeletonAccessor {

    @Accessor("bowGoal")
    RangedBowAttackGoal<AbstractSkeleton> tribulation$getBowGoal();
}
