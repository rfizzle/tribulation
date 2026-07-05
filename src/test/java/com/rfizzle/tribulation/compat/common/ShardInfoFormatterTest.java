// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardInfoFormatterTest {

    private static final String DROP_KEY = "item.tribulation.shatter_shard.info.drop";
    private static final String CONSUME_KEY = "item.tribulation.shatter_shard.info.consume";
    private static final String SIDE_EFFECTS_KEY = "item.tribulation.shatter_shard.info.side_effects";
    private static final String CONFIGURABLE_KEY = "item.tribulation.shatter_shard.info.configurable";

    private static TranslatableContents translatable(Component c) {
        assertInstanceOf(TranslatableContents.class, c.getContents(),
                "expected a translatable line, got: " + c.getContents());
        return (TranslatableContents) c.getContents();
    }

    private static String key(Component c) {
        return translatable(c).getKey();
    }

    @Test
    void defaultConfig_producesAllLines() {
        List<Component> lines = ShardInfoFormatter.infoLines(new TribulationConfig());

        // drop line, blank, consume line, side-effects line, blank, configurable line
        assertEquals(6, lines.size());

        TranslatableContents drop = translatable(lines.get(0));
        assertEquals(DROP_KEY, drop.getKey());
        // level (int) and the pre-formatted percentage string are passed as args
        assertArrayEquals(new Object[]{25, "0.5"}, drop.getArgs());

        assertEquals("", lines.get(1).getString(), "line 1 is a blank spacer");

        TranslatableContents consume = translatable(lines.get(2));
        assertEquals(CONSUME_KEY, consume.getKey());
        assertArrayEquals(new Object[]{5}, consume.getArgs());

        assertEquals(SIDE_EFFECTS_KEY, key(lines.get(3)));
        assertEquals("", lines.get(4).getString(), "line 4 is a blank spacer");
        assertEquals(CONFIGURABLE_KEY, key(lines.get(5)));
    }

    @Test
    void sideEffectsDisabled_omitsSideEffectsLine() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.shards.sideEffects = false;

        List<Component> lines = ShardInfoFormatter.infoLines(cfg);

        assertEquals(5, lines.size());
        assertTrue(lines.stream()
                        .filter(c -> c.getContents() instanceof TranslatableContents)
                        .noneMatch(c -> SIDE_EFFECTS_KEY.equals(key(c))),
                "side-effects line must be omitted");
        assertEquals(CONFIGURABLE_KEY, key(lines.get(lines.size() - 1)));
    }

    @Test
    void customValues_appearInArgs() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.shards.dropStartLevel = 100;
        cfg.shards.dropChance = 0.1;   // -> "10.0"
        cfg.shards.shardPower = 3;

        List<Component> lines = ShardInfoFormatter.infoLines(cfg);

        assertArrayEquals(new Object[]{100, "10.0"}, translatable(lines.get(0)).getArgs());
        assertArrayEquals(new Object[]{3}, translatable(lines.get(2)).getArgs());
    }

    @Test
    void noArgOverload_fallsBackToDefaultsWhenConfigUnset() {
        // Tribulation.getConfig() is null outside a running mod, so this exercises
        // the default-config fallback path and must never throw or return empty.
        List<Component> lines = ShardInfoFormatter.infoLines();

        assertFalse(lines.isEmpty());
        assertEquals(DROP_KEY, key(lines.get(0)));
        assertEquals(CONFIGURABLE_KEY, key(lines.get(lines.size() - 1)));
    }
}
