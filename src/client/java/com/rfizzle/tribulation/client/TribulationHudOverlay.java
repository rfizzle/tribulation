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
    private static final int ICON_SIZE = 12;
    private static final int ICON_TEXT_GAP = 2;
    private static final int BOX_PAD_X = 3;
    private static final int BOX_PAD_Y = 2;
    private static final int BG_COLOR = 0x99000000;
    private static final int BASE_X = 2;
    private static final int BASE_Y = 2;

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
        int color = getTextColor(tier, ClientTribulationState.getLevelUpTimestamp());

        int tierColor = color & 0x00FFFFFF;
        MutableComponent text = Component.literal("Lv. " + level)
                .withStyle(Style.EMPTY.withColor(tierColor));

        int textWidth = mc.font.width(text);
        int totalWidth = computeWidth(textWidth);
        int totalHeight = computeHeight();

        int x = BASE_X;
        int y = BASE_Y;

        drawBox(graphics, x, y, totalWidth, totalHeight);
        // Render and scale the 32x32 texture to 12x12 native size
        graphics.blit(ICON, x + BOX_PAD_X, y + BOX_PAD_Y, ICON_SIZE, ICON_SIZE, 0, 0, 32, 32, 32, 32);

        int textY = y + BOX_PAD_Y + (ICON_SIZE - mc.font.lineHeight) / 2;
        graphics.drawString(mc.font, text, x + BOX_PAD_X + ICON_SIZE + ICON_TEXT_GAP, textY, 0xFFFFFFFF, true);
    }

    static int computeWidth(int textWidth) {
        return BOX_PAD_X + ICON_SIZE + ICON_TEXT_GAP + textWidth + BOX_PAD_X;
    }

    static int computeHeight() {
        return BOX_PAD_Y + ICON_SIZE + BOX_PAD_Y;
    }

    private static void drawBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, BG_COLOR);
        g.fill(x, y + 1, x + w, y + h - 1, BG_COLOR);
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
