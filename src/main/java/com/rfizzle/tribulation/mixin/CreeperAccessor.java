package com.rfizzle.tribulation.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccessor {

    @Accessor("maxSwell")
    void tribulation$setMaxSwell(int maxSwell);

    @Accessor("DATA_IS_POWERED")
    static EntityDataAccessor<Boolean> tribulation$getDataIsPowered() {
        throw new UnsupportedOperationException();
    }
}
