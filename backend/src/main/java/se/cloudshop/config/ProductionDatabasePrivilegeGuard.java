package se.cloudshop.config;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app", name = "environment", havingValue = "production")
public class ProductionDatabasePrivilegeGuard implements SmartInitializingSingleton {

  private static final String PRIVILEGE_QUERY = """
      SELECT current_schema() = 'public' AS expected_schema,
             session_user = current_user AS direct_session,
             COALESCE((SELECT NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
                       FROM pg_roles WHERE rolname = session_user), false) AS unprivileged_role,
             COALESCE((SELECT NOT pg_has_role(session_user, datdba, 'MEMBER')
                       FROM pg_database WHERE datname = current_database()), false) AS not_database_owner,
             NOT has_database_privilege(session_user, current_database(), 'CREATE') AS no_database_create,
             COALESCE((SELECT NOT pg_has_role(session_user, oid, 'MEMBER')
                       FROM pg_namespace WHERE nspname = 'public'), false) AS not_schema_owner,
             NOT has_schema_privilege(session_user, 'public', 'CREATE') AS no_schema_create,
             (SELECT COUNT(DISTINCT relname) = 4 FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
              AND c.relname IN ('journal_entries', 'journal_voucher_registry',
                                'audit_events', 'invoice_originals')) AS protected_tables_present,
             NOT EXISTS (SELECT 1 FROM pg_class c
                         JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'public'
                         AND pg_has_role(session_user, c.relowner, 'MEMBER')) AS not_relation_owner,
             NOT EXISTS (SELECT 1 FROM pg_proc p
                         JOIN pg_namespace n ON n.oid = p.pronamespace
                         WHERE n.nspname = 'public'
                         AND pg_has_role(session_user, p.proowner, 'MEMBER')) AS not_function_owner,
             NOT EXISTS (SELECT 1 FROM pg_class c
                         JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                         AND (has_table_privilege(session_user, c.oid, 'TRIGGER')
                              OR has_table_privilege(session_user, c.oid, 'TRUNCATE')
                              OR has_table_privilege(session_user, c.oid, 'REFERENCES'))) AS no_dangerous_table_grants
      """;

  private static final List<String> REQUIRED_CHECKS = List.of(
      "expected_schema", "direct_session", "unprivileged_role", "not_database_owner",
      "no_database_create", "not_schema_owner", "no_schema_create", "protected_tables_present",
      "not_relation_owner", "not_function_owner", "no_dangerous_table_grants");

  private final JdbcTemplate jdbcTemplate;

  public ProductionDatabasePrivilegeGuard(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void afterSingletonsInstantiated() {
    Map<String, Object> checks;
    try {
      checks = jdbcTemplate.queryForMap(PRIVILEGE_QUERY);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Production database privileges could not be verified", exception);
    }
    List<String> failed = REQUIRED_CHECKS.stream()
        .filter(check -> checks == null || !Boolean.TRUE.equals(checks.get(check)))
        .toList();
    if (!failed.isEmpty()) {
      throw new IllegalStateException("Unsafe production database role: " + String.join(", ", failed));
    }
  }
}
