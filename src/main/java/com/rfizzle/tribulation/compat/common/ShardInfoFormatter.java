package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates info text for the Shatter Shard item, used by EMI, REI, and JEI
 * info panels. Reads current config values so the panel reflects the server's
 * tuning (or local defaults on a dedicated-multiplayer client).
 *
 * <p>Lines are returned as translatable {@link Component}s so the panels render
 * in the viewer's locale; the config-derived numbers are passed as pre-formatted
 * arguments (Minecraft's translation substitution is plain {@code %s}, not
 * {@code printf}). Blank spacer lines are {@link Component#empty()}.
 */
public final class ShardInfoFormatter {

    private ShardInfoFormatter() {}

    public static List<Component> infoLines() {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) cfg = new TribulationConfig();
        return infoLines(cfg);
    }

    static List<Component> infoLines(TribulationConfig cfg) {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable(
                "item.tribulation.shatter_shard.info.drop",
                cfg.shards.dropStartLevel,
                String.format(Locale.ROOT, "%.1f", cfg.shards.dropChance * 100)));

        lines.add(Component.empty());

        lines.add(Component.translatable(
                "item.tribulation.shatter_shard.info.consume",
                cfg.shards.shardPower));

        if (cfg.shards.sideEffects) {
            lines.add(Component.translatable("item.tribulation.shatter_shard.info.side_effects"));
        }

        lines.add(Component.empty());
        lines.add(Component.translatable("item.tribulation.shatter_shard.info.configurable"));

        return lines;
    }
}
