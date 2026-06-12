# Tribulation — Design Specification

> Difficulty Overhaul for Minecraft 1.21.1 Fabric

---

## 1. Brand Identity

### Narrative

Tribulation transforms Minecraft's flat difficulty curve into an escalating gauntlet where mobs grow stronger with playtime, distance from spawn, and depth. Optional permanent death penalties raise the stakes further. The name evokes suffering, endurance, and the trials that forge strength. The visual language draws from **mortality**, **blood and bone**, **crumbling stone**, and **the passage of time** — an hourglass measuring what you have left to lose.

### Tagline

*"Survive what comes next."*

### Logo Description

**Full Logo (`Tribulation-Logo.png`):** A stone-framed hourglass sits within a cracked circular stone border wrapped with dark, thorny vines. The upper chamber holds a glowing red pixel heart; the lower chamber is filled with fallen hearts, skulls, and bones — life draining away. A crimson-red glow emanates from behind the hourglass. The background is dark red-brown brickwork splattered with blood, scattered with bones and skeletal remains. Below, "TRIBULATION" in a blocky pixel font on a stone tablet, with "MINECRAFT DIFFICULTY OVERHAUL" subtitle.

**Icon (`Tribulation-Icon.png`):** The hourglass and stone frame isolated. The heart-to-skulls motif is clearly visible. Dark red glow radiating outward. Thorny vines wrap the stone border. No text.

**In-Game Icon (`assets/tribulation/icon.png`):** A pixel-art skull with a red upward-pointing flame/arrow behind it — white skull, crimson backdrop, suggesting danger and escalation.

### Color Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary | Deep Crimson Black | `#1a0a0a` | Backgrounds, dark surfaces |
| Secondary | Dark Blood | `#2e1010` | Mid-tones, card backgrounds |
| Accent 1 | Crimson | `#DC143C` | Glows, highlights, interactive elements |
| Accent 2 | Ember Orange | `#FF6B35` | Warm accents, fire, secondary highlights |
| Bright | Blood Red | `#FF4500` | Hover states, emphasis, danger |
| Glow | Dark Red | `#8B0000` | Deep shadows, ominous undertones |
| Bone | Skull White | `#e8e0d4` | Bone/skeleton accents, also body text |
| Heart | Pixel Heart Red | `#FF0000` | Heart icons, health references |
| Text Primary | Bone | `#e8e0d4` | Body text |
| Text Secondary | Ash | `#a89f93` | Muted text, descriptions |
| Text Tertiary | Smoke | `#6b6359` | Disabled, placeholder |
| Surface Base | Obsidian | `#0a0a0a` | Page backgrounds |
| Surface Card | Dark Stone | `#1a1a1a` | Cards, panels |
| Surface Elevated | Stone | `#222222` | Elevated surfaces, hover cards |

### Typography

- **Headings:** Pixel/blocky display font in gradient (`#DC143C` → `#FF6B35`)
- **Body:** Monospace stack: SF Mono, Cascadia Code, Fira Code, Consolas
- **Website gradient animation:** `ember-pulse` keyframes (4s ease-in-out, brightness 1→1.15)

---

## 2. Asset Inventory

### Existing Assets

| Asset | Location | Size | Status |
|-------|----------|------|--------|
| Full Logo | `/mnt/c/Users/colet/Downloads/Final-Minecraft-Mod-Logos/Tribulation-Logo.png` | ~6.6MB | Final |
| Icon (large) | `/mnt/c/Users/colet/Downloads/Final-Minecraft-Mod-Logos/Tribulation-Icon.png` | ~5.3MB | Final |
| Icon (1024px) | `/mnt/c/Users/colet/Downloads/Final-Minecraft-Mod-Logos/Tribulation-Icon-1024.png` | ~1.1MB | Final |
| Logo master | `art/logo.png` | — | Copied from above |
| Icon master | `art/icon-128.png` | 128×128 | Copy of the in-jar icon |
| Site Logo | `site/assets/logo.png` | — | Web copy of the master |
| Site Icon | `site/assets/icon.png` | — | Web copy of the master |
| In-Game Icon | `src/main/resources/assets/tribulation/icon.png` | 128×128 | Final — pixel-art skull with red flame |
| Website | `site/` content, built by the shared Concord template | — | Live at tribulation.rfizzle.com |

### Needed Assets

