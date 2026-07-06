// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.compat.common;

import com.rfizzle.tribulation.config.TribulationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipDetailProviderTest {

    @AfterEach
    void restoreDefaults() {
        // The bridge is static; reset so ordering between tests is irrelevant.
        TooltipDetailProvider.setShiftHeld(() -> false);
        TooltipDetailProvider.setEffectiveConfig(TribulationConfig::new);
    }

    @Test
    void defaults_areNullSafeBeforeClientInit() {
        // Outside a running client, Tribulation.getConfig() is null; the default
        // effective-config supplier must still hand back a non-null config, and
        // the Shift default must read false rather than throw.
        TooltipDetailProvider.setShiftHeld(() -> false);
        TooltipDetailProvider.setEffectiveConfig(() -> {
            var cfg = com.rfizzle.tribulation.Tribulation.getConfig();
            return cfg != null ? cfg : new TribulationConfig();
        });

        assertFalse(TooltipDetailProvider.isShiftHeld());
        assertNotNull(TooltipDetailProvider.effectiveConfig());
    }

    @Test
    void suppliers_takeEffectOnceSet() {
        TribulationConfig injected = new TribulationConfig();
        TooltipDetailProvider.setShiftHeld(() -> true);
        TooltipDetailProvider.setEffectiveConfig(() -> injected);

        assertTrue(TooltipDetailProvider.isShiftHeld());
        assertSame(injected, TooltipDetailProvider.effectiveConfig());
    }
}
