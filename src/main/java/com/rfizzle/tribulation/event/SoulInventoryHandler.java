package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SoulInventoryHandler {

    private static final Map<UUID, List<SlotEntry>> SOULBOUND_STASH = new WeakHashMap<>();

    private SoulInventoryHandler() {}

    public static void register() {
        ServerPlayerEvents.COPY_FROM.register(SoulInventoryHandler::onCopyFrom);
    }

    /**
     * Called by the mixin before vanilla drops inventory on death.
     * Iterates all inventory slots: stashes soulbound items and clears
     * non-soulbound items (voiding them instead of dropping).
     */
    public static void processDeathInventory(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.soulInventory.enabled) return;

        if (cfg.soulInventory.respectKeepInventory
                && player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        Optional<Holder.Reference<Enchantment>> soulboundHolder = resolveSoulboundEnchantment(player, cfg);

        Inventory inv = player.getInventory();
        List<SlotEntry> stashed = new ArrayList<>();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (soulboundHolder.isPresent() && hasSoulbound(stack, soulboundHolder.get())) {
                stashed.add(new SlotEntry(i, stack.copy()));
            }
            inv.setItem(i, ItemStack.EMPTY);
        }

        if (!stashed.isEmpty()) {
            SOULBOUND_STASH.put(player.getUUID(), stashed);
        }

        if (cfg.soulInventory.destroyXp) {
            player.setExperiencePoints(0);
            player.setExperienceLevels(0);
            player.experienceProgress = 0.0f;
        }
    }

    static void onCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (alive) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.soulInventory.enabled) return;

        List<SlotEntry> stashed = SOULBOUND_STASH.remove(oldPlayer.getUUID());
        if (stashed == null || stashed.isEmpty()) return;

        Inventory inv = newPlayer.getInventory();
        for (SlotEntry entry : stashed) {
            if (entry.slot >= 0 && entry.slot < inv.getContainerSize()) {
                inv.setItem(entry.slot, entry.stack);
            }
        }
    }

    static Optional<Holder.Reference<Enchantment>> resolveSoulboundEnchantment(ServerPlayer player, TribulationConfig cfg) {
        String enchantId = cfg.soulInventory.soulboundEnchantment;
        if (enchantId == null || enchantId.isBlank()) {
            Tribulation.LOGGER.warn("soulInventory.soulboundEnchantment is empty; all items will be voided");
            return Optional.empty();
        }

        ResourceLocation rl = ResourceLocation.tryParse(enchantId);
        if (rl == null) {
            Tribulation.LOGGER.warn("Invalid soulboundEnchantment ID '{}'; all items will be voided", enchantId);
            return Optional.empty();
        }

        Registry<Enchantment> registry = player.server.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT);
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, rl);
        Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(key);

        if (holder.isEmpty()) {
            Tribulation.LOGGER.warn("Soulbound enchantment '{}' not found in registry; all items will be voided", enchantId);
        }

        return holder;
    }

    static boolean hasSoulbound(ItemStack stack, Holder<Enchantment> enchantment) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0;
    }

    /**
     * Count soulbound items in a player's inventory. Used by the
     * {@code /tribulation inventory} command.
     */
    public static int countSoulboundItems(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.soulInventory.enabled) return 0;

        Optional<Holder.Reference<Enchantment>> holder = resolveSoulboundEnchantment(player, cfg);
        if (holder.isEmpty()) return 0;

        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && hasSoulbound(stack, holder.get())) {
                count++;
            }
        }
        return count;
    }

    record SlotEntry(int slot, ItemStack stack) {}
}
