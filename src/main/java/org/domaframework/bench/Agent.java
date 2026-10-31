package org.domaframework.bench;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs the agent under test in a workspace. */
final class Agent {

  /** The agent may only read and edit files and run a few shell commands. */
  static final List<String> CLAUDE_ALLOWED_TOOLS =
      List.of(
          "Read",
          "Edit",
          "Write",
          "Glob",
          "Grep",
          "Bash(./gradlew:*)",
          "Bash(mkdir:*)",
          "Bash(ls:*)",
          "Bash(cat:*)",
          "Bash(find:*)",
          "Bash(git status:*)",
          "Bash(git diff:*)",
          "Bash(git ls-files:*)");

  static final String CLAUDE_SANDBOX_NOTE =
      "In this environment the Bash tool runs only these commands: ./gradlew, mkdir, ls, cat, find, "
          + "git status, git diff and git ls-files. Run each one as a separate command: a command line that "
          + "chains commands with && or ;, or that writes files with a heredoc, is rejected as a whole. "
          + "Create and edit files with the file tools. If a command is rejected, split it up and try again; "
          + "the shell itself is still available.";

  private Agent() {}

  static Map<String, Object> run(Options o, Path task, Path ws, String prompt, Path out) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (o.agent.equals("reference")) {
      Shell.copyTree(task.resolve("reference"), ws);
      result.put("agent_exit", 0);
      return result;
    }

    List<String> command = new ArrayList<>();
    Path transcript;
    if (o.agent.equals("claude")) {
      command.addAll(
          List.of(
              "claude",
              "-p",
              "--output-format",
              "stream-json",
              "--verbose",
              "--permission-mode",
              "dontAsk",
              "--allowedTools"));
      command.addAll(CLAUDE_ALLOWED_TOOLS);
      command.addAll(
          List.of(
              "--no-session-persistence",
              "--strict-mcp-config",
              "--setting-sources",
              "project"));
      if (o.model != null) {
        command.addAll(List.of("--model", o.model));
      }
      if (o.maxTurns != null) {
        command.addAll(List.of("--max-turns", o.maxTurns.toString()));
      }
      if (o.bare) {
        command.add("--bare");
      }
      command.addAll(List.of("--append-system-prompt", CLAUDE_SANDBOX_NOTE));
      transcript = out.resolve("transcript.jsonl");
    } else {
      String shellCommand =
          o.agentCommand
              .replace("{prompt_file}", out.resolve("prompt.md").toString())
              .replace("{workspace}", ws.toString());
      command.addAll(List.of("sh", "-c", shellCommand));
      transcript = out.resolve("transcript.txt");
    }

    Integer exit =
        Shell.run(
            ws,
            command,
            transcript,
            out.resolve("agent.stderr"),
            prompt,
            Duration.ofSeconds(o.timeoutSeconds));
    result.put("agent_exit", exit);
    if (exit == null) {
      result.put("agent_timeout", true);
    }
    return result;
  }
}
