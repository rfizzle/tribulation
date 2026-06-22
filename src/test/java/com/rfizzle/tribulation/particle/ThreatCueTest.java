// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.particle;

import com.rfizzle.tribulation.particle.ThreatCue.Type;
import com.rfizzle.tribulation.particle.ThreatCue.Variant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreatCueTest {

    @Test
    void disabled_returnsNone() {
        assertEquals(Type.NONE, ThreatCue.decide(false, false, true, Variant.NONE, 5, 4));
    }

    @Test
    void invisible_returnsNone() {
        assertEquals(Type.NONE, ThreatCue.decide(true, true, true, Variant.NONE, 5, 4));
        // Even a variant mob stays dark while invisible.
        assertEquals(Type.NONE, ThreatCue.decide(true, true, true, Variant.BIG, 5, 4));
    }

    @Test
    void unscaled_returnsNone() {
        assertEquals(Type.NONE, ThreatCue.decide(true, false, false, Variant.NONE, 5, 4));
    }

    @Test
    void nonVariant_belowMinimumTier_returnsNone() {
        assertEquals(Type.NONE, ThreatCue.decide(true, false, true, Variant.NONE, 3, 4));
    }

    @Test
    void nonVariant_atOrAboveMinimumTier_returnsTier() {
        assertEquals(Type.TIER, ThreatCue.decide(true, false, true, Variant.NONE, 4, 4));
        assertEquals(Type.TIER, ThreatCue.decide(true, false, true, Variant.NONE, 7, 4));
    }

    @Test
    void bigVariant_atLowTier_returnsBig() {
        // Variant cues ignore minimumTier — a tier-1 Big Zombie still telegraphs.
        assertEquals(Type.BIG, ThreatCue.decide(true, false, true, Variant.BIG, 1, 4));
    }

    @Test
    void speedVariant_atLowTier_returnsSpeed() {
        assertEquals(Type.SPEED, ThreatCue.decide(true, false, true, Variant.SPEED, 1, 4));
    }
}
