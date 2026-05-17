package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates info text for the Shatter Shard item, used by EMI, REI, and JEI
 * info panels. Reads current config values so the panel reflects the server's
 * tuning (or local defaults on a dedicated-multiplayer client).
 */
public final class ShardInfoFormatter {

    private ShardInfoFormatter() {}

    public static List<String> infoLines() {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) cfg = new TribulationConfig();

        List<String> lines = new ArrayList<>();

        lines.add(String.format(Locale.ROOT,
                "Drops from hostile mobs scaled to level %d+ with a %.1f%% chance per kill.",
                cfg.shards.dropStartLevel, cfg.shards.dropChance * 100));

        lines.add("");

        lines.add(String.format(Locale.ROOT,
                "Right-click to consume and reduce your difficulty level by %d.",
                cfg.shards.shardPower));

        if (cfg.shards.sideEffects) {
            lines.add("Applies Slowness II, Mining Fatigue II, and Weakness II for 10 seconds.");
        }

        lines.add("");
        lines.add("All values are configurable by the server operator.");

        return lines;
    }
}
