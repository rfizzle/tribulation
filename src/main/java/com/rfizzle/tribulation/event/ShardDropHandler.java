package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import com.rfizzle.tribulation.item.TribulationItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Rolls shard drops on mob death. Rules, per DESIGN.md:
 * <ul>
 *   <li>Only {@link Mob}s killed by a {@link ServerPlayer} count.</li>
 *   <li>Killer's difficulty level must be ≥ {@code shards.dropStartLevel}.</li>
 *   <li>A {@code shards.dropChance} roll gates the drop.</li>
 * </ul>
 * The roll happens at death — glow-carrier pre-marking at spawn is
 * intentionally not implemented.
 */
public final class ShardDropHandler {

    private ShardDropHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(ShardDropHandler::onAfterDeath);
    }

    static void onAfterDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof Mob mob)) return;
        if (!(entity.level() instanceof ServerLevel world)) return;

        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null || !cfg.shards.enabled) return;

        ServerPlayer killer = resolveKiller(mob, damageSource);
        if (killer == null) return;

        MinecraftServer server = world.getServer();
        if (server == null) return;

        try {
            PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
            int killerLevel = state.getLevel(killer.getUUID());
            if (!shouldDrop(killerLevel, cfg.shards, world.getRandom())) {
                return;
            }
            spawnShard(world, mob);
        } catch (Exception e) {
            Tribulation.LOGGER.error("Failed to process shard drop on {}", mob, e);
        }
    }

    /**
     * Pure roll logic: killer must meet the minimum level, then a uniform
     * {@code dropChance} gate decides. Exposed for unit tests.
     */
    public static boolean shouldDrop(int killerLevel, TribulationConfig.Shards cfg, RandomSource random) {
        if (cfg == null || !cfg.enabled) return false;
        if (cfg.shardPower <= 0) return false;
        if (cfg.dropChance <= 0) return false;
        if (killerLevel < cfg.dropStartLevel) return false;
        if (cfg.dropChance >= 1.0) return true;
        // nextDouble keeps roll and threshold at the same precision as
        // cfg.dropChance (double) — avoids float→double promotion surprises
        // around small thresholds like the default 0.005.
        return random.nextDouble() < cfg.dropChance;
    }


    /**
     * Resolve the {@link ServerPlayer} credited with the kill. Prefers
     * {@link LivingEntity#getKillCredit()} (handles arrow/trident kills) and
     * falls back to the direct damager for edge cases.
     */
    static ServerPlayer resolveKiller(Mob mob, DamageSource source) {
        LivingEntity credit = mob.getKillCredit();
        if (credit instanceof ServerPlayer sp) {
            return sp;
        }
        if (source != null) {
            Entity attacker = source.getEntity();
            if (attacker instanceof ServerPlayer sp) {
                return sp;
            }
        }
        return null;
    }

    private static void spawnShard(ServerLevel world, Mob mob) {
        ItemStack stack = new ItemStack(TribulationItems.SHATTER_SHARD);
        ItemEntity dropped = new ItemEntity(world, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
        dropped.setDefaultPickUpDelay();
        world.addFreshEntity(dropped);
    }

}
