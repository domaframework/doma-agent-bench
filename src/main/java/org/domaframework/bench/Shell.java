package org.domaframework.bench;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** File and process helpers. */
final class Shell {

  private Shell() {}

  /** Copies a directory tree onto another, overwriting files. Does nothing if the source is missing. */
  static void copyTree(Path source, Path target) {
    if (!Files.exists(source)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(source)) {
      for (Path path : (Iterable<Path>) paths::iterator) {
        Path destination = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          // COPY_ATTRIBUTES keeps gradlew executable
          Files.copy(
              path,
              destination,
              StandardCopyOption.REPLACE_EXISTING,
              StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Deletes a directory tree, ignoring errors. */
  static void deleteTree(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    } catch (IOException ignored) {
      // best effort, like shutil.rmtree(ignore_errors=True)
    }
  }

  static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static void write(Path file, String text) {
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Files.writeString(file, text, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Path createTempDirectory(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Runs a command and returns its standard output. Failures are ignored. */
  static String output(Path dir, String... command) {
    try {
      Process process =
          new ProcessBuilder(command)
              .directory(dir.toFile())
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      process.getOutputStream().close();
      String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      process.waitFor();
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs a command with its output written to files.
   *
   * @param stderr where standard error goes, or {@code null} to merge it into {@code stdout}
   * @param stdin text written to standard input, or {@code null} for none
   * @return the exit code, or {@code null} if the command timed out and was killed
   */
  static Integer run(
      Path dir, List<String> command, Path stdout, Path stderr, String stdin, Duration timeout) {
    ProcessBuilder builder =
        new ProcessBuilder(command)
            .directory(dir.toFile())
            .redirectOutput(stdout.toFile());
    if (stderr == null) {
      builder.redirectErrorStream(true);
    } else {
      builder.redirectError(stderr.toFile());
    }
    try {
      Process process = builder.start();
      try (OutputStream in = process.getOutputStream()) {
        if (stdin != null) {
          in.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
      }
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        process.waitFor();
        return null;
      }
      return process.exitValue();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
