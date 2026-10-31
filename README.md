# doma-agent-bench

A benchmark that measures how well AI coding agents can build features with
[Doma](https://github.com/domaframework/doma), and shows **where they fail**.

The goal is not a leaderboard score. The goal is a list of failure patterns that
can be turned into improvements to Doma: documentation, error messages, APIs,
or agent-facing material such as `llms.txt`, skills and MCP servers.

## How it works

For each task × condition × repetition, `bench.py`:

1. Builds a fresh workspace in a temp directory: `template/` plus the task's `workspace/` overlay.
2. Gives the agent the task prompt, optionally with reference material (the *condition*).
3. Grades the result:
   - static checks on `src/main` (forbidden or required patterns, such as deprecated APIs)
   - `./gradlew compileJava`
   - replaces `src/test` with the hidden acceptance tests and runs `./gradlew test`
   - collects `DOMA4xxx` codes from the agent transcript and the build output
   - for Claude, collects turns, cost and every Gradle invocation the agent made
4. Writes `results/<run-id>/report.md`.

## Layout

```
bench.py              runner, grader and report
conditions.json       context given to the agent (C0: nothing, C1: docs/llms.txt, ...)
prompts/preamble.md   text prepended to every task prompt
template/             Gradle project shared by all tasks (Doma version in gradle.properties)
hidden-common/        test support (isolated H2 DB with seed data) shared by hidden tests
tasks/<name>/
  task.json           title, category, static checks
  prompt.md           what the agent is asked to do
  workspace/          files present before the agent starts (overlay on template/)
  reference/          a correct solution (overlay), used to validate the task
  hidden-tests/       acceptance tests, never visible to the agent
```

## Usage

Validate the tasks with the reference solutions (no LLM calls):

```bash
python3 bench.py run --agent reference --conditions C0,C1
```

Run Claude Code:

```bash
python3 bench.py run --agent claude --model claude-sonnet-5-5 --conditions C0,C1 --repeat 5
```

Run any other agent CLI. `{prompt_file}` and `{workspace}` are substituted, and the
prompt is also passed on stdin:

```bash
python3 bench.py run --agent command --agent-command 'my-agent --prompt-file {prompt_file}'
```

Measure an unreleased Doma build:

```bash
(cd ../doma && ./gradlew publishToMavenLocal)
python3 bench.py run --agent claude --doma-version 3.14.2-SNAPSHOT
```

Regenerate a report:

```bash
python3 bench.py report results/<run-id>
```

## Agent sandboxing

The Claude agent runs with `--permission-mode dontAsk` and an explicit tool
allowlist: `Read`, `Edit`, `Write`, `Glob`, `Grep` and `Bash(./gradlew:*)`.
It cannot run any other shell command.

Known limitations of this prototype:

- File tools accept absolute paths, so an agent could in principle read the hidden
  tests in this repository. Running each workspace in a container would close this.
- Without `--bare`, Claude Code still loads the user's own `~/.claude` instructions.
  `--bare` avoids this but requires `ANTHROPIC_API_KEY`.

## Metrics

| metric | meaning |
|---|---|
| pass | main code compiles and all hidden tests pass |
| clean pass | pass with no static check violations (for example, no deprecated API) |
| first build ok | the agent's first Gradle run succeeded |
| build attempts | number of Gradle runs by the agent |
| DOMA codes | Doma processor errors the agent ran into; codes that recur without being fixed point to error messages worth improving |

## Adding a task

1. Create `tasks/<name>/` with `task.json`, `prompt.md`, `hidden-tests/` and `reference/`.
2. Fix public names and signatures in the prompt, since the hidden tests depend on them.
3. Run `python3 bench.py run --agent reference --tasks <name>` and make sure it passes.
4. Run `python3 bench.py run --agent command --agent-command true --tasks <name>` and make sure it fails.

Keep a private, held-out set of tasks as well. Public tasks will eventually leak into training data.
