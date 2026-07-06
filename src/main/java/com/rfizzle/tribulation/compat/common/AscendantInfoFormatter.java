package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates info text for the Ascendant Shard item, used by EMI, REI, and JEI
 * info panels and by the item's base tooltip. Callers pass the effective config
 * so the text reflects the server's tuning: the client plugins resolve it through
 * {@code ClientConfigState.effective()} (synced server config, falling back to
 * the local file when not connected to a server that syncs).
 *
 * <p>Lines are returned as translatable {@link Component}s so the panels render
 * in the viewer's locale; the config-derived numbers are passed as pre-formatted
 * arguments (Minecraft's translation substitution is plain {@code %s}, not
 * {@code printf}). Blank spacer lines are {@link Component#empty()}.
 */
public final class AscendantInfoFormatter {

    private AscendantInfoFormatter() {}

    /**
     * Info lines derived from the local config. Client info panels should call
     * {@link #infoLines(TribulationConfig)} with the effective (server-synced)
     * config instead; this overload is the local-only fallback.
     */
    public static List<Component> infoLines() {
        TribulationConfig cfg = Tribulation.getConfig();
        if (cfg == null) cfg = new TribulationConfig();
        return infoLines(cfg);
    }

    public static List<Component> infoLines(TribulationConfig cfg) {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable(
                "item.tribulation.ascendant_shard.info.consume",
                cfg.ascension.raisePower));

        lines.add(Component.translatable(
                "item.tribulation.ascendant_shard.info.cap",
                cfg.general.maxLevel));

        if (cfg.ascension.sideEffects) {
            lines.add(Component.translatable("item.tribulation.ascendant_shard.info.side_effects"));
        }

        lines.add(Component.empty());
        lines.add(Component.translatable("item.tribulation.ascendant_shard.info.risk"));
        lines.add(Component.translatable("item.tribulation.ascendant_shard.info.configurable"));

        return lines;
    }
}
