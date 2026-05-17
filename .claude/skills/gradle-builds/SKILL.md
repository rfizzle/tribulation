---
name: gradle-builds
description: Best practices for running Gradle builds, tests, and gametests in this Fabric mod. Prevents wasted reruns from partial output capture. TRIGGER when running any Gradle command (build, test, runGametest, assemble, check), or when inspecting build output/reports.
---

The user is running a Gradle build, test, or gametest in this Fabric mod. Follow these rules to avoid losing output and forcing expensive reruns.

## Why this skill exists

Gradle builds and tests are slow (compilation, bootstrap, gametest server spin-up). Using `tail`, `head`, or other partial-read shell commands on build output loses critical information — early errors, test names, stack traces — and forces a full rerun just to see what was missed. Every rerun costs minutes.

## Rules

### 1. Never use partial-read shell commands on build output

**Banned patterns:**
- `tail -n N` / `tail -f` on build logs or piped Gradle output
- `head -n N` on build logs
- `grep` as the *first* pass on build output (fine after you already have the full picture)
- Piping Gradle output through `awk`, `sed`, or `cut` to trim it before reading

**Why:** These discard context you will need. A `tail -20` on a failed build misses the compilation error 200 lines up. A `head -50` on test output misses the actual failures at the bottom.

### 2. Capture full output from the Gradle command

**Preferred approach — direct Bash with timeout:**

```bash
# Use a generous timeout (up to 10 minutes) for slow builds
./gradlew test 2>&1
```

Set `timeout: 600000` on the Bash call if the build may take several minutes.

**For very long builds — use background execution:**

```bash
# Run in background, get notified when done
./gradlew test 2>&1 | tee /tmp/tribulation-test-output.txt
```

Use `run_in_background: true` on the Bash call. When notified of completion, `Read` the full output file.

### 3. Read build reports instead of re-running

After a Gradle test run completes, the structured reports are on disk. Read these instead of re-running:

| Report type | Path |
|-------------|------|
| HTML test report | `build/reports/tests/test/index.html` |
| JUnit XML results | `build/test-results/test/*.xml` |
| Gametest results | `build/gametest/` or console output |
| Build failure log | Full console output from the Bash call |

**Use the `Read` tool** to inspect these files with `offset`/`limit` for navigation — never shell partial-read commands.

### 4. If output is truncated, read the log file — don't rerun

If a Bash tool call's output gets truncated (common with verbose Gradle output):

1. Check if you piped to a file (e.g., `/tmp/tribulation-test-output.txt`) — `Read` it.
2. Check the HTML/XML reports under `build/` — they have the same information in structured form.
3. Only rerun as a **last resort**, and if you do, pipe to a file first.

### 5. Gradle command patterns

Always run from the repo root:

```bash
# Build
./gradlew build 2>&1

# Test (unit + fabric-loader-junit)
./gradlew test 2>&1

# Gametest (needs a server spin-up — slower)
./gradlew runGametest 2>&1

# Single test class
./gradlew test --tests "com.rfizzle.tribulation.SomeTest" 2>&1

# Clean + build (when class files are stale)
./gradlew clean build 2>&1
```

### 6. When a build fails

1. Read the **full** error output from the Bash result first.
2. If truncated, read the report files under `build/`.
3. Fix the issue.
4. Rerun **only the specific failing task** (e.g., `:test` not `:build` if only tests failed).
5. For compilation errors, run `:compileJava` alone first before running the full test suite.

### 7. Tee output for any build you suspect might be long

When in doubt about build duration, always tee:

```bash
./gradlew test 2>&1 | tee /tmp/tribulation-test-output.txt
```

This way even if the Bash call times out or output is truncated, the full log is on disk and readable via `Read`.
