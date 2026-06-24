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

## Branding masters

| Asset | Source | Final / derived copies |
|---|---|---|
| Full logo | `art/logo.png` — `.png` master (not glyph-based) | `site/assets/logo.png` |
| Mod icon | `art/glyphs/hud-skull-32.glyph` → `art/icon-128.png` (128 native), `art/icon-512.png` (512 store master) | `assets/tribulation/icon.png` (256, in-jar — Mod Menu, and the EMI/REI/JEI source-mod icon), `site/assets/icon.png` (256, favicon/nav), `site/assets/apple-touch-icon.png` (180) |

## In-game pixel art

| Asset | `.glyph` source | Final asset |
|---|---|---|
| HUD difficulty badge (tier-tinted skull) | `art/glyphs/hud-skull-32.glyph` | `assets/tribulation/textures/gui/hud_icon.png` (32×32, blitted at 16×16) |
| Tier detail panel frame | `art/glyphs/panel-frame-64.glyph` | `assets/tribulation/textures/gui/tier_detail_panel.png` (64×64, drawn nine-sliced) |
| Heart Fragment item | `art/glyphs/heart-fragment-32.glyph` | `assets/tribulation/textures/item/heart_fragment.png` |
| Shatter Shard item | `art/glyphs/shatter-shard-32.glyph` | `assets/tribulation/textures/item/shatter_shard.png` |
| Threat particle — cursed mote (tier 4+) | `art/glyphs/threat-tier-16.glyph` | `assets/tribulation/textures/particle/threat_tier.png` |
| Threat particle — Big-zombie dust | `art/glyphs/threat-big-16.glyph` | `assets/tribulation/textures/particle/threat_big.png` |
| Threat particle — Speed-zombie streak | `art/glyphs/threat-speed-16.glyph` | `assets/tribulation/textures/particle/threat_speed.png` |

## Audio (`.sfx` — procedurally synthesized)

| Asset | `.sfx` source | Final asset |
|---|---|---|
| Tier-up sting (`tribulation:tier_up`) | `art/audio/tier-up.sfx` | `assets/tribulation/sounds/tier_up.ogg` |

## Not yet created

| Asset | Source | Final asset |
|---|---|---|
| Recipe browser icon (EMI/REI/JEI tab) | `/glyph` | — (planned) |
| Tier threshold icons (set of 5) | `/glyph` | — (planned) |
| Death penalty icons (set of 4) | `/glyph` | — (planned) |
| Website hero background | Gemini | — (planned, `site/`) |
| Open Graph image | Gemini | — (planned, `site/assets/`) |
| Discord embed banner | Gemini | — (planned) |
