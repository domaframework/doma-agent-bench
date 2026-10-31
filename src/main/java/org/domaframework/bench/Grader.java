package org.domaframework.bench;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Grades a workspace after the agent has finished. */
final class Grader {

  static final Pattern DOMA_CODE = Pattern.compile("\\bDOMA\\d{4}\\b");

  private Grader() {}

  static Map<String, Object> grade(Path task, Path ws, Path out) {
    Shell.output(ws, "git", "add", "-A");
    Shell.write(out.resolve("changes.patch"), Shell.output(ws, "git", "diff", "--cached", "HEAD"));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("violations", runChecks(task, ws));
    boolean compileOk = gradle(ws, "compileJava", out.resolve("compile.log"));
    result.put("compile_ok", compileOk);

    // Replace whatever tests the agent wrote with the hidden acceptance tests
    Shell.deleteTree(ws.resolve("src").resolve("test"));
    Shell.copyTree(Bench.HIDDEN_COMMON, ws.resolve("src").resolve("test"));
    Shell.copyTree(task.resolve("hidden-tests"), ws.resolve("src").resolve("test"));

    int total = 0;
    int failed = 0;
    List<String> failedTests = new ArrayList<>();
    if (compileOk) {
      gradle(ws, "test", out.resolve("test.log"));
      for (Path xml : list(ws.resolve("build/test-results/test"), ".xml")) {
        Element suite = parseXml(xml).getDocumentElement();
        NodeList cases = suite.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
          Element testCase = (Element) cases.item(i);
          total++;
          if (testCase.getElementsByTagName("failure").getLength() > 0
              || testCase.getElementsByTagName("error").getLength() > 0) {
            failed++;
            failedTests.add(testCase.getAttribute("classname") + "." + testCase.getAttribute("name"));
          }
        }
      }
    }
    result.put("tests_total", total);
    result.put("tests_failed", failed);
    result.put("failed_tests", failedTests);
    result.put("test_compile_ok", compileOk && total > 0);

    boolean passed = compileOk && total > 0 && failed == 0;
    result.put("passed", passed);
    result.put("clean_pass", passed && ((List<?>) result.get("violations")).isEmpty());

    Path transcript =
        Files.exists(out.resolve("transcript.jsonl"))
            ? out.resolve("transcript.jsonl")
            : out.resolve("transcript.txt");
    result.put("doma_codes_during_session", domaCodes(transcript));
    result.put("doma_codes_final", domaCodes(out.resolve("compile.log")));
    return result;
  }

  static boolean gradle(Path ws, String taskName, Path log) {
    // --rerun-tasks: doma-compile-plugin before 4.0.4 does not declare SQL files as compileJava
    // inputs, so an up-to-date check would skip validating SQL templates the agent changed
    Integer exit =
        Shell.run(
            ws,
            List.of("./gradlew", taskName, "--console=plain", "--rerun-tasks"),
            log,
            null,
            null,
            Duration.ofSeconds(900));
    return exit != null && exit == 0;
  }

  static List<Map<String, Object>> runChecks(Path task, Path ws) {
    JsonNode spec = Bench.readJson(task.resolve("task.json")).path("checks");
    Map<Path, String> sources = new LinkedHashMap<>();
    Path main = ws.resolve("src").resolve("main");
    if (Files.exists(main)) {
      try (Stream<Path> paths = Files.walk(main)) {
        paths
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".sql"))
            .sorted()
            .forEach(p -> sources.put(p, Shell.read(p)));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    List<Map<String, Object>> violations = new ArrayList<>();
    for (JsonNode rule : spec.path("forbidden")) {
      Pattern pattern =
          Pattern.compile(
              rule.path("pattern").asText(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      List<String> hits =
          sources.entrySet().stream()
              .filter(e -> pattern.matcher(e.getValue()).find())
              .map(e -> ws.relativize(e.getKey()).toString())
              .toList();
      if (!hits.isEmpty()) {
        Map<String, Object> violation = new LinkedHashMap<>();
        violation.put("kind", "forbidden");
        violation.put("reason", rule.path("reason").asText());
        violation.put("files", hits);
        violations.add(violation);
      }
    }
    for (JsonNode rule : spec.path("required")) {
      boolean ok;
      if (rule.has("path")) {
        ok = Files.exists(ws.resolve(rule.path("path").asText()));
      } else {
        Pattern pattern = Pattern.compile(rule.path("pattern").asText());
        ok = sources.values().stream().anyMatch(text -> pattern.matcher(text).find());
      }
      if (!ok) {
        Map<String, Object> violation = new LinkedHashMap<>();
        violation.put("kind", "required");
        violation.put("reason", rule.path("reason").asText());
        violations.add(violation);
      }
    }
    return violations;
  }

  static List<String> domaCodes(Path file) {
    if (!Files.exists(file)) {
      return List.of();
    }
    TreeSet<String> codes = new TreeSet<>();
    Matcher matcher = DOMA_CODE.matcher(Shell.read(file));
    while (matcher.find()) {
      codes.add(matcher.group());
    }
    return new ArrayList<>(codes);
  }

  private static List<Path> list(Path dir, String suffix) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.list(dir)) {
      return paths.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Document parseXml(Path xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(xml.toFile());
    } catch (Exception e) {
      throw new IllegalStateException("cannot parse " + xml, e);
    }
  }
}
