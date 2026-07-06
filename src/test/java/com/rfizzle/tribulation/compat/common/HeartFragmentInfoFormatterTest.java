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

class HeartFragmentInfoFormatterTest {

    private static final String RESTORE_KEY = "item.tribulation.heart_fragment.info.restore";
    private static final String DISABLED_KEY = "item.tribulation.heart_fragment.info.disabled";
    private static final String CONFIGURABLE_KEY = "item.tribulation.heart_fragment.info.configurable";

    private static TranslatableContents translatable(Component c) {
        assertInstanceOf(TranslatableContents.class, c.getContents(),
                "expected a translatable line, got: " + c.getContents());
        return (TranslatableContents) c.getContents();
    }

    private static String key(Component c) {
        return translatable(c).getKey();
    }

    @Test
    void defaultConfig_disabledByDefault_includesDisabledNote() {
        // HardcoreHearts ships disabled, so the default config carries the note.
        List<Component> lines = HeartFragmentInfoFormatter.infoLines(new TribulationConfig());

        // restore line, disabled note, blank, configurable line
        assertEquals(4, lines.size());

        TranslatableContents restore = translatable(lines.get(0));
        assertEquals(RESTORE_KEY, restore.getKey());
        assertArrayEquals(new Object[]{2}, restore.getArgs());

        assertEquals(DISABLED_KEY, key(lines.get(1)));
        assertEquals("", lines.get(2).getString(), "line 2 is a blank spacer");
        assertEquals(CONFIGURABLE_KEY, key(lines.get(3)));
    }

    @Test
    void enabled_omitsDisabledNote() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.hardcoreHearts.enabled = true;

        List<Component> lines = HeartFragmentInfoFormatter.infoLines(cfg);

        assertEquals(3, lines.size());
        assertTrue(lines.stream()
                        .filter(c -> c.getContents() instanceof TranslatableContents)
                        .noneMatch(c -> DISABLED_KEY.equals(key(c))),
                "disabled note must be omitted when enabled");
        assertEquals(CONFIGURABLE_KEY, key(lines.get(lines.size() - 1)));
    }

    @Test
    void customValue_appearsInArgs() {
        TribulationConfig cfg = new TribulationConfig();
        cfg.hardcoreHearts.enabled = true;
        cfg.hardcoreHearts.heartsRestoredPerFragment = 4;

        List<Component> lines = HeartFragmentInfoFormatter.infoLines(cfg);

        assertArrayEquals(new Object[]{4}, translatable(lines.get(0)).getArgs());
    }

    @Test
    void noArgOverload_fallsBackToDefaultsWhenConfigUnset() {
        // Tribulation.getConfig() is null outside a running mod, so this exercises
        // the default-config fallback path and must never throw or return empty.
        List<Component> lines = HeartFragmentInfoFormatter.infoLines();

        assertFalse(lines.isEmpty());
        assertEquals(RESTORE_KEY, key(lines.get(0)));
        assertEquals(CONFIGURABLE_KEY, key(lines.get(lines.size() - 1)));
    }
}