| Asset | Generator | Priority | Spec |
|-------|-----------|----------|------|
| Recipe browser icon (EMI/REI/JEI tab) | PixelLab | High | 16×16 or 32×32, skull or hourglass motif, red/crimson palette |
| HUD difficulty level indicator | PixelLab | High | 16×16 icon + compact text — single persistent HUD element showing current difficulty level/tier |
| Tier threshold icons (set of 5) | PixelLab | Medium | 16×16 icons for tiers 1–5 showing escalating danger |
| Shatter Shard item texture | PixelLab | High | 16×16 Minecraft item texture — crystalline shard, red/purple |
| Heart Fragment item texture | PixelLab | High | 16×16 Minecraft item texture — partial heart, glowing red |
| Death penalty icons (set of 4) | PixelLab | Medium | 16×16 icons for Death Relief, Shatter, Hardcore Hearts, Soul Inventory |
| Website hero background | Gemini | Medium | 1920×600 — dark brickwork with blood splatter and bone accents |
| Open Graph image | Gemini | Medium | 1200×630, logo centered on dark background |
| CurseForge gallery screenshots | Screenshot | High | 1920×1080, showing scaled mobs, tier abilities, death penalties |
| Favicon (`.ico` / `.svg`) | Derived | Low | 32×32 / 16×16 from icon |
| Apple Touch Icon | Derived | Low | 180×180 from icon |
| Discord embed banner | Gemini | Low | 1280×640, logo on dark background |

---

## 3. Generation Prompts

### Gemini Prompts (Logos / High-Res Art)

**Open Graph / Social Card:**
```
Pixel art style, 1200x630 banner image for a Minecraft mod called "Tribulation".
Center the logo: a stone-framed hourglass with a glowing red pixel heart in the
upper chamber and skulls/bones in the lower chamber. Cracked stone circular
border with thorny dark vines. The word "TRIBULATION" in blocky pixel font
below. Dark crimson-black (#1a0a0a) background. Red glow and ember particle
effects. Blood splatter accents. Style consistent with the existing
Tribulation logo.
```

**Website Hero Background:**
```
Pixel art tileable background texture, 1920x600. Dark red-brown brickwork
(#1a0a0a to #2e1010 gradient) with subtle blood splatter marks and cracks.
Faint bone fragments embedded in mortar. Occasional ember spark. Very
subtle — this is a background behind text. Minecraft pixel art style,
16-pixel grid aligned.
```

**Discord Banner:**
```
Pixel art banner, 1280x640. The Tribulation hourglass icon centered on a
dark crimson-black (#1a0a0a) background. Deep red glow radiating from center.
Ember particles rising. "Tribulation" in crimson-red pixel font below the
icon. "Difficulty Overhaul" subtitle in lighter text. Clean, minimal.
```

### PixelLab Prompts (Pixel Art)

**Recipe Browser Icon (EMI/REI/JEI Tab):**
```
Theme: Danger / difficulty / mortality
Subject: Small hourglass with a heart dripping into skulls, or a skull with
         upward red arrow
Style: Minecraft item icon, pixel art
Size: 32x32
Colors: Crimson (#DC143C) primary, dark red (#8B0000) shadows,
        bone white (#e8e0d4) skull/accents
Notes: Must read clearly at 16x16 downscale. No text. Single centered motif.
       Should suggest "increasing danger" at a glance.
```

**HUD Difficulty Level Icon:**
```
Theme: Danger / difficulty escalation
Subject: Small skull or flame icon for the shared HUD element strip
Style: Minecraft HUD icon, pixel art, minimal and flat
Size: 16x16
Colors: Bone white (#e8e0d4) skull or crimson (#DC143C) flame,
        tier-dependent intensity (warm orange tier 1 → deep crimson tier 5)
Notes: Sits inside the shared semi-transparent HUD box alongside a text
       label like "Lv. 127 · T3". Must be legible at native 16x16 against
       the dark box background. Transparent PNG. No frame or border — the
       shared HUD box provides the container.
```

### Shared HUD Element Standard

All mods in the rfizzle suite that display a persistent HUD element follow a single
shared design pattern to ensure visual consistency when multiple mods are installed:

**Layout:** Simple semi-transparent dark box (`#000000` at ~50-60% opacity, 2px rounded
corners) containing a 16×16 mod-themed icon on the left and short informational text
on the right (e.g., "Lv. 42", "Trusted", "Frontier"). Text uses the vanilla Minecraft
font, white with a standard drop shadow.

**Position:** Top-left corner of the screen, below the vanilla debug/coordinates area.
Elements stack vertically in a fixed priority order:

