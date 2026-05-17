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

    @Test
    void applyAbilities_nullMob_noException() {
        TribulationConfig cfg = new TribulationConfig();
        assertDoesNotThrow(() -> AbilityManager.applyAbilities(null, 5, "zombie", cfg));
    }

    @Test
    void applyAbilities_nullConfig_noException() {
        assertDoesNotThrow(() -> AbilityManager.applyAbilities(null, 5, "zombie", null));
    }

    @Test
    void applyAbilities_nullMobKey_noException() {
        TribulationConfig cfg = new TribulationConfig();
        assertDoesNotThrow(() -> AbilityManager.applyAbilities(null, 5, null, cfg));
    }

    @Test
    void applyAbilities_zeroTier_noException() {
        TribulationConfig cfg = new TribulationConfig();
        assertDoesNotThrow(() -> AbilityManager.applyAbilities(null, 0, "zombie", cfg));
    }

    @Test
    void applyAbilities_negativeTier_noException() {
        TribulationConfig cfg = new TribulationConfig();
        assertDoesNotThrow(() -> AbilityManager.applyAbilities(null, -1, "zombie", cfg));
    }

    @Test
    void applyAbilities_unknownMobKey_noException() {
        TribulationConfig cfg = new TribulationConfig();
        assertDoesNotThrow(() -> AbilityManager.applyAbilities(null, 5, "unknown_mob", cfg));
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
    }

    @Test
    void defaultAbilities_toggleCountMatchesMobKeyAbilityCount() {
        // 19 toggles cover all implemented abilities. If new abilities are added,
        // this test reminds the developer to also add a config toggle.
        TribulationConfig.Abilities abilities = new TribulationConfig.Abilities();
        long fieldCount = java.util.Arrays.stream(abilities.getClass().getFields())
                .filter(f -> f.getType() == boolean.class)
                .count();
        assertEquals(19, fieldCount, "unexpected number of ability toggles — did you add a new ability?");
    }
}
