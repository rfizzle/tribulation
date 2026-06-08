package com.rfizzle.tribulation.api;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/**
 * Public API for Tribulation.
 * All methods are safe to call as a soft dependency.
 */
public final class TribulationAPI {

    private TribulationAPI() {}

    /**
     * Get the current Tribulation level of a player on the server.
     * Authoritative, server-side only.
     *
     * @param player the player
     * @return the player's level
     */
    public static int getLevel(ServerPlayer player) {
        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(player.server);
        return state.getLevel(player.getUUID());
    }

    /**
     * Get the current Tribulation tier (0-5) of a player on the server.
     * Authoritative, server-side only.
     *
     * @param player the player
     * @return the player's tier
     */
    public static int getTier(ServerPlayer player) {
        int level = getLevel(player);
        return TierManager.getTier(level, Tribulation.getConfig().tiers);
    }

    /**
     * Get the effective Tribulation level for an entity.
     * This is the level that would be used for scaling if the entity were a mob.
     * Server-side only.
     *
     * @param entity the entity
     * @return the effective level
     */
    public static int getEffectiveLevel(Entity entity) {
        if (entity.level() instanceof ServerLevel world) {
            return ScalingEngine.getEffectiveLevel(entity, world);
        }
        return 0;
    }

    /**
     * Get the local player's last-synced Tribulation level on the client.
     * Returns -1 if the level is unknown.
     * Safe to call on the client side only.
     *
     * <p>Implementation note: Uses reflection to access the client-side state
     * to avoid compile-time and runtime dependencies on client-only classes
     * from the server-side API surface.
     *
     * @return the client-side level
     */
    public static int getClientLevel() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            try {
                Class<?> clazz = Class.forName("com.rfizzle.tribulation.client.ClientTribulationState");
                return (int) clazz.getMethod("getLevel").invoke(null);
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Get the Tribulation tier the entity was scaled to at spawn.
     * Empty if the entity was never scaled.
     * Server-side only.
     *
     * @param entity the entity
     * @return the scaled tier
     */
    public static OptionalInt getScaledTier(Entity entity) {
        Integer tier = entity.getAttached(TribulationAttachments.SCALED_TIER);
        return tier != null ? OptionalInt.of(tier) : OptionalInt.empty();
    }

    /**
     * Check if an entity was scaled by Tribulation.
     * Server-side only.
     *
     * @param entity the entity
     * @return true if scaled
     */
    public static boolean wasScaledByTribulation(Entity entity) {
        return entity.hasAttached(TribulationAttachments.SCALED_TIER);
    }

    private static volatile ArmorDropChanceProvider armorDropChanceProvider = (mob, tier, slot, stack, defaultChance) -> defaultChance;

    /**
     * Set a provider to determine the drop chance of armor equipped by Tribulation.
     * Last writer wins. Safe to call as a soft dependency.
     *
     * @param provider the provider
     */
    public static void setArmorDropChanceProvider(ArmorDropChanceProvider provider) {
        if (provider != null) {
            armorDropChanceProvider = provider;
        }
    }

    /**
     * Internal use only. Resolves the drop chance for a piece of armor.
     * A misbehaving provider (throwing or returning a non-finite value) never
     * breaks mob spawning: it falls back to {@code defaultChance}.
     */
    public static float resolveArmorDropChance(Entity mob, int tier, EquipmentSlot slot, ItemStack stack, float defaultChance) {
        try {
            float resolved = armorDropChanceProvider.resolve(mob, tier, slot, stack, defaultChance);
            return Float.isFinite(resolved) ? resolved : defaultChance;
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Armor drop-chance provider threw; using default", e);
            return defaultChance;
        }
    }

    @FunctionalInterface
    public interface ArmorDropChanceProvider {
        /**
         * Resolve the drop chance for a specific armor piece.
         *
         * @param mob the mob being equipped
         * @param tier the mob's Tribulation tier
         * @param slot the equipment slot
         * @param stack the item stack being equipped
         * @param defaultChance the configured default drop chance
         * @return the drop chance to use (e.g., 0.085f, or 2.0f for guaranteed)
         */
        float resolve(Entity mob, int tier, EquipmentSlot slot, ItemStack stack, float defaultChance);
    }
}
