package com.rfizzle.tribulation.compat.jei;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.client.ClientConfigState;
import com.rfizzle.tribulation.compat.common.AscendantInfoFormatter;
import com.rfizzle.tribulation.compat.common.HeartFragmentInfoFormatter;
import com.rfizzle.tribulation.compat.common.ShardInfoFormatter;
import com.rfizzle.tribulation.item.TribulationItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class JeiShardPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(
            Tribulation.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                new ItemStack(TribulationItems.SHATTER_SHARD),
                ShardInfoFormatter.infoLines(ClientConfigState.effective()).toArray(Component[]::new));
        registration.addItemStackInfo(
                new ItemStack(TribulationItems.ASCENDANT_SHARD),
                AscendantInfoFormatter.infoLines(ClientConfigState.effective()).toArray(Component[]::new));
        registration.addItemStackInfo(
                new ItemStack(TribulationItems.HEART_FRAGMENT),
                HeartFragmentInfoFormatter.infoLines(ClientConfigState.effective()).toArray(Component[]::new));
    }
}
