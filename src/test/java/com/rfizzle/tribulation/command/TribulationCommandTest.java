// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.command;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.scaling.StructureBoostManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure helpers in {@link TribulationCommand}. The
 * Brigadier-wired paths require a ServerLevel and CommandSourceStack and are
 * exercised in-game; the formatting/duration logic and modifier-based variant
 * detection live here.
 */
class TribulationCommandTest {

    // ---- formatTicksAsDuration ----

    @Test
    void formatTicksAsDuration_subSecondReturnsTicks() {
        assertEquals("0t", TribulationCommand.formatTicksAsDuration(0));
        assertEquals("19t", TribulationCommand.formatTicksAsDuration(19));
    }

    @Test
    void formatTicksAsDuration_secondsAtOrAbove20() {
        assertEquals("1s", TribulationCommand.formatTicksAsDuration(20));
        assertEquals("59s", TribulationCommand.formatTicksAsDuration(59 * 20));
    }

    @Test
    void formatTicksAsDuration_minutes() {
        assertEquals("1m", TribulationCommand.formatTicksAsDuration(60 * 20));
        assertEquals("5m30s", TribulationCommand.formatTicksAsDuration(5 * 60 * 20 + 30 * 20));
        assertEquals("30m", TribulationCommand.formatTicksAsDuration(30 * 60 * 20));
    }

    @Test
    void formatTicksAsDuration_hoursMatchDesignDefault() {
        // 1 real-time hour @ 20 tps = 72000 ticks — matches DESIGN.md's levelUpTicks.
        assertEquals("1h", TribulationCommand.formatTicksAsDuration(72000));
        assertEquals("2h30m", TribulationCommand.formatTicksAsDuration(2 * 72000 + 30 * 60 * 20));
    }

    // ---- onOff ----

    @Test
    void onOff_rendersBooleans() {
        assertEquals("on", TribulationCommand.onOff(true));
        assertEquals("off", TribulationCommand.onOff(false));
    }

    // ---- formatConfigSummary ----

    @Test
    void formatConfigSummary_includesAllAxesAndCaps() {
        TribulationConfig cfg = new TribulationConfig();
        List<String> lines = TribulationCommand.formatConfigSummary(cfg);

        String joined = String.join("\n", lines);
        assertTrue(joined.contains("Max level: 250"), "shows max level");
        assertTrue(joined.contains("1h"), "level-up interval rendered as hours");
        assertTrue(joined.contains("Detection range"), "shows detection range");
        assertTrue(joined.contains("Axes:"), "has axes line");
        assertTrue(joined.contains("Distance:"), "distance details when enabled");
        assertTrue(joined.contains("Height:"), "height details when enabled");
        assertTrue(joined.contains("Stat caps:"), "lists stat caps");
        assertTrue(joined.contains("Death relief:"), "lists death relief");
        assertTrue(joined.contains("Shards:"), "lists shards");
        assertTrue(joined.contains("Bosses:"), "lists boss scaling");
        assertTrue(joined.contains("Tiers:"), "lists tier thresholds");
    }

