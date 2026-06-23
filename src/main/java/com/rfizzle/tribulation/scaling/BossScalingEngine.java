package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.Bosses;
import com.rfizzle.tribulation.config.TribulationConfig.DistanceScaling;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/**
 * Boss-specific scaling. Only time + distance apply (height is skipped per
 * DESIGN.md), and both axes use the flat {@code bossTimeFactor} and
 * {@code bossDistanceFactor} rates rather than the per-attribute mob rates.
 * Distance scaling ignores {@code excludeInOtherDimensions} so boss scaling
 * is active in the Nether/End.
 *
 * Only the {@link #BOSS_ATTRIBUTES} subset is touched — health and
 * damage only, avoiding ADD_VALUE armor translation for bosses whose
 * vanilla armor base is often zero.
 */
public final class BossScalingEngine {
    public static final String AXIS_BOSS_TIME = "boss_time";
    public static final String AXIS_BOSS_DISTANCE = "boss_distance";

    public static final List<String> BOSS_ATTRIBUTES = List.of(
            ScalingEngine.ATTR_HEALTH,
            ScalingEngine.ATTR_DAMAGE
    );

    private BossScalingEngine() {}

    // ---- Pure math ----

    /** Per-level time contribution for bosses. Uniform across attributes. */
    public static double computeTimeFactor(int playerLevel, Bosses bossCfg) {
        if (bossCfg == null || playerLevel <= 0 || bossCfg.bossTimeFactor <= 0) {
            return 0.0;
        }
        return playerLevel * bossCfg.bossTimeFactor;
    }

    /**
     * Per-distance-level contribution for bosses. Uses the normal
     * {@code startingDistance}/{@code increasingDistance} thresholds from
     * {@link DistanceScaling} but replaces the rate with
     * {@link Bosses#bossDistanceFactor}.
     */
    public static double computeDistanceFactor(double horizontalDistance, DistanceScaling distCfg, Bosses bossCfg) {
        if (distCfg == null || bossCfg == null || bossCfg.bossDistanceFactor <= 0) {
            return 0.0;
        }
        double levels = ScalingEngine.computeDistanceLevels(
                horizontalDistance,
                distCfg.startingDistance,
                distCfg.increasingDistance
        );
        return levels * bossCfg.bossDistanceFactor;
    }

    /**
     * Sum the two axes, clip to {@link Bosses#bossMaxFactor}. Returns raw sum
     * when the cap is non-positive (treated as disabled).
     */
    public static double combineFactor(double timeFactor, double distFactor, Bosses bossCfg) {
        double sum = timeFactor + distFactor;
        if (bossCfg == null || bossCfg.bossMaxFactor <= 0) {
            return sum;
        }
        return Math.min(sum, bossCfg.bossMaxFactor);
    }

    /**
     * Compute the boss factor breakdown for a single attribute. Both axes share
     * the same total cap, so when the sum exceeds it the individual axes are
     * scaled down proportionally to match — preserving the relative
     * contribution of each axis for inspect/debug output.
     */
    public static ScalingResult.AttributeFactor computeAttributeFactor(
            int playerLevel,
            double horizontalDistance,
            TribulationConfig config
    ) {
        Bosses bossCfg = config.bosses;
        DistanceScaling distCfg = config.distanceScaling;

        double timeFactor = computeTimeFactor(playerLevel, bossCfg);
        double distFactor = distCfg != null && distCfg.enabled
                ? computeDistanceFactor(horizontalDistance, distCfg, bossCfg)
                : 0.0;

        double sum = timeFactor + distFactor;
        double capped = combineFactor(timeFactor, distFactor, bossCfg);
        if (capped < sum && sum > 0) {
            double scale = capped / sum;
            timeFactor *= scale;
            distFactor *= scale;
        }
        // height and moon axes are always zero for bosses.
        return new ScalingResult.AttributeFactor(timeFactor, distFactor, 0.0, 0.0, capped);
    }

    // ---- Full compute ----

