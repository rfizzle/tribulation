package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates info text for the Heart Fragment item, used by EMI, REI, and JEI
 * info panels and by the item's base tooltip. Callers pass the effective config
 * so the text reflects the server's tuning: client surfaces resolve it through
 * {@code ClientConfigState.effective()} (synced server config, falling back to
 * the local file when not connected to a server that syncs).
 *
 * <p>Lines are returned as translatable {@link Component}s so surfaces render in
 * the viewer's locale; the config-derived numbers are passed as arguments.
 * Blank spacer lines are {@link Component#empty()}.
 */
public final class HeartFragmentInfoFormatter {

    private HeartFragmentInfoFormatter() {}

    /**
     * Info lines derived from the local config. Client surfaces should call
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
                "item.tribulation.heart_fragment.info.restore",
                cfg.hardcoreHearts.heartsRestoredPerFragment));

        if (!cfg.hardcoreHearts.enabled) {
            lines.add(Component.translatable("item.tribulation.heart_fragment.info.disabled"));
        }

        lines.add(Component.empty());
        lines.add(Component.translatable("item.tribulation.heart_fragment.info.configurable"));

        return lines;
    }
}