    @Test
    void formatConfigSummary_omitsDistanceDetailsWhenDisabled() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.distanceScaling.enabled = false;
        List<String> lines = TribulationCommand.formatConfigSummary(cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("distance=off"));
        assertFalse(joined.contains("  Distance:"),
                "indented distance-detail line should be omitted when disabled");
    }

    @Test
    void formatConfigSummary_omitsHeightDetailsWhenDisabled() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.heightScaling.enabled = false;
        List<String> lines = TribulationCommand.formatConfigSummary(cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("height=off"));
        assertFalse(joined.contains("  Height:"));
    }

    // ---- formatPlayerInfo ----

    @Test
    void formatPlayerInfo_showsNameLevelTierAndProgress() {
        TribulationConfig cfg = new TribulationConfig();
        List<String> lines = TribulationCommand.formatPlayerInfo("Alice", 5, 0, 0, cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("Alice"), "includes player name");
        assertTrue(joined.contains("Level: 5 / " + cfg.general.maxLevel), "shows level / max level");
        assertTrue(joined.contains("tier "), "shows tier");
        assertTrue(joined.contains("Progress:"), "shows progress line");
        assertTrue(joined.contains("until next level"), "shows time remaining");
    }

    @Test
    void formatPlayerInfo_rendersProgressAsDuration() {
        TribulationConfig cfg = new TribulationConfig();
        // Halfway through a one-hour level @ 20 tps → 30 minutes remaining.
        int halfway = cfg.general.levelUpTicks / 2;
        List<String> lines = TribulationCommand.formatPlayerInfo("Bob", 10, halfway, 0, cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("30m"), "remaining time rendered as duration: " + joined);
    }

    @Test
    void formatPlayerInfo_atMaxLevelOmitsProgress() {
        TribulationConfig cfg = new TribulationConfig();
        List<String> lines = TribulationCommand.formatPlayerInfo("Carol", cfg.general.maxLevel, 0, 0, cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("max level reached"));
        assertFalse(joined.contains("until next level"));
    }

    // ---- Variant enum ----

    @Test
    void variantLabels_matchDesignTerminology() {
        assertEquals("none", TribulationCommand.Variant.NONE.label());
        assertEquals("big", TribulationCommand.Variant.BIG.label());
        assertEquals("speed", TribulationCommand.Variant.SPEED.label());
    }

    @Test
    void detectVariant_nullMobReturnsNone() {
        assertEquals(TribulationCommand.Variant.NONE,
                TribulationCommand.detectVariant(null));
    }

    // ---- Command structural constants ----

    @Test
    void rootCommand_isStable() {
        assertEquals("tribulation", TribulationCommand.ROOT);
    }

    @Test
    void inspectRange_isPositive() {
        assertTrue(TribulationCommand.INSPECT_RANGE > 0);
    }

    // ---- formatPlayerInfo with hearts ----

    @Test
    void formatPlayerInfo_showsHeartsWhenEnabledAndLost() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.hardcoreHearts.enabled = true;
        List<String> lines = TribulationCommand.formatPlayerInfo("Dave", 10, 0, 6, cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("Hearts:"), "shows hearts line when penalty exists");
        assertTrue(joined.contains("14/20"), "shows reduced max HP");
        assertTrue(joined.contains("6 half-hearts lost"), "shows amount lost");
    }

    @Test
    void formatPlayerInfo_omitsHeartsWhenDisabled() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.hardcoreHearts.enabled = false;
        List<String> lines = TribulationCommand.formatPlayerInfo("Eve", 10, 0, 6, cfg);
        String joined = String.join("\n", lines);
        assertFalse(joined.contains("Hearts:"), "no hearts line when feature disabled");
    }

    @Test
    void formatPlayerInfo_omitsHeartsWhenNoneLost() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.hardcoreHearts.enabled = true;
        List<String> lines = TribulationCommand.formatPlayerInfo("Frank", 10, 0, 0, cfg);
        String joined = String.join("\n", lines);
        assertFalse(joined.contains("Hearts:"), "no hearts line when no penalty");
    }

    // ---- formatConfigSummary with new features ----

    @Test
    void formatConfigSummary_includesHardcoreHeartsAndSoulInventory() {
        TribulationConfig cfg = new TribulationConfig();
        List<String> lines = TribulationCommand.formatConfigSummary(cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("Hardcore Hearts:"), "shows hardcore hearts");
        assertTrue(joined.contains("Soul Inventory:"), "shows soul inventory");
        assertTrue(joined.contains("off"), "features disabled by default");
    }

    @Test
    void formatConfigSummary_hardcoreHeartsShowsParams() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.hardcoreHearts.enabled = true;
        cfg.hardcoreHearts.heartsLostPerDeath = 4;
        List<String> lines = TribulationCommand.formatConfigSummary(cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("Hardcore Hearts: on"), "shows enabled state");
        assertTrue(joined.contains("-4/death"), "shows hearts lost per death");
    }

    @Test
    void formatConfigSummary_soulInventoryShowsEnchantId() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.soulInventory.enabled = true;
        cfg.soulInventory.soulboundEnchantment = "meridian:tether";
        List<String> lines = TribulationCommand.formatConfigSummary(cfg);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("Soul Inventory: on"), "shows enabled state");
        assertTrue(joined.contains("enchant=meridian:tether"), "shows enchantment ID");
    }

    // ---- formatStructureBoostLine ----

    @Test
    void formatStructureBoostLine_outsideZones_showsNone() {
        StructureBoostManager.BoostZone[] zones = {
                new StructureBoostManager.BoostZone(0, 0, 0, 16, 16, 16, 20,
                        ResourceLocation.parse("minecraft:fortress")),
        };
        assertEquals("Structure boost: +0  (none)",
                TribulationCommand.formatStructureBoostLine(zones, 100, 64, 100));
        assertEquals("Structure boost: +0  (none)",
                TribulationCommand.formatStructureBoostLine(StructureBoostManager.NO_ZONES, 100, 64, 100));
    }

    @Test
    void formatStructureBoostLine_insideZones_showsBoostAndIds() {
        StructureBoostManager.BoostZone[] zones = {
                new StructureBoostManager.BoostZone(0, 0, 0, 100, 100, 100, 20,
                        ResourceLocation.parse("minecraft:fortress")),
                new StructureBoostManager.BoostZone(40, 40, 40, 60, 60, 60, 30,
                        ResourceLocation.parse("minecraft:ancient_city")),
        };
        assertEquals("Structure boost: +30  (minecraft:fortress, minecraft:ancient_city)",
                TribulationCommand.formatStructureBoostLine(zones, 50, 50, 50),
                "largest boost applies; every containing zone is listed");
        assertEquals("Structure boost: +20  (minecraft:fortress)",
                TribulationCommand.formatStructureBoostLine(zones, 10, 10, 10));
    }
}
