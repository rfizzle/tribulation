package com.rfizzle.tribulation.compat.wthit;

import net.minecraft.world.entity.Mob;

import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;

public final class WthitCommonPlugin implements IWailaCommonPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.entityData(MobScalingWthitProvider.INSTANCE, Mob.class);
    }
}
