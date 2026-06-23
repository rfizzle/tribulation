package com.rfizzle.tribulation.item;

import com.rfizzle.tribulation.Tribulation;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Registry-time item declarations for Tribulation. Must be loaded and
 * {@link #register()} invoked during {@code onInitialize} so entries land in
 * the item registry before it freezes.
 */
public final class TribulationItems {
    public static final String SHATTER_SHARD_PATH = "shatter_shard";
    public static final String HEART_FRAGMENT_PATH = "heart_fragment";
    public static final String CREATIVE_TAB_PATH = "main";

    public static final ResourceLocation SHATTER_SHARD_ID =
            Tribulation.id(SHATTER_SHARD_PATH);
    public static final ResourceLocation HEART_FRAGMENT_ID =
            Tribulation.id(HEART_FRAGMENT_PATH);
    public static final ResourceLocation CREATIVE_TAB_ID =
            Tribulation.id(CREATIVE_TAB_PATH);

    public static final Item SHATTER_SHARD = new ShatterShardItem(
            new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.UNCOMMON)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
    );

    public static final Item HEART_FRAGMENT = new HeartFragmentItem(
            new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.RARE)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
    );

    private static boolean registered = false;

    private TribulationItems() {}

    public static void register() {
        if (registered) return;
        registered = true;

        Registry.register(BuiltInRegistries.ITEM, SHATTER_SHARD_ID, SHATTER_SHARD);
        Registry.register(BuiltInRegistries.ITEM, HEART_FRAGMENT_ID, HEART_FRAGMENT);

        CreativeModeTab tab = FabricItemGroup.builder()
                .icon(() -> new ItemStack(SHATTER_SHARD))
                .title(Component.translatable("itemGroup.tribulation.main"))
                .displayItems((params, entries) -> {
                    entries.accept(SHATTER_SHARD);
                    entries.accept(HEART_FRAGMENT);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CREATIVE_TAB_ID, tab);
    }
}
