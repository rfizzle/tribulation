// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatParticleCullingTest {

    // Player looking straight along +X for the cone cases below.
    private static final double VX = 1.0, VY = 0.0, VZ = 0.0;

    @Test
    void inViewCone_directlyAhead_true() {
        assertTrue(ThreatParticleCulling.inViewCone(10.0, 0.0, 0.0, VX, VY, VZ));
    }

    @Test
    void inViewCone_directlyBehind_false() {
        assertFalse(ThreatParticleCulling.inViewCone(-10.0, 0.0, 0.0, VX, VY, VZ));
    }

    @Test
    void inViewCone_perpendicular_true() {
        // dot == 0, which is above the -0.35 threshold: a mob straight out to the
        // side is still within the wide front cone.
        assertTrue(ThreatParticleCulling.inViewCone(0.0, 0.0, 10.0, VX, VY, VZ));
    }

    @Test
    void inViewCone_justInsideAndJustOutsideBoundary() {
        // Aim a mob at exactly the threshold angle, then nudge either side of it.
        // to = (cos t, 0, sin t); dot with +X view is cos t. Choose cos t just
        // above / below VIEW_CONE_MIN_DOT.
        double justInsideDot = ThreatParticleCulling.VIEW_CONE_MIN_DOT + 0.02;
        double justOutsideDot = ThreatParticleCulling.VIEW_CONE_MIN_DOT - 0.02;
        assertTrue(onUnitVectorWithDot(justInsideDot));
        assertFalse(onUnitVectorWithDot(justOutsideDot));
        // The threshold is exclusive (strict >), so a mob exactly on it is culled.
        assertFalse(onUnitVectorWithDot(ThreatParticleCulling.VIEW_CONE_MIN_DOT));
    }

    @Test
    void inViewCone_atCamera_true() {
        // Near-zero eye->mob distance is always in cone (no meaningful direction).
        assertTrue(ThreatParticleCulling.inViewCone(0.0, 0.0, 0.0, VX, VY, VZ));
    }

    @Test
    void withinBudget_belowMax_true() {
        assertTrue(ThreatParticleCulling.withinBudget(0));
        assertTrue(ThreatParticleCulling.withinBudget(ThreatParticleCulling.MAX_PARTICLES_PER_TICK - 1));
    }

    @Test
    void withinBudget_atOrAboveMax_false() {
        assertFalse(ThreatParticleCulling.withinBudget(ThreatParticleCulling.MAX_PARTICLES_PER_TICK));
        assertFalse(ThreatParticleCulling.withinBudget(ThreatParticleCulling.MAX_PARTICLES_PER_TICK + 5));
    }

    @Test
    void maxParticlesPerTick_isPositive() {
        assertTrue(ThreatParticleCulling.MAX_PARTICLES_PER_TICK > 0);
    }

    /** Build a unit eye->mob vector in the X/Z plane whose dot with +X is {@code dot}. */
    private static boolean onUnitVectorWithDot(double dot) {
        double z = Math.sqrt(Math.max(0.0, 1.0 - dot * dot));
        return ThreatParticleCulling.inViewCone(dot, 0.0, z, VX, VY, VZ);
    }
}
