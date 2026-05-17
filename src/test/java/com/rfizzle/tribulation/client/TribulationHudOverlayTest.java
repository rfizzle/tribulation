// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.config.TribulationConfig.AnchorPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TribulationHudOverlayTest {

    private static final int SAMPLE_ELEMENT_WIDTH = 30;
    private static final int SAMPLE_ELEMENT_HEIGHT = 15;

    private static TribulationConfig.Hud hudWith(AnchorPosition anchor, int offsetX, int offsetY) {
        TribulationConfig.Hud hud = new TribulationConfig.Hud();
        hud.anchor = anchor;
        hud.offsetX = offsetX;
        hud.offsetY = offsetY;
        return hud;
    }

    // ---- Position tests ----

    @Test
    void computeX_topLeft_returnsOffsetX() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.TOP_LEFT, 4, 4);
        assertEquals(4, TribulationHudOverlay.computeX(hud, 800, SAMPLE_ELEMENT_WIDTH));
    }

    @Test
    void computeX_topRight_anchorsFromRight() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.TOP_RIGHT, 4, 4);
        // 800 - 30 - 4 = 766
        assertEquals(766, TribulationHudOverlay.computeX(hud, 800, SAMPLE_ELEMENT_WIDTH));
    }

    @Test
    void computeX_bottomLeft_returnsOffsetX() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.BOTTOM_LEFT, 10, 10);
        assertEquals(10, TribulationHudOverlay.computeX(hud, 1920, SAMPLE_ELEMENT_WIDTH));
    }

    @Test
    void computeX_bottomRight_anchorsFromRight() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.BOTTOM_RIGHT, 8, 8);
        // 1920 - 30 - 8 = 1882
        assertEquals(1882, TribulationHudOverlay.computeX(hud, 1920, SAMPLE_ELEMENT_WIDTH));
    }

    @Test
    void computeY_topLeft_returnsOffsetY() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.TOP_LEFT, 4, 4);
        assertEquals(4, TribulationHudOverlay.computeY(hud, 600, SAMPLE_ELEMENT_HEIGHT));
    }

    @Test
    void computeY_topRight_returnsOffsetY() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.TOP_RIGHT, 4, 8);
        assertEquals(8, TribulationHudOverlay.computeY(hud, 600, SAMPLE_ELEMENT_HEIGHT));
    }

    @Test
    void computeY_bottomLeft_anchorsFromBottom() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.BOTTOM_LEFT, 4, 4);
        // 600 - 15 - 4 = 581
        assertEquals(581, TribulationHudOverlay.computeY(hud, 600, SAMPLE_ELEMENT_HEIGHT));
    }

    @Test
    void computeY_bottomRight_anchorsFromBottom() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.BOTTOM_RIGHT, 4, 10);
        // 1080 - 15 - 10 = 1055
        assertEquals(1055, TribulationHudOverlay.computeY(hud, 1080, SAMPLE_ELEMENT_HEIGHT));
    }

    @Test
    void computeX_zeroOffset_touchesEdge() {
        TribulationConfig.Hud hud = hudWith(AnchorPosition.TOP_LEFT, 0, 0);
        assertEquals(0, TribulationHudOverlay.computeX(hud, 800, SAMPLE_ELEMENT_WIDTH));

        TribulationConfig.Hud hudR = hudWith(AnchorPosition.TOP_RIGHT, 0, 0);
        assertEquals(800 - SAMPLE_ELEMENT_WIDTH, TribulationHudOverlay.computeX(hudR, 800, SAMPLE_ELEMENT_WIDTH));
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
