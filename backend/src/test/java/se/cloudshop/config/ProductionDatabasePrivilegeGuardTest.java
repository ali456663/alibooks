package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductionDatabasePrivilegeGuardTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfiguration.class)
      .withBean(JdbcTemplate.class, () -> jdbc);

  @Test
  void acceptsAnUnprivilegedApplicationRole() {
    when(jdbc.queryForMap(anyString())).thenReturn(safeRole());
    runner.withPropertyValues("app.environment=production")
        .run(context -> assertThat(context.getStartupFailure()).isNull());
  }

  @Test
  void rejectsOwnershipAndTriggerRights() {
    Map<String, Object> checks = safeRole();
    checks.put("not_relation_owner", false);
    checks.put("not_function_owner", false);
    checks.put("no_dangerous_table_grants", false);
    when(jdbc.queryForMap(anyString())).thenReturn(checks);
    runner.withPropertyValues("app.environment=production")
        .run(context -> assertThat(context.getStartupFailure())
            .hasMessageContaining("not_relation_owner")
            .hasMessageContaining("not_function_owner")
            .hasMessageContaining("no_dangerous_table_grants"));
  }

  @Test
  void rejectsAnIncompleteSchema() {
    Map<String, Object> checks = safeRole();
    checks.put("protected_tables_present", false);
    when(jdbc.queryForMap(anyString())).thenReturn(checks);
    runner.withPropertyValues("app.environment=production")
        .run(context -> assertThat(context.getStartupFailure())
            .hasMessageContaining("protected_tables_present"));
  }

  @Test
  void databaseFailureBlocksStartup() {
    when(jdbc.queryForMap(anyString())).thenThrow(new IllegalStateException("database unavailable"));
    runner.withPropertyValues("app.environment=production")
        .run(context -> assertThat(context.getStartupFailure())
            .hasMessageContaining("Production database privileges could not be verified"));
  }

  @Test
  void localModeDoesNotCheckDatabasePrivileges() {
    when(jdbc.queryForMap(anyString())).thenThrow(new IllegalStateException("must not query"));
    runner.withPropertyValues("app.environment=local")
        .run(context -> assertThat(context.getStartupFailure()).isNull());
  }

  private static Map<String, Object> safeRole() {
    Map<String, Object> result = new HashMap<>();
    for (String key : new String[] {
        "expected_schema", "direct_session", "unprivileged_role", "not_database_owner",
        "no_database_create", "not_schema_owner", "no_schema_create", "protected_tables_present",
        "not_relation_owner", "not_function_owner", "no_dangerous_table_grants"}) {
      result.put(key, true);
    }
    return result;
  }

  @Configuration(proxyBeanMethods = false)
  @Import(ProductionDatabasePrivilegeGuard.class)
  static class TestConfiguration {}
}
