package org.domaframework.bench;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Locations in the repository and JSON helpers. */
final class Bench {

  static final Path ROOT = Path.of("").toAbsolutePath();
  static final Path TEMPLATE = ROOT.resolve("template");
  static final Path TASKS = ROOT.resolve("tasks");
  static final Path HIDDEN_COMMON = ROOT.resolve("hidden-common");
  static final Path CONDITIONS_FILE = ROOT.resolve("conditions.json");
  static final Path PREAMBLE = ROOT.resolve("prompts").resolve("preamble.md");
  static final Path RESULTS = ROOT.resolve("results");

  static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private Bench() {}

  static List<Path> selectTasks(Options o) {
    if (o.tasks != null) {
      return Arrays.stream(o.tasks.split(",")).map(TASKS::resolve).toList();
    }
    try (Stream<Path> dirs = Files.list(TASKS)) {
      return dirs.filter(p -> Files.exists(p.resolve("task.json"))).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static JsonNode readJson(Path file) {
    try {
      return JSON.readTree(file.toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static void writeJson(Path file, Object value) {
    try {
      Shell.write(file, JSON.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> readMap(Path file) {
    try {
      return JSON.readValue(file.toFile(), Map.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<String> strings(JsonNode array) {
    List<String> list = new ArrayList<>();
    if (array != null) {
      array.forEach(node -> list.add(node.asText()));
    }
    return list;
  }
}
