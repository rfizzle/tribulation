package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.XpAndLoot;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Extra XP and loot rewards for scaled mobs. XP multiplication is applied via
 * {@code LivingEntityExperienceMixin} so it fires through the vanilla XP drop
 * path; this handler owns the pure-math helpers and the on-death extra loot
 * roll.
 *
 * <p>Both paths derive the mob's "difficulty factor" from
 * {@link ScalingEngine#readHealthScalingFactor(Mob)} — reading back the
 * MAX_HEALTH axis modifiers applied at spawn time. No extra per-entity state
 * is stored; the existing persistent attribute modifiers are the source of
 * truth, so factor persists automatically across chunk unload, save/load, and
 * server restarts.
 */
public final class XpLootHandler {
    /**
     * Search box half-size (in blocks) used to find freshly-dropped item
     * entities around the mob's death position. 1.5 blocks covers the typical
     * spread {@code spawnAtLocation} applies and stays tight enough not to pull
     * in drops from an unrelated mob that died in the same tick nearby.
     */
    private static final double LOOT_SCAN_RADIUS = 1.5;

    /**
     * Maximum age (in ticks) of an {@link ItemEntity} that will be considered
     * "a drop from this mob's death". {@code AFTER_DEATH} runs in the same tick
     * as the drop spawn, so fresh drops are always age 0 when we see them.
     */
    private static final int LOOT_MAX_AGE_TICKS = 1;

    private XpLootHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(XpLootHandler::onAfterDeath);
    }

    // ---- Pure math (no Minecraft world types) ----

    /**
     * Compute the XP multiplier for a mob with the given scaling factor. The
     * addend on top of 1.0 is capped at {@code maxXpFactor - 1} so the total
     * multiplier never exceeds {@code maxXpFactor}. Returns 1.0 when disabled,
     * when the mob has no scaling, or when the cap is &le; 1.
     */
    public static double computeXpMultiplier(double healthFactor, XpAndLoot cfg) {
        if (cfg == null || !cfg.extraXp) return 1.0;
        if (healthFactor <= 0) return 1.0;
        double maxAddend = cfg.maxXpFactor - 1.0;
        if (maxAddend <= 0) return 1.0;
        return 1.0 + Math.min(healthFactor, maxAddend);
    }

    /**
     * Apply the XP multiplier to a base XP amount. Rounds to the nearest int
     * so a tiny factor on a 1-XP drop doesn't silently floor back to 1.
     */
    public static int applyXpMultiplier(int baseXp, double healthFactor, XpAndLoot cfg) {
        if (baseXp <= 0) return baseXp;
        double mult = computeXpMultiplier(healthFactor, cfg);
        if (mult == 1.0) return baseXp;
        long scaled = Math.round(baseXp * mult);
        if (scaled > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) scaled;
    }

    /**
     * Probability that a scaled mob triggers an extra loot drop: linear in the
     * factor, capped at {@code maxLootChance}. Returns 0 when disabled.
     */
    public static double computeExtraLootChance(double healthFactor, XpAndLoot cfg) {
        if (cfg == null || !cfg.dropMoreLoot) return 0.0;
        if (healthFactor <= 0 || cfg.moreLootChance <= 0) return 0.0;
        double chance = healthFactor * cfg.moreLootChance;
        if (cfg.maxLootChance > 0) {
            chance = Math.min(chance, cfg.maxLootChance);
        }
        return chance;
    }

    /**
     * Roll the extra-loot gate. Strict {@code <} so a 0 chance never wins and a
     * 1.0 chance always does (mirrors {@link ShardDropHandler#shouldDrop}).
     */
    public static boolean shouldDropExtraLoot(double healthFactor, XpAndLoot cfg, RandomSource random) {
        double chance = computeExtraLootChance(healthFactor, cfg);
        if (chance <= 0) return false;
        if (chance >= 1.0) return true;
        return random.nextDouble() < chance;
    }

    // ---- World handler ----

    static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Mob mob)) return;
        if (!(entity.level() instanceof ServerLevel world)) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || cfg.xpAndLoot == null || !cfg.xpAndLoot.dropMoreLoot) return;

        try {
            double factor = ScalingEngine.readHealthScalingFactor(mob);
            if (!shouldDropExtraLoot(factor, cfg.xpAndLoot, world.getRandom())) {
                return;
            }
            duplicateRandomDrop(world, mob);
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to apply extra loot for {}", mob, e);
        }
    }

    /**
     * Scan the death area for freshly-spawned drops and duplicate a random one.
     * Scanning is used (rather than intercepting {@code spawnAtLocation}) so the
     * duplicate is drawn uniformly across all drops — intercepting the spawn
     * path would bias toward the first or last drop depending on where the hook
     * lives.
     */
    private static void duplicateRandomDrop(ServerLevel world, Mob mob) {
        AABB box = AABB.ofSize(mob.position(), LOOT_SCAN_RADIUS * 2, LOOT_SCAN_RADIUS * 2, LOOT_SCAN_RADIUS * 2);
        List<ItemEntity> fresh = world.getEntitiesOfClass(
                ItemEntity.class,
                box,
                item -> item.getAge() <= LOOT_MAX_AGE_TICKS && !item.getItem().isEmpty()
        );
        if (fresh.isEmpty()) return;

        ItemEntity pick = fresh.get(world.getRandom().nextInt(fresh.size()));
        ItemStack copy = pick.getItem().copy();
        if (copy.isEmpty()) return;

        ItemEntity dup = new ItemEntity(world, mob.getX(), mob.getY() + 0.5, mob.getZ(), copy);
        dup.setDefaultPickUpDelay();
        world.addFreshEntity(dup);
    }
}
