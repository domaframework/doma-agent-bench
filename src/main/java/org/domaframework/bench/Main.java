package org.domaframework.bench;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Doma agent benchmark: runs coding agents on Doma tasks and grades the results.
 *
 * <p>Commands: {@code validate}, {@code run} and {@code report}. See {@link Options#USAGE}.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    Options o;
    try {
      o = Options.parse(args);
    } catch (IllegalArgumentException e) {
      System.err.println("error: " + e.getMessage());
      System.err.println(Options.USAGE);
      System.exit(2);
      return;
    }
    int exitCode =
        switch (o.command) {
          case "validate" -> validate(o);
          case "run" -> run(o);
          default -> {
            System.out.println(Report.generate(o.runDir));
            yield 0;
          }
        };
    System.exit(exitCode);
  }

  /** Checks that every task passes with its reference solution and fails when nothing is done. */
  static int validate(Options o) {
    List<String> unexpected = new ArrayList<>();
    for (Path task : Bench.selectTasks(o)) {
      for (boolean useReference : new boolean[] {true, false}) {
        String label = useReference ? "reference" : "no-op";
        Path ws = Workspace.prepare(task, o);
        Path out = Shell.createTempDirectory("doma-bench-validate-");
        Map<String, Object> result;
        try {
          if (useReference) {
            Shell.copyTree(task.resolve("reference"), ws);
          }
          result = Grader.grade(task, ws, out);
        } finally {
          Shell.deleteTree(ws.getParent());
          Shell.deleteTree(out);
        }
        // The reference must also satisfy the static checks; doing nothing must fail
        boolean ok =
            useReference
                ? Boolean.TRUE.equals(result.get("clean_pass"))
                : !Boolean.TRUE.equals(result.get("passed"));
        System.out.println(
            "[" + task.getFileName() + " " + label + "] " + (ok ? "ok" : "UNEXPECTED"));
        if (!ok) {
          unexpected.add(task.getFileName() + " " + label);
        }
      }
    }
    if (!unexpected.isEmpty()) {
      System.out.println("Unexpected results: " + String.join(", ", unexpected));
      return 1;
    }
    return 0;
  }

  static int run(Options o) {
    JsonNode conditions = Bench.readJson(Bench.CONDITIONS_FILE);
    List<Path> tasks = Bench.selectTasks(o);
    String runId =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + o.agent;
    if (o.label != null) {
      runId += "-" + o.label;
    }
    Path runDir = Bench.RESULTS.resolve(runId);
    Bench.writeJson(runDir.resolve("run.json"), o.toMap());

    for (Path task : tasks) {
      for (String cond : o.conditions.split(",")) {
        for (int i = 1; i <= o.repeat; i++) {
          Path out = runDir.resolve(task.getFileName().toString()).resolve(cond).resolve(String.valueOf(i));
          System.out.print("[" + task.getFileName() + " " + cond + " #" + i + "] ");
          System.out.flush();

          Path ws = Workspace.prepare(task, o);
          String prompt =
              Workspace.buildPrompt(
                  task, Bench.strings(conditions.path(cond).path("context")), o.domaRepo);
          Shell.write(out.resolve("prompt.md"), prompt);

          Map<String, Object> result = new LinkedHashMap<>();
          result.put("task", task.getFileName().toString());
          result.put("condition", cond);
          result.put("repeat", i);
          result.put("agent", o.agent);
          result.put("model", o.model);
          // The versions the workspace actually used, also when they come from the template
          result.put("doma_version", Workspace.property(ws, "domaVersion"));
          result.put("doma_compile_version", Workspace.property(ws, "domaCompileVersion"));
          result.putAll(Agent.run(o, task, ws, prompt, out));
          result.putAll(Transcript.parse(out.resolve("transcript.jsonl")));
          if (result.get("api_error_status") != null) {
            // Authentication, quota or API outages say nothing about the model or Doma
            result.put("infra_error", true);
            Bench.writeJson(out.resolve("result.json"), result);
            Shell.deleteTree(ws.getParent());
            System.err.println(
                "API error "
                    + result.get("api_error_status")
                    + "; see "
                    + out.resolve("transcript.jsonl"));
            return 1;
          }
          result.putAll(Grader.grade(task, ws, out));
          Bench.writeJson(out.resolve("result.json"), result);

          int total = ((Number) result.get("tests_total")).intValue();
          int failed = ((Number) result.get("tests_failed")).intValue();
          System.out.println(
              (Boolean.TRUE.equals(result.get("passed")) ? "PASS" : "FAIL")
                  + " tests="
                  + (total - failed)
                  + "/"
                  + total
                  + " violations="
                  + ((List<?>) result.get("violations")).size());
          if (o.keep) {
            System.out.println("  workspace: " + ws);
          } else {
            Shell.deleteTree(ws.getParent());
          }
        }
      }
    }

    System.out.println();
    System.out.println(Report.generate(runDir));
    return 0;
  }
}
