// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.ability;

import com.rfizzle.tribulation.config.TribulationConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the declarative ability registry's query surface — the contract the
 * client tier detail panel relies on. These inspect only entry metadata and the
 * {@code enabled} predicates; they never invoke an entry's {@code apply} action
 * (that touches live entity state and is covered by {@code AbilitiesGameTest}).
 */
class MobAbilitiesTest {

    private static TribulationConfig defaultCfg() {
        return new TribulationConfig();
    }

    @Test
    void registry_hasOneEntryPerMobAbilityPair() {
        // 31 declarations across 21 mob keys. cave_spider reuses spider_web_placing
        // and endermite reuses silverfish_call_sleepers, so 29 distinct ability keys
        // — matching the 29 config toggles.
        assertEquals(31, MobAbilities.REGISTRY.size(), "expected 31 ability declarations");
        long distinctKeys = MobAbilities.REGISTRY.stream()
                .map(MobAbility::abilityKey)
                .distinct()
                .count();
        assertEquals(29, distinctKeys, "expected 29 distinct ability keys (one per toggle)");
    }

    @Test
    void registry_everyUnlockTierInRange() {
        for (MobAbility ability : MobAbilities.REGISTRY) {
            assertTrue(ability.unlockTier() >= 1 && ability.unlockTier() <= 5,
                    ability.abilityKey() + " unlock tier out of [1,5]: " + ability.unlockTier());
        }
    }

    @Test
    void activeAt_tierZero_isEmpty() {
        assertTrue(MobAbilities.activeAt(0, defaultCfg()).isEmpty(),
                "no ability unlocks at tier 0");
    }

    @Test
    void activeAt_eachTier_matchesUnlockTierFilter() {
        TribulationConfig cfg = defaultCfg();
        for (int t = 1; t <= 5; t++) {
            final int tier = t;
            long expected = MobAbilities.REGISTRY.stream()
                    .filter(a -> a.unlockTier() <= tier)
                    .count();
            List<MobAbility> active = MobAbilities.activeAt(tier, cfg);
            assertEquals(expected, active.size(), "active count at tier " + tier);
            assertTrue(active.stream().allMatch(a -> a.unlockTier() <= tier),
                    "tier " + tier + " returned an ability unlocked above it");
        }
    }

    @Test
    void activeAt_isCumulativeAcrossTiers() {
        TribulationConfig cfg = defaultCfg();
        for (int tier = 2; tier <= 5; tier++) {
            assertTrue(MobAbilities.activeAt(tier, cfg).size() >= MobAbilities.activeAt(tier - 1, cfg).size(),
                    "tier " + tier + " should include everything from tier " + (tier - 1));
        }
        // Default config has every toggle on, so tier 5 surfaces the whole registry.
        assertEquals(MobAbilities.REGISTRY.size(), MobAbilities.activeAt(5, cfg).size());
    }

    @Test
    void forMob_returnsOnlyThatMobInUnlockTierOrder() {
        List<MobAbility> zombie = MobAbilities.forMob("zombie");
        assertEquals(3, zombie.size(), "zombie has 3 abilities");
        assertTrue(zombie.stream().allMatch(a -> a.mobKey().equals("zombie")));
        for (int i = 1; i < zombie.size(); i++) {
            assertTrue(zombie.get(i - 1).unlockTier() <= zombie.get(i).unlockTier(),
                    "forMob entries must be in unlock-tier order");
        }
        assertTrue(MobAbilities.forMob("nonexistent_mob").isEmpty());
    }

    @Test
    void forMob_coversEveryConfiguredMobKey() {
        // Each mob key with abilities must resolve to at least one entry, and
        // every registry mob key must be a known config mob key.
        List<String> known = List.of(TribulationConfig.MOB_KEYS);
        for (MobAbility ability : MobAbilities.REGISTRY) {
            assertTrue(known.contains(ability.mobKey()),
                    "registry references unknown mob key: " + ability.mobKey());
        }
    }

    @Test
    void activeForMob_gatesByMobTierAndToggle() {
        TribulationConfig cfg = defaultCfg();
        // Zombie door breaking unlocks at tier 3.
        assertTrue(MobAbilities.activeForMob("zombie", 2, cfg).stream()
                .noneMatch(a -> a.abilityKey().equals("zombie_door_breaking")));
        assertTrue(MobAbilities.activeForMob("zombie", 3, cfg).stream()
                .anyMatch(a -> a.abilityKey().equals("zombie_door_breaking")));

        cfg.abilities.zombieDoorBreaking = false;
        assertTrue(MobAbilities.activeForMob("zombie", 3, cfg).stream()
                .noneMatch(a -> a.abilityKey().equals("zombie_door_breaking")),
                "disabled toggle must drop the entry");
    }

    @Test
    void disablingOneToggle_removesOnlyItsEntryFromActiveAt() {
        TribulationConfig cfg = defaultCfg();
        int fullCount = MobAbilities.activeAt(5, cfg).size();

        cfg.abilities.zombieReinforcements = false;
        List<MobAbility> reduced = MobAbilities.activeAt(5, cfg);
        assertEquals(fullCount - 1, reduced.size(), "disabling one toggle drops exactly one entry");
        assertTrue(reduced.stream().noneMatch(a -> a.abilityKey().equals("zombie_reinforcements")));
        // An unrelated ability is untouched.
        assertTrue(reduced.stream().anyMatch(a -> a.abilityKey().equals("creeper_charged")));
    }

    /**
     * Orphan-toggle guard: flipping any one ability toggle from true to false
     * must flip at least one registry entry's {@code enabled} predicate. This
     * proves every toggle actually gates an entry — a field can't be compared to
     * a predicate directly, so the flip is the testable signal.
     */
    @Test
    void everyAbilityToggle_gatesAtLeastOneRegistryEntry() {
        TribulationConfig.Abilities allTrue = new TribulationConfig.Abilities();
        List<Field> toggles = java.util.Arrays.stream(TribulationConfig.Abilities.class.getFields())
                .filter(f -> f.getType() == boolean.class)
                .collect(Collectors.toList());
        assertEquals(29, toggles.size(), "expected 29 ability toggles");

        for (Field toggle : toggles) {
            TribulationConfig.Abilities flipped = new TribulationConfig.Abilities();
            try {
                toggle.setBoolean(flipped, false);
            } catch (IllegalAccessException e) {
                fail("could not flip toggle " + toggle.getName() + ": " + e);
            }
            boolean someEntryFlipped = MobAbilities.REGISTRY.stream()
                    .anyMatch(a -> a.enabled().test(allTrue) && !a.enabled().test(flipped));
            assertTrue(someEntryFlipped,
                    "toggle " + toggle.getName() + " gates no registry entry (orphaned toggle)");
        }
    }
}
