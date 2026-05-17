package com.rfizzle.tribulation.compat.wthit;

import net.minecraft.world.entity.Mob;

import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IWailaClientPlugin;

public final class WthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(MobScalingWthitProvider.INSTANCE, Mob.class);
    }
}
