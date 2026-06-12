# AGENTS.md

Guidance for AI coding agents (Claude Code, Google Jules, and any future tool)
working on this repository. `CLAUDE.md` is a symlink to this file — both names
point at the same content so each agent finds what it expects to read.

## Project overview

Tribulation is a Minecraft 1.21.1 Fabric mod — a difficulty overhaul that adds
mob scaling, tier abilities, and death penalties. Java 21, Fabric Loader 0.16.10,
Loom 1.9. The feature surface is documented in [`README.md`](README.md) and
[tribulation.rfizzle.com](https://tribulation.rfizzle.com). Work is tracked in GitHub Issues —
see the [Development lifecycle](#development-lifecycle) section below.

## Suite standards (Concord)

This mod is a member of Concord, the Vanilla+ collection. Suite-wide standards live in
the [concord repo](https://github.com/rfizzle/concord) — checked out at `../concord/`
in the local workspace. Normative for this repo:

- [API-STANDARD.md](https://github.com/rfizzle/concord/blob/master/API-STANDARD.md) — the `api` package conventions (conforms to v1)
- [HUD-STANDARD.md](https://github.com/rfizzle/concord/blob/master/HUD-STANDARD.md) — HUD slot, stacking, accessors (conforms to v1)
- [DESIGN-SYSTEM.md](https://github.com/rfizzle/concord/blob/master/design/DESIGN-SYSTEM.md) — palette, typography, logo rules
- [REPO-LAYOUT.md](https://github.com/rfizzle/concord/blob/master/REPO-LAYOUT.md) — where non-code files live (conforms)

## Build commands

```bash
./gradlew build          # compile + test + jar
./gradlew test           # JUnit tests only
./gradlew runGametest    # Fabric gametests (headless server)
./gradlew runClient      # launch dev client
./gradlew runServer      # launch dev server
./gradlew genSources     # decompile MC sources for IDE nav
```

Run a single JUnit test:
`./gradlew test --tests "com.rfizzle.tribulation.SomeTest"`

Read [`.ai/skills/mc-gradle-builds/SKILL.md`](.ai/skills/mc-gradle-builds/SKILL.md)
**before running any Gradle command** — it covers how to avoid wasted reruns
from partial output capture.

## Source layout

Loom's `splitEnvironmentSourceSets()` is enabled — three source sets:

| Source set | Root | Purpose |
|---|---|---|
| `main` | `src/main/java` | Server + common logic. Entrypoint: `Tribulation.java` |
| `client` | `src/client/java` | Client-only code. Entrypoint: `TribulationClient.java` |
| `gametest` | `src/gametest/java` | Fabric gametests (run with `runGametest`). Has `main` on its classpath but is NOT included in the jar. Two entrypoints: `MobScalingGameTest`, `DeathPenaltiesGameTest`. |

JUnit tests go in the standard `src/test/java` directory. The test classpath
includes `fabric-loader-junit` but excludes `fabric-api` — tests that need
Fabric APIs must use gametests instead.

## Key conventions

- **Mod ID:** `tribulation` — use `Tribulation.id("path")` to create
  `ResourceLocation`s. Never construct `ResourceLocation` directly with the
  mod ID inlined.
- **Mappings:** Official Mojang mappings (not Yarn). Use Mojang class/method
  names everywhere (`CompoundTag`, not `NbtCompound`; `Level`, not `World`).
- **Assets:** Tribulation has its own custom assets at `assets/tribulation/`
  (textures, models, sounds).
- **Mixin config:** `tribulation.mixins.json` in `src/main/resources`. Mixin
  package: `com.rfizzle.tribulation.mixin`.
- **Performance hot path:** the mob scaling handler runs on every hostile
  spawn — avoid per-spawn allocations and expensive lookups in scaling-formula
  code paths.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/)
  with a topical scope naming the feature area: `feat(scaling): …`,
  `fix(abilities): …`, `refactor(death-penalty): …`, `ci(review): …`,
  `build(test): …`, `chore(ai): …`, `docs(readme): …`. Allowed types:
  `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`, `perf`,
  `style`. Subject line in imperative mood, no trailing period, ≤72 chars.
  Reference the issue in the body footer: `Closes #42` (or `Refs #42` for
  partial work).

## Compat integrations

The mod has optional integrations (all `modCompileOnly` — not bundled):

- **Mod Menu** — config screen entry via `ModMenuIntegration`
- **Cloth Config** — settings GUI builder
- **Jade / WTHIT** — tooltip overlays
- **EMI / REI / JEI** — recipe viewer support

Compat classes live under `com.rfizzle.tribulation.compat.<modid>`.

## Where things live

| Path | Purpose |
|---|---|
| `README.md` | Project overview and feature summary. |
| `site/pages/features.json` | Detailed feature surface and tunable knobs (source for [tribulation.rfizzle.com](https://tribulation.rfizzle.com)). |
| GitHub Issues | Active work — feature requests, bugs, in-flight specs. |
| `.ai/skills/` | Domain skills — read these before working in their subject area. |
| `.github/workflows/` | Thin trigger stubs — workflow logic, default CI prompts, and [review criteria](https://github.com/rfizzle/concord/blob/master/.ai/review-criteria.yml) live in [rfizzle/concord](https://github.com/rfizzle/concord). |

## Working with domain skills

Eight skills under `.ai/skills/` cover this codebase. Claude Code auto-loads
them via the `.claude/skills` symlink; Jules and other agents should read the
relevant `SKILL.md` directly before working in its subject area.

| When you're touching… | Read first |
|---|---|
| `*Registry.java`, `Registry.register()` calls, creative tabs, particles, sounds, commands | [`mc-registration`](.ai/skills/mc-registration/SKILL.md) |
| Data generation providers (models, blockstates, recipes, tags, loot, advancements) | [`mc-datagen`](.ai/skills/mc-datagen/SKILL.md) |
| Any test — JUnit, fabric-loader-junit, or Fabric gametest | [`mc-mod-testing`](.ai/skills/mc-mod-testing/SKILL.md) |
| Mock players in gametests | [`mc-testing-mock`](.ai/skills/mc-testing-mock/SKILL.md) |
| Custom packets, payload types, stream codecs, client/server sync | [`mc-networking`](.ai/skills/mc-networking/SKILL.md) |
| Managers, caches, hot-reloadable registries, any shared mutable state | [`mc-shared-state`](.ai/skills/mc-shared-state/SKILL.md) |
| Running `./gradlew` for tests/builds/gametests/datagen | [`mc-gradle-builds`](.ai/skills/mc-gradle-builds/SKILL.md) |
| Mixins, accessors, invokers, access wideners | [`mc-mixin-craft`](.ai/skills/mc-mixin-craft/SKILL.md) |

## Development lifecycle

1. **Issue opened** using the feature or bug template under `.github/ISSUE_TEMPLATE/`.
2. **Triage** — human discussion in the issue.
3. **`needs-spec` label** added → `.github/workflows/claude-spec.yml` fires,
   Claude posts a structured implementation spec as an issue comment
   (prompt: concord's default `spec-writer.md`, unless a repo-local
   `.ai/prompts/spec-writer.md` override exists).
4. **Human review** — spec edited or approved.
5. **`jules` label** added (remove `needs-spec`) → Jules picks up the issue
   and opens a draft PR.
6. **PR opened** → `claude-code-review.yml` posts a structured ✓/⚠/✗ review
   (categories from concord's default `review-criteria.yml`, unless a
   repo-local `.ai/review-criteria.yml` override exists). `ci.yml` runs the
   full build, unit tests + gametests, and uploads coverage + results to
   Codecov.
7. **Human review + merge.**

`@claude <message>` in any issue or PR comment also invokes Claude for ad-hoc
help via `.github/workflows/claude.yml`.

## Version scheme

Version is computed from git tags at build time (`build.gradle`,
`computeModVersion()`). Base version is in `gradle.properties` as
`mod_version`. Tagged commits produce clean versions; post-tag commits append
`+<commits>.g<sha>`.
