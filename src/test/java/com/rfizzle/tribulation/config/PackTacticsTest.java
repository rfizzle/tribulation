// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.config;

import com.rfizzle.tribulation.config.TribulationConfig.PackTactics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-arithmetic coverage of the {@link PackTactics} helpers and their
 * validation clamps. No Minecraft imports — the gating formulas are
 * deliberately decoupled from the event/spawn pipeline so they can be
 * exercised here.
 */
class PackTacticsTest {

    private static PackTactics cfg(boolean enabled, int threshold, int bonus) {
        PackTactics pt = new PackTactics();
        pt.enabled = enabled;
        pt.tierThreshold = threshold;
        pt.groupSizeBonus = bonus;
        return pt;
    }

    // ---- isActiveAtTier ----

    @Test
    void isActiveAtTier_belowThreshold_inactive() {
        PackTactics pt = cfg(true, 3, 2);
        assertFalse(pt.isActiveAtTier(0));
        assertFalse(pt.isActiveAtTier(2));
    }

    @Test
    void isActiveAtTier_atOrAboveThreshold_active() {
        PackTactics pt = cfg(true, 3, 2);
        assertTrue(pt.isActiveAtTier(3));
        assertTrue(pt.isActiveAtTier(5));
    }

    @Test
    void isActiveAtTier_disabled_neverActive() {
        PackTactics pt = cfg(false, 0, 2);
        assertFalse(pt.isActiveAtTier(5));
    }

    // ---- spawnGroupBonus ----

    @Test
    void spawnGroupBonus_gatedByThreshold() {
        PackTactics pt = cfg(true, 3, 2);
        assertEquals(0, pt.spawnGroupBonus(2));
        assertEquals(2, pt.spawnGroupBonus(3));
        assertEquals(2, pt.spawnGroupBonus(5));
    }

    @Test
    void spawnGroupBonus_disabled_isZero() {
        PackTactics pt = cfg(false, 0, 2);
        assertEquals(0, pt.spawnGroupBonus(5));
    }

    // ---- defaults ----

    @Test
    void defaults_matchSpec() {
        PackTactics pt = new PackTactics();
        assertTrue(pt.enabled);
        assertEquals(3, pt.tierThreshold);
        assertEquals(16.0, pt.alertRadius, 1e-9);
        assertEquals(2, pt.groupSizeBonus);
        assertEquals(
                java.util.List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"),
                pt.eligibleMobs);
    }

    // ---- validation clamps ----

    @Test
    void validate_clampsOutOfRangeValues() {
        TribulationConfig config = new TribulationConfig();
        config.packTactics.tierThreshold = -1;
        config.packTactics.alertRadius = 1000.0;
        config.packTactics.groupSizeBonus = 99;
        config.validate();
        assertEquals(0, config.packTactics.tierThreshold);
        assertEquals(PackTactics.MAX_ALERT_RADIUS, config.packTactics.alertRadius, 1e-9);
        assertEquals(PackTactics.MAX_GROUP_SIZE_BONUS, config.packTactics.groupSizeBonus);
    }

    @Test
    void validate_clampsNegativeRadiusAndBonus() {
        TribulationConfig config = new TribulationConfig();
        config.packTactics.alertRadius = -5.0;
        config.packTactics.groupSizeBonus = -3;
        config.validate();
        assertEquals(0.0, config.packTactics.alertRadius, 1e-9);
        assertEquals(0, config.packTactics.groupSizeBonus);
    }
}
