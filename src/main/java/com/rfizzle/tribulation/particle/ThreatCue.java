package com.rfizzle.tribulation.particle;

/**
 * Pure decision logic for which threat-telegraphing cue (if any) a mob should
 * emit. Lives in the {@code main} source set — free of any client/{@code Level}
 * dependency — so the branch table is unit-testable and shared by the
 * client-side emitter.
 */
public final class ThreatCue {
    /** The cue to emit, or {@link #NONE}. */
    public enum Type { NONE, TIER, BIG, SPEED }

    /** Zombie variant detected from synced attribute modifiers. */
    public enum Variant { NONE, BIG, SPEED }

    private ThreatCue() {}

    /**
     * Decide the cue. Variant cues ({@link Type#BIG}/{@link Type#SPEED}) ignore
     * {@code minimumTier} — a visibly-huge or blink-fast low-tier zombie still
     * telegraphs — while the generic {@link Type#TIER} cue is gated by it.
     */
    public static Type decide(boolean enabled, boolean invisible, boolean scaled,
                              Variant variant, int tier, int minimumTier) {
        if (!enabled || invisible || !scaled) return Type.NONE;
        if (variant == Variant.BIG) return Type.BIG;
        if (variant == Variant.SPEED) return Type.SPEED;
        if (tier >= minimumTier) return Type.TIER;
        return Type.NONE;
    }
}
