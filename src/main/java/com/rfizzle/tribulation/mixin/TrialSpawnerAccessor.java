package com.rfizzle.tribulation.mixin;

import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TrialSpawner.class)
public interface TrialSpawnerAccessor {
    @Accessor("data")
    TrialSpawnerData getData();
}
