package com.rfizzle.tribulation.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public interface MobCountsAccessor {
    @Accessor("counts")
    Object2IntMap<MobCategory> tribulation$getCounts();
}
