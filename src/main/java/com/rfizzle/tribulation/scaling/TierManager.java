package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.config.TribulationConfig.Tiers;

/**
 * Tier 0..5 classifier. Tiers gate the ability system — see
 * {@link com.rfizzle.tribulation.ability.AbilityManager}. A mob's tier
 * is derived from the nearest player's difficulty level at spawn and is
 * frozen for the rest of the mob's life along with the rest of its scaling.
 *
 * The threshold check is inclusive on the lower bound: a player at exactly
 * {@code tier1} levels yields tier 1, and so on up to {@code tier5}.
 */
public final class TierManager {
    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 5;

    private TierManager() {}

    public static int getTier(int playerLevel, Tiers tiers) {
        if (tiers == null) return MIN_TIER;
        if (playerLevel >= tiers.tier5) return 5;
        if (playerLevel >= tiers.tier4) return 4;
        if (playerLevel >= tiers.tier3) return 3;
        if (playerLevel >= tiers.tier2) return 2;
        if (playerLevel >= tiers.tier1) return 1;
        return MIN_TIER;
    }
}
