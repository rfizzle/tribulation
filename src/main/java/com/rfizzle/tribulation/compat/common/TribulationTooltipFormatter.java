package com.rfizzle.tribulation.compat.common;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formats mob scaling data from a {@link CompoundTag} into human-readable
 * tooltip lines. Shared by Jade and WTHIT client-side providers.
 */
public final class TribulationTooltipFormatter {

    private TribulationTooltipFormatter() {}

    public static List<String> format(CompoundTag data) {
        List<String> lines = new ArrayList<>();
        if (data == null || !data.getBoolean(MobScalingDataCollector.KEY_SCALED)) {
            return lines;
        }

        double healthFactor = data.getDouble(MobScalingDataCollector.KEY_HEALTH_FACTOR);
        String variant = data.getString(MobScalingDataCollector.KEY_VARIANT);
        String abilities = data.getString(MobScalingDataCollector.KEY_ABILITIES);

        StringBuilder header = new StringBuilder("Tribulation Scaled");
        if (healthFactor > 0) {
            header.append(String.format(Locale.ROOT, " (+%s%% HP)",
                    formatFactor(healthFactor)));
        }
        if (!variant.isEmpty()) {
            String variantLabel = switch (variant) {
                case "big" -> "Big";
                case "speed" -> "Speed";
                default -> variant;
            };
            header.append(" • ").append(variantLabel);
        }
        lines.add(header.toString());

        if (!abilities.isEmpty()) {
            lines.add(abilities);
        }

        return lines;
    }

    private static String formatFactor(double factor) {
        double pct = factor * 100.0;
        if (pct == Math.floor(pct)) {
            return String.format(Locale.ROOT, "%.0f", pct);
        }
        return String.format(Locale.ROOT, "%.1f", pct);
    }
}
