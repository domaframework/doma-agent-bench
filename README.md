# doma-agent-bench

A benchmark that measures how well AI coding agents can build features with
[Doma](https://github.com/domaframework/doma), and shows **where they fail**.

The goal is not a leaderboard score. The goal is a list of failure patterns that
can be turned into improvements to Doma: documentation, error messages, APIs,
or agent-facing material such as `llms.txt`, skills and MCP servers.

## How it works

For each task × condition × repetition, the runner:

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
src/main/java/        runner, grader and report (Java, built with Gradle)
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

Requirements: JDK 17 or later and git. Running Claude Code also needs the `claude` CLI.
The runner is a Java application; `./gradlew run` builds and starts it from the repository root.

Validate the tasks: each one must pass with its reference solution and fail when
nothing is changed. No LLM is called, and CI runs this on every push:

```bash
./gradlew -q run --args="validate"
```

Run Claude Code:

```bash
./gradlew -q run --args="run --agent claude --model claude-sonnet-5-5 --conditions C0,C1 --repeat 5"
```

Run any other agent CLI. `{prompt_file}` and `{workspace}` are substituted, and the
prompt is also passed on stdin:

```bash
./gradlew -q run --args="run --agent command --agent-command 'my-agent --prompt-file {prompt_file}'"
```

Measure a specific Doma or doma-compile-plugin version, including an unreleased build.
The versions are written into each workspace, so the agent's own builds use them too:

```bash
(cd ../doma && ./gradlew :doma-core:publishToMavenLocal :doma-processor:publishToMavenLocal --no-configuration-cache)
./gradlew -q run --args="run --agent claude --doma-version 3.14.2-SNAPSHOT --doma-compile-version 4.0.4"
```

Run `./gradlew -q run` without arguments to list every option.

Regenerate a report:

```bash
./gradlew -q run --args="report results/<run-id>"
```

## Agent sandboxing

The Claude agent runs with `--permission-mode dontAsk` and an explicit tool
allowlist: `Read`, `Edit`, `Write`, `Glob`, `Grep`, and Bash limited to `./gradlew`,
`mkdir`, `ls`, `cat`, `find`, `git status`, `git diff` and `git ls-files`.
It cannot run any other shell command.

Known limitations of this prototype:

- A compound command (for example `mkdir ... && cat > ... && ./gradlew build`) is denied
  as a whole. Some agents then conclude that the shell is unavailable and finish without
  building, which turns fixable mistakes into failures. The `denials` and
  `build attempts` columns show when this happens.

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
3. Run `./gradlew -q run --args="validate --tasks <name>"`.

Keep a private, held-out set of tasks as well. Public tasks will eventually leak into training data.

## Results so far

Claude Sonnet 5.5, 5 runs per cell, Doma 3.14.1. C0 gives the agent no Doma material;
C1 prepends `docs/llms.txt` to the prompt (the agent cannot follow its links).

### `llms.txt` with links only vs. with essentials

Runs that passed the hidden tests **and** the static checks:

| task | C0 | C1, links only | C1, with essentials ([domaframework/doma#1713]) |
|---|---|---|---|
| crud-dao | 5/5 | 5/5 | 5/5 |
| criteria-join | 2/5 | 1/5 | 5/5 |
| dynamic-sql | 0/5 | 1/5 | 5/5 |
| **total** | 7/15 | 7/15 | 15/15 |

The essentials were written from the failures in these three tasks, so this table
overstates how far they generalize. Typical failures before the change: an invented
`/*%where*/` directive, `escape '\'` with `@prefix` (which escapes with `$`), and the
deprecated `NativeSql`/`Entityql` APIs.

### Tasks written after the change

| task | C0 | C1, with essentials |
|---|---|---|
| batch-dao | 4/5 | 4/5 |
| fix-errors | 5/5 | 4/5 |

The essentials did not help on tasks they were not derived from. The failures pointed
at Doma itself instead: a `DOMA4002` message without a hint toward `sqlFile = true`, and
an unquoted `/*%expand e*/` alias that compiled but failed at runtime.

### Changes to Doma that came out of this

| change | layer |
|---|---|
| [domaframework/doma-compile-plugin#219]: declare SQL files as `compileJava` inputs, so editing only a SQL file is validated | compile-time validation |
| [domaframework/doma#1713]: essentials for writing Doma code in `llms.txt` | documentation |
| [domaframework/doma#1714]: `DOMA4501` says to use `sqlFile = true` or `@Sql` | error message |
| [domaframework/doma#1715]: validate the alias expression of `/*%expand*/` | compile-time validation |
| [domaframework/doma#1716]: support primitive arrays in IN lists and `/*%for*/` (found by auditing runtime against compile-time handling) | runtime |

### Doma before and after these changes

All five tasks, measured again after the changes were merged:

- before: Doma 3.14.1 with doma-compile-plugin 4.0.3
- after: Doma master (3.14.2-SNAPSHOT, including #1714 to #1716) with doma-compile-plugin 4.0.4

Both use the `llms.txt` with essentials for C1, and both tell Claude which shell commands
the sandbox allows, so fewer agents give up on building. Runs that passed:

| task | C0 before | C0 after | C1 before | C1 after |
|---|---|---|---|---|
| crud-dao | 4/5 | 5/5 | 5/5 | 5/5 |
| criteria-join | 5/5 | 5/5 | 5/5 | 5/5 |
| dynamic-sql | 1/5 | 0/5 | 5/5 | 5/5 |
| batch-dao | 5/5 | 5/5 | 5/5 | 5/5 |
| fix-errors | 3/5 | 5/5 | 4/5 | 5/5 |
| **total** | 18/25 | 20/25 | 24/25 | 25/25 |

What the transcripts show:

- **fix-errors**: with the new alias validation ([domaframework/doma#1715]), three agents
  that wrote `/*%expand e*/` got `DOMA4092` at compile time and fixed it. Before, one
  agent shipped the same alias, and it failed at runtime. Two other failures before left
  an unaliased `/*%expand*/` in a join, which no check catches; that all five C0 runs
  quoted the alias afterwards is likely chance.
- **batch-dao**: once agents kept building, they fixed the missing `sqlFile = true` with or
  without the new `DOMA4501` hint (seen 3 times before, 4 times after). The better message
  made no measurable difference here.
- **Remaining failures are all silent or runtime-only**: `escape '\'` with `@prefix` in
  dynamic-sql C0 (4 runs before, 5 after; C1 avoids it through `llms.txt`), and an entity
  without `naming = NamingType.SNAKE_LOWER_CASE` in crud-dao C0 (1 run before).

[domaframework/doma-compile-plugin#219]: https://github.com/domaframework/doma-compile-plugin/pull/219
[domaframework/doma#1713]: https://github.com/domaframework/doma/pull/1713
[domaframework/doma#1714]: https://github.com/domaframework/doma/pull/1714
[domaframework/doma#1715]: https://github.com/domaframework/doma/pull/1715
[domaframework/doma#1716]: https://github.com/domaframework/doma/pull/1716

## License

[Apache License 2.0](LICENSE), the same as Doma.