| Priority | Mod | HUD Element | Example Display |
|----------|-----|-------------|-----------------|
| 1 | Tribulation | Difficulty level + tier | `[skull] Lv. 127 · T3` |
| 2 | Mercantile | Reputation tier | `[emerald] Trusted` |
| 3 | Prosperity | Loot distance tier | `[chest] Frontier` |

Each element is independently togglable via its mod's config. Elements shift up to
fill gaps when a mod above them is absent or its HUD is disabled. Small vertical
padding (2px) between stacked elements.

**Implementation notes:**
- Each mod renders its own element at the correct offset based on how many higher-priority
  mods are present and have their HUD enabled. Check via `FabricLoader.getInstance().isModLoaded()`.
- The semi-transparent box auto-sizes to fit the icon + text content.
- No custom fonts, no ornate frames, no animations. Must blend with vanilla HUD.
- Hide during F1 (HUD hidden), during screen/GUI open, and during death screen.

### Tribulation HUD

Tribulation occupies **priority 1** (topmost position) in the shared HUD strip.
Displays difficulty level and tier (e.g., `Lv. 127 · T3`). All detailed mob scaling
info (stat breakdown, tier abilities, scaling axes) is surfaced through Jade/WTHIT
overlays when looking at mobs, and via the `/tribulation info` and `/tribulation debug`
commands.

**Tier Threshold Icons (set of 5):**
```
Theme: Escalating mob danger
Subject: Five 16x16 icons representing difficulty tiers:
  1. Tier 1 (Level 50) — single small flame, warm orange
  2. Tier 2 (Level 100) — double flame, red-orange
  3. Tier 3 (Level 150) — skull with flame, crimson
  4. Tier 4 (Level 200) — flaming skull, dark red glow
  5. Tier 5 (Level 250) — inferno skull with crown, maximum danger
Style: Minecraft item icons, pixel art, consistent set
Size: 16x16 each
Colors: Progression from warm orange (#FF6B35) through crimson (#DC143C)
        to deep blood red (#8B0000) with increasing intensity
```

**Shatter Shard Item Texture:**
```
Theme: Crystalline power / level reduction
Subject: Angular crystal shard, cracked, with inner energy
Style: Minecraft item texture, pixel art
Size: 16x16
Colors: Deep purple (#7B2FBE) crystal body, crimson (#DC143C) energy veins,
        white (#FFFFFF) edge highlights
Notes: Standard Minecraft item texture format. Should look like a fragment
       broken from something larger. Subtle inner glow effect.
```

**Heart Fragment Item Texture:**
```
Theme: Restored vitality / health recovery
Subject: Half or quarter of a pixel heart, glowing
Style: Minecraft item texture, pixel art
Size: 16x16
Colors: Red (#FF0000) heart body matching vanilla hearts, gold (#DAA520) glow/trim,
        pink (#FF6B9D) highlight
Notes: Standard Minecraft item texture format. Should clearly read as a
       "piece of a heart" — the recovery item for Hardcore Hearts mode.
       Resembles a cracked half-heart with golden repair lines (kintsugi style).
```

**Death Penalty Mode Icons (set of 4):**
```
Theme: Death penalty mechanics
Subject: Four 16x16 icons representing penalty modes:
  1. Death Relief — downward arrow with level number, calming blue tint
  2. Shatter Shards — crystal shard breaking, purple/red
  3. Hardcore Hearts — heart with crack/X, permanent loss
  4. Soul Inventory — ghostly inventory grid, spectral blue
Style: Minecraft item icons, pixel art, consistent set
Size: 16x16 each
Colors: Each uses crimson (#DC143C) as base with mode-specific secondary:
        blue for relief, purple for shatter, red for hearts, spectral blue for soul
```

---

## 4. Image References

| Image | Reference Source | Notes |
|-------|----------------|-------|
| Hourglass motif | Tribulation-Icon.png | Stone-framed hourglass with heart → skulls |
| Heart symbolism | Tribulation-Logo.png upper chamber | Glowing red pixel heart — Minecraft-style |
| Skull/bone detail | Tribulation-Logo.png lower chamber | Hearts and skulls collecting at bottom |
| Thorny vines | Tribulation-Icon.png border | Dark, dead-looking vines unlike Mercantile's lush ones |
| Blood/splatter | Tribulation-Logo.png background | Red splatter marks on dark brickwork |
| Red glow style | Tribulation-Icon.png outer glow | Deep crimson radial, ominous |
| Pixel density | `assets/tribulation/icon.png` | Skull + flame — sets in-game pixel style |
| Ember particles | Tribulation-Logo.png background | Orange sparks among the blood and bone |
| Website color scheme | `site/site.json` theme (rendered by the shared Concord template) | Crimson/ember gradient, existing design tokens |

