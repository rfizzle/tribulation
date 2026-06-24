package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig.Xp;
import com.rfizzle.tribulation.scaling.ScalingEngine;

/**
 * Bonus XP rewards for scaled mobs. The multiplier is applied via
 * {@code LivingEntityExperienceMixin} so it fires through the vanilla XP drop
 * path; this class owns the pure-math helpers it calls.
 *
 * <p>The mob's "difficulty factor" is derived from
 * {@link ScalingEngine#readHealthScalingFactor} — reading back the MAX_HEALTH
 * axis modifiers applied at spawn time. No extra per-entity state is stored;
 * the existing persistent attribute modifiers are the source of truth, so the
 * factor persists automatically across chunk unload, save/load, and server
 * restarts.
 */
public final class XpRewardHandler {

    private XpRewardHandler() {}

    /**
     * Compute the XP multiplier for a mob with the given scaling factor:
     * {@code 1 + healthFactor * xpMultiplier}. No separate ceiling is applied —
     * {@code healthFactor} is already bounded upstream by
     * {@code statCaps.maxFactorHealth}, so {@code xpMultiplier} is a plain gain
     * dial. Returns 1.0 when disabled ({@code xpMultiplier <= 0}) or when the
     * mob has no scaling.
     */
    public static double computeXpMultiplier(double healthFactor, Xp cfg) {
        if (cfg == null || cfg.xpMultiplier <= 0) return 1.0;
        if (healthFactor <= 0) return 1.0;
        return 1.0 + healthFactor * cfg.xpMultiplier;
    }

    /**
     * Apply the XP multiplier to a base XP amount. Rounds to the nearest int
     * so a tiny factor on a 1-XP drop doesn't silently floor back to 1.
     */
    public static int applyXpMultiplier(int baseXp, double healthFactor, Xp cfg) {
        if (baseXp <= 0) return baseXp;
        double mult = computeXpMultiplier(healthFactor, cfg);
        if (mult == 1.0) return baseXp;
        long scaled = Math.round(baseXp * mult);
        if (scaled > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) scaled;
    }
}
