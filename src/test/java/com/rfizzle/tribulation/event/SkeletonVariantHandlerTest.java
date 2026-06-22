// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig.SpecialSkeletons;
import com.rfizzle.tribulation.event.SkeletonVariantHandler.Variant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for the skeleton variant handler. Eligibility + roll
 * selection can be exercised without bootstrapping Minecraft; applying the
 * modifiers, the tag stamp, and the bow-interval re-invocation are
 * integration-tested in-game (requires an AbstractSkeleton with an attached
 * Level/Server and active goals).
 */
class SkeletonVariantHandlerTest {

    private static SpecialSkeletons config(boolean enabled, int deadeyeChance, int bruteChance) {
        SpecialSkeletons cfg = new SpecialSkeletons();
        cfg.enabled = enabled;
        cfg.deadeyeSkeletonChance = deadeyeChance;
        cfg.bruteSkeletonChance = bruteChance;
        return cfg;
    }

    // ---- Eligibility ----

    @Test
    void isEligibleType_acceptsSkeletonStrayBogged() {
        assertTrue(SkeletonVariantHandler.isEligibleType("skeleton"));
        assertTrue(SkeletonVariantHandler.isEligibleType("stray"));
        assertTrue(SkeletonVariantHandler.isEligibleType("bogged"));
    }

    @Test
    void isEligibleType_rejectsWitherSkeletonAndUnrelated() {
        assertFalse(SkeletonVariantHandler.isEligibleType("wither_skeleton"));
        assertFalse(SkeletonVariantHandler.isEligibleType("zombie"));
        assertFalse(SkeletonVariantHandler.isEligibleType("creeper"));
        assertFalse(SkeletonVariantHandler.isEligibleType("pillager"));
    }

    @Test
    void isEligibleType_rejectsNullAndEmpty() {
        assertFalse(SkeletonVariantHandler.isEligibleType(null));
        assertFalse(SkeletonVariantHandler.isEligibleType(""));
    }

    // ---- Roll selection ----

    @Test
    void rollVariant_deadeyeWinsWhenRollBelowDeadeyeChance() {
        SpecialSkeletons cfg = config(true, 10, 10);
        assertEquals(Variant.DEADEYE, SkeletonVariantHandler.rollVariant(cfg, 0, 50));
        assertEquals(Variant.DEADEYE, SkeletonVariantHandler.rollVariant(cfg, 9, 50));
    }

    @Test
    void rollVariant_bruteWinsOnlyWhenDeadeyeFails() {
        SpecialSkeletons cfg = config(true, 10, 10);
        // deadeyeRoll=10 fails the 10-chance; bruteRoll 0..9 then triggers Brute
        assertEquals(Variant.BRUTE, SkeletonVariantHandler.rollVariant(cfg, 10, 0));
        assertEquals(Variant.BRUTE, SkeletonVariantHandler.rollVariant(cfg, 50, 9));
    }

    @Test
    void rollVariant_noneWhenBothRollsFail() {
        SpecialSkeletons cfg = config(true, 10, 10);
        assertEquals(Variant.NONE, SkeletonVariantHandler.rollVariant(cfg, 10, 10));
        assertEquals(Variant.NONE, SkeletonVariantHandler.rollVariant(cfg, 99, 99));
    }

    @Test
    void rollVariant_deadeyeChecksFirst_mutuallyExclusive() {
        // Both rolls succeed; deadeye must be chosen (not brute).
        SpecialSkeletons cfg = config(true, 100, 100);
        assertEquals(Variant.DEADEYE, SkeletonVariantHandler.rollVariant(cfg, 0, 0));
        assertEquals(Variant.DEADEYE, SkeletonVariantHandler.rollVariant(cfg, 50, 0));
    }

    @Test
    void rollVariant_zeroChanceSkipsThatVariant() {
        SpecialSkeletons cfg = config(true, 0, 10);
        // deadeyeSkeletonChance=0 — no matter the roll, deadeye never wins
        assertEquals(Variant.BRUTE, SkeletonVariantHandler.rollVariant(cfg, 0, 5));
        assertEquals(Variant.NONE, SkeletonVariantHandler.rollVariant(cfg, 0, 50));

        SpecialSkeletons cfg2 = config(true, 10, 0);
        assertEquals(Variant.DEADEYE, SkeletonVariantHandler.rollVariant(cfg2, 5, 0));
        assertEquals(Variant.NONE, SkeletonVariantHandler.rollVariant(cfg2, 50, 0));
    }

    @Test
    void rollVariant_nullConfigReturnsNone() {
        assertEquals(Variant.NONE, SkeletonVariantHandler.rollVariant(null, 0, 0));
    }

    @Test
    void rollVariant_hundredPercentAlwaysWins() {
        SpecialSkeletons cfg = config(true, 100, 100);
        // 100% deadeye chance: roll=99 still < 100
        assertEquals(Variant.DEADEYE, SkeletonVariantHandler.rollVariant(cfg, 99, 99));
    }

    // ---- Constants ----

    @Test
    void processedTag_isStableStringConstant() {
        assertEquals("tribulation_skeleton_variant_processed", SkeletonVariantHandler.PROCESSED_TAG);
    }

    @Test
    void perVariantTags_areStableStringConstants() {
        assertEquals("tribulation_variant_deadeye", SkeletonVariantHandler.DEADEYE_TAG);
        assertEquals("tribulation_variant_brute", SkeletonVariantHandler.BRUTE_TAG);
    }

    @Test
    void eligibleKeys_containsExactlyThreeSkeletonFamily() {
        assertEquals(3, SkeletonVariantHandler.ELIGIBLE_KEYS.size());
        assertTrue(SkeletonVariantHandler.ELIGIBLE_KEYS.contains("skeleton"));
        assertTrue(SkeletonVariantHandler.ELIGIBLE_KEYS.contains("stray"));
        assertTrue(SkeletonVariantHandler.ELIGIBLE_KEYS.contains("bogged"));
    }

    @Test
    void modifierIds_useTribulationNamespace() {
        assertEquals("tribulation", SkeletonVariantHandler.DEADEYE_HEALTH_ID.getNamespace());
        assertEquals("tribulation", SkeletonVariantHandler.BRUTE_HEALTH_ID.getNamespace());
        assertEquals("tribulation", SkeletonVariantHandler.BRUTE_KNOCKBACK_ID.getNamespace());
        assertEquals("tribulation", SkeletonVariantHandler.BRUTE_SIZE_ID.getNamespace());
        assertEquals("variant_deadeye_health", SkeletonVariantHandler.DEADEYE_HEALTH_ID.getPath());
        assertEquals("variant_brute_health", SkeletonVariantHandler.BRUTE_HEALTH_ID.getPath());
        assertEquals("variant_brute_knockback", SkeletonVariantHandler.BRUTE_KNOCKBACK_ID.getPath());
        assertEquals("variant_brute_size", SkeletonVariantHandler.BRUTE_SIZE_ID.getPath());
    }
}
