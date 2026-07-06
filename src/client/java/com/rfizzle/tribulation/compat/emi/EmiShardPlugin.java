package com.rfizzle.tribulation.compat.emi;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.client.ClientConfigState;
import com.rfizzle.tribulation.compat.common.AscendantInfoFormatter;
import com.rfizzle.tribulation.compat.common.HeartFragmentInfoFormatter;
import com.rfizzle.tribulation.compat.common.ShardInfoFormatter;
import com.rfizzle.tribulation.item.TribulationItems;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;

import java.util.List;

public final class EmiShardPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TribulationItems.SHATTER_SHARD)),
                ShardInfoFormatter.infoLines(ClientConfigState.effective()),
                Tribulation.id("/shard_info")));

        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TribulationItems.ASCENDANT_SHARD)),
                AscendantInfoFormatter.infoLines(ClientConfigState.effective()),
                Tribulation.id("/ascendant_shard_info")));

        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TribulationItems.HEART_FRAGMENT)),
                HeartFragmentInfoFormatter.infoLines(ClientConfigState.effective()),
                Tribulation.id("/heart_fragment_info")));
    }
}
