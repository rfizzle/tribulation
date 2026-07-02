package com.rfizzle.tribulation.api;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.scaling.BossScalingEngine;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Public API for Tribulation.
 * All methods are safe to call as a soft dependency.
 */
@Stable
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
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return -1;
        }
        MethodHandle handle = CLIENT_LEVEL.resolve();
        if (handle == null) {
            return -1;
        }
        try {
            return (int) handle.invokeExact();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Whether Tribulation's HUD element is currently visible on this client.
     * Returns {@code false} when called server-side, when the HUD is disabled
     * in config, or when any of the standard visibility rules currently hide
     * it (F1/hideGui, an open screen, spectator mode, the death screen).
     * Safe to call unconditionally from common code on either side.
     *
     * <p>Sibling mods use this with {@link #getHudHeight()} to stack their
     * own HUD elements below Tribulation's slot without hardcoding its
     * height (Concord HUD Standard coordination accessors).
     *
     * <p>Implementation note: Uses reflection to access the client-side
     * overlay to avoid compile-time and runtime dependencies on client-only
     * classes from the server-side API surface — same pattern as
     * {@link #getClientLevel()}.
     *
     * @return true if the HUD element is being drawn right now
     */
    public static boolean isHudVisible() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return false;
        }
        MethodHandle handle = HUD_VISIBLE.resolve();
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Get the height contribution of Tribulation's HUD element in pixels —
     * the standard 20px element plus the 2px stack gap (22) when visible,
     * {@code 0} when hidden or when called server-side. Sibling mods sum
     * this over higher-priority HUD slots each render pass to compute their
     * own stacking offset. Safe to call unconditionally from common code.
     *
     * <p>Implementation note: reflection-backed, same pattern as
     * {@link #getClientLevel()}.
     *
     * @return the element's stacking contribution in px, or 0 if not visible
     */
    public static int getHudHeight() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return 0;
        }
        MethodHandle handle = HUD_HEIGHT.resolve();
        if (handle == null) {
            return 0;
        }
        try {
            return (int) handle.invokeExact();
        } catch (Throwable t) {
            return 0;
        }
    }

    // Reflection-backed bridges to client-only state. The server-side API
    // surface must not reference client classes directly, so each accessor
    // resolves its target method once into a cached MethodHandle (the
    // isHudVisible/getHudHeight pair is called per-render by sibling mods, so
    // an uncached Class.forName + getMethod per call would be wasteful). The
    // first resolution failure is logged; thereafter the sentinel is returned
    // silently.
    private static final ClientAccessor CLIENT_LEVEL = new ClientAccessor(
            "com.rfizzle.tribulation.client.ClientTribulationState", "getLevel", int.class);
    private static final ClientAccessor HUD_VISIBLE = new ClientAccessor(
            "com.rfizzle.tribulation.client.TribulationHudOverlay", "isHudVisible", boolean.class);
    private static final ClientAccessor HUD_HEIGHT = new ClientAccessor(
            "com.rfizzle.tribulation.client.TribulationHudOverlay", "getHudHeightContribution", int.class);

    private static final class ClientAccessor {
        private final String className;
        private final String methodName;
        private final Class<?> returnType;
        private final AtomicBoolean logged = new AtomicBoolean(false);
        private volatile boolean resolved;
        private volatile MethodHandle handle;

        ClientAccessor(String className, String methodName, Class<?> returnType) {
            this.className = className;
            this.methodName = methodName;
            this.returnType = returnType;
        }

        /**
         * Resolve the target static method once into a cached MethodHandle,
         * or {@code null} if the client class/method is unavailable. The first
         * failure is logged; the result is memoized either way so callers on
         * the per-render hot path never re-pay reflection cost.
         */
        MethodHandle resolve() {
            if (resolved) {
                return handle;
            }
            synchronized (this) {
                if (resolved) {
                    return handle;
                }
                MethodHandle resolvedHandle = null;
                try {
                    Class<?> clazz = Class.forName(className);
                    resolvedHandle = MethodHandles.publicLookup()
                            .findStatic(clazz, methodName, MethodType.methodType(returnType));
                } catch (Throwable t) {
                    if (logged.compareAndSet(false, true)) {
                        Tribulation.LOGGER.warn("Tribulation client accessor {}.{} unavailable; returning sentinel",
                                className, methodName, t);
                    }
                }
                handle = resolvedHandle;
                resolved = true;
                return handle;
            }
        }
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

    /**
     * Check if an entity received boss-formula scaling (uniform health+damage
     * rates over the time and distance axes) rather than the normal per-mob
     * formula. Detected via the boss-axis attribute modifiers Tribulation
     * attaches, so it is distinguishable from normal scaling and survives
     * chunk reload. Server-side only.
     *
     * @param entity the entity
     * @return true if the entity carries boss-formula scaling
     */
    public static boolean isBossScaled(Entity entity) {
        return entity instanceof Mob mob && BossScalingEngine.hasAnyModifier(mob);
    }

    /**
     * Get the configured level thresholds at which tiers 1-5 begin, in
     * ascending tier order ({@code [tier1, tier2, tier3, tier4, tier5]};
     * defaults 50/100/150/200/250). The threshold check is inclusive: a
     * player at exactly the tier-1 threshold is tier 1. Consumers should
     * read these instead of hardcoding the defaults — they are config-driven.
     *
     * @return a fresh array of the five tier thresholds
     */
    public static int[] getTierThresholds() {
        TribulationConfig cfg = Tribulation.getConfig();
        TribulationConfig.Tiers tiers = cfg != null && cfg.tiers != null
                ? cfg.tiers
                : new TribulationConfig.Tiers();
        return new int[]{tiers.tier1, tiers.tier2, tiers.tier3, tiers.tier4, tiers.tier5};
    }

    /**
     * Whether Tribulation's soul-inventory is enabled and owns keep-on-death
     * handling for enchants in {@code #c:soulbound}. Sibling mods with their
     * own keep-on-death mechanic (e.g. Meridian's Tether) probe this to stand
     * down when {@code true}, so exactly one mod captures a given death.
     * Config is hot-reloadable — re-query per death rather than caching.
     *
     * @return {@code true} when the soul-inventory feature is enabled
     */
    public static boolean isSoulInventoryActive() {
        TribulationConfig cfg = Tribulation.getConfig();
        return cfg != null && cfg.soulInventory.enabled;
    }

    /**
     * Get a read-only summary of the scaling a mob received at spawn: its
     * frozen tier, whether the boss formula was used, and the health/damage
     * modifier sums currently attached. Cheap reads — the values come from the
     * entity's attachment and persistent attribute modifiers, no recomputation.
     * Empty if the entity was never scaled. Server-side only.
     *
     * @param entity the entity
     * @return the scaling summary, or empty for unscaled entities
     */
    public static Optional<MobScalingSummary> getMobScalingSummary(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return Optional.empty();
        }
        Integer tier = entity.getAttached(TribulationAttachments.SCALED_TIER);
        if (tier == null) {
            return Optional.empty();
        }
        return Optional.of(new MobScalingSummary(
                tier,
                BossScalingEngine.hasAnyModifier(mob),
                ScalingEngine.readScalingFactor(mob, ScalingEngine.ATTR_HEALTH),
                ScalingEngine.readScalingFactor(mob, ScalingEngine.ATTR_DAMAGE)
        ));
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

    private static volatile WeaponDropChanceProvider weaponDropChanceProvider = (mob, tier, stack, defaultChance) -> defaultChance;

    /**
     * Set a provider to determine the drop chance of a weapon equipped by Tribulation.
     * Last writer wins. Safe to call as a soft dependency.
     *
     * @param provider the provider
     */
    public static void setWeaponDropChanceProvider(WeaponDropChanceProvider provider) {
        if (provider != null) {
            weaponDropChanceProvider = provider;
        }
    }

    /**
     * Internal use only. Resolves the drop chance for a weapon.
     * A misbehaving provider (throwing or returning a non-finite value) never
     * breaks mob spawning: it falls back to {@code defaultChance}.
     */
    public static float resolveWeaponDropChance(Entity mob, int tier, ItemStack stack, float defaultChance) {
        try {
            float resolved = weaponDropChanceProvider.resolve(mob, tier, stack, defaultChance);
            return Float.isFinite(resolved) ? resolved : defaultChance;
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Weapon drop-chance provider threw; using default", e);
            return defaultChance;
        }
    }

    @FunctionalInterface
    public interface WeaponDropChanceProvider {
        /**
         * Resolve the drop chance for a specific weapon.
         *
         * @param mob the mob being equipped
         * @param tier the mob's Tribulation tier
         * @param stack the item stack being equipped
         * @param defaultChance the configured default drop chance
         * @return the drop chance to use (e.g., 0.085f, or 2.0f for guaranteed)
         */
        float resolve(Entity mob, int tier, ItemStack stack, float defaultChance);
    }
}
