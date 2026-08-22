package se.cloudshop.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseSchemaPatchTest {

  @Test
  void disabledPatchDoesNotMutateDatabase() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    DatabaseSchemaPatch patch = new DatabaseSchemaPatch(jdbcTemplate, false);

    patch.run();

    verify(jdbcTemplate, never()).execute(anyString());
  }

  @Test
  void enabledPatchRunsStartupSchemaStatements() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    DatabaseSchemaPatch patch = new DatabaseSchemaPatch(jdbcTemplate, true);

    patch.run();

    verify(jdbcTemplate, atLeastOnce()).execute(anyString());
  }
}
