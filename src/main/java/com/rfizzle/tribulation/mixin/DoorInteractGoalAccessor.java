package com.rfizzle.tribulation.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected {@code mob} field that {@link DoorInteractGoal} (the
 * superclass of {@code BreakDoorGoal}) owns, so {@link BreakDoorGoalMixin} can
 * read the door-breaker's tags from outside the goal package.
 */
@Mixin(DoorInteractGoal.class)
public interface DoorInteractGoalAccessor {

    @Accessor("mob")
    Mob tribulation$getMob();
}
