// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig.SpecialZombies;
import com.rfizzle.tribulation.event.ZombieVariantHandler.Variant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for the variant handler. Eligibility + roll selection can
 * be exercised without bootstrapping Minecraft; applying the modifiers is
 * integration-tested in-game (requires a Mob with an attached Level/Server).
 */
class ZombieVariantHandlerTest {

    private static SpecialZombies config(boolean enabled, int speedChance, int bigChance) {
        SpecialZombies cfg = new SpecialZombies();
        cfg.enabled = enabled;
        cfg.speedZombieChance = speedChance;
        cfg.bigZombieChance = bigChance;
        return cfg;
    }

    // ---- Eligibility ----

    @Test
    void isEligibleType_acceptsAllFourZombieFamilyKeys() {
        assertTrue(ZombieVariantHandler.isEligibleType("zombie"));
        assertTrue(ZombieVariantHandler.isEligibleType("husk"));
        assertTrue(ZombieVariantHandler.isEligibleType("drowned"));
        assertTrue(ZombieVariantHandler.isEligibleType("zombified_piglin"));
    }

    @Test
    void isEligibleType_rejectsZombieVillagerAndUnrelated() {
        assertFalse(ZombieVariantHandler.isEligibleType("zombie_villager"));
        assertFalse(ZombieVariantHandler.isEligibleType("skeleton"));
        assertFalse(ZombieVariantHandler.isEligibleType("creeper"));
        assertFalse(ZombieVariantHandler.isEligibleType("pillager"));
    }

    @Test
    void isEligibleType_rejectsNullAndEmpty() {
        assertFalse(ZombieVariantHandler.isEligibleType(null));
        assertFalse(ZombieVariantHandler.isEligibleType(""));
    }

    // ---- Roll selection ----

    @Test
    void rollVariant_speedWinsWhenRollBelowSpeedChance() {
        SpecialZombies cfg = config(true, 10, 10);
        // speedRoll 0..9 should trigger Speed
        assertEquals(Variant.SPEED, ZombieVariantHandler.rollVariant(cfg, 0, 50));
        assertEquals(Variant.SPEED, ZombieVariantHandler.rollVariant(cfg, 9, 50));
    }

    @Test
    void rollVariant_bigWinsOnlyWhenSpeedFails() {
        SpecialZombies cfg = config(true, 10, 10);
        // speedRoll=10 fails the 10-chance; bigRoll 0..9 then triggers Big
        assertEquals(Variant.BIG, ZombieVariantHandler.rollVariant(cfg, 10, 0));
        assertEquals(Variant.BIG, ZombieVariantHandler.rollVariant(cfg, 50, 9));
    }

    @Test
    void rollVariant_noneWhenBothRollsFail() {
        SpecialZombies cfg = config(true, 10, 10);
        assertEquals(Variant.NONE, ZombieVariantHandler.rollVariant(cfg, 10, 10));
        assertEquals(Variant.NONE, ZombieVariantHandler.rollVariant(cfg, 99, 99));
    }

    @Test
    void rollVariant_speedChecksFirst_mutuallyExclusive() {
        // Both rolls succeed; speed must be chosen (not big).
        SpecialZombies cfg = config(true, 100, 100);
        assertEquals(Variant.SPEED, ZombieVariantHandler.rollVariant(cfg, 0, 0));
        assertEquals(Variant.SPEED, ZombieVariantHandler.rollVariant(cfg, 50, 0));
    }

    @Test
    void rollVariant_zeroChanceSkipsThatVariant() {
        SpecialZombies cfg = config(true, 0, 10);
        // speedZombieChance=0 — no matter the roll, speed never wins
        assertEquals(Variant.BIG, ZombieVariantHandler.rollVariant(cfg, 0, 5));
        assertEquals(Variant.NONE, ZombieVariantHandler.rollVariant(cfg, 0, 50));

        SpecialZombies cfg2 = config(true, 10, 0);
        assertEquals(Variant.SPEED, ZombieVariantHandler.rollVariant(cfg2, 5, 0));
        assertEquals(Variant.NONE, ZombieVariantHandler.rollVariant(cfg2, 50, 0));
    }

    @Test
    void rollVariant_nullConfigReturnsNone() {
        assertEquals(Variant.NONE, ZombieVariantHandler.rollVariant(null, 0, 0));
    }

    @Test
    void rollVariant_hundredPercentAlwaysWins() {
        SpecialZombies cfg = config(true, 100, 100);
        // 100% speed chance: roll=99 still < 100
        assertEquals(Variant.SPEED, ZombieVariantHandler.rollVariant(cfg, 99, 99));
    }

    // ---- Constants ----

    @Test
    void processedTag_isStableStringConstant() {
        assertEquals("tribulation_variant_processed", ZombieVariantHandler.PROCESSED_TAG);
    }

    @Test
    void eligibleKeys_containsExactlyFourZombieFamily() {
        assertEquals(4, ZombieVariantHandler.ELIGIBLE_KEYS.size());
        assertTrue(ZombieVariantHandler.ELIGIBLE_KEYS.contains("zombie"));
        assertTrue(ZombieVariantHandler.ELIGIBLE_KEYS.contains("husk"));
        assertTrue(ZombieVariantHandler.ELIGIBLE_KEYS.contains("drowned"));
        assertTrue(ZombieVariantHandler.ELIGIBLE_KEYS.contains("zombified_piglin"));
    }

    @Test
    void modifierIds_useFizzleNamespace() {
        assertEquals("tribulation", ZombieVariantHandler.BIG_HEALTH_ID.getNamespace());
        assertEquals("tribulation", ZombieVariantHandler.SPEED_HEALTH_ID.getNamespace());
        assertEquals("variant_big_health", ZombieVariantHandler.BIG_HEALTH_ID.getPath());
        assertEquals("variant_speed_speed", ZombieVariantHandler.SPEED_SPEED_ID.getPath());
    }
}
