package com.rfizzle.tribulation.compat.meridian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * Pure-core coverage for the Meridian enchant selection math. No Meridian jar and no live
 * enchantment registry — every case is plain arithmetic over the candidate arrays.
 */
class MeridianEnchantSelectionTest {

    @Test
    void bonusEnchantCount_scalesWithTier() {
        assertEquals(0, MeridianEnchantSelection.bonusEnchantCount(1));
        assertEquals(0, MeridianEnchantSelection.bonusEnchantCount(3));
        assertEquals(1, MeridianEnchantSelection.bonusEnchantCount(4));
        assertEquals(2, MeridianEnchantSelection.bonusEnchantCount(5));
        assertEquals(2, MeridianEnchantSelection.bonusEnchantCount(6));
    }

    @Test
    void effectiveMaxLevel_takesTheSmallerCapAndFloorsAtZero() {
        assertEquals(3, MeridianEnchantSelection.effectiveMaxLevel(3, 4), "Tribulation cap is lower");
        assertEquals(2, MeridianEnchantSelection.effectiveMaxLevel(4, 2), "Meridian cap is lower");
        assertEquals(0, MeridianEnchantSelection.effectiveMaxLevel(5, -1), "Meridian disabled -> 0");
        assertEquals(0, MeridianEnchantSelection.effectiveMaxLevel(0, 3), "no tier budget -> 0");
    }

    @Test
    void rollLevels_zeroDesiredCount_choosesNothing() {
        int[] result = MeridianEnchantSelection.rollLevels(new int[] {3, 3, 3}, 0, RandomSource.create(1));
        assertEquals(0, countChosen(result));
    }

    @Test
    void rollLevels_allCapsZero_choosesNothing() {
        int[] result = MeridianEnchantSelection.rollLevels(new int[] {0, 0, 0}, 2, RandomSource.create(1));
        assertEquals(0, countChosen(result));
    }

    @Test
    void rollLevels_picksExactlyMinOfDesiredAndEligible() {
        // Four candidates, only two eligible (positive cap); ask for three -> two chosen.
        int[] caps = {0, 4, 0, 4};
        int[] result = MeridianEnchantSelection.rollLevels(caps, 3, RandomSource.create(7));
        assertEquals(2, countChosen(result), "only the two eligible candidates can be chosen");
        assertEquals(0, result[0], "ineligible candidate stays unchosen");
        assertEquals(0, result[2], "ineligible candidate stays unchosen");
    }

    @Test
    void rollLevels_neverExceedsPerCandidateCapAndStaysAtLeastOne() {
        int[] caps = {1, 2, 3, 5};
        for (long seed = 0; seed < 500; seed++) {
            int[] result = MeridianEnchantSelection.rollLevels(caps, 4, RandomSource.create(seed));
            int chosen = 0;
            for (int i = 0; i < caps.length; i++) {
                if (result[i] != 0) {
                    chosen++;
                    assertTrue(result[i] >= 1 && result[i] <= caps[i],
                            "level " + result[i] + " out of [1," + caps[i] + "] at index " + i);
                }
            }
            assertEquals(4, chosen, "all four eligible candidates chosen when desiredCount == eligible");
        }
    }

    @Test
    void rollLevels_singleEligibleCandidate_alwaysThatOne() {
        int[] caps = {0, 0, 2, 0};
        for (long seed = 0; seed < 50; seed++) {
            int[] result = MeridianEnchantSelection.rollLevels(caps, 1, RandomSource.create(seed));
            assertEquals(1, countChosen(result));
            assertTrue(result[2] >= 1 && result[2] <= 2, "the only eligible candidate is enchanted");
        }
    }

    private static int countChosen(int[] levels) {
        int count = 0;
        for (int level : levels) {
            if (level != 0) {
                count++;
            }
        }
        return count;
    }
}