    /**
     * Compute the full boss scaling result for a mob. Does not mutate the mob.
     * Height axis stays at zero. Distance is measured from overworld spawn
     * coords, same as the normal engine.
     */
    public static ScalingResult compute(
            Mob mob,
            ServerLevel world,
            int playerLevel,
            TribulationConfig config
    ) {
        double mobX = mob.getX();
        double mobZ = mob.getZ();
        double horizDist = ScalingEngine.horizontalDistanceFromSpawn(world, mobX, mobZ);

        DistanceScaling distCfg = config.distanceScaling;
        double distLevels = distCfg != null && distCfg.enabled
                ? ScalingEngine.computeDistanceLevels(horizDist, distCfg.startingDistance, distCfg.increasingDistance)
                : 0.0;

        ScalingResult.Builder builder = ScalingResult.builder()
                .effectivePlayerLevel(Math.max(0, playerLevel))
                .horizontalDistanceFromSpawn(horizDist)
                .distanceLevels(distLevels)
                .heightLevels(0.0)
                .rawDistanceFactor(distCfg != null && distCfg.enabled
                        ? computeDistanceFactor(horizDist, distCfg, config.bosses) : 0.0)
                .rawHeightFactor(0.0)
                .rawMoonFactor(0.0)
                .tier(ScalingEngine.computeTier(playerLevel, config.tiers));

        for (String attr : BOSS_ATTRIBUTES) {
            builder.attributeFactor(attr, computeAttributeFactor(playerLevel, horizDist, config));
        }
        return builder.build();
    }

    // ---- Apply modifiers ----

    /**
     * Compute and apply boss scaling. Returns null (no-op) when
     * {@code affectBosses} is false so the caller can distinguish
     * scaled from skipped bosses. Mob HP is not topped up here — the caller
     * handles that.
     */
    public static ScalingResult applyModifiers(
            Mob mob,
            ServerLevel world,
            int playerLevel,
            TribulationConfig config
    ) {
        if (config == null || config.bosses == null || !config.bosses.affectBosses) {
            return null;
        }
        ScalingResult result = compute(mob, world, playerLevel, config);
        for (String attr : BOSS_ATTRIBUTES) {
            applyAttributeModifiers(mob, attr, result.factorFor(attr));
        }
        return result;
    }

    /**
     * Apply the two boss-axis modifiers for one attribute. Uses
     * ADD_MULTIPLIED_BASE for all boss attributes (no ADD_VALUE path — boss
     * armor is often zero in vanilla and would be a no-op anyway).
     */
    public static void applyAttributeModifiers(Mob mob, String attributeKey, ScalingResult.AttributeFactor factor) {
        Holder<Attribute> holder = ScalingEngine.attributeHolder(attributeKey);
        if (holder == null) return;
        AttributeInstance instance = mob.getAttribute(holder);
        if (instance == null) return;

        applyAxis(instance, AXIS_BOSS_TIME, attributeKey, factor.timeFactor());
        applyAxis(instance, AXIS_BOSS_DISTANCE, attributeKey, factor.distanceFactor());
    }

    private static void applyAxis(AttributeInstance instance, String axis, String attributeKey, double amount) {
        ResourceLocation id = modifierId(axis, attributeKey);
        instance.removeModifier(id);
        if (amount > 0) {
            instance.addPermanentModifier(new AttributeModifier(
                    id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }
    }

    /** Boss-namespaced modifier ID so inspect/debug can tell boss from mob modifiers. */
    public static ResourceLocation modifierId(String axis, String attribute) {
        return Tribulation.id(axis + "_" + attribute);
    }

    /**
     * True if this mob already has at least one tribulation boss
     * modifier. Used by tests and debug commands; the spawn handler relies on
     * the {@code PROCESSED_TAG} scoreboard tag instead.
     */
    public static boolean hasAnyModifier(Mob mob) {
        for (String attr : BOSS_ATTRIBUTES) {
            Holder<Attribute> holder = ScalingEngine.attributeHolder(attr);
            if (holder == null) continue;
            AttributeInstance instance = mob.getAttribute(holder);
            if (instance == null) continue;
            if (instance.hasModifier(modifierId(AXIS_BOSS_TIME, attr))
                    || instance.hasModifier(modifierId(AXIS_BOSS_DISTANCE, attr))) {
                return true;
            }
        }
        return false;
    }
}
