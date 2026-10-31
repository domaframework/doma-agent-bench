#!/usr/bin/env python3
"""Doma agent benchmark: runs coding agents on Doma tasks and grades the results.

Usage:
  python3 bench.py run --agent reference
  python3 bench.py run --agent claude --model claude-sonnet-5-5 --conditions C0,C1 --repeat 5
  python3 bench.py report results/<run-id>
"""

import argparse
import datetime
import json
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEMPLATE = ROOT / "template"
TASKS = ROOT / "tasks"
HIDDEN_COMMON = ROOT / "hidden-common"
CONDITIONS_FILE = ROOT / "conditions.json"
PREAMBLE = ROOT / "prompts" / "preamble.md"
RESULTS = ROOT / "results"

DOMA_CODE = re.compile(r"\bDOMA\d{4}\b")
GRADLE_CMD = re.compile(r"(^|[\s;&|(])(\./)?gradlew?\s")

# The agent may only read and edit files and run the Gradle wrapper; no other shell access
CLAUDE_ALLOWED_TOOLS = [
    "Read", "Edit", "Write", "Glob", "Grep",
    "Bash(./gradlew:*)", "Bash(mkdir:*)", "Bash(ls:*)", "Bash(cat:*)", "Bash(find:*)",
    "Bash(git status:*)", "Bash(git diff:*)", "Bash(git ls-files:*)",
]


# ---------------------------------------------------------------------------
# workspace


def copy_tree(src: Path, dst: Path) -> None:
    if src.exists():
        shutil.copytree(src, dst, dirs_exist_ok=True)


def git(ws: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=ws, capture_output=True, text=True, check=False
    ).stdout


def prepare_workspace(task: Path) -> Path:
    # A fresh temp directory outside the bench repo, so the agent cannot see hidden tests
    ws = Path(tempfile.mkdtemp(prefix="doma-bench-")) / "app"
    copy_tree(TEMPLATE, ws)
    copy_tree(task / "workspace", ws)
    git(ws, "init", "-q")
    git(ws, "add", "-A")
    git(ws, "-c", "user.name=bench", "-c", "user.email=bench@localhost",
        "commit", "-q", "-m", "initial")
    return ws


def build_prompt(task: Path, context_files: list[str], doma_repo: Path) -> str:
    parts = []
    for f in context_files:
        path = Path(f.replace("{doma_repo}", str(doma_repo)))
        parts.append(f"Reference material ({path.name}):\n\n{path.read_text()}\n\n---\n")
    parts.append(PREAMBLE.read_text())
    parts.append((task / "prompt.md").read_text())
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# agents


def run_agent(args, task: Path, ws: Path, prompt: str, out: Path) -> dict:
    if args.agent == "reference":
        copy_tree(task / "reference", ws)
        return {"agent_exit": 0}

    if args.agent == "claude":
        cmd = [
            "claude", "-p",
            "--output-format", "stream-json", "--verbose",
            "--permission-mode", "dontAsk",
            "--allowedTools", *CLAUDE_ALLOWED_TOOLS,
            "--no-session-persistence",
            "--strict-mcp-config",
            "--setting-sources", "project",
        ]
        if args.model:
            cmd += ["--model", args.model]
        if args.max_turns:
            cmd += ["--max-turns", str(args.max_turns)]
        if args.bare:
            cmd += ["--bare"]
        transcript = out / "transcript.jsonl"
    else:  # command
        prompt_file = out / "prompt.md"
        cmd = ["sh", "-c", args.agent_command.format(prompt_file=prompt_file, workspace=ws)]
        transcript = out / "transcript.txt"

    try:
        with open(transcript, "w") as stdout, open(out / "agent.stderr", "w") as stderr:
            proc = subprocess.run(
                cmd, cwd=ws, input=prompt, text=True,
                stdout=stdout, stderr=stderr, timeout=args.timeout,
            )
        return {"agent_exit": proc.returncode}
    except subprocess.TimeoutExpired:
        return {"agent_exit": None, "agent_timeout": True}


