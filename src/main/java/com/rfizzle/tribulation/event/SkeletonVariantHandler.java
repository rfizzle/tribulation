package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig.SpecialSkeletons;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;

import java.util.Set;

/**
 * Rolls and applies the Deadeye/Brute skeleton variants to eligible
 * skeleton-family mobs, mirroring {@link ZombieVariantHandler}. Runs after base
 * time/distance/height scaling so variant bonuses layer on already-scaled
 * values. Roll order is deadeye-first, brute-second, mutually exclusive.
 *
 * <p>Variant stat changes use their own {@code tribulation:variant_*}
 * identifiers so they never collide with the axis modifiers applied by
 * {@link com.rfizzle.tribulation.scaling.ScalingEngine}. Brute size is
 * expressed through vanilla {@link Attributes#SCALE}, which in 1.21 natively
 * drives both the rendered model and the entity bounding box.
 *
 * <p>Bow cadence cannot be driven by an attribute in 1.21.1 — it is hardcoded
 * in {@code AbstractSkeleton.reassessWeaponGoal()}. Each variant therefore also
 * carries a per-variant scoreboard tag ({@link #DEADEYE_TAG} / {@link #BRUTE_TAG})
 * that {@code AbstractSkeletonMixin} reads to override the bow goal's attack
 * interval. That tag — not the attribute modifiers — is the canonical "is this a
 * variant" signal, since a Deadeye whose health malus is configured to 0 carries
 * no attribute modifier at all.
 */
public final class SkeletonVariantHandler {
    public static final String PROCESSED_TAG = "tribulation_skeleton_variant_processed";

    /** Per-variant scoreboard tags — always applied, drive the bow mixin and detection. */
    public static final String DEADEYE_TAG = "tribulation_variant_deadeye";
    public static final String BRUTE_TAG = "tribulation_variant_brute";

    public static final ResourceLocation DEADEYE_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_deadeye_health");
    public static final ResourceLocation BRUTE_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_brute_health");
    public static final ResourceLocation BRUTE_KNOCKBACK_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_brute_knockback");
    public static final ResourceLocation BRUTE_SIZE_ID =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "variant_brute_size");

    /**
     * The skeleton-family mob keys that can roll a special variant. Wither
     * Skeleton is intentionally excluded — it already carries tier abilities and
     * boss-adjacent mobs are kept clean.
     */
    public static final Set<String> ELIGIBLE_KEYS = Set.of(
            "skeleton", "stray", "bogged"
    );

    public enum Variant {
        NONE, DEADEYE, BRUTE
    }

    private SkeletonVariantHandler() {}

    public static boolean isEligibleType(String toggleKey) {
        return toggleKey != null && ELIGIBLE_KEYS.contains(toggleKey);
    }

    /**
     * Apply a variant to the mob if eligible. Returns the variant that was
     * applied (or {@link Variant#NONE} if the mob is ineligible or the rolls
     * failed). Marking the processed tag is unconditional so a failed roll
     * isn't re-rolled when the chunk reloads.
     */
    public static Variant apply(Mob mob, String toggleKey, SpecialSkeletons cfg, RandomSource random) {
        if (mob == null || cfg == null || random == null) return Variant.NONE;
        if (!cfg.enabled) return Variant.NONE;
        if (!isEligibleType(toggleKey)) return Variant.NONE;
        if (!(mob instanceof AbstractSkeleton skeleton)) return Variant.NONE;
        if (skeleton.getTags().contains(PROCESSED_TAG)) return Variant.NONE;

        Variant chosen = rollVariant(cfg, random.nextInt(100), random.nextInt(100));

        try {
            switch (chosen) {
                case DEADEYE -> applyDeadeyeVariant(skeleton, cfg);
                case BRUTE -> applyBruteVariant(skeleton, cfg);
                case NONE -> { /* no-op */ }
            }

            // Vanilla calls reassessWeaponGoal() from the AbstractSkeleton
            // constructor and again at the end of finalizeSpawn — both run before
            // ServerEntityEvents.ENTITY_LOAD fires, i.e. before the variant tag
            // exists. Re-run it now, with the tag present, so AbstractSkeletonMixin
            // can override the bow goal's attack interval. Without this the cadence
            // silently never changes.
            if (chosen != Variant.NONE) {
                skeleton.reassessWeaponGoal();
            }
        } catch (Exception e) {
            Tribulation.LOGGER.warn("Failed to apply skeleton variant {} to {}", chosen, mob, e);
        }

        skeleton.addTag(PROCESSED_TAG);
        return chosen;
    }

    /**
     * Pure variant selection: deadeye-first, then brute, mutually exclusive.
     * Rolls are integers in [0, 100); a roll below the configured chance wins.
     */
    public static Variant rollVariant(SpecialSkeletons cfg, int deadeyeRoll, int bruteRoll) {
        if (cfg == null) return Variant.NONE;
        if (cfg.deadeyeSkeletonChance > 0 && deadeyeRoll < cfg.deadeyeSkeletonChance) {
            return Variant.DEADEYE;
        }
        if (cfg.bruteSkeletonChance > 0 && bruteRoll < cfg.bruteSkeletonChance) {
            return Variant.BRUTE;
        }
        return Variant.NONE;
    }

    private static void applyDeadeyeVariant(AbstractSkeleton skeleton, SpecialSkeletons cfg) {
        // Always carry the per-variant tag, even when the malus is 0, so the tag
        // remains the canonical detection signal and the bow-cadence mixin fires.
        skeleton.addTag(DEADEYE_TAG);

        if (cfg.deadeyeSkeletonMalusHealth > 0) {
            setAttributeModifier(skeleton, Attributes.MAX_HEALTH, DEADEYE_HEALTH_ID,
                    -cfg.deadeyeSkeletonMalusHealth, AttributeModifier.Operation.ADD_VALUE);
        }
    }

    private static void applyBruteVariant(AbstractSkeleton skeleton, SpecialSkeletons cfg) {
        skeleton.addTag(BRUTE_TAG);

        if (cfg.bruteSkeletonBonusHealth > 0) {
            setAttributeModifier(skeleton, Attributes.MAX_HEALTH, BRUTE_HEALTH_ID,
                    cfg.bruteSkeletonBonusHealth, AttributeModifier.Operation.ADD_VALUE);
        }
        if (cfg.bruteSkeletonBonusKnockbackResistance > 0) {
            setAttributeModifier(skeleton, Attributes.KNOCKBACK_RESISTANCE, BRUTE_KNOCKBACK_ID,
                    cfg.bruteSkeletonBonusKnockbackResistance, AttributeModifier.Operation.ADD_VALUE);
        }

        // Size: vanilla Attributes.SCALE drives both render and hitbox (exactly as
        // Big Zombie does), so a single attribute modifier syncs automatically
        // through the existing attribute packet — no TrackedData or renderer mixin.
        double sizeDelta = cfg.bruteSkeletonSize - 1.0;
        if (sizeDelta != 0) {
            setAttributeModifier(skeleton, Attributes.SCALE, BRUTE_SIZE_ID,
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
