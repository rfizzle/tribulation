// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.config.TribulationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TribulationHudOverlayTest {

    // ---- Layout tests ----

    @Test
    void computeWidth_returnsIconWidth() {
        // Icon-only badge: footprint is always the 16-px icon width.
        assertEquals(16, TribulationHudOverlay.computeWidth());
    }

    @Test
    void computeHeight_returnsIconPlusBar() {
        // ICON_SIZE(16) + BAR_GAP(1) + BAR_HEIGHT(2) = 19
        assertEquals(19, TribulationHudOverlay.computeHeight());
    }

    // ---- Anchor origin tests ----

    @Test
    void computeOriginX_topLeft_appliesOffsetFromLeft() {
        assertEquals(4, TribulationHudOverlay.computeOriginX(TribulationConfig.Anchor.TOP_LEFT, 854, 4));
    }

    @Test
    void computeOriginX_topRight_appliesOffsetFromRight() {
        // screenW - offset - iconSize.
        assertEquals(854 - 4 - 16, TribulationHudOverlay.computeOriginX(TribulationConfig.Anchor.TOP_RIGHT, 854, 4));
    }

    @Test
    void computeOriginX_bottomLeft_matchesTopLeft() {
        // X axis behaves identically for left/right pairs.
        assertEquals(
                TribulationHudOverlay.computeOriginX(TribulationConfig.Anchor.TOP_LEFT, 854, 4),
                TribulationHudOverlay.computeOriginX(TribulationConfig.Anchor.BOTTOM_LEFT, 854, 4));
    }

    @Test
    void computeOriginX_bottomRight_matchesTopRight() {
        assertEquals(
                TribulationHudOverlay.computeOriginX(TribulationConfig.Anchor.TOP_RIGHT, 854, 4),
                TribulationHudOverlay.computeOriginX(TribulationConfig.Anchor.BOTTOM_RIGHT, 854, 4));
    }

    @Test
    void computeOriginY_topLeft_returnsOffset() {
        assertEquals(4, TribulationHudOverlay.computeOriginY(TribulationConfig.Anchor.TOP_LEFT, 480, 4));
    }

    @Test
    void computeOriginY_topRight_returnsOffset() {
        assertEquals(4, TribulationHudOverlay.computeOriginY(TribulationConfig.Anchor.TOP_RIGHT, 480, 4));
    }

    @Test
    void computeOriginY_bottomLeft_offsetFromBottom() {
        // screenH - offset - computeHeight(19).
        assertEquals(480 - 4 - 19, TribulationHudOverlay.computeOriginY(TribulationConfig.Anchor.BOTTOM_LEFT, 480, 4));
    }

    @Test
    void computeOriginY_bottomRight_offsetFromBottom() {
        assertEquals(480 - 4 - 19, TribulationHudOverlay.computeOriginY(TribulationConfig.Anchor.BOTTOM_RIGHT, 480, 4));
    }

    // ---- Color tests ----

    @ParameterizedTest
    @CsvSource({
            "0, 255, 255, 255, 255", // white
            "1, 255, 255, 255, 0",   // yellow
            "2, 255, 255, 140, 0",   // orange
            "3, 255, 255, 96, 96",   // light red
            "4, 255, 255, 0, 0",     // red
            "5, 255, 139, 0, 0",     // dark crimson
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
    void getAnimatedColor_noAnimation_returnsTierColor() {
        long oldTimestamp = System.currentTimeMillis() - 5000;
        int color = TribulationHudOverlay.getAnimatedColor(0, oldTimestamp);
        assertEquals(TribulationHudOverlay.getTierColor(0), color);
    }

    @Test
    void getAnimatedColor_duringAnimation_returnsBlendedColor() {
        long recentTimestamp = System.currentTimeMillis() - 100;
        int color = TribulationHudOverlay.getAnimatedColor(0, recentTimestamp);
        int tierColor = TribulationHudOverlay.getTierColor(0);
        assertNotEquals(tierColor, color);
    }

    @Test
    void getAnimatedColor_negativeTimestamp_returnsTierColor() {
        int color = TribulationHudOverlay.getAnimatedColor(3, -1);
        assertEquals(TribulationHudOverlay.getTierColor(3), color);
    }

    @Test
    void getAnimatedColor_noFlash_returnsTierColor() {
        int color = TribulationHudOverlay.getAnimatedColor(2, -1, -1);
        assertEquals(TribulationHudOverlay.getTierColor(2), color);
    }

    @Test
    void getAnimatedColor_drop_blendsFromCooling() {
        long now = System.currentTimeMillis();
        // Start of the flash blends from the cooling start color, distinct
        // from both the tier color and the gold level-up color.
        int color = TribulationHudOverlay.getAnimatedColor(0, -1, now);
        assertNotEquals(TribulationHudOverlay.getTierColor(0), color);
        assertNotEquals(TribulationHudOverlay.getAnimatedColor(0, now, -1), color,
                "drop flash should differ from the gold level-up flash");
    }

    @Test
    void getAnimatedColor_moreRecentFlashWins() {
        long now = System.currentTimeMillis();
        // Drop happened more recently than the up-flash: cooling wins.
        int both = TribulationHudOverlay.getAnimatedColor(0, now - 500, now);
        int dropOnly = TribulationHudOverlay.getAnimatedColor(0, -1, now);
        assertEquals(dropOnly, both);
    }
}
