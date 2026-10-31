package org.domaframework.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Aggregates the results of a run into report.md. */
final class Report {

  private Report() {}

  static String generate(Path runDir) {
    // Grouped by (task, condition), sorted like the original bench.py
    Map<String, List<Map<String, Object>>> groups = new TreeMap<>();
    for (Path path : resultFiles(runDir)) {
      Map<String, Object> r = Bench.readMap(path);
      // Re-derive agent metrics so older runs are reported with the current parser
      r.putAll(Transcript.parse(path.getParent().resolve("transcript.jsonl")));
      if (Boolean.TRUE.equals(r.get("infra_error"))) {
        continue;
      }
      String key = r.get("task") + "\u0000" + r.get("condition");
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
    }

    List<String> lines = new ArrayList<>();
    lines.add("# Doma agent bench: " + runDir.getFileName());
    lines.add("");
    lines.add(
        "| task | cond | n | pass | clean pass | first build ok | build attempts | denials | turns | cost (USD) |");
    lines.add("|---|---|---|---|---|---|---|---|---|---|");
    Map<String, Integer> violations = new LinkedHashMap<>();
    Map<String, Integer> codes = new LinkedHashMap<>();
    Map<String, Integer> failedTests = new LinkedHashMap<>();
    for (List<Map<String, Object>> rs : groups.values()) {
      String task = (String) rs.get(0).get("task");
      String cond = (String) rs.get(0).get("condition");
      lines.add(
          "| "
              + task
              + " | "
              + cond
              + " | "
              + rs.size()
              + " | "
              + rate(values(rs, "passed"))
              + " | "
              + rate(values(rs, "clean_pass"))
              + " | "
              + rate(values(rs, "first_build_ok"))
              + " | "
              + mean(values(rs, "build_attempts"), 1)
              + " | "
              + mean(values(rs, "permission_denials"), 1)
              + " | "
              + mean(values(rs, "num_turns"), 1)
              + " | "
              + mean(values(rs, "cost_usd"), 2)
              + " |");
      for (Map<String, Object> r : rs) {
        for (Object v : (Collection<?>) r.getOrDefault("violations", List.of())) {
          increment(violations, task + ": " + ((Map<?, ?>) v).get("reason"));
        }
        for (Object code : (Collection<?>) r.getOrDefault("doma_codes_during_session", List.of())) {
          increment(codes, (String) code);
        }
        for (Object test : (Collection<?>) r.getOrDefault("failed_tests", List.of())) {
          increment(failedTests, (String) test);
        }
        if (!Boolean.TRUE.equals(r.get("compile_ok"))) {
          increment(failedTests, task + ": main code does not compile");
        } else if (!Boolean.TRUE.equals(r.get("test_compile_ok"))) {
          increment(failedTests, task + ": hidden tests do not compile (API mismatch)");
        }
      }
    }

    section(lines, "Check violations", violations);
    section(lines, "Failed tests", failedTests);
    section(lines, "DOMA codes seen during agent sessions (runs)", codes);
    String text = String.join("\n", lines) + "\n";
    Shell.write(runDir.resolve("report.md"), text);
    return text;
  }

  /** Formats the share of true values like Python's "{:.0%}", or "-" when there are none. */
  static String rate(List<Object> values) {
    List<Object> present = values.stream().filter(Objects::nonNull).toList();
    if (present.isEmpty()) {
      return "-";
    }
    long trues = present.stream().filter(Boolean.TRUE::equals).count();
    // Python multiplies the double by 100, then rounds its exact binary value half-even
    double percent = (double) trues / present.size() * 100;
    return new BigDecimal(percent).setScale(0, RoundingMode.HALF_EVEN).toPlainString() + "%";
  }

  /** Formats the mean like Python's "{:.Nf}", or "-" when there are no values. */
  static String mean(List<Object> values, int scale) {
    List<Number> present =
        values.stream().filter(Objects::nonNull).map(Number.class::cast).toList();
    if (present.isEmpty()) {
      return "-";
    }
    // A plain left-to-right sum; DoubleStream.sum() compensates and can differ in the last bit
    double sum = 0;
    for (Number n : present) {
      sum += n.doubleValue();
    }
    return new BigDecimal(sum / present.size())
        .setScale(scale, RoundingMode.HALF_EVEN)
        .toPlainString();
  }

  private static List<Object> values(List<Map<String, Object>> rs, String key) {
    List<Object> values = new ArrayList<>();
    rs.forEach(r -> values.add(r.get(key)));
    return values;
  }

  private static void increment(Map<String, Integer> counter, String key) {
    counter.merge(key, 1, Integer::sum);
  }

  private static void section(List<String> lines, String title, Map<String, Integer> counter) {
    lines.add("");
    lines.add("## " + title);
    lines.add("");
    if (counter.isEmpty()) {
      lines.add("- none");
      return;
    }
    // Most common first; ties keep first-seen order, like collections.Counter.most_common
    counter.entrySet().stream()
        .sorted(Comparator.comparing(Map.Entry<String, Integer>::getValue).reversed())
        .forEach(e -> lines.add("- " + e.getKey() + ": " + e.getValue()));
  }

  private static List<Path> resultFiles(Path runDir) {
    try (Stream<Path> paths = Files.walk(runDir)) {
      return paths
          .filter(p -> p.getFileName().toString().equals("result.json"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
