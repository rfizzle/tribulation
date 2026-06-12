# Handoff: Feature Review + Vanilla+ Expansion Vision for Tribulation

> Prompt for Fable 5. Reviews Tribulation's existing feature surface and produces a
> forward-looking, Vanilla+ expansion vision consistent with the rfizzle mod suite.

---

You are reviewing **Tribulation**, a Minecraft 1.21.1 Fabric difficulty-overhaul mod,
and producing a forward-looking vision statement. This is part of the **rfizzle mod
suite** — four mods that each overhaul a different vanilla system. Your output will be
read by a human maintainer and used to seed GitHub issues, so it must be concrete,
opinionated, and grounded in what already exists.

## Required reading (read before writing anything)
- `README.md` — feature summary and developer API surface
- `docs/design/DESIGN.md` — brand identity, design tokens, shared-HUD standard, companion-mod context
- `docs/features.html`, `docs/config.html`, `docs/commands.html` — full feature/config/command surface
- `src/main/java/...` — skim the actual implementation to ground claims (scaling formulas,
  tier abilities, death penalties, the public `TribulationAPI`)
- `CLAUDE.md` / `AGENTS.md` — conventions, source layout, lifecycle
- **Sibling mods** (same parent directory as this repo: `../meridian`, `../mercantile`,
  `../prosperity`) — skim each one's `README.md` and its `DESIGN.md` (repo root, where
  present; mercantile may not have one yet) so the "stay in your lane" boundary is grounded
  in what those mods actually do, not just the summary table below. If a sibling repo isn't
  present, fall back to the suite table.

Do **not** rely on the docs alone — cross-check at least the scaling and tier-ability
systems against the source so your review reflects reality, not the marketing copy.

## Context: what Tribulation is
A formula-driven difficulty overhaul. Three-axis mob scaling (time/playtime level 0–250,
distance from spawn, depth from sea level), tier-gated mob abilities at levels
50/100/150/200/250, and opt-in death penalties (Death Relief, Shatter Shards, Hardcore
Hearts, Soul Inventory). Zero hard external dependencies. Crimson/mortality brand identity
("Survive what comes next").

## Context: the suite (avoid scope overlap)
Each mod owns one system. Recommendations must stay in **Tribulation's lane** (difficulty,
mob threat, death stakes, escalation) and explicitly defer adjacent ideas to the right mod:
- **Meridian** — Enchanting (violet/gold)
- **Mercantile** — Villagers & Trade (green/emerald)
- **Tribulation** — Difficulty & Scaling (crimson/red)  ← this mod
- **Prosperity** — Loot & Containers (gold/cyan)

If a feature idea belongs to enchanting, trade, or loot, note it and hand it off rather
than building it here.

## The "Vanilla+" constraint (the hard filter)
Every recommendation must pass this test. Vanilla+ means it feels like it *could* have
shipped in vanilla Minecraft:
- Reuses vanilla mobs, items, mechanics, and visual language — no fantasy classes, mana
  bars, RPG skill trees, or HUD clutter that breaks the vanilla aesthetic
- No new dimensions, no sweeping content packs, no mechanics that demand a wiki to play
- Additive and configurable — every feature toggleable, formula-driven, multiplayer-fair,
  and respectful of the shared-HUD standard in DESIGN.md §"Shared HUD Element Standard"
- Server-friendly and performant — this is a scaling mod; per-tick cost matters
Reject your own ideas that fail this test and say why you rejected them.

## Deliverable
Produce a single markdown document with these sections:

1. **Feature Review** — For each existing system: what it does, whether the implementation
   matches the docs, and any gaps, rough edges, balance concerns, or design inconsistencies
   you find. Be a critic, not a cheerleader. Flag anything that feels un-vanilla, fiddly,
   or unfair in multiplayer.

2. **Vision Statement** — 2–4 paragraphs articulating where Tribulation should head over the
   next several releases, consistent with the brand ("Survive what comes next") and the
   escalation/mortality theme. What is the *complete* expression of this mod?

3. **Recommended Features** — A prioritized list (High/Med/Low). For each:
   - One-line pitch
   - How it fits the escalation/mortality theme and the three-axis scaling model
   - Why it's Vanilla+ (and what un-vanilla version you deliberately avoided)
   - Rough implementation sketch (which vanilla systems/mixins/events it touches)
   - Suite-fit note: does it stay in Tribulation's lane, or lean on Meridian/Mercantile/
     Prosperity? Does it want anything from the public `TribulationAPI`?
   - Config knobs it would expose

4. **Explicitly Out of Scope** — Tempting ideas you rejected, with the reason (wrong mod,
   not vanilla+, too costly, unfair in MP).

## Style
Concrete over aspirational. Name vanilla mobs, items, and mechanics specifically. Prefer a
short list of strong, coherent ideas over an exhaustive brainstorm. Assume the reader knows
Minecraft deeply and is allergic to bloat.
