package org.domaframework.bench;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prepares the project an agent works in, and the prompt it receives. */
final class Workspace {

  private Workspace() {}

  static Path prepare(Path task, Options o) {
    // A fresh temp directory outside the bench repo, so the agent cannot see hidden tests
    Path ws = Shell.createTempDirectory("doma-bench-").resolve("app");
    Shell.copyTree(Bench.TEMPLATE, ws);
    Shell.copyTree(task.resolve("workspace"), ws);
    // Write the versions into the workspace, so that the agent's own builds use them too
    Path properties = ws.resolve("gradle.properties");
    String text = Shell.read(properties);
    text = setProperty(text, "domaVersion", o.domaVersion);
    text = setProperty(text, "domaCompileVersion", o.domaCompileVersion);
    Shell.write(properties, text);
    Shell.output(ws, "git", "init", "-q");
    Shell.output(ws, "git", "add", "-A");
    Shell.output(
        ws,
        "git",
        "-c",
        "user.name=bench",
        "-c",
        "user.email=bench@localhost",
        "commit",
        "-q",
        "-m",
        "initial");
    return ws;
  }

  static String setProperty(String text, String key, String value) {
    if (value == null) {
      return text;
    }
    return Pattern.compile("^" + key + "=.*$", Pattern.MULTILINE)
        .matcher(text)
        .replaceAll(Matcher.quoteReplacement(key + "=" + value));
  }

  static String buildPrompt(Path task, List<String> contextFiles, Path domaRepo) {
    List<String> parts = new ArrayList<>();
    for (String file : contextFiles) {
      Path path = Path.of(file.replace("{doma_repo}", domaRepo.toString()));
      parts.add(
          "Reference material ("
              + path.getFileName()
              + "):\n\n"
              + Shell.read(path)
              + "\n\n---\n");
    }
    parts.add(Shell.read(Bench.PREAMBLE));
    parts.add(Shell.read(task.resolve("prompt.md")));
    return String.join("\n", parts);
  }
}