def parse_claude_transcript(path: Path) -> dict:
    """Extracts cost, turns and every Gradle invocation the agent made."""
    if not path.exists():
        return {}
    builds = {}  # tool_use_id -> command
    outcomes = []
    metrics = {}
    for line in path.read_text().splitlines():
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            continue
        if msg.get("type") == "result":
            metrics = {
                "num_turns": msg.get("num_turns"),
                "cost_usd": msg.get("total_cost_usd"),
                "duration_ms": msg.get("duration_ms"),
                "is_error": msg.get("is_error"),
                "api_error_status": msg.get("api_error_status"),
                "permission_denials": len(msg.get("permission_denials") or []),
            }
        content = (msg.get("message") or {}).get("content")
        if not isinstance(content, list):
            continue
        for block in content:
            if block.get("type") == "tool_use" and block.get("name") == "Bash":
                command = (block.get("input") or {}).get("command", "")
                if GRADLE_CMD.search(command):
                    builds[block.get("id")] = command
            elif block.get("type") == "tool_result" and block.get("tool_use_id") in builds:
                text = json.dumps(block.get("content"))
                if "Permission to use Bash has been denied" in text:
                    continue  # never ran, so it says nothing about the build
                outcomes.append("BUILD SUCCESSFUL" in text and not block.get("is_error"))
    metrics["build_attempts"] = len(outcomes)
    metrics["first_build_ok"] = outcomes[0] if outcomes else None
    return metrics


# ---------------------------------------------------------------------------
# grading


def gradle(ws: Path, task_name: str, doma_version: str | None, log: Path) -> bool:
    # --rerun-tasks: doma-compile-plugin does not declare SQL files as compileJava inputs,
    # so an up-to-date check would skip validating SQL templates the agent changed
    cmd = ["./gradlew", task_name, "--console=plain", "--rerun-tasks"]
    if doma_version:
        cmd.append(f"-PdomaVersion={doma_version}")
    with open(log, "w") as f:
        proc = subprocess.run(cmd, cwd=ws, stdout=f, stderr=subprocess.STDOUT, timeout=900)
    return proc.returncode == 0


def run_checks(task: Path, ws: Path) -> list[dict]:
    spec = json.loads((task / "task.json").read_text()).get("checks", {})
    sources = {
        p: p.read_text(errors="replace")
        for p in (ws / "src" / "main").rglob("*")
        if p.is_file() and p.suffix in (".java", ".sql")
    }
    violations = []
    for rule in spec.get("forbidden", []):
        pattern = re.compile(rule["pattern"], re.IGNORECASE)
        hits = [str(p.relative_to(ws)) for p, text in sources.items() if pattern.search(text)]
        if hits:
            violations.append({"kind": "forbidden", "reason": rule["reason"], "files": hits})
    for rule in spec.get("required", []):
        if "path" in rule:
            ok = (ws / rule["path"]).exists()
        else:
            pattern = re.compile(rule["pattern"])
            ok = any(pattern.search(text) for text in sources.values())
        if not ok:
            violations.append({"kind": "required", "reason": rule["reason"]})
    return violations


def parse_junit(ws: Path) -> dict:
    total = failed = 0
    failures = []
    for xml in (ws / "build" / "test-results" / "test").glob("*.xml"):
        suite = ET.parse(xml).getroot()
        for case in suite.iter("testcase"):
            total += 1
            if case.find("failure") is not None or case.find("error") is not None:
                failed += 1
                failures.append(f"{case.get('classname')}.{case.get('name')}")
    return {"tests_total": total, "tests_failed": failed, "failed_tests": failures}