---

## 5. Website Specification

### Domain & Hosting

- **Domain:** `tribulation.rfizzle.com`
- **Hosting:** GitHub Pages (source: Actions) — `site/` content built and deployed by
  concord's reusable `build-site.yml` via the repo's `site.yml` stub
- **CNAME:** `site/site.json` `domain` field → `tribulation.rfizzle.com`

### Current Pages

| Page | File | Content |
|------|------|---------|
| Home | `index.html` | Hero with logo, feature overview, download links |
| Features | `features.html` | Detailed feature breakdown |
| Config | `config.html` | Configuration reference |
| Commands | `commands.html` | Command reference |

### Pages to Add

| Page | File | Content |
|------|------|---------|
| Getting Started | `guide.html` | Installation, understanding levels/tiers, choosing penalty modes |
| Mob Reference | `mobs.html` | Per-mob scaling rates, caps, and tier abilities |
| FAQ | `faq.html` | Performance impact, multiplayer fairness, compatibility |
| Changelog | `changelog.html` | Version history |

### Website Design Tokens (Tailwind)

```javascript
colors: {
    base: '#0a0a0a',
    card: '#1a1a1a',
    elevated: '#222222',
    crimson: { DEFAULT: '#DC143C', dark: '#8B0000' },
    ember: { DEFAULT: '#FF6B35', bright: '#FF4500' },
    bone: '#e8e0d4',
    ash: '#a89f93',
    smoke: '#6b6359',
}
```

### SEO & Social

- **Title pattern:** `{Page} — Tribulation | Difficulty Overhaul for Minecraft`
- **og:image:** Must be absolute URL (`https://tribulation.rfizzle.com/logo.png`)
- **og:url:** Already set (`https://tribulation.rfizzle.com`)
- **twitter:card:** `summary_large_image` (upgrade from `summary`)
- **Favicon:** `<link rel="icon" type="image/png" href="icon.png">`
- **Apple Touch:** `<link rel="apple-touch-icon" href="apple-touch-icon.png">` (need to create)

### Cross-Mod Navigation

Footer section linking to all companion mods:
```
Part of the rfizzle mod suite:
[Meridian] [Mercantile] [Tribulation] [Prosperity]
```

---

## 6. Distribution Listings

### CurseForge / Modrinth

**Description Template:**
1. Logo image (centered)
2. One-paragraph summary
3. Feature list with headers (Three-Axis Scaling, Tier Abilities, Zombie Variants, Boss Scaling, Death Penalties, Rewards)
4. Screenshot gallery (3–5 images)
5. Requirements section (Fabric Loader, Fabric API, Cloth Config)
6. Optional dependencies (Jade/WTHIT)
7. Links to companion mods

**Screenshot Standards:**
- Resolution: 1920×1080
- Shader: Complementary Shaders (or vanilla for clarity)
- HUD: F3 debug or Jade tooltip showing mob stats preferred
- Subjects: (1) Scaled mob with visible stat boost, (2) Tier 5 mob ability in action, (3) Big/Speed zombie variants, (4) `/tribulation info` command output, (5) Hardcore hearts after death

**Changelog Format:**
```markdown
## [0.1.1] — 2025-XX-XX
### Added
- Feature description
### Changed
- Change description
### Fixed
- Fix description
```

### README Badges

```markdown
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)
![GitHub](https://img.shields.io/github/v/release/rfizzle/tribulation)
```

---

## 7. Companion Mod Context

Tribulation is part of a four-mod suite. Each mod overhauls a different Minecraft system:

| Mod | Domain | Color Signature | Icon Motif |
|-----|--------|----------------|------------|
| **Meridian** | Enchanting | Violet / Gold | Compass rose |
| **Mercantile** | Villagers & Trade | Green / Emerald | Market stall / scales |
| **Tribulation** | Difficulty & Scaling | Crimson / Red | Hourglass with hearts |
| **Prosperity** | Loot & Containers | Gold / Diamond Cyan | Trophy chalice |

All four share:
- Minecraft 1.21.1, Java 21, Fabric
- Dark base website theme (`#0a0a0a` / `#1a1a1a` / `#222222`)
- Bone/Ash/Smoke text palette
- Monospace font stack
- Pixel art logo style (Gemini-generated)
- Same website structural pattern (hero → features → config → commands)
- MIT license
- Optional Jade/WTHIT, EMI/REI/JEI, ModMenu, Cloth Config integrations
