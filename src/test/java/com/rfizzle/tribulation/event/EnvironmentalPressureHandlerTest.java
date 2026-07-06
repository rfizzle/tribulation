// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvironmentalPressureHandlerTest {

    /**
     * Time-of-day fractions per {@code Level.getTimeOfDay}: 0.0 is noon,
     * 0.25 is dusk, 0.5 is midnight, 0.75 is dawn. The fully dark band is
     * roughly day-time ticks 13000–23000, i.e. fractions ~0.29–0.71.
     */
    @ParameterizedTest
    @CsvSource({
            "0.0, false",   // noon
            "0.25, false",  // dusk ramp, not yet fully dark
            "0.3, true",    // just inside the dark band
            "0.5, true",    // midnight
            "0.7, true",    // just before dawn
            "0.75, false",  // dawn ramp
            "0.9, false"    // morning
    })
    void isNightTime_matchesFullyDarkBand(float timeOfDay, boolean expected) {
        assertEquals(expected, EnvironmentalPressureHandler.isNightTime(timeOfDay));
    }
}
