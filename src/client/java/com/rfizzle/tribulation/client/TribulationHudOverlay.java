package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class TribulationHudOverlay implements HudRenderCallback {
    private static final ResourceLocation SHIELD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Tribulation.MOD_ID, "textures/gui/tribulation_shield.png");

    static final int ICON_SIZE = 9;
    private static final int PADDING = 3;
    private static final int ICON_TEXT_GAP = 2;
    private static final int BG_COLOR = 0x80000000;
    private static final long ANIMATION_DURATION_MS = 2000;
    private static final int GOLD_COLOR = 0xFFFFD700;

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
        String text = String.valueOf(level);
        int textWidth = mc.font.width(text);
        int textHeight = mc.font.lineHeight;

        int contentWidth = ICON_SIZE + ICON_TEXT_GAP + textWidth;
        int contentHeight = Math.max(ICON_SIZE, textHeight);
        int totalWidth = contentWidth + PADDING * 2;
        int totalHeight = contentHeight + PADDING * 2;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int bgX = computeX(config.hud, screenWidth, totalWidth);
        int bgY = computeY(config.hud, screenHeight, totalHeight);

        graphics.fill(bgX, bgY, bgX + totalWidth, bgY + totalHeight, BG_COLOR);

        int iconX = bgX + PADDING;
        int iconY = bgY + PADDING + (contentHeight - ICON_SIZE) / 2;
        graphics.blit(SHIELD_TEXTURE, iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        int textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        int textY = bgY + PADDING + (contentHeight - textHeight) / 2 + 1;
        int color = getTextColor(tier, ClientTribulationState.getLevelUpTimestamp());
        graphics.drawString(mc.font, text, textX, textY, color, true);
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
