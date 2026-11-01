package org.domaframework.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceTest {

  @TempDir Path ws;

  private static final String PROPERTIES =
      """
      # comment
      domaVersion=3.14.1
      domaCompileVersion=4.0.4
      """;

  @Test
  void setPropertyReplacesOnlyTheGivenKey() {
    String text = Workspace.setProperty(PROPERTIES, "domaVersion", "3.14.2-SNAPSHOT");
    assertEquals(
        """
        # comment
        domaVersion=3.14.2-SNAPSHOT
        domaCompileVersion=4.0.4
        """,
        text);
  }

  @Test
  void setPropertyWithoutValueKeepsTheText() {
    assertEquals(PROPERTIES, Workspace.setProperty(PROPERTIES, "domaVersion", null));
  }

  @Test
  void property() {
    Shell.write(ws.resolve("gradle.properties"), PROPERTIES);
    assertEquals("3.14.1", Workspace.property(ws, "domaVersion"));
    assertEquals("4.0.4", Workspace.property(ws, "domaCompileVersion"));
    assertNull(Workspace.property(ws, "missing"));
  }
}
