package com.rfizzle.tribulation.api;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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
}
