package benchtest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.function.Supplier;
import org.seasar.doma.jdbc.SimpleConfig;

/** Creates an isolated H2 database per test, loaded with the benchmark schema and seed data. */
public final class TestDb {

  private final SimpleConfig config;

  private TestDb(SimpleConfig config) {
    this.config = config;
  }

  public static TestDb create() {
    String url = "jdbc:h2:mem:bench_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    try (Connection c = DriverManager.getConnection(url, "sa", "");
        Statement s = c.createStatement()) {
      for (String script : new String[] {"/benchtest/schema.sql", "/benchtest/data.sql"}) {
        for (String sql : read(script).split(";")) {
          if (!sql.isBlank()) {
            s.execute(sql);
          }
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
    return new TestDb(SimpleConfig.builder(url, "sa", "").build());
  }

  public SimpleConfig config() {
    return config;
  }

  public <T> T tx(Supplier<T> supplier) {
    return config.getLocalTransactionManager().required(supplier);
  }

  public void tx(Runnable runnable) {
    config.getLocalTransactionManager().required(runnable);
  }

  private static String read(String path) {
    try (InputStream in = TestDb.class.getResourceAsStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
