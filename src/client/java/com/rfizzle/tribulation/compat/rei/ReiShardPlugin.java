package com.rfizzle.tribulation.compat.rei;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.client.ClientConfigState;
import com.rfizzle.tribulation.compat.common.AscendantInfoFormatter;
import com.rfizzle.tribulation.compat.common.HeartFragmentInfoFormatter;
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
        DefaultInformationDisplay shard = DefaultInformationDisplay.createFromEntry(
                EntryStacks.of(TribulationItems.SHATTER_SHARD),
                Component.translatable("item.tribulation.shatter_shard"));
        for (Component line : ShardInfoFormatter.infoLines(ClientConfigState.effective())) {
            shard.line(line);
        }
        registry.add(shard);

        DefaultInformationDisplay ascendant = DefaultInformationDisplay.createFromEntry(
                EntryStacks.of(TribulationItems.ASCENDANT_SHARD),
                Component.translatable("item.tribulation.ascendant_shard"));
        for (Component line : AscendantInfoFormatter.infoLines(ClientConfigState.effective())) {
            ascendant.line(line);
        }
        registry.add(ascendant);

        DefaultInformationDisplay fragment = DefaultInformationDisplay.createFromEntry(
                EntryStacks.of(TribulationItems.HEART_FRAGMENT),
                Component.translatable("item.tribulation.heart_fragment"));
        for (Component line : HeartFragmentInfoFormatter.infoLines(ClientConfigState.effective())) {
            fragment.line(line);
        }
        registry.add(fragment);
    }
}
