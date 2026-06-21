// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.scaling;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.ScalingMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math coverage of the level→tier pipeline {@code TrialSpawnerMixin} and
 * {@code TrialSpawnerDataMixin} run over a spawner's detected players: fold the
 * detected-player levels with the configured {@link ScalingMode}, then resolve a
 * tier and apply the ominous {@code minimumTier} gate. The live spawn path is
 * covered by {@code TrialSpawnerGameTest}.
 */
class TrialSpawnerScalingTest {

    private static int tierFor(ScalingMode mode, List<Integer> detectedLevels, TribulationConfig.Tiers tiers) {
        return TierManager.getTier(ScalingEngine.foldLevels(mode, detectedLevels), tiers);
    }

    @Test
    void detectedPlayers_foldToEffectiveLevelPerMode() {
        // Two detected players: levels 40 and 60 (spec scenario).
        List<Integer> detected = List.of(40, 60);

        assertEquals(60, ScalingEngine.foldLevels(ScalingMode.MAX, detected));
        assertEquals(50, ScalingEngine.foldLevels(ScalingMode.AVERAGE, detected));
        // NEAREST has no proximity data in the fold; it takes the first entry.
        assertEquals(40, ScalingEngine.foldLevels(ScalingMode.NEAREST, detected));
    }

    @Test
    void emptyDetectedList_foldsToZero() {
        assertEquals(0, ScalingEngine.foldLevels(ScalingMode.MAX, List.of()));
        assertEquals(0, ScalingEngine.foldLevels(ScalingMode.AVERAGE, List.of()));
        assertEquals(0, ScalingEngine.foldLevels(ScalingMode.NEAREST, List.of()));
    }

    @Test
    void detectedPlayers_resolveTierPerMode() {
        // Default breakpoints: tier1=50, tier2=100, tier3=150, tier4=200, tier5=250.
        TribulationConfig.Tiers tiers = new TribulationConfig().tiers;
        List<Integer> detected = List.of(40, 60);

        // MAX → level 60 → tier 1; AVERAGE → 50 → tier 1; NEAREST → 40 → tier 0.
        assertEquals(1, tierFor(ScalingMode.MAX, detected, tiers));
        assertEquals(1, tierFor(ScalingMode.AVERAGE, detected, tiers));
        assertEquals(0, tierFor(ScalingMode.NEAREST, detected, tiers));
    }

    @Test
    void ominousGate_onlyFiresAtOrAboveMinimumTier() {
        TribulationConfig cfg = new TribulationConfig();
        int minimumTier = cfg.trialSpawner.ominousUpgrade.minimumTier; // 3 by default

        // A single high-level player past the tier-3 breakpoint (150) clears the gate.
        int highTier = tierFor(ScalingMode.MAX, List.of(150), cfg.tiers);
        assertTrue(highTier >= minimumTier, "level 150 should reach the ominous minimum tier");

        // A mid-level player below the breakpoint does not.
        int lowTier = tierFor(ScalingMode.MAX, List.of(100), cfg.tiers);
        assertFalse(lowTier >= minimumTier, "level 100 should not reach the ominous minimum tier");
    }
}
