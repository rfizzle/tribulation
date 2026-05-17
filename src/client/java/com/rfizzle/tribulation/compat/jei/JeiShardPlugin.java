package com.rfizzle.tribulation.compat.jei;

import com.rfizzle.tribulation.Tribulation;
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
        Component[] lines = ShardInfoFormatter.infoLines().stream()
                .map(Component::literal)
                .toArray(Component[]::new);
        registration.addItemStackInfo(new ItemStack(TribulationItems.SHATTER_SHARD), lines);
    }
}
