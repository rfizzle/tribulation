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
 * Hold-to-peek tier detail panel. While {@link TribulationClient#KEY_PEEK_DETAIL}
 * is held — and the HUD's normal visibility rules pass — this overlays a framed
 * panel with the player's level, tier, progress to the next level, and the
 * abilities that nearby scaled mobs have at the current tier.
 *
 * <p>The ability listing is read from {@link MobAbilities}, the same registry
 * {@link com.rfizzle.tribulation.ability.AbilityManager} applies server-side, so
 * the panel can never claim an ability the game isn't actually granting.
 *
 * <p>No {@code Screen} is opened: the panel never captures the mouse, pauses the
 * game, or blocks movement — it behaves like vanilla's hold-Tab player list.
 * Because a non-focused HUD layer can't scroll without capturing input, the
 * body keeps a comfortable fixed size: everything that fits is shown at once,
 * and any overflow is paged — the body cross-fades between pages of mobs, with
 * page dots, while the header and progress stay static.
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
    private static final float MAX_SCREEN_FRACTION = 0.92f;

    /** A page is a comfortable, bounded body: up to this many columns... */
    private static final int PAGE_MAX_COLS = 2;
    /** ...each up to this many rows (also clamped down by the screen-height budget). */
    private static final int PAGE_COMFORT_ROWS = 10;

    /** Time one page stays up, including its fade in/out (ms). */
    private static final long PAGE_HOLD_MS = 2600L;
    /** Cross-fade duration at each page boundary (ms). */
    private static final long FADE_MS = 350L;

    /** How often (in game ticks) the nearby-mob scan refreshes its cache. */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** Hard cap on the scan radius so a misconfigured detection range can't inflate a huge AABB. */
    private static final double MAX_SCAN_RANGE = 64.0;

    private static final int DOT_SIZE = 3;
    private static final int DOT_GAP = 3;

    private static final int COLOR_BONE = 0xFFE8E0D4;
    private static final int COLOR_ASH = 0xFFA89F93;
    private static final int COLOR_BAR_TRACK = 0xFF26241F;
    private static final int COLOR_DOT_OFF = 0xFF55504A;

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
        // plus the hold-to-peek gate: the panel shows only while the bound key is
        // held down.
        if (!TribulationHudOverlay.isHudVisible()) return;
        if (TribulationClient.KEY_PEEK_DETAIL == null || !TribulationClient.KEY_PEEK_DETAIL.isDown()) return;

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
        for (String mobKey : TribulationConfig.MOB_KEYS) {
            if (!nearbyMobKeys.contains(mobKey)) continue;
            List<MobAbility> abilities = MobAbilities.activeForMob(mobKey, tier, config);
            if (abilities.isEmpty()) continue;
            groups.add(new Group(Component.translatable("entity.minecraft." + mobKey), abilities));
        }
        boolean hasList = !groups.isEmpty();
        Component emptyText = Component.translatable("hud.tribulation.detail.no_abilities");
        Component nearbyHeading = Component.translatable("hud.tribulation.detail.nearby_heading");

        // Fixed-height "chrome" (everything but the ability rows) sets the row budget.
        int chromeH = 2 * INSET
                + ICON_SIZE + SECTION_GAP        // header
                + 1 + SECTION_GAP                // divider
                + LINE_H + 3 + BAR_H + 3 + LINE_H + SECTION_GAP  // level + bar + figures
                + 1 + SECTION_GAP;               // divider
        int headingH = hasList ? LINE_H : 0;

        // A comfortable page: up to PAGE_MAX_COLS columns of up to `rowsPerCol`
        // rows, where rowsPerCol is the smaller of the comfort cap and what the
        // screen height actually allows. Overflow beyond one page is cycled.
        int rowBudget = (int) (graphics.guiHeight() * MAX_SCREEN_FRACTION) - chromeH - headingH;
        int rowsPerCol = Math.max(1, Math.min(PAGE_COMFORT_ROWS, rowBudget / LINE_H));
        List<List<List<Group>>> pages = paginate(groups, PAGE_MAX_COLS, rowsPerCol);
        int numPages = pages.size();

        // Fixed body dimensions across all pages, so cycling never resizes the panel.
        int uniformColW = 0;
        int bodyRows = hasList ? 0 : 1;
        int maxColsUsed = 1;
        if (hasList) {
            for (List<List<Group>> page : pages) {
                int colsUsed = 0;
                for (List<Group> col : page) {
                    if (col.isEmpty()) continue;
                    colsUsed++;
                    uniformColW = Math.max(uniformColW, columnWidth(font, col));
                    bodyRows = Math.max(bodyRows, rowCount(col));
                }
                maxColsUsed = Math.max(maxColsUsed, colsUsed);
            }
        }
        int abilityW = hasList
                ? maxColsUsed * uniformColW + (maxColsUsed - 1) * COL_GAP
                : font.width(emptyText);

        // ---- Header / progress strings ----
        Component title = Component.translatable("hud.tribulation.detail.title");
        Component tierLabel = Component.translatable("hud.tribulation.detail.tier_label", tier);
        Component levelText = Component.translatable("hud.tribulation.detail.level", level);
        int nextLevel = nextTierLevel(tier, config.tiers);
        Component nextText = nextLevel < 0
                ? Component.translatable("hud.tribulation.detail.max_tier")
                : Component.translatable("hud.tribulation.detail.next_tier", nextLevel);
        float fraction = ClientTribulationState.getProgressFraction();
        int percent = Math.round(fraction * 100f);
        Component progressText = Component.translatable("hud.tribulation.detail.progress",
                ClientTribulationState.getProgressTicks(), ClientTribulationState.getGoalTicks());
        Component percentText = Component.translatable("hud.tribulation.detail.percent", percent);

        int dotsW = numPages > 1 ? numPages * DOT_SIZE + (numPages - 1) * DOT_GAP : 0;
        int headingRowW = font.width(nearbyHeading) + (dotsW > 0 ? 12 + dotsW : 0);
        int headerW = ICON_SIZE + 4 + font.width(title) + 12 + font.width(tierLabel);
        int levelRowW = font.width(levelText) + 12 + font.width(nextText);
        int figuresW = font.width(progressText) + 12 + font.width(percentText);
        int contentW = max(MIN_CONTENT_W, headerW, levelRowW, figuresW, abilityW, headingRowW);

        int contentH = chromeH - 2 * INSET + headingH + bodyRows * LINE_H;
        int panelW = contentW + 2 * INSET;
        int panelH = contentH + 2 * INSET;
        int panelX = (graphics.guiWidth() - panelW) / 2;
        int panelY = (graphics.guiHeight() - panelH) / 2;

        // ---- Safety net: scale the whole panel down if it still wouldn't fit ----
        graphics.setColor(1f, 1f, 1f, 1f);
        float fitScale = Math.min(1f, Math.min(
                graphics.guiWidth() * MAX_SCREEN_FRACTION / panelW,
                graphics.guiHeight() * MAX_SCREEN_FRACTION / panelH));
        boolean scaled = fitScale < 1f;
        if (scaled) {
            float scx = graphics.guiWidth() / 2f;
            float scy = graphics.guiHeight() / 2f;
            graphics.pose().pushPose();
            graphics.pose().translate(scx, scy, 0f);
            graphics.pose().scale(fitScale, fitScale, 1f);
            graphics.pose().translate(-scx, -scy, 0f);
        }

        // ---- Frame, content ----
        drawNineSlice(graphics, panelX, panelY, panelW, panelH);

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

        // ---- Body: static "Nearby" caption + dots, with the page cross-fading ----
        if (!hasList) {
            graphics.drawString(font, emptyText, contentX, y, COLOR_ASH, true);
        } else {
            graphics.drawString(font, nearbyHeading, contentX, y, COLOR_ASH, true);

            int page = 0;
            float bodyAlpha = 1f;
            if (numPages > 1) {
                long now = System.currentTimeMillis();
                page = (int) ((now / PAGE_HOLD_MS) % numPages);
                bodyAlpha = pageAlpha(now % PAGE_HOLD_MS);
                drawDots(graphics, contentX + contentW - dotsW, y + 2, numPages, page, tierColor);
            }
            y += LINE_H;

            int headerColor = fade(COLOR_BONE, bodyAlpha);
            int abilityColor = fade(COLOR_ASH, bodyAlpha);
            int cx = contentX;
            for (List<Group> col : pages.get(page)) {
                if (col.isEmpty()) continue;
                drawColumn(graphics, font, col, cx, y, headerColor, abilityColor);
                cx += uniformColW + COL_GAP;
            }
        }

        if (scaled) {
            graphics.pose().popPose();
        }
        graphics.setColor(1f, 1f, 1f, 1f);

        // Commit the batch before anything can re-batch or read the framebuffer,
        // so ImmediatelyFast / Blur+ can't drop these sprites (Concord HUD Standard).
        graphics.flush();
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
     * Partition groups into pages of up to {@code pageCols} columns, each up to
     * {@code rowsPerCol} rows, never splitting a group. Columns fill top-to-bottom
     * then left-to-right; a full page starts a new one. Always returns at least
     * one (possibly empty) page.
     */
    private static List<List<List<Group>>> paginate(List<Group> groups, int pageCols, int rowsPerCol) {
        List<List<List<Group>>> pages = new ArrayList<>();
        List<List<Group>> page = emptyPage(pageCols);
        int col = 0;
        int colRows = 0;
        for (Group g : groups) {
            int gr = g.rowCount();
            if (colRows > 0 && colRows + gr > rowsPerCol) {
                col++;
                colRows = 0;
                if (col >= pageCols) {
                    pages.add(page);
                    page = emptyPage(pageCols);
                    col = 0;
                }
            }
            page.get(col).add(g);
            colRows += gr;
        }
        pages.add(page);
        return pages;
    }

    private static List<List<Group>> emptyPage(int pageCols) {
        List<List<Group>> page = new ArrayList<>(pageCols);
        for (int i = 0; i < pageCols; i++) {
            page.add(new ArrayList<>());
        }
        return page;
    }

    /** Triangular fade: 0→1 over the first {@link #FADE_MS}, 1→0 over the last. */
    private static float pageAlpha(long within) {
        if (within < FADE_MS) return within / (float) FADE_MS;
        if (within > PAGE_HOLD_MS - FADE_MS) return (PAGE_HOLD_MS - within) / (float) FADE_MS;
        return 1f;
    }

    private static void drawDots(GuiGraphics g, int x, int y, int count, int active, int activeColor) {
        int dx = x;
        for (int i = 0; i < count; i++) {
            g.fill(dx, y, dx + DOT_SIZE, y + DOT_SIZE, i == active ? activeColor : COLOR_DOT_OFF);
            dx += DOT_SIZE + DOT_GAP;
        }
    }

    private static void drawColumn(GuiGraphics graphics, Font font, List<Group> column, int x, int top,
                                   int headerColor, int abilityColor) {
        int cy = top;
        for (Group g : column) {
            graphics.drawString(font, g.header(), x, cy, headerColor, true);
            cy += LINE_H;
            for (MobAbility ability : g.abilities()) {
                graphics.drawString(font, abilityComponent(ability), x + ABILITY_INDENT, cy, abilityColor, true);
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

    /** Scale a colour's existing alpha by {@code a} (0..1) — used for the body fade. */
    private static int fade(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int scaled = Math.round(base * Math.max(0f, Math.min(1f, a)));
        return (scaled << 24) | (color & 0xFFFFFF);
    }

    private static int max(int... values) {
        int m = Integer.MIN_VALUE;
        for (int v : values) {
            m = Math.max(m, v);
        }
        return m;
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
