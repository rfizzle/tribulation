# Agent Prompt: Tribulation Death Penalties

You are implementing two optional death-penalty features in the Tribulation companion
mod (`companions/tribulation/`). Both are disabled by default.

## How to work

You have two reference documents — read both before starting:

1. **Spec:** `companions/tribulation/doc/DEATH_PENALTIES_SPEC.md` — full design, config
   shapes, mixin targets, attribute modifiers, and test plan.
2. **TODO:** `companions/tribulation/doc/TODO_DEATH_PENALTIES.md` — phased checklist
   of every task.

**Work through the TODO one task at a time.** After completing each task (including
its tests if any), stop and ask the user whether to continue to the next task. Do not
batch multiple tasks or skip ahead. If a task has sub-bullets, complete all sub-bullets
before stopping.

When you finish a task, briefly state what you did and what the next task is, then wait.

## Context

Tribulation is a Fabric 1.21.1 mod that scales mob difficulty over time. It already
has: `TribulationConfig` (JSON config with inner classes, `fillDefaults()`, `validate()`,
Cloth Config/ModMenu screen), `ConfigMigrator` (versioned JSON migrations),
`PlayerDifficultyState` (SavedData with per-player NBT), `DeathReliefHandler`
(AFTER_DEATH listener), `ShardDropHandler` (mob-kill item drops), `ShatterShardItem`
(consumable that reduces difficulty), `TribulationCommand` (Brigadier subcommands),
and `ModMenuIntegration` (Cloth Config categories).

## Patterns to follow

- Read existing files before editing. Match the code style exactly.
- `ShatterShardItem` → `HeartFragmentItem` (item pattern)
- `ShardDropHandler` → fragment drop logic (mob-kill drop pattern)
- `DeathReliefHandler` → `HardcoreHeartsHandler` (death event pattern)
- `TribulationConfig` inner classes → new config sections
- `ConfigMigrator.MIGRATIONS` array → new migration entry
- `ModMenuIntegration` category methods → new categories
- `PlayerDifficultyState` fields/NBT → new `heartsLost` field

## Testing

Use the `/fabric-testing` skill before writing tests. Tests go in `src/test/java/`
(JUnit + fabric-loader-junit) and `src/gametest/java/` (fabric-gametest). Follow
existing test patterns in the codebase. Run tests with `./gradlew test` from the
`companions/tribulation/` directory. Use the `/gradle-builds` skill for build commands.
