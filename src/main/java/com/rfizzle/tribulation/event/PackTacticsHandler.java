package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.PackTactics;
import com.rfizzle.tribulation.data.TribulationAttachments;
import com.rfizzle.tribulation.scaling.ScalingEngine;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tier-gated pack tactics: when a player hurts an eligible mob whose frozen
 * scaled tier is at or above the configured threshold, same-type mobs within
 * the alert radius that have line-of-sight to the victim acquire the attacker
 * as their target. Hooked on {@link ServerLivingEntityEvents#AFTER_DAMAGE},
 * so there is no per-tick cost outside combat; the radius search is a single
 * bounded AABB query and the number of alerted packmates is capped at
 * {@link #MAX_ALERTED}.
 *
 * <p>Also supplies the natural-spawn group-size bonus consumed by
 * {@code NaturalSpawnerGroupSizeMixin}: eligible types spawning where the
 * effective tier meets the threshold get {@code groupSizeBonus} extra group
 * members.
 */
public final class PackTacticsHandler {

    /** Upper bound on packmates alerted per hit, keeping the response cheap. */
    static final int MAX_ALERTED = 16;

    // Eligible-mob IDs resolve to EntityType references once per config
    // generation: the resolved set is published atomically together with the
    // source list it was built from, so hot-path checks are a reference
    // compare + set lookup and a racing config swap can never pair one list's
    // key with another list's types.
    private record EligibleCache(List<String> source, Set<EntityType<?>> types) {}

    private static volatile EligibleCache eligibleCache = new EligibleCache(null, Set.of());

    private PackTacticsHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(PackTacticsHandler::onAfterDamage);
    }

    static void onAfterDamage(LivingEntity entity, DamageSource source,
                              float baseDamageTaken, float damageTaken, boolean blocked) {
        handle(entity, source, Tribulation.getConfig());
    }

    /** Config-injected core of the damage hook, exercised directly by gametests. */
    public static void handle(LivingEntity entity, DamageSource source, TribulationConfig cfg) {
        if (!(entity instanceof Mob victim)) return;
        if (!(victim.level() instanceof ServerLevel level)) return;

        if (cfg == null || !cfg.packTactics.enabled) return;

        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        // No creative/spectator special-casing: vanilla target AI already
        // drops untargetable players on its own, matching HurtByTargetGoal.
        if (attacker.isSpectator()) return;

        if (!eligibleTypes(cfg.packTactics).contains(victim.getType())) return;

        Integer tier = victim.getAttached(TribulationAttachments.SCALED_TIER);
        if (tier == null || !cfg.packTactics.isActiveAtTier(tier)) return;

        try {
            alertPack(level, victim, attacker, cfg.packTactics.alertRadius);
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Error alerting pack for {}", victim, e);
        }
    }

    /**
     * Retarget same-type mobs around the victim onto the attacker. Packmates
     * must have line-of-sight to the victim (no alerting through walls) and
     * must not already be fighting a living target — an engaged mob is never
     * yanked off another player. Returns the number of mobs alerted.
     */
    static int alertPack(ServerLevel level, Mob victim, ServerPlayer attacker, double radius) {
        if (radius <= 0) return 0;
        List<Mob> packmates = level.getEntitiesOfClass(Mob.class,
                victim.getBoundingBox().inflate(radius),
                mate -> mate != victim
                        && mate.getType() == victim.getType()
                        && mate.isAlive()
                        && (mate.getTarget() == null || !mate.getTarget().isAlive()));
        int alerted = 0;
        for (Mob mate : packmates) {
            if (alerted >= MAX_ALERTED) break;
            if (!mate.hasLineOfSight(victim)) continue;
            mate.setTarget(attacker);
            alerted++;
        }
        return alerted;
    }

    // Memo for the group-size expression in NaturalSpawnerGroupSizeMixin: the
    // vanilla pick reads minCount twice and maxCount once, so one expression
    // triggers three redirect calls with identical arguments. Serving all
    // three from one computed value keeps the +bonus shift exact even if the
    // config is swapped mid-expression (three independent computations could
    // otherwise disagree and drive the random bound negative), and keeps the
    // player-scan cost to one per group pick. Plain fields are safe here:
    // natural spawning runs only on the server thread.
    private static ServerLevel memoLevel;
    private static EntityType<?> memoType;
    private static long memoGameTime = -1;
    private static long memoPos = -1;
    private static int memoBonus;

    /**
     * Extra natural-spawn group members for {@code type} spawning at
     * {@code pos}: the configured bonus when the type is eligible and the
     * effective tier at the position meets the threshold, else 0. Called from
     * {@code NaturalSpawnerGroupSizeMixin}; server thread only.
     */
    public static int spawnGroupBonus(EntityType<?> type, ServerLevel level, BlockPos pos) {
        long gameTime = level.getGameTime();
        long posKey = pos.asLong();
        if (level == memoLevel && type == memoType
                && gameTime == memoGameTime && posKey == memoPos) {
            return memoBonus;
        }
        int bonus = computeSpawnGroupBonus(type, level, pos);
        memoLevel = level;
        memoType = type;
        memoGameTime = gameTime;
        memoPos = posKey;
        memoBonus = bonus;
        return bonus;
    }

    private static int computeSpawnGroupBonus(EntityType<?> type, ServerLevel level, BlockPos pos) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) return 0;
        PackTactics pt = cfg.packTactics;
        if (!pt.enabled || pt.groupSizeBonus <= 0) return 0;
        if (!eligibleTypes(pt).contains(type)) return 0;
        int effectiveLevel = ScalingEngine.getEffectiveLevelAt(
                level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return pt.spawnGroupBonus(ScalingEngine.computeTier(effectiveLevel, cfg.tiers));
    }

    static Set<EntityType<?>> eligibleTypes(PackTactics cfg) {
        List<String> source = cfg.eligibleMobs;
        if (source == null) return Set.of();
        EligibleCache cache = eligibleCache;
        if (source != cache.source()) {
            Set<EntityType<?>> resolved = new HashSet<>();
            for (String id : source) {
                ResourceLocation key = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
                if (key == null || BuiltInRegistries.ENTITY_TYPE.getOptional(key).isEmpty()) {
                    Tribulation.LOGGER.warn("packTactics.eligibleMobs entry '{}' is not a known entity type; ignoring", id);
                    continue;
                }
                resolved.add(BuiltInRegistries.ENTITY_TYPE.get(key));
            }
            cache = new EligibleCache(source, Set.copyOf(resolved));
            eligibleCache = cache;
        }
        return cache.types();
    }
}
