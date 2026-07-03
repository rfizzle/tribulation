package com.rfizzle.tribulation.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalMobCapCalculator.class)
public interface LocalMobCapCalculatorAccessor {
    @Accessor("chunkMap")
    ChunkMap tribulation$getChunkMap();
}
