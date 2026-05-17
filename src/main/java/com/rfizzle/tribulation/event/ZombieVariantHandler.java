package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig.SpecialZombies;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;

import java.util.Set;

/**
 * Rolls and applies the Big/Speed zombie variants to eligible zombie-family
 * mobs. Runs after base time/distance/height scaling so variant bonuses layer
 * on already-scaled values (example: a level-250 zombie has 70 HP; Big adds
 * +10 → 80 HP). Roll order is speed-first, big-second, mutually exclusive.
 *
 * <p>Variant modifiers use their own {@code tribulation:variant_*}
 * identifiers so they never collide with the axis modifiers applied by
 * {@link com.rfizzle.tribulation.scaling.ScalingEngine}. Big zombie
 * size is expressed through vanilla {@link Attributes#SCALE}, which in 1.21
 * natively drives both the rendered model and the entity bounding box — no
 * TrackedData or custom renderer mixin is needed.
 */
public final class ZombieVariantHandler {
    public static final String PROCESSED_TAG = "tribulation_variant_processed";

    public static final ResourceLocation BIG_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_big_health");
    public static final ResourceLocation BIG_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_big_damage");
    public static final ResourceLocation BIG_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_big_speed");
    public static final ResourceLocation BIG_SIZE_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_big_size");
    public static final ResourceLocation SPEED_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_speed_speed");
    public static final ResourceLocation SPEED_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_speed_health");

    /**
     * The zombie-family mob keys that can roll a special variant. Matches
     * DESIGN.md: "Applies to all zombie-family mobs: Zombie, Husk, Drowned,
     * Zombified Piglin." ZombieVillager is intentionally excluded.
     */
    public static final Set<String> ELIGIBLE_KEYS = Set.of(
            "zombie", "husk", "drowned", "zombified_piglin"
    );

    public enum Variant {
        NONE, SPEED, BIG
    }

    private ZombieVariantHandler() {}

    public static boolean isEligibleType(String toggleKey) {
        return toggleKey != null && ELIGIBLE_KEYS.contains(toggleKey);
    }

    /**
     * Apply a variant to the mob if eligible. Returns the variant that was
     * applied (or {@link Variant#NONE} if the mob is ineligible or the rolls
     * failed). Marking the processed tag is unconditional so a failed roll
     * isn't re-rolled when the chunk reloads.
     */
    public static Variant apply(Mob mob, String toggleKey, SpecialZombies cfg, RandomSource random) {
        if (mob == null || cfg == null || random == null) return Variant.NONE;
        if (!cfg.enabled) return Variant.NONE;
        if (!isEligibleType(toggleKey)) return Variant.NONE;
        if (!(mob instanceof Zombie zombie)) return Variant.NONE;
        if (zombie.isBaby()) return Variant.NONE;
        if (zombie.getTags().contains(PROCESSED_TAG)) return Variant.NONE;

        Variant chosen = rollVariant(cfg, random.nextInt(100), random.nextInt(100));

        try {
            switch (chosen) {
                case SPEED -> applySpeedVariant(zombie, cfg);
                case BIG -> applyBigVariant(zombie, cfg);
                case NONE -> { /* no-op */ }
            }
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Failed to apply zombie variant {} to {}", chosen, mob, e);
        }

        zombie.addTag(PROCESSED_TAG);
        return chosen;
    }

    /**
     * Pure variant selection: speed-first, then big, mutually exclusive.
     * Rolls are integers in [0, 100); a roll below the configured chance wins.
     */
    public static Variant rollVariant(SpecialZombies cfg, int speedRoll, int bigRoll) {
        if (cfg == null) return Variant.NONE;
        if (cfg.speedZombieChance > 0 && speedRoll < cfg.speedZombieChance) {
            return Variant.SPEED;
        }
        if (cfg.bigZombieChance > 0 && bigRoll < cfg.bigZombieChance) {
            return Variant.BIG;
        }
        return Variant.NONE;
    }

    private static void applySpeedVariant(Zombie zombie, SpecialZombies cfg) {
        // Speed multiplier: 1.3 → +30% of final, so use ADD_MULTIPLIED_TOTAL.
        // That way the variant multiplies the already-scaled movement speed
        // instead of adding to the time-axis base multiplier.
        double speedDelta = cfg.speedZombieSpeedFactor - 1.0;
        if (speedDelta != 0) {
            setAttributeModifier(zombie, Attributes.MOVEMENT_SPEED, SPEED_SPEED_ID,
                    speedDelta, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }

        if (cfg.speedZombieMalusHealth > 0) {
            setAttributeModifier(zombie, Attributes.MAX_HEALTH, SPEED_HEALTH_ID,
                    -cfg.speedZombieMalusHealth, AttributeModifier.Operation.ADD_VALUE);
        }
    }

    private static void applyBigVariant(Zombie zombie, SpecialZombies cfg) {
        if (cfg.bigZombieBonusHealth > 0) {
            setAttributeModifier(zombie, Attributes.MAX_HEALTH, BIG_HEALTH_ID,
                    cfg.bigZombieBonusHealth, AttributeModifier.Operation.ADD_VALUE);
        }
        if (cfg.bigZombieBonusDamage > 0) {
            setAttributeModifier(zombie, Attributes.ATTACK_DAMAGE, BIG_DAMAGE_ID,
                    cfg.bigZombieBonusDamage, AttributeModifier.Operation.ADD_VALUE);
        }
        // Slowness as a percentage of final: 0.7 → -30% of final via ADD_MULTIPLIED_TOTAL.
        double slownessDelta = cfg.bigZombieSlowness - 1.0;
        if (slownessDelta != 0) {
            setAttributeModifier(zombie, Attributes.MOVEMENT_SPEED, BIG_SPEED_ID,
                    slownessDelta, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }

        // Size: vanilla Attributes.SCALE drives both render and hitbox (via
        // LivingEntity#getDimensions and the client scale hook), so a single
        // attribute modifier syncs automatically through the existing
        // attribute packet — no TrackedData or renderer mixin needed.
        double sizeDelta = cfg.bigZombieSize - 1.0;
        if (sizeDelta != 0) {
            setAttributeModifier(zombie, Attributes.SCALE, BIG_SIZE_ID,
                    sizeDelta, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    private static void setAttributeModifier(
            Mob mob,
            Holder<Attribute> attr,
            ResourceLocation id,
            double amount,
            AttributeModifier.Operation op
    ) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }
}
