package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MoneyMigrationVerifierTest {

  @Test
  void usesFreshFullRunAsCheckpointWhenAllRuntimeTriggersExist() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(
        contains("money_migration_runs WHERE migration_key"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("FROM pg_trigger"), eq(Long.class)))
        .thenReturn(13L);
    when(jdbcTemplate.queryForList(contains("FROM money_migration_runs"), eq("core-minor-v2-verified")))
        .thenReturn(List.of());
    when(jdbcTemplate.queryForObject(contains("target_unit = 'verified-restore'"), eq(Long.class)))
        .thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class)))
        .thenReturn(2);
    when(jdbcTemplate.queryForMap(contains("run_id::text AS run_id"), eq("core-minor-v2-verified")))
        .thenReturn(Map.of(
            "run_id", "checkpoint-run",
            "checked_rows", 42L,
            "issue_count", 0L,
            "finished_at", new Timestamp(0L)
        ));

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    verifier.run();

    assertThat(verifier.status())
        .containsEntry("state", "verified-checkpoint")
        .containsEntry("verified", true)
        .containsEntry("readyForAuthoritativeCutover", false)
        .containsEntry("zeroIssueFullCheckStreak", 0)
        .containsEntry("verifiedBackupRestore", false)
        .containsEntry("runId", "checkpoint-run");
    verify(jdbcTemplate, never()).update(contains("INSERT INTO money_migration_runs"), any(), any());
  }

  @Test
  void requiresAllCutoverEvidenceBeforeReportingAuthoritativeReadiness() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(
        contains("money_migration_runs WHERE migration_key"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("FROM pg_trigger"), eq(Long.class)))
        .thenReturn(13L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class)))
        .thenReturn(2);
    when(jdbcTemplate.queryForMap(contains("run_id::text AS run_id"), eq("core-minor-v2-verified")))
        .thenReturn(Map.of(
            "run_id", "checkpoint-run",
            "checked_rows", 42L,
            "issue_count", 0L,
            "finished_at", new Timestamp(0L)
        ));

    List<Map<String, Object>> cleanRuns = new ArrayList<>();
    for (int index = 0; index < 7; index++) {
      cleanRuns.add(Map.of("state", "VERIFIED", "issue_count", 0L));
    }
    when(jdbcTemplate.queryForList(contains("FROM money_migration_runs"), eq("core-minor-v2-verified")))
        .thenReturn(cleanRuns);
    when(jdbcTemplate.queryForObject(contains("target_unit = 'verified-restore'"), eq(Long.class)))
        .thenReturn(1L);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L, true);

    verifier.run();

    assertThat(verifier.status())
        .containsEntry("zeroIssueFullCheckStreak", 7)
        .containsEntry("verifiedBackupRestore", true)
        .containsEntry("apiMinorUnitAuthoritative", true)
        .containsEntry("cutoverEligible", true)
        .containsEntry("readyForAuthoritativeCutover", true)
        .containsEntry("cutoverBlockers", List.of());
  }

  @Test
  void recordsRowsWhereOnlyTheMinorShadowExists() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
        .thenReturn(0L);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    verifier.run();

    verify(jdbcTemplate, atLeastOnce()).update(
        contains("FROM products WHERE price IS NULL AND price_minor IS NOT NULL"),
        any(), any(), any(), any(), any(), any());
  }
}
