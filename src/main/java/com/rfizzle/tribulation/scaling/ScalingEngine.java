package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.DistanceScaling;
import com.rfizzle.tribulation.config.TribulationConfig.HeightScaling;
import com.rfizzle.tribulation.config.TribulationConfig.MobScaling;
import com.rfizzle.tribulation.config.TribulationConfig.StatCaps;
import com.rfizzle.tribulation.config.TribulationConfig.Tiers;
import com.rfizzle.tribulation.data.PlayerDifficultyState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stateless calculator that turns (player level, mob position, mob config)
 * into per-attribute scaling factors and applies them as attribute modifiers.
 *
 * Three axes contribute to each attribute: time (player level), distance
 * (horizontal 2D from world spawn), and height (Y offset from sea level).
 * Time applies to all scaled attributes. Distance/height apply only to the
 * {@link #POSITION_SCALED} subset.
 */
public final class ScalingEngine {
    public static final String ATTR_HEALTH = "health";
    public static final String ATTR_DAMAGE = "damage";
    public static final String ATTR_SPEED = "speed";
    public static final String ATTR_FOLLOW_RANGE = "follow_range";
    public static final String ATTR_ARMOR = "armor";
    public static final String ATTR_TOUGHNESS = "toughness";

    public static final String AXIS_TIME = "time";
    public static final String AXIS_DISTANCE = "distance";
    public static final String AXIS_HEIGHT = "height";

    public static final List<String> ALL_ATTRIBUTES = List.of(
            ATTR_HEALTH, ATTR_DAMAGE, ATTR_SPEED, ATTR_FOLLOW_RANGE, ATTR_ARMOR, ATTR_TOUGHNESS
    );

    public static final Set<String> POSITION_SCALED = Set.of(
            ATTR_HEALTH, ATTR_DAMAGE, ATTR_ARMOR, ATTR_TOUGHNESS
    );

    public static final Set<String> ADD_VALUE_ATTRIBUTES = Set.of(ATTR_ARMOR, ATTR_TOUGHNESS);

    /**
     * Holder lookups are deferred to a nested class so pure-math tests can load
     * {@link ScalingEngine} without bootstrapping Minecraft's attribute registry.
     */
    private static final class Holders {
        static final Map<String, Holder<Attribute>> MAP;
        static {
            Map<String, Holder<Attribute>> m = new LinkedHashMap<>();
            m.put(ATTR_HEALTH, Attributes.MAX_HEALTH);
            m.put(ATTR_DAMAGE, Attributes.ATTACK_DAMAGE);
            m.put(ATTR_SPEED, Attributes.MOVEMENT_SPEED);
            m.put(ATTR_FOLLOW_RANGE, Attributes.FOLLOW_RANGE);
            m.put(ATTR_ARMOR, Attributes.ARMOR);
            m.put(ATTR_TOUGHNESS, Attributes.ARMOR_TOUGHNESS);
            MAP = Collections.unmodifiableMap(m);
        }
    }

    private ScalingEngine() {}

    // ---- Identifier helpers ----

    public static ResourceLocation modifierId(String axis, String attribute) {
        return ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, axis + "_" + attribute);
    }

    public static Holder<Attribute> attributeHolder(String attributeKey) {
        return Holders.MAP.get(attributeKey);
    }

    public static boolean usesAddValue(String attributeKey) {
        return ADD_VALUE_ATTRIBUTES.contains(attributeKey);
    }

    public static boolean isPositionScaled(String attributeKey) {
        return POSITION_SCALED.contains(attributeKey);
    }

    // ---- Pure math (no Minecraft types) ----

    /** Per-attribute time factor: rate * level, clipped to the per-attribute cap. */
    public static double computeTimeFactor(int playerLevel, double rate, double cap) {
        if (playerLevel <= 0 || rate <= 0 || cap <= 0) {
            return 0.0;
        }
        return Math.min(playerLevel * rate, cap);
    }

    /** Raw distance "levels" — blocks past startingDistance divided by increasingDistance. */
    public static double computeDistanceLevels(double horizontalDistance, double startingDistance, double increasingDistance) {
        if (increasingDistance <= 0) {
            return 0.0;
        }
        double past = horizontalDistance - startingDistance;
        if (past <= 0) {
            return 0.0;
        }
        return past / increasingDistance;
    }

    /** Distance factor: levels * rate, capped. Returns 0 if disabled. */
    public static double computeDistanceFactor(double horizontalDistance, DistanceScaling cfg) {
        if (cfg == null || !cfg.enabled) {
            return 0.0;
        }
        double levels = computeDistanceLevels(horizontalDistance, cfg.startingDistance, cfg.increasingDistance);
        return Math.min(levels * cfg.distanceFactor, cfg.maxDistanceFactor);
    }

    /** Height "levels" — absolute Y delta divided by heightDistance, honoring pos/neg toggles. */
    public static double computeHeightLevels(double mobY, HeightScaling cfg) {
        if (cfg == null || cfg.heightDistance <= 0) {
            return 0.0;
        }
        double delta = mobY - cfg.startingHeight;
        if (delta > 0 && !cfg.positiveHeightScaling) return 0.0;
        if (delta < 0 && !cfg.negativeHeightScaling) return 0.0;
        return Math.abs(delta) / cfg.heightDistance;
    }

    /** Height factor: levels * rate, capped. Returns 0 if disabled. */
    public static double computeHeightFactor(double mobY, HeightScaling cfg) {
        if (cfg == null || !cfg.enabled) {
            return 0.0;
        }
        double levels = computeHeightLevels(mobY, cfg);
        return Math.min(levels * cfg.heightFactor, cfg.maxHeightFactor);
    }

    /** Sum the three axes and clip the total at the global cap. */
    public static double combineFactor(double timeFactor, double distanceFactor, double heightFactor, double globalCap) {
        double sum = timeFactor + distanceFactor + heightFactor;
        if (globalCap <= 0) {
            return sum;
        }
        return Math.min(sum, globalCap);
    }

    /** Pick the time rate for an attribute from the mob's scaling config. */
    public static double rateFor(String attributeKey, MobScaling scaling) {
        if (scaling == null) return 0.0;
        return switch (attributeKey) {
            case ATTR_HEALTH -> scaling.healthRate;
            case ATTR_DAMAGE -> scaling.damageRate;
            case ATTR_SPEED -> scaling.speedRate;
            case ATTR_FOLLOW_RANGE -> scaling.followRangeRate;
            case ATTR_ARMOR -> scaling.armorRate;
            case ATTR_TOUGHNESS -> scaling.toughnessRate;
            default -> 0.0;
        };
    }

    /** Pick the per-attribute time cap from the mob's scaling config. */
    public static double capFor(String attributeKey, MobScaling scaling) {
        if (scaling == null) return 0.0;
        return switch (attributeKey) {
            case ATTR_HEALTH -> scaling.healthCap;
            case ATTR_DAMAGE -> scaling.damageCap;
            case ATTR_SPEED -> scaling.speedCap;
            case ATTR_FOLLOW_RANGE -> scaling.followRangeCap;
            case ATTR_ARMOR -> scaling.armorCap;
            case ATTR_TOUGHNESS -> scaling.toughnessCap;
            default -> 0.0;
        };
    }

    /**
     * Global cap for an attribute. For ADD_MULTIPLIED_BASE attributes this is a
     * dimensionless multiplier (e.g. maxFactorHealth=4.0 means up to +400% base).
     * For ADD_VALUE attributes the returned value is the multiplier itself;
     * callers convert to absolute via {@code cap * attributeCap}.
     */
    public static double globalCapFor(String attributeKey, StatCaps caps) {
        if (caps == null) return 0.0;
        return switch (attributeKey) {
            case ATTR_HEALTH -> caps.maxFactorHealth;
            case ATTR_DAMAGE -> caps.maxFactorDamage;
            case ATTR_SPEED -> caps.maxFactorSpeed;
            case ATTR_FOLLOW_RANGE -> caps.maxFactorFollowRange;
            case ATTR_ARMOR, ATTR_TOUGHNESS -> caps.maxFactorProtection;
            default -> 0.0;
        };
    }

    /**
     * Compute the per-attribute factor breakdown in native units (multiplier for
     * ADD_MULTIPLIED_BASE; absolute amount for ADD_VALUE). The sum is clipped by
     * the effective global cap for the attribute, and if clipping occurred each
     * axis is scaled down proportionally so the per-axis amounts still sum to the
     * total.
     */
    public static ScalingResult.AttributeFactor computeAttributeFactor(
            String attributeKey,
            int playerLevel,
            double rawDistanceFactor,
            double rawHeightFactor,
            MobScaling scaling,
            StatCaps caps
    ) {
        double rate = rateFor(attributeKey, scaling);
        double cap = capFor(attributeKey, scaling);
        double timeFactor = computeTimeFactor(playerLevel, rate, cap);

        double distFactor = 0.0;
        double heightFactor = 0.0;
        if (isPositionScaled(attributeKey)) {
            distFactor = rawDistanceFactor;
            heightFactor = rawHeightFactor;
        }

        boolean addValue = usesAddValue(attributeKey);
        if (addValue) {
            // For ADD_VALUE attributes the per-axis dist/height factors are
            // dimensionless multipliers; translate them into native units by
            // multiplying by the attribute's per-axis cap.
            distFactor *= cap;
            heightFactor *= cap;
        }

        double globalCapRaw = globalCapFor(attributeKey, caps);
        double globalMax;
        if (addValue) {
            // ADD_VALUE: the cap is a multiplier of the attribute's own cap.
            globalMax = globalCapRaw * cap;
        } else {
            globalMax = globalCapRaw;
        }

        double sum = timeFactor + distFactor + heightFactor;
        double total = globalMax > 0 ? Math.min(sum, globalMax) : sum;
        if (total < sum && sum > 0) {
            double scale = total / sum;
            timeFactor *= scale;
            distFactor *= scale;
            heightFactor *= scale;
        }

        return new ScalingResult.AttributeFactor(timeFactor, distFactor, heightFactor, total);
    }

    /** Derive tier 0..5 from player level and the configured thresholds. */
    public static int computeTier(int playerLevel, Tiers tiers) {
        return TierManager.getTier(playerLevel, tiers);
    }

    // ---- World-aware ----

    /**
     * Resolve the effective Tribulation level for an entity based on the nearest
     * player within the configured detection range. Returns 0 if no player is
     * nearby or if the range is disabled.
     */
    public static int getEffectiveLevel(Entity entity, ServerLevel world) {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) return 0;
        double range = cfg.general.mobDetectionRange;
        if (range <= 0) return 0;

        Player nearest = world.getNearestPlayer(entity, range);
        if (!(nearest instanceof ServerPlayer sp)) return 0;

        MinecraftServer server = world.getServer();
        if (server == null) return 0;

        PlayerDifficultyState state = PlayerDifficultyState.getOrCreate(server);
        return state.getLevel(sp.getUUID());
    }

    /** 2D horizontal distance from world spawn (Y is excluded by design). */
    public static double horizontalDistanceFromSpawn(ServerLevel world, double mobX, double mobZ) {
        BlockPos spawn = world.getSharedSpawnPos();
        double dx = mobX - spawn.getX();
        double dz = mobZ - spawn.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean isOverworld(ServerLevel world) {
        ResourceKey<Level> dim = world.dimension();
        return dim == Level.OVERWORLD;
    }

    public static boolean distanceAppliesInDimension(ServerLevel world, DistanceScaling cfg) {
        if (cfg == null || !cfg.enabled) return false;
        if (cfg.excludeInOtherDimensions && !isOverworld(world)) return false;
        return true;
    }

    public static boolean heightAppliesInDimension(ServerLevel world, HeightScaling cfg) {
        if (cfg == null || !cfg.enabled) return false;
        if (cfg.excludeInOtherDimensions && !isOverworld(world)) return false;
        return true;
    }

    // ---- Full compute ----

    /**
     * Compute the full scaling result for a mob at a location with the given
     * player level. Does not mutate the mob.
     */
    public static ScalingResult compute(
            Mob mob,
            ServerLevel world,
            int playerLevel,
            TribulationConfig config,
            MobScaling scaling
    ) {
        double mobX = mob.getX();
        double mobY = mob.getY();
        double mobZ = mob.getZ();
        double horizDist = horizontalDistanceFromSpawn(world, mobX, mobZ);

        double rawDistFactor = distanceAppliesInDimension(world, config.distanceScaling)
                ? computeDistanceFactor(horizDist, config.distanceScaling)
                : 0.0;
        double rawHeightFactor = heightAppliesInDimension(world, config.heightScaling)
                ? computeHeightFactor(mobY, config.heightScaling)
                : 0.0;

        double distLevels = distanceAppliesInDimension(world, config.distanceScaling)
                ? computeDistanceLevels(horizDist, config.distanceScaling.startingDistance, config.distanceScaling.increasingDistance)
                : 0.0;
        double heightLevels = heightAppliesInDimension(world, config.heightScaling)
                ? computeHeightLevels(mobY, config.heightScaling)
                : 0.0;

        ScalingResult.Builder builder = ScalingResult.builder()
                .effectivePlayerLevel(Math.max(0, playerLevel))
                .horizontalDistanceFromSpawn(horizDist)
                .distanceLevels(distLevels)
                .heightLevels(heightLevels)
                .rawDistanceFactor(rawDistFactor)
                .rawHeightFactor(rawHeightFactor)
                .tier(computeTier(playerLevel, config.tiers));

        for (String attr : ALL_ATTRIBUTES) {
            builder.attributeFactor(
                    attr,
                    computeAttributeFactor(attr, playerLevel, rawDistFactor, rawHeightFactor, scaling, config.statCaps)
            );
        }
        return builder.build();
    }

    // ---- Apply modifiers ----

    /**
     * Compute scaling for the mob and apply per-axis attribute modifiers.
     * Existing modifiers with tribulation IDs are removed first to avoid
     * stacking on re-application.
     */
    public static ScalingResult applyModifiers(
            Mob mob,
            ServerLevel world,
            int playerLevel,
            TribulationConfig config,
            MobScaling scaling
    ) {
        ScalingResult result = compute(mob, world, playerLevel, config, scaling);
        for (String attr : ALL_ATTRIBUTES) {
            applyAttributeModifiers(mob, attr, result.factorFor(attr));
        }
        return result;
    }

    /**
     * Apply the three per-axis modifiers for one attribute. Each axis with a
     * non-zero factor gets a persistent modifier; zero-factor axes have their
     * prior modifier cleared (so toggling config off actually removes state).
     */
    public static void applyAttributeModifiers(Mob mob, String attributeKey, ScalingResult.AttributeFactor factor) {
        Holder<Attribute> holder = attributeHolder(attributeKey);
        if (holder == null) return;
        AttributeInstance instance = mob.getAttribute(holder);
        if (instance == null) return;

        AttributeModifier.Operation op = usesAddValue(attributeKey)
                ? AttributeModifier.Operation.ADD_VALUE
                : AttributeModifier.Operation.ADD_MULTIPLIED_BASE;

        applyAxis(instance, AXIS_TIME, attributeKey, factor.timeFactor(), op);
        applyAxis(instance, AXIS_DISTANCE, attributeKey, factor.distanceFactor(), op);
        applyAxis(instance, AXIS_HEIGHT, attributeKey, factor.heightFactor(), op);
    }

    private static void applyAxis(AttributeInstance instance, String axis, String attributeKey, double amount, AttributeModifier.Operation op) {
        ResourceLocation id = modifierId(axis, attributeKey);
        instance.removeModifier(id);
        if (amount > 0) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, op));
        }
    }

    /**
     * Sum of the tribulation axis modifiers on MAX_HEALTH — the canonical
     * "how scaled is this mob?" proxy used by XP/loot and command inspection.
     * Covers both normal ({@code time_/distance_/height_}) and boss
     * ({@code boss_time_/boss_distance_}) axes. Variant modifiers are excluded
     * by ID — they use {@code variant_*} identifiers that aren't in this set.
     *
     * <p>Reading from the persistent attribute modifier instead of caching on
     * the entity means the value survives chunk reload/server restart for free,
     * and re-applying scaling just overwrites the same IDs.
     */
    public static double readHealthScalingFactor(Mob mob) {
        if (mob == null) return 0.0;
        Holder<Attribute> holder = attributeHolder(ATTR_HEALTH);
        if (holder == null) return 0.0;
        AttributeInstance instance = mob.getAttribute(holder);
        if (instance == null) return 0.0;

        double total = 0.0;
        for (String axis : List.of(AXIS_TIME, AXIS_DISTANCE, AXIS_HEIGHT)) {
            AttributeModifier mod = instance.getModifier(modifierId(axis, ATTR_HEALTH));
            if (mod != null) total += mod.amount();
        }
        for (String axis : List.of(BossScalingEngine.AXIS_BOSS_TIME, BossScalingEngine.AXIS_BOSS_DISTANCE)) {
            AttributeModifier mod = instance.getModifier(BossScalingEngine.modifierId(axis, ATTR_HEALTH));
            if (mod != null) total += mod.amount();
        }
        return total;
    }

    /**
     * Returns true if this mob already has at least one tribulation
     * modifier attached — used by the spawn handler to detect "already scaled"
     * mobs that should be left alone.
     */
    public static boolean hasAnyModifier(Mob mob) {
        for (String attr : ALL_ATTRIBUTES) {
            Holder<Attribute> holder = attributeHolder(attr);
            if (holder == null) continue;
            AttributeInstance instance = mob.getAttribute(holder);
            if (instance == null) continue;
            if (instance.hasModifier(modifierId(AXIS_TIME, attr))
                    || instance.hasModifier(modifierId(AXIS_DISTANCE, attr))
                    || instance.hasModifier(modifierId(AXIS_HEIGHT, attr))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Proportionally scale down the tribulation axis modifiers for an attribute
     * if the total value (base + all modifiers) exceeds the ceiling.
     *
     * <p>Formula:
     * 1. Calculate surplus = total - ceiling.
     * 2. Calculate sum of tribulation modifiers.
     * 3. If surplus > 0 and tribulation sum > 0:
     *    new_amount = old_amount * (tribulationSum - surplus) / tribulationSum
     *    (clamped to 0).
     */
    public static void clampToCeiling(Mob mob, String attributeKey, double ceiling) {
        if (ceiling <= 0) return;
        Holder<Attribute> holder = attributeHolder(attributeKey);
        if (holder == null) return;
        AttributeInstance instance = mob.getAttribute(holder);
        if (instance == null) return;

        double total = instance.getValue();
        double surplus = total - ceiling;
        if (surplus <= 0) return;

        double tribulationSum = 0;
        List<AttributeModifier> mods = new ArrayList<>();
        for (String axis : List.of(AXIS_TIME, AXIS_DISTANCE, AXIS_HEIGHT)) {
            AttributeModifier mod = instance.getModifier(modifierId(axis, attributeKey));
            if (mod != null) {
                tribulationSum += mod.amount();
                mods.add(mod);
            }
        }

        if (tribulationSum <= 0) return;

        double scale = Math.max(0, (tribulationSum - surplus) / tribulationSum);
        AttributeModifier.Operation op = usesAddValue(attributeKey)
                ? AttributeModifier.Operation.ADD_VALUE
                : AttributeModifier.Operation.ADD_MULTIPLIED_BASE;

        for (AttributeModifier mod : mods) {
            ResourceLocation id = mod.id();
            double newAmount = mod.amount() * scale;
            instance.removeModifier(id);
            if (newAmount > 0) {
                instance.addPermanentModifier(new AttributeModifier(id, newAmount, op));
            }
        }
    }

    /** Map view of attribute key → Holder for external consumers (commands, etc.). */
    public static Map<String, Holder<Attribute>> attributeHolders() {
        return Holders.MAP;
    }
}
