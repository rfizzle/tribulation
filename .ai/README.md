# `.ai/` — agent-shared assets

Single, vendor-neutral home for everything AI agents (Claude Code, Jules, future tools) read when working on this repo.

| Path | What lives there |
|---|---|
| `skills/` | Domain skills (`mc-*/SKILL.md`). Claude Code auto-loads via `.claude/skills` symlink; Jules reads them when `AGENTS.md` points to them. |
| `review-criteria.yml` | Tribulation-specific categories the reviewer scores — a whole-file override of the [concord default](https://github.com/rfizzle/concord/blob/master/.ai/review-criteria.yml). Edit this to change review behavior without touching workflow YAML. |

The CI prompts (`code-reviewer.md`, `spec-writer.md`) are **not** kept here —
the generic suite defaults in [rfizzle/concord](https://github.com/rfizzle/concord)
`.ai/prompts/` are used. Resolution order in the reusable workflows: explicit
`prompt-file`/`criteria-file` workflow input → repo-local `.ai/` file
(whole-file override) → concord default. To specialize a prompt for this repo,
copy the concord default into `.ai/prompts/` and edit it.

## How to change the review

1. Edit `review-criteria.yml` (add/remove categories, tweak descriptions).
2. Re-run the review (push a commit, or use the workflow's re-run button).
3. The prompt is generic; the criteria file drives the categories.

## How to change the spec format

Copy concord's `.ai/prompts/spec-writer.md` into `.ai/prompts/` here and edit
it — the workflow picks up the repo-local override at runtime, no YAML changes
needed.

## Adding a new skill

1. Create `skills/<name>/SKILL.md` with the standard frontmatter (`name`, `description`).
2. Add a row to the "Working with domain skills" table in `AGENTS.md` so Jules knows when to read it.
