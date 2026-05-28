// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TribulationHudOverlayTest {

    // ---- Layout tests ----

    @Test
    void computeWidth_returnsExpected() {
        // BOX_PAD_X(3) + ICON_SIZE(12) + ICON_TEXT_GAP(2) + textWidth(20) + BOX_PAD_X(3) = 40
        assertEquals(40, TribulationHudOverlay.computeWidth(20));
    }

    @Test
    void computeHeight_returnsExpected() {
        // BOX_PAD_Y(2) + ICON_SIZE(12) + BOX_PAD_Y(2) = 16
        assertEquals(16, TribulationHudOverlay.computeHeight());
    }

    // ---- Color tests ----

    @ParameterizedTest
    @CsvSource({
            "0, 255, 255, 255, 255", // white
            "1, 255, 255, 255, 0",   // yellow
            "2, 255, 255, 140, 0",   // orange
            "3, 255, 255, 96, 96",   // light red
            "4, 255, 255, 0, 0",     // red
            "5, 255, 139, 0, 139",   // purple
    })
    void getTierColor_returnsExpectedForTier(int tier, int expectedA, int expectedR, int expectedG, int expectedB) {
        int color = TribulationHudOverlay.getTierColor(tier);
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        assertEquals(expectedA, a, "alpha mismatch for tier " + tier);
        assertEquals(expectedR, r, "red mismatch for tier " + tier);
        assertEquals(expectedG, g, "green mismatch for tier " + tier);
        assertEquals(expectedB, b, "blue mismatch for tier " + tier);
    }

    @Test
    void getTierColor_negativeTier_clampsToZero() {
        assertEquals(TribulationHudOverlay.getTierColor(0), TribulationHudOverlay.getTierColor(-5));
    }

    @Test
    void getTierColor_excessiveTier_clampsToMax() {
        assertEquals(TribulationHudOverlay.getTierColor(5), TribulationHudOverlay.getTierColor(99));
    }

    @Test
    void lerpColor_atZero_returnsFrom() {
        int from = 0xFFFF0000;
        int to = 0xFF0000FF;
        assertEquals(from, TribulationHudOverlay.lerpColor(from, to, 0.0f));
    }

    @Test
    void lerpColor_atOne_returnsTo() {
        int from = 0xFFFF0000;
        int to = 0xFF0000FF;
        assertEquals(to, TribulationHudOverlay.lerpColor(from, to, 1.0f));
    }

    @Test
    void lerpColor_atHalf_returnsMidpoint() {
        int from = 0xFF000000;
        int to = 0xFF646464;
        int result = TribulationHudOverlay.lerpColor(from, to, 0.5f);
        int r = (result >> 16) & 0xFF;
        int g = (result >> 8) & 0xFF;
        int b = result & 0xFF;
        assertEquals(50, r);
        assertEquals(50, g);
        assertEquals(50, b);
    }

    @Test
    void getTextColor_noAnimation_returnsTierColor() {
        long oldTimestamp = System.currentTimeMillis() - 5000;
        int color = TribulationHudOverlay.getTextColor(0, oldTimestamp);
        assertEquals(TribulationHudOverlay.getTierColor(0), color);
    }

    @Test
    void getTextColor_duringAnimation_returnsBlendedColor() {
        long recentTimestamp = System.currentTimeMillis() - 100;
        int color = TribulationHudOverlay.getTextColor(0, recentTimestamp);
        int tierColor = TribulationHudOverlay.getTierColor(0);
        assertNotEquals(tierColor, color);
    }

    @Test
    void getTextColor_negativeTimestamp_returnsTierColor() {
        int color = TribulationHudOverlay.getTextColor(3, -1);
        assertEquals(TribulationHudOverlay.getTierColor(3), color);
    }
}
