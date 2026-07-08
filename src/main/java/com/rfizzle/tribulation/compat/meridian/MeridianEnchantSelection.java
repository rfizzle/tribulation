package com.rfizzle.tribulation.compat.meridian;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * Pure selection and level-clamp math for the Meridian mob-equipment enchant bonus.
 *
 * <p>Deliberately free of every Meridian type — and of the enchantment registry — so it
 * unit-tests without the Meridian jar on the classpath, per the Concord API Standard's rule
 * that a compat mapping core stays sibling-free. {@link MeridianEquipmentCompat} owns the
 * foreign references and the registry lookups; this class owns only the arithmetic.
 */
public final class MeridianEnchantSelection {

    private MeridianEnchantSelection() {
    }

    /**
     * How many bonus Meridian enchants a mob of the given tier draws on top of its vanilla
     * enchants: two at tier 5, one at tier 4, none below. Kept modest so a tier-5 mob is
     * flavored by Meridian rather than carrying a maxed everything.
     *
     * @param tier the mob's Tribulation tier
     * @return the number of bonus enchants to attempt
     */
    public static int bonusEnchantCount(int tier) {
        if (tier >= 5) {
            return 2;
        }
        return tier == 4 ? 1 : 0;
    }

    /**
     * Effective per-enchant max level: the smaller of Tribulation's per-tier cap and Meridian's
     * configured {@code getMaxLevel()}, floored at zero. Clamping to both means the integration
     * can only stay within the configured base, never exceed it.
     *
     * @param tierMaxLevel     Tribulation's per-tier enchant cap
     * @param meridianMaxLevel Meridian's configured max level for the enchant
     * @return the effective cap, never negative
     */
    public static int effectiveMaxLevel(int tierMaxLevel, int meridianMaxLevel) {
        return Math.max(0, Math.min(tierMaxLevel, meridianMaxLevel));
    }

    /**
     * Picks up to {@code desiredCount} distinct candidates — only those with an effective max
     * level of at least one — and rolls a low-biased level in {@code [1, effectiveMax]} for each.
     *
     * @param effectiveMax per-candidate effective caps (parallel to the caller's candidate list)
     * @param desiredCount how many candidates to try to enchant
     * @param random       the mob's random source
     * @return a per-candidate level array parallel to {@code effectiveMax}; {@code 0} marks a
     *         candidate that was not chosen
     */
    public static int[] rollLevels(int[] effectiveMax, int desiredCount, RandomSource random) {
        int[] chosenLevels = new int[effectiveMax.length];
        if (desiredCount <= 0) {
            return chosenLevels;
        }

        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < effectiveMax.length; i++) {
            if (effectiveMax[i] >= 1) {
                eligible.add(i);
            }
        }

        int pick = Math.min(desiredCount, eligible.size());
        // Partial Fisher-Yates: swap a random still-unpicked index into slot n, then take it.
        for (int n = 0; n < pick; n++) {
            int swapWith = n + random.nextInt(eligible.size() - n);
            int idx = eligible.get(swapWith);
            eligible.set(swapWith, eligible.get(n));
            eligible.set(n, idx);
            chosenLevels[idx] = rollLevel(effectiveMax[idx], random);
        }
        return chosenLevels;
    }

    /**
     * Low-biased level roll in {@code [1, max]}: two draws, take the minimum. Mirrors the roll
     * the vanilla-enchant path already uses in the equipment handlers.
     *
     * @param max    the inclusive maximum level ({@code <= 0} yields {@code 0})
     * @param random the mob's random source
     * @return the rolled level
     */
    public static int rollLevel(int max, RandomSource random) {
        if (max <= 0) {
            return 0;
        }
        if (max == 1) {
            return 1;
        }
        return Math.min(random.nextInt(max) + 1, random.nextInt(max) + 1);
    }
}
