package com.rfizzle.tribulation.compat.jade;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.compat.common.MobScalingDataCollector;
import com.rfizzle.tribulation.compat.common.TribulationTooltipFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

final class MobScalingJadeProvider
        implements IServerDataProvider<EntityAccessor>, IEntityComponentProvider {

    static final MobScalingJadeProvider INSTANCE = new MobScalingJadeProvider();

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Tribulation.MOD_ID, "mob_scaling");

    private MobScalingJadeProvider() {}

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (accessor.getEntity() instanceof Mob mob) {
            MobScalingDataCollector.collect(mob, data);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        for (String line : TribulationTooltipFormatter.format(data)) {
            tooltip.add(Component.literal(line));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
