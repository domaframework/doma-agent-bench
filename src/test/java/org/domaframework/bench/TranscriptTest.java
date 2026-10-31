package org.domaframework.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranscriptTest {

  @TempDir Path dir;

  private static String toolUse(String id, String command) {
    return """
        {"type":"assistant","message":{"content":[{"type":"tool_use","id":"%s","name":"Bash","input":{"command":"%s"}}]}}"""
        .formatted(id, command);
  }

  private static String toolResult(String id, String text, boolean isError) {
    return """
        {"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"%s","content":"%s","is_error":%s}]}}"""
        .formatted(id, text, isError);
  }

  @Test
  void deniedCommandsAreNotCountedAsBuilds() {
    Path transcript = dir.resolve("transcript.jsonl");
    Shell.write(
        transcript,
        String.join(
            "\n",
            toolUse("1", "mkdir -p src && ./gradlew build"),
            toolResult("1", "Permission to use Bash has been denied", true),
            toolUse("2", "./gradlew build"),
            toolResult("2", "BUILD FAILED", true),
            toolUse("3", "./gradlew build 2>&1 | tail -20"),
            toolResult("3", "BUILD SUCCESSFUL in 2s", false),
            """
            {"type":"result","num_turns":7,"total_cost_usd":0.15,"is_error":false,"permission_denials":[{}]}"""));

    Map<String, Object> metrics = Transcript.parse(transcript);

    assertEquals(2, metrics.get("build_attempts"));
    assertEquals(false, metrics.get("first_build_ok"));
    assertEquals(1, metrics.get("permission_denials"));
    assertEquals(7L, metrics.get("num_turns"));
    assertEquals(0.15, metrics.get("cost_usd"));
  }

  @Test
  void readingBuildFilesIsNotABuild() {
    Path transcript = dir.resolve("transcript.jsonl");
    Shell.write(
        transcript,
        String.join(
            "\n",
            toolUse("1", "cat build.gradle.kts gradle.properties"),
            toolResult("1", "plugins {}", false)));

    Map<String, Object> metrics = Transcript.parse(transcript);

    assertEquals(0, metrics.get("build_attempts"));
    assertTrue(metrics.containsKey("first_build_ok"));
    assertEquals(null, metrics.get("first_build_ok"));
  }

  @Test
  void missingTranscript() {
    assertTrue(Transcript.parse(dir.resolve("none.jsonl")).isEmpty());
  }
}