def grade(args, task: Path, ws: Path, out: Path) -> dict:
    git(ws, "add", "-A")
    (out / "changes.patch").write_text(git(ws, "diff", "--cached", "HEAD"))

    result = {"violations": run_checks(task, ws)}
    result["compile_ok"] = gradle(ws, "compileJava", args.doma_version, out / "compile.log")

    # Replace whatever tests the agent wrote with the hidden acceptance tests
    shutil.rmtree(ws / "src" / "test", ignore_errors=True)
    copy_tree(HIDDEN_COMMON, ws / "src" / "test")
    copy_tree(task / "hidden-tests", ws / "src" / "test")

    if result["compile_ok"]:
        gradle(ws, "test", args.doma_version, out / "test.log")
        result.update(parse_junit(ws))
        result["test_compile_ok"] = result["tests_total"] > 0
    else:
        result.update({"tests_total": 0, "tests_failed": 0, "failed_tests": []})
        result["test_compile_ok"] = False

    result["passed"] = result["compile_ok"] and result["tests_total"] > 0 and result["tests_failed"] == 0
    result["clean_pass"] = result["passed"] and not result["violations"]

    codes = {}
    for name in ("transcript.jsonl", "transcript.txt", "compile.log"):
        if (out / name).exists():
            codes[name] = sorted(set(DOMA_CODE.findall((out / name).read_text(errors="replace"))))
    result["doma_codes_during_session"] = codes.get("transcript.jsonl", codes.get("transcript.txt", []))
    result["doma_codes_final"] = codes.get("compile.log", [])
    return result


# ---------------------------------------------------------------------------
# commands


def select_tasks(args) -> list[Path]:
    return [TASKS / t for t in args.tasks.split(",")] if args.tasks else sorted(
        p for p in TASKS.iterdir() if (p / "task.json").exists()
    )


def cmd_validate(args) -> int:
    """Checks that every task passes with its reference solution and fails when nothing is done."""
    unexpected = []
    for task in select_tasks(args):
        for label, use_reference in (("reference", True), ("no-op", False)):
            ws = prepare_workspace(task)
            out = Path(tempfile.mkdtemp(prefix="doma-bench-validate-"))
            try:
                if use_reference:
                    copy_tree(task / "reference", ws)
                result = grade(args, task, ws, out)
            finally:
                shutil.rmtree(ws.parent, ignore_errors=True)
                shutil.rmtree(out, ignore_errors=True)
            # The reference must also satisfy the static checks; doing nothing must fail
            ok = result["clean_pass"] if use_reference else not result["passed"]
            print(f"[{task.name} {label}] {'ok' if ok else 'UNEXPECTED'}", flush=True)
            if not ok:
                unexpected.append(f"{task.name} {label}")
    if unexpected:
        print("Unexpected results: " + ", ".join(unexpected))
        return 1
    return 0


def cmd_run(args) -> None:
    conditions = json.loads(CONDITIONS_FILE.read_text())
    tasks = select_tasks(args)
    run_id = datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + f"-{args.agent}"
    run_dir = RESULTS / run_id
    run_dir.mkdir(parents=True)
    (run_dir / "run.json").write_text(json.dumps(vars(args), indent=2, default=str))

    for task in tasks:
        for cond in args.conditions.split(","):
            for i in range(1, args.repeat + 1):
                out = run_dir / task.name / cond / str(i)
                out.mkdir(parents=True)
                print(f"[{task.name} {cond} #{i}] ", end="", flush=True)

                ws = prepare_workspace(task)
                prompt = build_prompt(task, conditions[cond]["context"], Path(args.doma_repo))
                (out / "prompt.md").write_text(prompt)

                result = {"task": task.name, "condition": cond, "repeat": i, "agent": args.agent,
                          "model": args.model, "doma_version": args.doma_version}
                result.update(run_agent(args, task, ws, prompt, out))
                result.update(parse_claude_transcript(out / "transcript.jsonl"))
                if result.get("api_error_status"):
                    # Authentication, quota or API outages say nothing about the model or Doma
                    result["infra_error"] = True
                    (out / "result.json").write_text(json.dumps(result, indent=2))
                    shutil.rmtree(ws.parent, ignore_errors=True)
                    sys.exit(f"API error {result['api_error_status']}; see {out / 'transcript.jsonl'}")
                result.update(grade(args, task, ws, out))
                (out / "result.json").write_text(json.dumps(result, indent=2))

                status = "PASS" if result["passed"] else "FAIL"
                print(f"{status} tests={result['tests_total'] - result['tests_failed']}/"
                      f"{result['tests_total']} violations={len(result['violations'])}")
                if args.keep:
                    print(f"  workspace: {ws}")
                else:
                    shutil.rmtree(ws.parent, ignore_errors=True)

    print()
    print(report(run_dir))


