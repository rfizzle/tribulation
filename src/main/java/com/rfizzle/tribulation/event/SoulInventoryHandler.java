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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SoulInventoryHandler {

    /**
     * Convention tag shared across Concord mods for keep-on-death enchantments
     * (e.g. {@code meridian:tether}). Any enchant in this tag qualifies an item
     * for the soul inventory, in addition to the configured enchantment id.
     */
    public static final TagKey<Enchantment> SOULBOUND_ENCHANTMENTS =
            TagKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath("c", "soulbound"));

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

            if (isSoulbound(stack, soulboundHolder)) {
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

        // ≥1 soulbound item carried through death — the Soulbound milestone.
        com.rfizzle.tribulation.advancement.TribulationCriteria.SOULBOUND_SURVIVED.trigger(newPlayer);
    }

    static Optional<Holder.Reference<Enchantment>> resolveSoulboundEnchantment(ServerPlayer player, TribulationConfig cfg) {
        String enchantId = cfg.soulInventory.soulboundEnchantment;
        if (enchantId == null || enchantId.isBlank()) {
            // Blank is a supported tag-only mode: items qualify via #c:soulbound alone.
            return Optional.empty();
        }

        ResourceLocation rl = ResourceLocation.tryParse(enchantId);
        if (rl == null) {
            Tribulation.LOGGER.warn("Invalid soulboundEnchantment ID '{}'; only #c:soulbound enchants will be kept", enchantId);
            return Optional.empty();
        }

        Registry<Enchantment> registry = player.server.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT);
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, rl);
        Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(key);

        if (holder.isEmpty()) {
            Tribulation.LOGGER.warn("Soulbound enchantment '{}' not found in registry; only #c:soulbound enchants will be kept", enchantId);
        }

        return holder;
    }

    /**
     * An item is soulbound if it carries the configured soulbound enchantment
     * or any enchantment in {@code #c:soulbound} (e.g. Meridian's Tether).
     * Reads the crafting view of the enchantments, so enchanted books (stored
     * enchantments) qualify too.
     */
    static boolean isSoulbound(ItemStack stack, Optional<Holder.Reference<Enchantment>> configured) {
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(SOULBOUND_ENCHANTMENTS)
                    || (configured.isPresent() && enchantment.is(configured.get().key()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Count soulbound items in a player's inventory. Used by the
     * {@code /tribulation inventory} command.
     */
    public static int countSoulboundItems(ServerPlayer player) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.soulInventory.enabled) return 0;

        Optional<Holder.Reference<Enchantment>> holder = resolveSoulboundEnchantment(player, cfg);

        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && isSoulbound(stack, holder)) {
                count++;
            }
        }
        return count;
    }

    record SlotEntry(int slot, ItemStack stack) {}
}
