package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class TribulationHudOverlay implements HudRenderCallback {
    private static final ResourceLocation ICON = Tribulation.id("textures/gui/hud_icon.png");
    private static final int ICON_SIZE = 16;

    private static final int BAR_HEIGHT = 2;
    private static final int BAR_GAP = 1;
    private static final int BAR_BG_COLOR = 0xC0202020;

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
        int textColor = getTextColor(tier, ClientTribulationState.getLevelUpTimestamp()) & 0x00FFFFFF;
        int tierColor = getTierColor(tier);

        MutableComponent text = Component.literal(String.valueOf(level))
                .withStyle(Style.EMPTY.withColor(textColor));

        int textWidthForLayout = mc.font.width(text);
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        TribulationConfig.Anchor anchor = config.hud.anchor != null ? config.hud.anchor : TribulationConfig.Anchor.TOP_LEFT;
        int x = computeOriginX(anchor, screenW, config.hud.offsetX, textWidthForLayout);
        int y = computeOriginY(anchor, screenH, config.hud.offsetY);

        float r = ((tierColor >> 16) & 0xFF) / 255f;
        float g = ((tierColor >> 8) & 0xFF) / 255f;
        float b = (tierColor & 0xFF) / 255f;
        graphics.setColor(r, g, b, 1.0f);
        graphics.blit(ICON, x, y, ICON_SIZE, ICON_SIZE, 0, 0, 32, 32, 32, 32);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Level badge anchored to the top-right of the icon — right edge of text
        // aligns with right edge of icon, top edge aligns with top of icon. No
        // centering math, so the result is robust regardless of which icon
        // texture is used or how the glyph sits inside its cell.
        int textX = x + ICON_SIZE - textWidthForLayout;
        int textY = y;
        graphics.drawString(mc.font, text, textX, textY, 0xFFFFFFFF, true);

        // Progress bar under the icon — fraction of ticks toward next level.
        int barX = x;
        int barY = y + ICON_SIZE + BAR_GAP;
        int barW = ICON_SIZE;
        graphics.fill(barX, barY, barX + barW, barY + BAR_HEIGHT, BAR_BG_COLOR);
        float fraction = ClientTribulationState.getProgressFraction();
        int filledW = Math.round(barW * fraction);
        if (filledW > 0) {
            graphics.fill(barX, barY, barX + filledW, barY + BAR_HEIGHT, tierColor);
        }
    }

    static int computeWidth(int textWidth) {
        // Number overlays the icon and may extend leftward for wide values; bar
        // sits below at icon width. Total footprint is the larger of the two.
        return Math.max(ICON_SIZE, textWidth);
    }

    static int computeHeight() {
        return ICON_SIZE + BAR_GAP + BAR_HEIGHT;
    }

    /**
     * Origin = top-left corner of the icon. Offset is measured from the
     * configured anchor edge inward, so the badge stays the same distance
     * from its corner regardless of screen size or how wide the number is.
     */
    static int computeOriginX(TribulationConfig.Anchor anchor, int screenW, int offsetX, int textWidth) {
        int footprint = computeWidth(textWidth);
        return switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX + (footprint - ICON_SIZE);
            case TOP_RIGHT, BOTTOM_RIGHT -> screenW - offsetX - ICON_SIZE;
        };
    }

    static int computeOriginY(TribulationConfig.Anchor anchor, int screenH, int offsetY) {
        return switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - offsetY - computeHeight();
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