def rate(values) -> str:
    values = [v for v in values if v is not None]
    return f"{sum(values) / len(values):.0%}" if values else "-"


def mean(values, fmt="{:.2f}") -> str:
    values = [v for v in values if v is not None]
    return fmt.format(sum(values) / len(values)) if values else "-"


def report(run_dir: Path) -> str:
    groups = defaultdict(list)
    for path in sorted(run_dir.rglob("result.json")):
        r = json.loads(path.read_text())
        # Re-derive agent metrics so older runs are reported with the current parser
        r.update(parse_claude_transcript(path.parent / "transcript.jsonl"))
        if r.get("infra_error"):
            continue
        groups[(r["task"], r["condition"])].append(r)

    lines = [
        f"# Doma agent bench: {run_dir.name}",
        "",
        "| task | cond | n | pass | clean pass | first build ok | build attempts | denials | turns | cost (USD) |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    violations = Counter()
    codes = Counter()
    failed_tests = Counter()
    for (task, cond), rs in sorted(groups.items()):
        lines.append(
            f"| {task} | {cond} | {len(rs)} | {rate(r['passed'] for r in rs)} "
            f"| {rate(r['clean_pass'] for r in rs)} "
            f"| {rate(r.get('first_build_ok') for r in rs)} "
            f"| {mean((r.get('build_attempts') for r in rs), '{:.1f}')} "
            f"| {mean((r.get('permission_denials') for r in rs), '{:.1f}')} "
            f"| {mean((r.get('num_turns') for r in rs), '{:.1f}')} "
            f"| {mean(r.get('cost_usd') for r in rs)} |"
        )
        for r in rs:
            violations.update(f"{task}: {v['reason']}" for v in r["violations"])
            codes.update(r.get("doma_codes_during_session", []))
            failed_tests.update(r.get("failed_tests", []))
            if not r["compile_ok"]:
                failed_tests[f"{task}: main code does not compile"] += 1
            elif not r["test_compile_ok"]:
                failed_tests[f"{task}: hidden tests do not compile (API mismatch)"] += 1

    def section(title, counter):
        lines.extend(["", f"## {title}", ""])
        lines.extend(f"- {k}: {v}" for k, v in counter.most_common()) if counter else lines.append("- none")

    section("Check violations", violations)
    section("Failed tests", failed_tests)
    section("DOMA codes seen during agent sessions (runs)", codes)
    text = "\n".join(lines) + "\n"
    (run_dir / "report.md").write_text(text)
    return text


def main() -> int | None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    run = sub.add_parser("run", help="run tasks and grade them")
    run.add_argument("--agent", choices=["reference", "claude", "command"], default="reference")
    run.add_argument("--agent-command", help="shell template for --agent command; {prompt_file} and {workspace} are substituted")
    run.add_argument("--model")
    run.add_argument("--tasks", help="comma-separated task names (default: all)")
    run.add_argument("--conditions", default="C0")
    run.add_argument("--repeat", type=int, default=1)
    run.add_argument("--doma-version", help="Doma version to build against (default: template/gradle.properties)")
    run.add_argument("--doma-repo", default=str(ROOT.parent / "doma"), help="path to the doma repository for context files")
    run.add_argument("--timeout", type=int, default=1800, help="agent timeout in seconds")
    run.add_argument("--max-turns", type=int)
    run.add_argument("--bare", action="store_true", help="pass --bare to claude (requires ANTHROPIC_API_KEY)")
    run.add_argument("--keep", action="store_true", help="keep workspaces after grading")

    rep = sub.add_parser("report", help="regenerate the report for a run")
    rep.add_argument("run_dir")

    val = sub.add_parser("validate", help="check each task with its reference solution and with no changes")
    val.add_argument("--tasks", help="comma-separated task names (default: all)")
    val.add_argument("--doma-version", help="Doma version to build against (default: template/gradle.properties)")

    args = parser.parse_args()
    if args.command == "run":
        if args.agent == "command" and not args.agent_command:
            parser.error("--agent command requires --agent-command")
        cmd_run(args)
    elif args.command == "validate":
        return cmd_validate(args)
    else:
        print(report(Path(args.run_dir)))


if __name__ == "__main__":
    sys.exit(main())
