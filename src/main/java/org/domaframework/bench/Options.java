package org.domaframework.bench;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Command line options. Option names and defaults follow the original bench.py. */
final class Options {

  static final String USAGE =
      """
      Usage:
        ./gradlew -q run --args="validate [--tasks a,b] [--doma-version V] [--doma-compile-version V]"
        ./gradlew -q run --args="run --agent claude --model claude-sonnet-5-5 --conditions C0,C1 --repeat 5"
        ./gradlew -q run --args="report results/<run-id>"

      run options:
        --agent reference|claude|command   (default: reference)
        --agent-command TEMPLATE           shell command for --agent command; {prompt_file} and {workspace} are substituted
        --model MODEL
        --tasks a,b                        (default: all)
        --conditions C0,C1                 (default: C0)
        --repeat N                         (default: 1)
        --doma-version V                   (default: template/gradle.properties)
        --doma-compile-version V           (default: template/gradle.properties)
        --doma-repo PATH                   doma repository for context files (default: ../doma)
        --timeout SECONDS                  agent timeout (default: 1800)
        --max-turns N
        --label LABEL                      suffix for the run directory name
        --bare                             pass --bare to claude (requires ANTHROPIC_API_KEY)
        --keep                             keep workspaces after grading
      """;

  String command;
  String agent = "reference";
  String agentCommand;
  String model;
  String tasks;
  String conditions = "C0";
  int repeat = 1;
  String domaVersion;
  String domaCompileVersion;
  Path domaRepo = Bench.ROOT.getParent().resolve("doma");
  int timeoutSeconds = 1800;
  Integer maxTurns;
  String label;
  boolean bare;
  boolean keep;
  Path runDir;

  static Options parse(String[] args) {
    if (args.length == 0) {
      throw new IllegalArgumentException("missing command");
    }
    Options o = new Options();
    o.command = args[0];
    if (!List.of("run", "report", "validate").contains(o.command)) {
      throw new IllegalArgumentException("unknown command: " + o.command);
    }
    List<String> positional = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "--bare" -> o.bare = true;
        case "--keep" -> o.keep = true;
        default -> {
          if (!arg.startsWith("--")) {
            positional.add(arg);
            continue;
          }
          if (i + 1 >= args.length) {
            throw new IllegalArgumentException("missing value for " + arg);
          }
          String value = args[++i];
          switch (arg) {
            case "--agent" -> o.agent = value;
            case "--agent-command" -> o.agentCommand = value;
            case "--model" -> o.model = value;
            case "--tasks" -> o.tasks = value;
            case "--conditions" -> o.conditions = value;
            case "--repeat" -> o.repeat = Integer.parseInt(value);
            case "--doma-version" -> o.domaVersion = value;
            case "--doma-compile-version" -> o.domaCompileVersion = value;
            case "--doma-repo" -> o.domaRepo = Path.of(value);
            case "--timeout" -> o.timeoutSeconds = Integer.parseInt(value);
            case "--max-turns" -> o.maxTurns = Integer.parseInt(value);
            case "--label" -> o.label = value;
            default -> throw new IllegalArgumentException("unknown option: " + arg);
          }
        }
      }
    }
    if (o.command.equals("report")) {
      if (positional.size() != 1) {
        throw new IllegalArgumentException("report takes one run directory");
      }
      o.runDir = Path.of(positional.get(0));
    } else if (!positional.isEmpty()) {
      throw new IllegalArgumentException("unexpected argument: " + positional.get(0));
    }
    if (!List.of("reference", "claude", "command").contains(o.agent)) {
      throw new IllegalArgumentException("unknown agent: " + o.agent);
    }
    if (o.agent.equals("command") && o.agentCommand == null) {
      throw new IllegalArgumentException("--agent command requires --agent-command");
    }
    return o;
  }

  /** The options as recorded in run.json. */
  Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("command", command);
    map.put("agent", agent);
    map.put("agent_command", agentCommand);
    map.put("model", model);
    map.put("tasks", tasks);
    map.put("conditions", conditions);
    map.put("repeat", repeat);
    map.put("doma_version", domaVersion);
    map.put("doma_compile_version", domaCompileVersion);
    map.put("doma_repo", domaRepo.toString());
    map.put("timeout", timeoutSeconds);
    map.put("max_turns", maxTurns);
    map.put("label", label);
    map.put("bare", bare);
    map.put("keep", keep);
    return map;
  }
}
