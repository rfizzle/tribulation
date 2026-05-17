package com.rfizzle.tribulation.compat.emi;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.compat.common.ShardInfoFormatter;
import com.rfizzle.tribulation.item.TribulationItems;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class EmiShardPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        List<Component> text = ShardInfoFormatter.infoLines().stream()
                .map(Component::literal)
                .map(c -> (Component) c)
                .toList();

        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TribulationItems.SHATTER_SHARD)),
                text,
                ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "/shard_info")));
    }
}
