package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class TribulationHudOverlay implements HudRenderCallback {
    static final String SHIELD_CHAR = "⛨";
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 3;
    private static final int BG_COLOR = 0x80000000;
    private static final long ANIMATION_DURATION_MS = 2000;
    private static final int GOLD_COLOR = 0xFFFFD700;
    private static final int ICON_COLOR = 0xFFFFFFFF;

    private static final int[] TIER_COLORS = {
            0xFFFFFFFF, // Tier 0: White
            0xFFFFFF00, // Tier 1: Yellow
            0xFFFF8C00, // Tier 2: Orange
            0xFFFF6060, // Tier 3: Light Red
            0xFFFF0000, // Tier 4: Red
            0xFF8B008B, // Tier 5: Dark Red / Purple
    };

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;
        if (mc.screen != null) return;
        if (mc.player.isSpectator()) return;

        TribulationConfig config = Tribulation.getConfig();
        if (config == null) return;
        if (config.hud == null || !config.hud.enabled) return;

        int level = ClientTribulationState.getLevel();
        int tier = TierManager.getTier(level, config.tiers);
        int color = getTextColor(tier, ClientTribulationState.getLevelUpTimestamp());

        int tierColor = color & 0x00FFFFFF;
        MutableComponent text = Component.literal(SHIELD_CHAR + " ")
                .withStyle(Style.EMPTY.withColor(ICON_COLOR & 0x00FFFFFF))
                .append(Component.literal(String.valueOf(level))
                        .withStyle(Style.EMPTY.withColor(tierColor)));

        int textWidth = mc.font.width(text);
        int totalWidth = textWidth + PADDING_X * 2;
        int totalHeight = mc.font.lineHeight + PADDING_Y * 2;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int bgX = computeX(config.hud, screenWidth, totalWidth);
        int bgY = computeY(config.hud, screenHeight, totalHeight);

        graphics.fill(bgX, bgY, bgX + totalWidth, bgY + totalHeight, BG_COLOR);
        graphics.drawString(mc.font, text, bgX + PADDING_X, bgY + PADDING_Y, 0xFFFFFFFF, true);
    }

    static int computeX(TribulationConfig.Hud hud, int screenWidth, int elementWidth) {
        return switch (hud.anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - elementWidth - hud.offsetX;
            default -> hud.offsetX;
        };
    }

    static int computeY(TribulationConfig.Hud hud, int screenHeight, int elementHeight) {
        return switch (hud.anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - elementHeight - hud.offsetY;
            default -> hud.offsetY;
        };
    }

    static int getTextColor(int tier, long levelUpTimestamp) {
        long elapsed = System.currentTimeMillis() - levelUpTimestamp;
        if (elapsed >= 0 && elapsed < ANIMATION_DURATION_MS) {
            float progress = (float) elapsed / ANIMATION_DURATION_MS;
            int tierColor = getTierColor(tier);
            return lerpColor(GOLD_COLOR, tierColor, progress);
        }
        return getTierColor(tier);
    }

    static int getTierColor(int tier) {
        if (tier < 0) tier = 0;
        if (tier >= TIER_COLORS.length) tier = TIER_COLORS.length - 1;
        return TIER_COLORS[tier];
    }

    static int lerpColor(int from, int to, float t) {
        int fa = (from >> 24) & 0xFF;
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;

        int ta = (to >> 24) & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;

        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
