package org.domaframework.bench;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Extracts metrics from a Claude Code stream-json transcript. */
final class Transcript {

  static final Pattern GRADLE_COMMAND = Pattern.compile("(^|[\\s;&|(])(\\./)?gradlew?\\s");

  private Transcript() {}

  /** Returns cost, turns and the outcome of every Gradle run the agent made. */
  static Map<String, Object> parse(Path path) {
    Map<String, Object> metrics = new LinkedHashMap<>();
    if (!Files.exists(path)) {
      return metrics;
    }
    Map<String, String> builds = new HashMap<>(); // tool_use id -> command
    List<Boolean> outcomes = new ArrayList<>();
    for (String line : Shell.read(path).split("\n", -1)) {
      JsonNode msg;
      try {
        msg = Bench.JSON.readTree(line);
      } catch (IOException e) {
        continue;
      }
      if (msg == null || !msg.isObject()) {
        continue;
      }
      if ("result".equals(msg.path("type").asText())) {
        metrics = new LinkedHashMap<>();
        metrics.put("num_turns", value(msg.get("num_turns")));
        metrics.put("cost_usd", value(msg.get("total_cost_usd")));
        metrics.put("duration_ms", value(msg.get("duration_ms")));
        metrics.put("is_error", value(msg.get("is_error")));
        metrics.put("api_error_status", value(msg.get("api_error_status")));
        JsonNode denials = msg.get("permission_denials");
        metrics.put("permission_denials", denials != null && denials.isArray() ? denials.size() : 0);
      }
      JsonNode content = msg.path("message").get("content");
      if (content == null || !content.isArray()) {
        continue;
      }
      for (JsonNode block : content) {
        String type = block.path("type").asText();
        if (type.equals("tool_use") && block.path("name").asText().equals("Bash")) {
          String command = block.path("input").path("command").asText("");
          if (GRADLE_COMMAND.matcher(command).find()) {
            builds.put(block.path("id").asText(), command);
          }
        } else if (type.equals("tool_result")
            && builds.containsKey(block.path("tool_use_id").asText())) {
          String text = String.valueOf(block.get("content"));
          if (text.contains("Permission to use Bash has been denied")) {
            continue; // never ran, so it says nothing about the build
          }
          outcomes.add(text.contains("BUILD SUCCESSFUL") && !block.path("is_error").asBoolean());
        }
      }
    }
    metrics.put("build_attempts", outcomes.size());
    metrics.put("first_build_ok", outcomes.isEmpty() ? null : outcomes.get(0));
    return metrics;
  }

  private static Object value(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isBoolean()) {
      return node.asBoolean();
    }
    if (node.isIntegralNumber()) {
      return node.asLong();
    }
    if (node.isNumber()) {
      return node.asDouble();
    }
    return node.asText();
  }
}
