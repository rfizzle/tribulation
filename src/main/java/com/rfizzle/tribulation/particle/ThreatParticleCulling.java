package com.rfizzle.tribulation.particle;

/**
 * Pure, Minecraft-free culling and budget math for the client-side threat
 * particle emitter. Lives in the {@code main} source set — free of any
 * client/{@code Level} dependency — so the view-cone and budget arithmetic is
 * unit-testable without bootstrapping the client, and the tick handler stays a
 * thin shell over these functions (see the {@code mc-world-render} skill's
 * "keep the math pure" guidance).
 */
public final class ThreatParticleCulling {
    /**
     * Maximum particles a single {@code onClientTick} call may spawn, across all
     * mobs and both cue types. Caps the total so a crowd of eligible tier-4+
     * mobs (a raid, a mob farm) can't flood the frame; generous enough that a
     * legitimate large fight still telegraphs.
     */
    public static final int MAX_PARTICLES_PER_TICK = 32;

    /**
     * Dot-product threshold for the view cone. The eye→mob direction is compared
     * (after normalization) against the player's normalized look vector; a value
     * below this is outside the cone and skipped. {@code -0.35} is a wide front
     * cone that still admits mobs just past the screen edge, so cues don't pop as
     * the player pans — matching the skill's documented threshold.
     */
    public static final double VIEW_CONE_MIN_DOT = -0.35;

    private ThreatParticleCulling() {}

    /**
     * Whether a mob lies within the player's view cone. {@code (toX, toY, toZ)}
     * is the eye→mob vector (mob position minus eye position) and
     * {@code (viewX, viewY, viewZ)} is the player's <em>normalized</em> look
     * vector. A mob effectively on top of the camera (near-zero distance) is
     * always considered in cone.
     */
    public static boolean inViewCone(double toX, double toY, double toZ,
                                     double viewX, double viewY, double viewZ) {
        double lenSqr = toX * toX + toY * toY + toZ * toZ;
        if (lenSqr < 1.0e-6) return true;
        double dot = toX * viewX + toY * viewY + toZ * viewZ;
        return dot / Math.sqrt(lenSqr) > VIEW_CONE_MIN_DOT;
    }

    /**
     * Whether another particle may be spawned given the count already spawned
     * this tick. The caller holds the counter, keeping this class stateless.
     */
    public static boolean withinBudget(int spawnedSoFar) {
        return spawnedSoFar < MAX_PARTICLES_PER_TICK;
    }
}
