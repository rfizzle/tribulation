package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.advancement.TribulationCriteria;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Grants the "kill a tier-5 scaled mob" advancement. Mirrors the tier-5 kill
 * statistic in {@code TribulationStats}: same {@link ServerLivingEntityEvents#AFTER_DEATH}
 * hook, same {@link TribulationAttachments#SCALED_TIER} attachment check, and
 * the same {@link ShardDropHandler#resolveKiller} resolution so kill credit and
 * direct-attacker fallback stay consistent across the mod.
 */
public final class AdvancementsHandler {

    private AdvancementsHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(AdvancementsHandler::onAfterDeath);
    }

    static void onAfterDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof Mob mob)) return;

        Integer tier = mob.getAttached(TribulationAttachments.SCALED_TIER);
        if (tier == null || tier != TierManager.MAX_TIER) return;

        ServerPlayer killer = ShardDropHandler.resolveKiller(mob, damageSource);
        if (killer != null) {
            TribulationCriteria.TIER_FIVE_MOB_KILLED.trigger(killer);
        }
    }
}
