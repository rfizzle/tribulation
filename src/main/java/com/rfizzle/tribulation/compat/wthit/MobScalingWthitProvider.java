package com.rfizzle.tribulation.compat.wthit;

import com.rfizzle.tribulation.compat.common.MobScalingDataCollector;
import com.rfizzle.tribulation.compat.common.TribulationTooltipFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;

final class MobScalingWthitProvider
        implements IDataProvider<Mob>, IEntityComponentProvider {

    static final MobScalingWthitProvider INSTANCE = new MobScalingWthitProvider();

    private MobScalingWthitProvider() {}

    @Override
    public void appendData(IDataWriter data, IServerAccessor<Mob> accessor, IPluginConfig config) {
        MobScalingDataCollector.collect(accessor.getTarget(), data.raw());
    }

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getData().raw();
        for (String line : TribulationTooltipFormatter.format(data)) {
            tooltip.addLine(Component.literal(line));
        }
    }
}
