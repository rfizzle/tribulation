// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.compat.common;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TribulationTooltipFormatterTest {

    private static CompoundTag scaled(double healthFactor, String variant, String abilities) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MobScalingDataCollector.KEY_SCALED, true);
        tag.putDouble(MobScalingDataCollector.KEY_HEALTH_FACTOR, healthFactor);
        tag.putString(MobScalingDataCollector.KEY_VARIANT, variant);
        tag.putString(MobScalingDataCollector.KEY_ABILITIES, abilities);
        return tag;
    }

    @Test
    void nullData_returnsEmpty() {
        assertTrue(TribulationTooltipFormatter.format(null).isEmpty());
    }

    @Test
    void unscaledData_returnsEmpty() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MobScalingDataCollector.KEY_SCALED, false);
        assertTrue(TribulationTooltipFormatter.format(tag).isEmpty());
    }

    @Test
    void healthFactor_rendersWholePercentWithoutDecimals() {
        List<String> lines = TribulationTooltipFormatter.format(scaled(0.5, "", ""));
        assertEquals(List.of("Tribulation Scaled (+50% HP)"), lines);
    }

    @Test
    void healthFactor_rendersFractionalPercentWithOneDecimal() {
        // 0.125 -> 12.5%, exercising the non-integer branch of formatFactor
        List<String> lines = TribulationTooltipFormatter.format(scaled(0.125, "", ""));
        assertEquals(List.of("Tribulation Scaled (+12.5% HP)"), lines);
    }

    @Test
    void zeroHealthFactor_omitsHpSuffix() {
        List<String> lines = TribulationTooltipFormatter.format(scaled(0.0, "", ""));
        assertEquals(List.of("Tribulation Scaled"), lines);
    }

    @Test
    void knownVariants_useFriendlyLabels() {
        assertEquals("Tribulation Scaled • Big",
                TribulationTooltipFormatter.format(scaled(0.0, "big", "")).get(0));
        assertEquals("Tribulation Scaled • Speed",
                TribulationTooltipFormatter.format(scaled(0.0, "speed", "")).get(0));
    }

    @Test
    void unknownVariant_fallsBackToRawValue() {
        assertEquals("Tribulation Scaled • mystery",
                TribulationTooltipFormatter.format(scaled(0.0, "mystery", "")).get(0));
    }

    @Test
    void healthFactorAndVariant_combineInHeader() {
        List<String> lines = TribulationTooltipFormatter.format(scaled(0.5, "big", ""));
        assertEquals(List.of("Tribulation Scaled (+50% HP) • Big"), lines);
    }

    @Test
    void abilities_appendAsSecondLine() {
        List<String> lines = TribulationTooltipFormatter.format(scaled(0.5, "", "Web Placing, Sprint"));
        assertEquals(2, lines.size());
        assertEquals("Tribulation Scaled (+50% HP)", lines.get(0));
        assertEquals("Web Placing, Sprint", lines.get(1));
    }
}
