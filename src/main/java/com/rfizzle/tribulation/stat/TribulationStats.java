package com.rfizzle.tribulation.stat;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.advancement.TribulationCriteria;
import com.rfizzle.tribulation.api.TribulationLevelCallback;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.event.ShardDropHandler;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Mob;

/**
 * Registry for Tribulation-specific statistics. Stats are registered in vanilla's
 * CUSTOM_STAT registry and persist via the standard player statistics system.
 */
public final class TribulationStats {
    public static final ResourceLocation HIGHEST_LEVEL_REACHED = Tribulation.id("highest_level_reached");
    public static final ResourceLocation LEVELS_LOST_TO_DEATH_RELIEF = Tribulation.id("levels_lost_to_death_relief");
    public static final ResourceLocation SHATTER_SHARDS_USED = Tribulation.id("shatter_shards_used");
    public static final ResourceLocation HEARTS_LOST = Tribulation.id("hearts_lost");
    public static final ResourceLocation HEARTS_RESTORED = Tribulation.id("hearts_restored");
    public static final ResourceLocation TIER_5_MOBS_KILLED = Tribulation.id("tier_5_mobs_killed");

    private TribulationStats() {}

    public static void register() {
        registerStat(HIGHEST_LEVEL_REACHED, StatFormatter.DEFAULT);
        registerStat(LEVELS_LOST_TO_DEATH_RELIEF, StatFormatter.DEFAULT);
        registerStat(SHATTER_SHARDS_USED, StatFormatter.DEFAULT);
        registerStat(HEARTS_LOST, StatFormatter.DEFAULT);
        registerStat(HEARTS_RESTORED, StatFormatter.DEFAULT);
        registerStat(TIER_5_MOBS_KILLED, StatFormatter.DEFAULT);

        // Monotonic highest level tracking
        TribulationLevelCallback.EVENT.register((player, oldLevel, newLevel) -> {
            int currentHighest = player.getStats().getValue(Stats.CUSTOM.get(HIGHEST_LEVEL_REACHED));
            if (newLevel > currentHighest) {
                player.getStats().setValue(player, Stats.CUSTOM.get(HIGHEST_LEVEL_REACHED), newLevel);
            }
        });

        // Tier-5 kill: award the stat and grant the advancement from a single
        // killer resolution so the AFTER_DEATH traversal only runs once.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Mob mob) {
                Integer tier = mob.getAttached(TribulationAttachments.SCALED_TIER);
                if (tier != null && tier == TierManager.MAX_TIER) {
                    ServerPlayer killer = ShardDropHandler.resolveKiller(mob, damageSource);
                    if (killer != null) {
                        killer.awardStat(TIER_5_MOBS_KILLED);
                        TribulationCriteria.TIER_FIVE_MOB_KILLED.trigger(killer);
                    }
                }
            }
        });
    }

    private static void registerStat(ResourceLocation id, StatFormatter formatter) {
        Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
        Stats.CUSTOM.get(id, formatter);
    }
}
