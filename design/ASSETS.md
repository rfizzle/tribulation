# Tribulation — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, or a `.png` master for logos) and the final file it
> ships as. **`MISSING`** in the glyph column flags a pixel asset that has no
> `.glyph` source yet — a candidate for the glyph pipeline (concord
> [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §8).
> [`DESIGN.md`](DESIGN.md) covers *why* each asset exists; this file covers *where* it lives.
>
> Final paths are under `src/main/resources/` unless noted. A separate report sweeps
> the resource tree for any final asset lacking a `.glyph` source.

## Branding masters (`.png` — not glyph-based)

| Asset | `art/` master | Final / derived copies |
|---|---|---|
| Full logo | `art/logo.png` | `site/assets/logo.png` |
| Mod icon (128) | `art/icon-128.png` | `assets/tribulation/icon.png` (in-jar), `site/assets/icon.png`, `site/assets/apple-touch-icon.png` |

## In-game pixel art

| Asset | `.glyph` source | Final asset |
|---|---|---|
| HUD difficulty badge (tier-tinted skull) | `art/glyphs/hud-skull-32.glyph` | `assets/tribulation/textures/gui/hud_icon.png` (32×32, blitted at 16×16) |
| Heart Fragment item | `art/glyphs/heart-fragment-32.glyph` | `assets/tribulation/textures/item/heart_fragment.png` |
| Shatter Shard item | `art/glyphs/shatter-shard-32.glyph` | `assets/tribulation/textures/item/shatter_shard.png` |

## Not yet created

| Asset | Source | Final asset |
|---|---|---|
| Recipe browser icon (EMI/REI/JEI tab) | `/glyph` | — (planned) |
| Tier threshold icons (set of 5) | `/glyph` | — (planned) |
| Death penalty icons (set of 4) | `/glyph` | — (planned) |
| Website hero background | Gemini | — (planned, `site/`) |
| Open Graph image | Gemini | — (planned, `site/assets/`) |
| Discord embed banner | Gemini | — (planned) |
