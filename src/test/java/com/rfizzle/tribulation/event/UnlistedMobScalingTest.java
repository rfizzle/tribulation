// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.MobScaling;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TribulationConfig#resolveScalingForEntity} — the
 * precedence ladder that decides which {@link MobScaling} applies to a given
 * entity. Uses the boolean overload so the {@code instanceof Monster} branch
 * can be exercised without bootstrapping Minecraft and instantiating a real
 * mob.
 *
 * <p>MobScalingHandler integrates this via its handler callback; the event
 * plumbing itself is integration-tested in-game.
 */
class UnlistedMobScalingTest {

    private static ResourceLocation id(String ns, String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }

    /** Build a distinctive MobScaling for identity comparisons. */
    private static MobScaling marker(double healthRate) {
        MobScaling m = new MobScaling();
        m.healthRate = healthRate;
        return m;
    }

    // ---- 1. Full-ID override ----

    @Test
    void fullIdOverride_beatsVanillaPathLookup() {
        TribulationConfig cfg = new TribulationConfig();
        MobScaling override = marker(0.5);
        cfg.scaling.put("minecraft:zombie", override);

        MobScaling resolved = cfg.resolveScalingForEntity(id("minecraft", "zombie"), true);

        // Full-ID override wins — not the "zombie" path entry.
        assertSame(override, resolved);
        assertNotSame(cfg.scaling.get("zombie"), resolved);
    }

    @Test
    void fullIdOverride_beatsModdedFallback() {
        TribulationConfig cfg = new TribulationConfig();
        MobScaling override = marker(0.5);
        cfg.scaling.put("mutantmonsters:mutant_zombie", override);

        MobScaling resolved = cfg.resolveScalingForEntity(
                id("mutantmonsters", "mutant_zombie"), true);

        assertSame(override, resolved);
        assertNotSame(cfg.unlistedHostileMobs.scaling, resolved);
    }

    @Test
    void fullIdOverride_worksForBothVanillaAndModdedNamespaces() {
        TribulationConfig cfg = new TribulationConfig();
        MobScaling vanillaOverride = marker(0.7);
        MobScaling moddedOverride = marker(0.3);
        cfg.scaling.put("minecraft:skeleton", vanillaOverride);
        cfg.scaling.put("moddedmod:custom", moddedOverride);

        assertSame(vanillaOverride, cfg.resolveScalingForEntity(id("minecraft", "skeleton"), true));
        assertSame(moddedOverride, cfg.resolveScalingForEntity(id("moddedmod", "custom"), true));
    }

    // ---- 2. Vanilla path lookup ----

    @Test
    void vanillaPath_returnsPerMobScalingWhenToggleEnabled() {
        TribulationConfig cfg = new TribulationConfig();

        MobScaling resolved = cfg.resolveScalingForEntity(id("minecraft", "zombie"), true);

        assertNotNull(resolved);
        assertSame(cfg.scaling.get("zombie"), resolved);
    }

    @Test
    void vanillaPath_disabledToggleReturnsNull_doesNotFallThroughToFallback() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.mobToggles.put("zombie", false);

        MobScaling resolved = cfg.resolveScalingForEntity(id("minecraft", "zombie"), true);

        // Explicit no-scale wins — fallback is NOT used even though enabled.
        assertNull(resolved);
    }

    @Test
    void vanillaPath_unlistedVanillaMob_returnsNull() {
        // e.g. phantom — intentionally omitted from mobToggles per DESIGN.md
        TribulationConfig cfg = new TribulationConfig();

        MobScaling resolved = cfg.resolveScalingForEntity(id("minecraft", "phantom"), true);

        // Not in mobToggles → isMobEnabled is false → return null. Fallback is
        // reserved for non-minecraft namespaces, so phantom never gets it.
        assertNull(resolved);
    }

    // ---- 3. Modded fallback ----

    @Test
    void moddedFallback_appliesToUnlistedMonsterFromNonExcludedNamespace() {
        TribulationConfig cfg = new TribulationConfig();

        MobScaling resolved = cfg.resolveScalingForEntity(
                id("mutantmonsters", "mutant_zombie"), true);

        assertNotNull(resolved);
        assertSame(cfg.unlistedHostileMobs.scaling, resolved);
        // Default fallback: health + damage only.
        assertTrue(resolved.healthRate > 0);
        assertTrue(resolved.damageRate > 0);
        assertEquals(0.0, resolved.speedRate);
        assertEquals(0.0, resolved.armorRate);
        assertEquals(0.0, resolved.toughnessRate);
    }

    @Test
    void moddedFallback_skipsWhenDisabled() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.unlistedHostileMobs.enabled = false;

        MobScaling resolved = cfg.resolveScalingForEntity(
                id("mutantmonsters", "mutant_zombie"), true);

        assertNull(resolved);
    }

    @Test
    void moddedFallback_skipsNonMonsterEntity() {
        TribulationConfig cfg = new TribulationConfig();

        // instanceof Monster is false → do not scale even with enabled fallback.
        MobScaling resolved = cfg.resolveScalingForEntity(
                id("someanimalsmod", "passive_critter"), false);

        assertNull(resolved);
    }

    @Test
    void moddedFallback_respectsExcludedNamespaces() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.unlistedHostileMobs.excludedNamespaces = new ArrayList<>(
                List.of("bosses_of_mass_destruction"));

        assertNull(cfg.resolveScalingForEntity(
                id("bosses_of_mass_destruction", "obsidilith"), true));
        // Other namespaces still get the fallback.
        assertSame(cfg.unlistedHostileMobs.scaling,
                cfg.resolveScalingForEntity(id("othermod", "some_mob"), true));
    }

    @Test
    void moddedFallback_nullExcludedNamespacesListIsTreatedAsEmpty() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.unlistedHostileMobs.excludedNamespaces = null;

        assertSame(cfg.unlistedHostileMobs.scaling,
                cfg.resolveScalingForEntity(id("somemod", "some_mob"), true));
    }

    @Test
    void moddedFallback_nullUnlistedConfigDoesNotNpe() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.unlistedHostileMobs = null;

        assertNull(cfg.resolveScalingForEntity(id("somemod", "some_mob"), true));
    }

    // ---- Defaults ----

    @Test
    void defaults_unlistedHostileMobsScalingIsHealthAndDamageOnly() {
        TribulationConfig cfg = new TribulationConfig();

        assertTrue(cfg.unlistedHostileMobs.enabled);
        assertNotNull(cfg.unlistedHostileMobs.scaling);
        assertNotNull(cfg.unlistedHostileMobs.excludedNamespaces);
        assertTrue(cfg.unlistedHostileMobs.excludedNamespaces.isEmpty());

        MobScaling m = cfg.unlistedHostileMobs.scaling;
        assertEquals(0.010, m.healthRate);
        assertEquals(2.50, m.healthCap);
        assertEquals(0.015, m.damageRate);
        assertEquals(3.75, m.damageCap);
        assertEquals(0.0, m.speedRate);
        assertEquals(0.0, m.speedCap);
        assertEquals(0.0, m.followRangeRate);
        assertEquals(0.0, m.followRangeCap);
        assertEquals(0.0, m.armorRate);
        assertEquals(0.0, m.armorCap);
        assertEquals(0.0, m.toughnessRate);
        assertEquals(0.0, m.toughnessCap);
    }

    // ---- Null-type safety ----

    @Test
    void resolveScalingForEntity_nullTypeReturnsNull() {
        TribulationConfig cfg = new TribulationConfig();
        assertNull(cfg.resolveScalingForEntity(null, true));
        assertNull(cfg.resolveScalingForEntity(null, false));
    }
}
