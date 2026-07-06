// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the config-screen tooltip contract: every config entry label key in
 * {@code en_us.json} must have a matching {@code <key>.tooltip} translation, so
 * the Cloth config screen never renders a raw lang key as a tooltip.
 */
class ConfigTooltipLangTest {

    private static final Path LANG =
            Path.of("src/main/resources/assets/tribulation/lang/en_us.json");

    private static JsonObject loadLang() throws IOException {
        String json = Files.readString(LANG, StandardCharsets.UTF_8);
        return new Gson().fromJson(json, JsonObject.class);
    }

    @Test
    void everyConfigLabelHasATooltip() throws IOException {
        JsonObject lang = loadLang();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, ?> e : lang.entrySet()) {
            String key = e.getKey();
            if (!isConfigLabel(key)) continue;
            String tooltipKey = key + ".tooltip";
            if (!lang.has(tooltipKey)) {
                missing.add(tooltipKey);
            }
        }
        assertTrue(missing.isEmpty(),
                "Config entries missing a .tooltip lang key: " + missing);
    }

    @Test
    void noTooltipIsBlank() throws IOException {
        JsonObject lang = loadLang();
        for (Map.Entry<String, ?> e : lang.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("config.tribulation.") || !key.endsWith(".tooltip")) continue;
            String value = lang.get(key).getAsString().trim();
            assertFalse(value.isEmpty(), "Blank tooltip for " + key);
        }
    }

    /** A config entry label: {@code config.tribulation.<section>.<key>}, not a category, title, or tooltip. */
    private static boolean isConfigLabel(String key) {
        return key.startsWith("config.tribulation.")
                && !key.equals("config.tribulation.title")
                && !key.contains(".category.")
                && !key.endsWith(".tooltip");
    }
}
