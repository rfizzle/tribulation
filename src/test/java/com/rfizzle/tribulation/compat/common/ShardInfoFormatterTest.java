// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.config.TribulationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardInfoFormatterTest {

    private static final String SIDE_EFFECTS_LINE =
            "Applies Slowness II, Mining Fatigue II, and Weakness II for 10 seconds.";
    private static final String CONFIGURABLE_LINE =
            "All values are configurable by the server operator.";

    @Test
    void defaultConfig_producesAllLines() {
        List<String> lines = ShardInfoFormatter.infoLines(new TribulationConfig());

        // drop line, blank, consume line, side-effects line, blank, configurable line
        assertEquals(6, lines.size());
        assertEquals("Drops from hostile mobs scaled to level 25+ with a 0.5% chance per kill.",
                lines.get(0));
        assertEquals("", lines.get(1));
        assertEquals("Right-click to consume and reduce your difficulty level by 5.",
                lines.get(2));
        assertEquals(SIDE_EFFECTS_LINE, lines.get(3));
        assertEquals("", lines.get(4));
        assertEquals(CONFIGURABLE_LINE, lines.get(5));
    }

    @Test
    void sideEffectsDisabled_omitsSideEffectsLine() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.shards.sideEffects = false;

        List<String> lines = ShardInfoFormatter.infoLines(cfg);

        assertEquals(5, lines.size());
        assertFalse(lines.contains(SIDE_EFFECTS_LINE));
        assertEquals(CONFIGURABLE_LINE, lines.get(lines.size() - 1));
    }

    @Test
    void customValues_appearInText() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.shards.dropStartLevel = 100;
        cfg.shards.dropChance = 0.1;   // -> "10.0%"
        cfg.shards.shardPower = 3;

        List<String> lines = ShardInfoFormatter.infoLines(cfg);

        assertEquals("Drops from hostile mobs scaled to level 100+ with a 10.0% chance per kill.",
                lines.get(0));
        assertEquals("Right-click to consume and reduce your difficulty level by 3.",
                lines.get(2));
    }

    @Test
    void noArgOverload_fallsBackToDefaultsWhenConfigUnset() {
        // Tribulation.getConfig() is null outside a running mod, so this exercises
        // the default-config fallback path and must never throw or return empty.
        List<String> lines = ShardInfoFormatter.infoLines();

        assertFalse(lines.isEmpty());
        assertTrue(lines.get(0).startsWith("Drops from hostile mobs scaled to level"));
        assertEquals(CONFIGURABLE_LINE, lines.get(lines.size() - 1));
    }
}
