package com.rfizzle.tribulation.client;

import com.rfizzle.tribulation.Tribulation;
import com.rfizzle.tribulation.ability.MobAbilities;
import com.rfizzle.tribulation.ability.MobAbility;
import com.rfizzle.tribulation.config.TribulationConfig;
import com.rfizzle.tribulation.scaling.TierManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hold-to-peek tier detail panel. While {@link TribulationClient#KEY_TIER_DETAIL}
 * is held — and the HUD's normal visibility rules pass — this overlays a framed
 * panel with the player's level, tier, progress to the next level, and the
 * abilities scaled mobs have at the current tier.
 *
 * <p>The ability listing is read from {@link MobAbilities}, the same registry
 * {@link com.rfizzle.tribulation.ability.AbilityManager} applies server-side, so
 * the panel can never claim an ability the game isn't actually granting.
 *
 * <p>No {@code Screen} is opened: the panel never captures the mouse, pauses the
 * game, or blocks movement — it behaves like vanilla's hold-Tab player list.
 * Because a non-focused HUD layer can't scroll without capturing input, the
 * ability list never scrolls: it reflows into as many balanced columns as it
 * takes to keep the panel within the screen height (growing wider, not taller).
 */
public final class TierDetailPanelRenderer implements HudRenderCallback {
    private static final ResourceLocation PANEL = Tribulation.id("textures/gui/tier_detail_panel.png");
    private static final ResourceLocation ICON = Tribulation.id("textures/gui/hud_icon.png");

    private static final int TEX_SIZE = 64;   // panel texture is 64x64
    private static final int SLICE = 12;       // 9-slice corner inset (holds the crimson accents)
    private static final int ICON_SIZE = 16;

    private static final int INSET = 14;       // content inset from the panel edge
    private static final int LINE_H = 10;
    private static final int SECTION_GAP = 5;
    private static final int COL_GAP = 16;
    private static final int ABILITY_INDENT = 6;
    private static final int BAR_H = 6;
    private static final int MIN_CONTENT_W = 188;
    private static final int MAX_COLUMNS = 4;
    private static final float MAX_SCREEN_FRACTION = 0.92f;

    /** How often (in game ticks) the nearby-mob scan refreshes its cache. */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** Hard cap on the scan radius so a misconfigured detection range can't inflate a huge AABB. */
    private static final double MAX_SCAN_RANGE = 64.0;

    private static final int COLOR_BONE = 0xFFE8E0D4;
    private static final int COLOR_ASH = 0xFFA89F93;
    private static final int COLOR_BAR_TRACK = 0xFF26241F;

    private static final String BULLET = "› ";

    /**
     * Cached set of nearby vanilla mob keys, refreshed at most every {@link
     * #SCAN_INTERVAL_TICKS} ticks and only while the panel is held open. Reused
     * across frames so per-frame rendering only reads it.
     */
    private final Set<String> nearbyMobKeys = new HashSet<>();
    private long lastScanTick = Long.MIN_VALUE;

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        // Same visibility rules as the badge (F1, open screen, spectator, death),
        // plus the hold-to-peek gate. The keymapping is unbound by default, so
        // isDown() stays false until the player binds it.
        if (!TribulationHudOverlay.isHudVisible()) return;
        if (TribulationClient.KEY_TIER_DETAIL == null || !TribulationClient.KEY_TIER_DETAIL.isDown()) return;

        Minecraft mc = Minecraft.getInstance();
        TribulationConfig config = Tribulation.getConfig();
        if (mc == null || config == null) return;
        Font font = mc.font;

        int level = Math.max(0, ClientTribulationState.getLevel());
        int tier = TierManager.getTier(level, config.tiers);
        int tierColor = TribulationHudOverlay.getTierColor(tier);

        // ---- Build the ability groups for mob types actually near the player ----
        // The nearby set is cached and refreshed on a tick interval (see
        // refreshNearbyIfStale), so this loop is a cheap registry lookup per
        // nearby mob type, not an entity scan.
        refreshNearbyIfStale(mc, config);
        List<Group> groups = new ArrayList<>();
        int totalRows = 0;
        for (String mobKey : TribulationConfig.MOB_KEYS) {
            if (!nearbyMobKeys.contains(mobKey)) continue;
            List<MobAbility> abilities = MobAbilities.activeForMob(mobKey, tier, config);
            if (abilities.isEmpty()) continue;
            groups.add(new Group(Component.translatable("entity.minecraft." + mobKey), abilities));
            totalRows += 1 + abilities.size();
        }
        Component emptyText = Component.translatable("hud.tribulation.tier_detail.no_abilities");
        Component nearbyHeading = Component.translatable("hud.tribulation.tier_detail.nearby_heading");

        // ---- Reflow the ability list into enough columns to fit the screen ----
        // Everything except the ability rows is fixed-height "chrome"; the rows
        // get whatever vertical budget remains, and we add columns until they fit.
        int chromeH = 2 * INSET
                + ICON_SIZE + SECTION_GAP        // header
                + 1 + SECTION_GAP                // divider
                + LINE_H + 3 + BAR_H + 3 + LINE_H + SECTION_GAP  // level + bar + figures
                + 1 + SECTION_GAP;               // divider
        // Reserve a line for the "Nearby" caption when there's a list to head.
        int headingH = groups.isEmpty() ? 0 : LINE_H;
        int rowBudget = (int) (graphics.guiHeight() * MAX_SCREEN_FRACTION) - chromeH - headingH;
        int maxRowsPerCol = Math.max(1, rowBudget / LINE_H);
        int numCols = groups.isEmpty()
                ? 1
                : Math.min(MAX_COLUMNS, Math.max(1, (totalRows + maxRowsPerCol - 1) / maxRowsPerCol));
        List<List<Group>> columns = distribute(groups, numCols, totalRows);

        int[] colW = new int[numCols];
        int abilityW = 0;
        int abilityRows = 1;
        if (groups.isEmpty()) {
            abilityW = font.width(emptyText);
        } else {
            int gaps = 0;
            abilityRows = 0;
            for (int i = 0; i < numCols; i++) {
                colW[i] = columnWidth(font, columns.get(i));
                if (colW[i] > 0) {
                    abilityW += colW[i] + (gaps > 0 ? COL_GAP : 0);
                    gaps++;
                }
                abilityRows = Math.max(abilityRows, rowCount(columns.get(i)));
            }
        }

        // ---- Header / progress strings ----
        Component title = Component.translatable("hud.tribulation.tier_detail.title");
        Component tierLabel = Component.translatable("hud.tribulation.tier_detail.tier_label", tier);
        Component levelText = Component.translatable("hud.tribulation.tier_detail.level", level);
        int nextLevel = nextTierLevel(tier, config.tiers);
        Component nextText = nextLevel < 0
                ? Component.translatable("hud.tribulation.tier_detail.max_tier")
                : Component.translatable("hud.tribulation.tier_detail.next_tier", nextLevel);
        float fraction = ClientTribulationState.getProgressFraction();
        int percent = Math.round(fraction * 100f);
        Component progressText = Component.translatable("hud.tribulation.tier_detail.progress",
                ClientTribulationState.getProgressTicks(), ClientTribulationState.getGoalTicks());
        Component percentText = Component.translatable("hud.tribulation.tier_detail.percent", percent);

        int headerW = ICON_SIZE + 4 + font.width(title) + 12 + font.width(tierLabel);
        int levelRowW = font.width(levelText) + 12 + font.width(nextText);
        int figuresW = font.width(progressText) + 12 + font.width(percentText);
        int contentW = Math.max(MIN_CONTENT_W, Math.max(Math.max(headerW, levelRowW), Math.max(figuresW, abilityW)));

        int contentH = chromeH - 2 * INSET + headingH + abilityRows * LINE_H;
        int panelW = contentW + 2 * INSET;
        int panelH = contentH + 2 * INSET;
        int panelX = (graphics.guiWidth() - panelW) / 2;
        int panelY = (graphics.guiHeight() - panelH) / 2;

        // ---- Frame, watermark, content ----
        graphics.setColor(1f, 1f, 1f, 1f);
        drawNineSlice(graphics, panelX, panelY, panelW, panelH);
        drawSkullWatermark(graphics, panelX + panelW / 2, panelY + panelH / 2, tierColor);

        int contentX = panelX + INSET;
        int y = panelY + INSET;

        // Header: tier-tinted skull + title, with the tier label right-aligned.
        blitTinted(graphics, ICON, contentX, y, tierColor);
        graphics.drawString(font, title, contentX + ICON_SIZE + 4, y + 4, COLOR_BONE, true);
        graphics.drawString(font, tierLabel, contentX + contentW - font.width(tierLabel), y + 4, tierColor, true);
        y += ICON_SIZE + SECTION_GAP;

        y = divider(graphics, contentX, y, contentW, tierColor);

        // Level + next-tier row.
        graphics.drawString(font, levelText, contentX, y, COLOR_BONE, true);
        graphics.drawString(font, nextText, contentX + contentW - font.width(nextText), y, COLOR_ASH, true);
        y += LINE_H + 3;

        // Progress bar: ash track, tier-colored fill.
        graphics.fill(contentX, y, contentX + contentW, y + BAR_H, COLOR_BAR_TRACK);
        int filledW = Math.round(contentW * Math.max(0f, Math.min(1f, fraction)));
        if (filledW > 0) {
            graphics.fill(contentX, y, contentX + filledW, y + BAR_H, tierColor);
        }
        y += BAR_H + 3;

        // Progress figures row.
        graphics.drawString(font, progressText, contentX, y, COLOR_ASH, true);
        graphics.drawString(font, percentText, contentX + contentW - font.width(percentText), y, COLOR_ASH, true);
        y += LINE_H + SECTION_GAP;

        y = divider(graphics, contentX, y, contentW, tierColor);

        // Ability columns, under a "Nearby" caption.
        if (groups.isEmpty()) {
            graphics.drawString(font, emptyText, contentX, y, COLOR_ASH, true);
        } else {
            graphics.drawString(font, nearbyHeading, contentX, y, COLOR_ASH, true);
            y += LINE_H;
            int cx = contentX;
            for (int i = 0; i < numCols; i++) {
                if (columns.get(i).isEmpty()) continue;
                drawColumn(graphics, font, columns.get(i), cx, y);
                cx += colW[i] + COL_GAP;
            }
        }

        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Refresh {@link #nearbyMobKeys} if the cache is older than {@link
     * #SCAN_INTERVAL_TICKS} ticks (or the world's game time jumped backward,
     * i.e. a world change). The scan uses the level's chunk-section spatial
     * index via a bounded AABB, so it only visits entities in nearby chunks —
     * never the whole world — and only runs while the panel is held open.
     */
    private void refreshNearbyIfStale(Minecraft mc, TribulationConfig config) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        long now = level.getGameTime();
        boolean fresh = lastScanTick != Long.MIN_VALUE && now >= lastScanTick && now - lastScanTick < SCAN_INTERVAL_TICKS;
        if (fresh) return;
        lastScanTick = now;

        nearbyMobKeys.clear();
        double configured = config.general != null ? config.general.mobDetectionRange : 32.0;
        double range = Math.min(MAX_SCAN_RANGE, Math.max(1.0, configured));
        AABB box = player.getBoundingBox().inflate(range);
        for (Entity entity : level.getEntities(player, box, e -> e instanceof Mob)) {
            ResourceLocation id = EntityType.getKey(entity.getType());
            if ("minecraft".equals(id.getNamespace())) {
                nearbyMobKeys.add(id.getPath());
            }
        }
    }

    /**
     * Distribute groups across {@code numCols} columns, balancing row counts and
     * never splitting a group. Fills a column up to the per-column row target,
     * then moves on — preserving {@code MOB_KEYS} order left-to-right.
     */
    private static List<List<Group>> distribute(List<Group> groups, int numCols, int totalRows) {
        List<List<Group>> columns = new ArrayList<>();
        for (int i = 0; i < numCols; i++) {
            columns.add(new ArrayList<>());
        }
        int target = (totalRows + numCols - 1) / numCols;
        int col = 0;
        int acc = 0;
        for (Group g : groups) {
            if (acc >= target && col < numCols - 1) {
                col++;
                acc = 0;
            }
            columns.get(col).add(g);
            acc += g.rowCount();
        }
        return columns;
    }

    private static void drawColumn(GuiGraphics graphics, Font font, List<Group> column, int x, int top) {
        int cy = top;
        for (Group g : column) {
            graphics.drawString(font, g.header(), x, cy, COLOR_BONE, true);
            cy += LINE_H;
            for (MobAbility ability : g.abilities()) {
                graphics.drawString(font, abilityComponent(ability), x + ABILITY_INDENT, cy, COLOR_ASH, true);
                cy += LINE_H;
            }
        }
    }

    private static Component abilityComponent(MobAbility ability) {
        return Component.literal(BULLET)
                .append(Component.translatable("config.tribulation.abilities." + ability.abilityKey()));
    }

    private static int columnWidth(Font font, List<Group> column) {
        int w = 0;
        for (Group g : column) {
            w = Math.max(w, font.width(g.header()));
            for (MobAbility ability : g.abilities()) {
                w = Math.max(w, ABILITY_INDENT + font.width(abilityComponent(ability)));
            }
        }
        return w;
    }

    private static int rowCount(List<Group> column) {
        int rows = 0;
        for (Group g : column) {
            rows += g.rowCount();
        }
        return rows;
    }

    private int divider(GuiGraphics graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, withAlpha(color, 0xB0));
        return y + 1 + SECTION_GAP;
    }

    /** Draw the 64x64 panel texture as a 9-slice scaled to {@code w}x{@code h}. */
    private static void drawNineSlice(GuiGraphics g, int x, int y, int w, int h) {
        int s = SLICE;
        int center = TEX_SIZE - 2 * s;          // 40px center region in the texture
        int innerW = w - 2 * s;
        int innerH = h - 2 * s;
        // Corners (unstretched).
        blit(g, x, y, s, s, 0, 0, s, s);
        blit(g, x + w - s, y, s, s, TEX_SIZE - s, 0, s, s);
        blit(g, x, y + h - s, s, s, 0, TEX_SIZE - s, s, s);
        blit(g, x + w - s, y + h - s, s, s, TEX_SIZE - s, TEX_SIZE - s, s, s);
        // Edges (stretched along one axis).
        blit(g, x + s, y, innerW, s, s, 0, center, s);
        blit(g, x + s, y + h - s, innerW, s, s, TEX_SIZE - s, center, s);
        blit(g, x, y + s, s, innerH, 0, s, s, center);
        blit(g, x + w - s, y + s, s, innerH, TEX_SIZE - s, s, s, center);
        // Center (stretched both axes).
        blit(g, x + s, y + s, innerW, innerH, s, s, center, center);
    }

    private static void blit(GuiGraphics g, int x, int y, int dw, int dh, int u, int v, int sw, int sh) {
        g.blit(PANEL, x, y, dw, dh, u, v, sw, sh, TEX_SIZE, TEX_SIZE);
    }

    /** A large, very faint tier-tinted skull behind the content. */
    private static void drawSkullWatermark(GuiGraphics g, int cx, int cy, int tierColor) {
        int size = 56;
        float r = ((tierColor >> 16) & 0xFF) / 255f;
        float gr = ((tierColor >> 8) & 0xFF) / 255f;
        float b = (tierColor & 0xFF) / 255f;
        g.setColor(r, gr, b, 0.06f);
        g.blit(ICON, cx - size / 2, cy - size / 2, size, size, 0, 0, 32, 32, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void blitTinted(GuiGraphics g, ResourceLocation tex, int x, int y, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        g.setColor(r, gr, b, 1f);
        g.blit(tex, x, y, ICON_SIZE, ICON_SIZE, 0, 0, 32, 32, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static int nextTierLevel(int tier, TribulationConfig.Tiers tiers) {
        if (tiers == null) return -1;
        return switch (tier) {
            case 0 -> tiers.tier1;
            case 1 -> tiers.tier2;
            case 2 -> tiers.tier3;
            case 3 -> tiers.tier4;
            case 4 -> tiers.tier5;
            default -> -1;   // already at max tier
        };
    }

    private record Group(Component header, List<MobAbility> abilities) {
        int rowCount() {
            return 1 + abilities.size();
        }
    }
}
