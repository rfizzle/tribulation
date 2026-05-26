# `.ai/` — agent-shared assets

Single, vendor-neutral home for everything AI agents (Claude Code, Jules, future tools) read when working on this repo.

| Path | What lives there |
|---|---|
| `skills/` | Domain skills (`mc-*/SKILL.md`). Claude Code auto-loads via `.claude/skills` symlink; Jules reads them when `AGENTS.md` points to them. |
| `prompts/spec-writer.md` | Prompt for the `needs-spec` label workflow — produces an implementation spec as an issue comment. |
| `prompts/code-reviewer.md` | Prompt for `claude-code-review.yml` — produces the structured `✓/⚠/✗` review table. |
| `review-criteria.yml` | Repo-tailored categories the reviewer scores. Edit this to change review behavior without touching workflow YAML. |

## How to change the review

1. Edit `review-criteria.yml` (add/remove categories, tweak descriptions).
2. Re-run the review (push a commit, or use the workflow's re-run button).
3. The prompt is generic; the criteria file drives the categories.

## How to change the spec format

Edit `prompts/spec-writer.md`. The workflow loads it at runtime — no YAML changes needed.

## Adding a new skill

1. Create `skills/<name>/SKILL.md` with the standard frontmatter (`name`, `description`).
2. Add a row to the "Working with domain skills" table in `AGENTS.md` so Jules knows when to read it.
