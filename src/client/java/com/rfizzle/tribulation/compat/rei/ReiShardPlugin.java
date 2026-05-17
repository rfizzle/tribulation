package com.rfizzle.tribulation.compat.rei;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.compat.common.ShardInfoFormatter;
import com.rfizzle.tribulation.item.TribulationItems;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.network.chat.Component;

public final class ReiShardPlugin implements REIClientPlugin {

    @Override
    public String getPluginProviderName() {
        return Tribulation.MOD_ID;
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        DefaultInformationDisplay info = DefaultInformationDisplay.createFromEntry(
                EntryStacks.of(TribulationItems.SHATTER_SHARD),
                Component.translatable("item.tribulation.shatter_shard"));
        for (String line : ShardInfoFormatter.infoLines()) {
            info.line(Component.literal(line));
        }
        registry.add(info);
    }
}
