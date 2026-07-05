// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.ability;

import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbilityManagerTest {

    @Test
    void abilityId_returnsNamespacedLocation() {
        ResourceLocation id = AbilityManager.abilityId("zombie_sprint");
        assertEquals("tribulation", id.getNamespace());
        assertEquals("ability_zombie_sprint", id.getPath());
    }

    @Test
    void abilityId_differentNames_returnDifferentIds() {
        assertNotEquals(AbilityManager.abilityId("zombie_sprint"), AbilityManager.abilityId("hoglin_kb"));
    }

    // ---- selection predicate (AbilityManager#matches) --------------------
    // Zombie has three declared abilities gated by ascending unlock tier:
    //   zombie_reinforcements @1, zombie_door_breaking @3, zombie_sprinting @5.
    // These exercise the predicate that decides what applyAbilities applies,
    // without needing a live Mob (which is covered by AbilitiesGameTest).

    private static MobAbility ability(String mobKey, int unlockTier) {
        return MobAbilities.REGISTRY.stream()
                .filter(a -> a.mobKey().equals(mobKey) && a.unlockTier() == unlockTier)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no registry ability for " + mobKey + " @tier " + unlockTier));
    }

    @Test
    void matches_unlockTierReached_isTrue() {
        MobAbility sprint = ability("zombie", 5);
        assertTrue(AbilityManager.matches(sprint, 5, "zombie", new TribulationConfig()));
    }

    @Test
    void matches_unlockTierNotReached_isFalse() {
        MobAbility sprint = ability("zombie", 5);
        assertFalse(AbilityManager.matches(sprint, 4, "zombie", new TribulationConfig()),
                "an ability must not apply below its unlock tier");
    }

    @Test
    void matches_tierZeroOrNegative_isFalseForEveryAbility() {
        TribulationConfig cfg = new TribulationConfig();
        // Every ability unlocks at tier >= 1, so tier 0 and negative tiers select nothing.
        for (MobAbility a : MobAbilities.REGISTRY) {
            assertFalse(AbilityManager.matches(a, 0, a.mobKey(), cfg), a.abilityKey() + " @tier 0");
            assertFalse(AbilityManager.matches(a, -1, a.mobKey(), cfg), a.abilityKey() + " @tier -1");
        }
    }

    @Test
    void matches_mobKeyMismatch_isFalse() {
        MobAbility sprint = ability("zombie", 5);
        assertFalse(AbilityManager.matches(sprint, 5, "skeleton", new TribulationConfig()),
                "a zombie ability must not apply to a skeleton");
    }

    @Test
    void matches_unknownMobKey_isFalse() {
        MobAbility sprint = ability("zombie", 5);
        assertFalse(AbilityManager.matches(sprint, 5, "unknown_mob", new TribulationConfig()));
    }

    @Test
    void matches_disabledToggle_isFalse() {
        MobAbility sprint = ability("zombie", 5);
        TribulationConfig cfg = new TribulationConfig();
        cfg.abilities.zombieSprinting = false;
        assertFalse(AbilityManager.matches(sprint, 5, "zombie", cfg),
                "a disabled toggle must exclude its ability even at the unlock tier");
    }

    @Test
    void applyAbilities_guardConditions_areNoOps() {
        // The public entry point must swallow the degenerate inputs its callers
        // guard against (null mob/config/key, non-positive tier) rather than throw.
        TribulationConfig cfg = new TribulationConfig();
        assertDoesNotThrow(() -> {
            AbilityManager.applyAbilities(null, 5, "zombie", cfg);
            AbilityManager.applyAbilities(null, 5, "zombie", null);
            AbilityManager.applyAbilities(null, 5, null, cfg);
            AbilityManager.applyAbilities(null, 0, "zombie", cfg);
        });
    }

    @Test
    void defaultAbilities_allTogglesEnabledByDefault() {
        TribulationConfig.Abilities abilities = new TribulationConfig.Abilities();
        assertTrue(abilities.zombieReinforcements, "zombieReinforcements");
        assertTrue(abilities.zombieDoorBreaking, "zombieDoorBreaking");
        assertTrue(abilities.zombieSprinting, "zombieSprinting");
        assertTrue(abilities.creeperShorterFuse, "creeperShorterFuse");
        assertTrue(abilities.creeperCharged, "creeperCharged");
        assertTrue(abilities.skeletonSwordSwitch, "skeletonSwordSwitch");
        assertTrue(abilities.skeletonFlameArrows, "skeletonFlameArrows");
        assertTrue(abilities.spiderWebPlacing, "spiderWebPlacing");
        assertTrue(abilities.spiderCropTrample, "spiderCropTrample");
        assertTrue(abilities.spiderLeapAttack, "spiderLeapAttack");
        assertTrue(abilities.huskHunger, "huskHunger");
        assertTrue(abilities.witherSkeletonSprint, "witherSkeletonSprint");
        assertTrue(abilities.witherSkeletonFireAspect, "witherSkeletonFireAspect");
        assertTrue(abilities.drownedTrident, "drownedTrident");
        assertTrue(abilities.hoglinKnockbackResist, "hoglinKnockbackResist");
        assertTrue(abilities.zoglinFireResist, "zoglinFireResist");
        assertTrue(abilities.vindicatorResistance, "vindicatorResistance");
        assertTrue(abilities.zombifiedPiglinAggro, "zombifiedPiglinAggro");
        assertTrue(abilities.piglinCrossbow, "piglinCrossbow");
        assertTrue(abilities.straySlownessUpgrade, "straySlownessUpgrade");
        assertTrue(abilities.boggedPoisonUpgrade, "boggedPoisonUpgrade");
        assertTrue(abilities.witchLingeringPotions, "witchLingeringPotions");
        assertTrue(abilities.witchAggressiveHealing, "witchAggressiveHealing");
        assertTrue(abilities.pillagerQuickCharge, "pillagerQuickCharge");
        assertTrue(abilities.pillagerMultishot, "pillagerMultishot");
        assertTrue(abilities.vindicatorDoorBreaking, "vindicatorDoorBreaking");
        assertTrue(abilities.guardianFasterBeam, "guardianFasterBeam");
        assertTrue(abilities.ravagerRoarExpansion, "ravagerRoarExpansion");
        assertTrue(abilities.silverfishCallSleepers, "silverfishCallSleepers");
    }

    @Test
    void defaultAbilities_toggleCountMatchesMobKeyAbilityCount() {
        // 29 toggles cover all implemented abilities. If new abilities are added,
        // this test reminds the developer to also add a config toggle.
        TribulationConfig.Abilities abilities = new TribulationConfig.Abilities();
        long fieldCount = java.util.Arrays.stream(abilities.getClass().getFields())
                .filter(f -> f.getType() == boolean.class)
                .count();
        assertEquals(29, fieldCount, "unexpected number of ability toggles — did you add a new ability?");
    }
}
