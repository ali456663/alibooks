package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MoneyMigrationVerifierTest {

  @Test
  void missingControlSchemaBlocksStartupWithoutAttemptingDdl() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(
        contains("money_migration_runs WHERE migration_key"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L)))
        .thenThrow(new IllegalStateException("money_migration_runs is missing"));

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("money_migration_runs is missing");
    assertThat(verifier.status()).containsEntry("verified", false);
    verify(jdbcTemplate, never()).execute(anyString());
  }

  @Test
  void usesFreshFullRunAsCheckpointWhenAllRuntimeTriggersExist() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(
        contains("money_migration_runs WHERE migration_key"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class)))
        .thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("LEFT JOIN money_migration_checks check_row"),
        eq(Long.class), eq("core-minor-v2-verified"), eq("core-minor-v2-verified"))).thenReturn(0L);
    when(jdbcTemplate.queryForList(contains("FROM money_migration_runs"), eq(26L), eq("core-minor-v2-verified")))
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
            "finished_at", Timestamp.from(Instant.now())
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
    verify(jdbcTemplate).queryForObject(
        argThat(sql -> sql.contains("('shadow:products.price')")
            && sql.contains("('shadow:journal_entries.credit')")
            && sql.contains("('currency:audit_events')")
            && sql.contains("('journal:account-number')")
            && sql.contains("('journal:voucher-date-consistency')")
            && sql.contains("('journal:account-total-reconciliation')")
            && sql.contains("('database:customer-payment-exact-guard')")
            && sql.contains("('database:operational-money-guard')")
            && sql.contains("check_row.run_id = latest_run.run_id")
            && sql.contains("check_row.status = 'PASS'")
            && sql.contains("check_row.issue_count = 0")
            && sql.contains("WHERE check_row.check_name IS NULL")),
        eq(Long.class), eq("core-minor-v2-verified"), eq("core-minor-v2-verified"));
    verify(jdbcTemplate, never()).update(contains("INSERT INTO money_migration_runs"), any(), any());
  }

  @Test
  void checkpointExpiresDuringRuntimeEvenWhenStartupAcceptedIt() {
    JdbcTemplate jdbcTemplate = checkpointWithDays(List.of());
    when(jdbcTemplate.queryForMap(contains("run_id::text AS run_id"), eq("core-minor-v2-verified")))
        .thenReturn(Map.of("run_id", "old-run", "checked_rows", 42L,
            "issue_count", 0L, "finished_at", Timestamp.from(Instant.now().minus(27, java.time.temporal.ChronoUnit.HOURS))));
    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    verifier.run();

    assertThat(verifier.status())
        .containsEntry("state", "stale-checkpoint")
        .containsEntry("verified", false)
        .containsEntry("readyForAuthoritativeCutover", false)
        .containsEntry("cutoverEligible", false);
  }

  @Test
  void fullVerificationIsNotReportedAsVerifiedWhileItRuns() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    AtomicReference<MoneyMigrationVerifier> currentVerifier = new AtomicReference<>();
    when(jdbcTemplate.update(contains("INSERT INTO money_migration_runs"), any(), any()))
        .thenAnswer(invocation -> {
          assertThat(currentVerifier.get().status())
              .containsEntry("state", "verifying")
              .containsEntry("verified", false);
          return 1;
        });
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);
    currentVerifier.set(verifier);

    verifier.run();

    assertThat(verifier.status()).containsEntry("state", "verified-shadow")
        .containsEntry("verified", true);
  }

  @Test
  void cannotDeclareApiAuthoritativeFromConfigurationAlone() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(
        contains("money_migration_runs WHERE migration_key"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class)))
        .thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("LEFT JOIN money_migration_checks check_row"),
        eq(Long.class), eq("core-minor-v2-verified"), eq("core-minor-v2-verified"))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class)))
        .thenReturn(2);
    when(jdbcTemplate.queryForMap(contains("run_id::text AS run_id"), eq("core-minor-v2-verified")))
        .thenReturn(Map.of(
            "run_id", "checkpoint-run",
            "checked_rows", 42L,
            "issue_count", 0L,
            "finished_at", Timestamp.from(Instant.now())
        ));

    List<Map<String, Object>> cleanRuns = new ArrayList<>();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (int index = 0; index < 7; index++) {
      cleanRuns.add(Map.of("check_day", java.sql.Date.valueOf(today.minusDays(index)),
          "clean", true, "fresh", index == 0));
    }
    when(jdbcTemplate.queryForList(contains("FROM money_migration_runs"), eq(26L), eq("core-minor-v2-verified")))
        .thenReturn(cleanRuns);
    when(jdbcTemplate.queryForObject(contains("target_unit = 'verified-restore'"), eq(Long.class)))
        .thenReturn(1L);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    verifier.run();

    assertThat(verifier.status())
        .containsEntry("zeroIssueFullCheckStreak", 7)
        .containsEntry("verifiedBackupRestore", true)
        .containsEntry("apiMinorUnitAuthoritative", false)
        .containsEntry("cutoverEligible", false)
        .containsEntry("readyForAuthoritativeCutover", false)
        .containsEntry("cutoverBlockers", List.of("API and booking flows are not yet minor-unit authoritative"));
  }

  @Test
  void repeatedChecksOnOneDayDoNotCountAsSevenDays() {
    JdbcTemplate jdbcTemplate = checkpointWithDays(List.of(
        day(LocalDate.now(ZoneOffset.UTC), true, true)));

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);
    verifier.run();

    assertThat(verifier.status()).containsEntry("zeroIssueFullCheckStreak", 1)
        .containsEntry("readyForAuthoritativeCutover", false);
    verify(jdbcTemplate).queryForList(
        argThat(sql -> sql.contains("GROUP BY check_day") && sql.contains("BOOL_AND")
            && sql.contains("COALESCE(finished_at, started_at)")),
        eq(26L), eq("core-minor-v2-verified"));
  }

  @Test
  void aMissingOrFailedDayBreaksTheCutoverStreak() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    JdbcTemplate jdbcTemplate = checkpointWithDays(List.of(
        day(today, true, true), day(today.minusDays(1), true, false),
        day(today.minusDays(3), true, false)));
    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);
    verifier.run();
    assertThat(verifier.status()).containsEntry("zeroIssueFullCheckStreak", 2);

    when(jdbcTemplate.queryForList(contains("FROM money_migration_runs"), eq(26L), eq("core-minor-v2-verified")))
        .thenReturn(List.of(day(today, true, true), day(today.minusDays(1), false, false)));
    verifier.run();
    assertThat(verifier.status()).containsEntry("zeroIssueFullCheckStreak", 1);
  }

  @Test
  void staleChecksCannotQualifyForCutover() {
    JdbcTemplate jdbcTemplate = checkpointWithDays(List.of(
        day(LocalDate.now(ZoneOffset.UTC).minusDays(1), true, false)));
    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);
    verifier.run();
    assertThat(verifier.status()).containsEntry("zeroIssueFullCheckStreak", 0);
  }

  @Test
  void oldCheckpointWithoutWholeKronaCheckForcesFullVerification() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(contains("money_migration_runs WHERE migration_key"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L))).thenReturn(1L);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("LEFT JOIN money_migration_checks check_row"),
        eq(Long.class), eq("core-minor-v2-verified"), eq("core-minor-v2-verified"))).thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    verifier.run();

    assertThat(verifier.status()).containsEntry("state", "verified-shadow");
    verify(jdbcTemplate).update(contains("INSERT INTO money_migration_runs"), any(), eq("core-minor-v2-verified"));
    verify(jdbcTemplate).queryForObject(contains("LEFT JOIN money_migration_checks check_row"),
        eq(Long.class), eq("core-minor-v2-verified"), eq("core-minor-v2-verified"));
  }

  private JdbcTemplate checkpointWithDays(List<Map<String, Object>> days) {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(contains("money_migration_runs WHERE migration_key"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L))).thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("LEFT JOIN money_migration_checks check_row"),
        eq(Long.class), eq("core-minor-v2-verified"), eq("core-minor-v2-verified"))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);
    when(jdbcTemplate.queryForMap(contains("run_id::text AS run_id"), eq("core-minor-v2-verified")))
        .thenReturn(Map.of("run_id", "checkpoint-run", "checked_rows", 42L,
            "issue_count", 0L, "finished_at", Timestamp.from(Instant.now())));
    when(jdbcTemplate.queryForList(contains("FROM money_migration_runs"), eq(26L), eq("core-minor-v2-verified")))
        .thenReturn(days);
    return jdbcTemplate;
  }

  private Map<String, Object> day(LocalDate day, boolean clean, boolean fresh) {
    return Map.of("check_day", java.sql.Date.valueOf(day), "clean", clean, "fresh", fresh);
  }

  @Test
  void recordsRowsWhereOnlyTheMinorShadowExists() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
        .thenReturn(0L);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    verifier.run();

    verify(jdbcTemplate, atLeastOnce()).update(
        contains("FROM products WHERE price IS NULL AND price_minor IS NOT NULL"),
        any(), any(), any(), any(), any(), any());
  }

  @Test
  void blocksStartupWhenAnyMoneyShadowTriggerIsDisabledOrMisconfigured() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(
        contains("money_migration_runs WHERE migration_key"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(31L);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("money shadow trigger");

    assertThat(verifier.status())
        .containsEntry("state", "blocked")
        .containsEntry("verified", false);
    String expenseArguments = HexFormat.of().formatHex(
        "net_amount\0net_amount_minor\0vat_amount\0vat_amount_minor\0total_amount\0total_amount_minor\0"
            .getBytes(StandardCharsets.UTF_8));
    String orderArguments = HexFormat.of().formatHex(
        "ordinary_price\0ordinary_price_minor\0discount_amount\0discount_amount_minor\0net_amount\0net_amount_minor\0"
            .concat("vat_amount\0vat_amount_minor\0total_amount\0total_amount_minor\0paid_amount\0paid_amount_minor\0")
            .concat("refunded_amount\0refunded_amount_minor\0")
            .getBytes(StandardCharsets.UTF_8));
    verify(jdbcTemplate).queryForObject(
        argThat(sql -> sql.contains("trigger_row.tgenabled IN ('O', 'A')")
            && sql.contains("trigger_function.proname = expected.function_name")
            && sql.contains("(trigger_row.tgtype & expected.event_mask) = expected.event_mask")
            && sql.contains("trigger_row.tgnargs = expected.argument_count")
            && sql.contains("encode(trigger_row.tgargs, 'hex') = expected.argument_hex")
            && sql.contains("function_namespace.nspname = current_schema()")
            && sql.contains("('expenses','a_expenses_money_integrity_guard','guard_operational_money_integrity',23,false,6,'"
                + expenseArguments + "')")
            && sql.contains("('customer_orders','a_customer_orders_money_integrity_guard','guard_customer_order_money_integrity',23,false,14,'"
                + orderArguments + "')")
            && sql.contains("('invoice_originals','invoice_originals_immutable','protect_invoice_original',27,false,0,'')")
            && sql.contains("('audit_events','audit_events_immutable','protect_audit_event',27,false,0,'')")
            && sql.contains("('audit_events','audit_events_no_truncate','protect_audit_event',34,false,0,'')")
            && sql.contains("trigger_row.tginitdeferred")),
        eq(Long.class));
  }

  @Test
  void doesNotReuseAnOlderVerifiedCheckpointWhenTheLatestRunFailed() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(
        contains("ORDER BY started_at DESC LIMIT 1"),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L)))
        .thenReturn(0L);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    verifier.run();

    assertThat(verifier.status())
        .containsEntry("state", "verified-shadow")
        .containsEntry("verified", true);
    verify(jdbcTemplate).update(
        contains("INSERT INTO money_migration_runs"), any(), eq("core-minor-v2-verified"));
    verify(jdbcTemplate).queryForObject(
        argThat(sql -> sql.contains("finished_at >= (SELECT applied_at FROM money_migration_ledger")
            && sql.contains("journal-money-guard-v1")
            && sql.contains("journal-voucher-balance-v1")
            && sql.contains("journal-voucher-seal-v1")
            && sql.contains("supplier-payment-exact-v1")
            && sql.contains("customer-payment-exact-v1")
            && sql.contains("customer-order-exact-v1")
            && sql.contains("product-money-guard-v1")),
        eq(Long.class), eq("core-minor-v2-verified"), eq(26L));
  }

  @Test
  void blocksHistoricalFractionalSupplierPaymentDespiteExactCentShadow() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("FROM supplier_invoice_payments WHERE amount IS NOT NULL "
        + "AND CAST(amount AS numeric) <> TRUNC(CAST(amount AS numeric))"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("supplier_invoice_payments.amount");
    assertThat(verifier.status()).containsEntry("state", "blocked");
    verify(jdbcTemplate).update(argThat(sql -> sql.contains("FROM supplier_invoice_payments")
            && sql.contains("TRUNC(CAST(amount AS numeric))")),
        any(), any(), any(), any(), eq("FRACTIONAL_LEGACY"), any());
  }

  @Test
  void blocksFractionalJournalDebitEvenWhenItsCentShadowMatches() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("FROM journal_entries WHERE debit IS NOT NULL "
        + "AND CAST(debit AS numeric) <> TRUNC(CAST(debit AS numeric))"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("journal_entries.debit");
    assertThat(verifier.status()).containsEntry("state", "blocked");
    verify(jdbcTemplate).update(argThat(sql -> sql.contains("FROM journal_entries")
            && sql.contains("TRUNC(CAST(debit AS numeric))")),
        any(), any(), any(), any(), eq("FRACTIONAL_LEGACY"), any());
  }

  @Test
  void rejectsWrongSekExponentBeforeVerifyingAnyAmounts() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(0);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exponent of 2");
    assertThat(verifier.status()).containsEntry("verified", false);
  }

  @Test
  void countsVoucherAndAccountReconciliationFailuresInRunAndStatus() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("HAVING COALESCE(SUM(debit_minor)"), eq(Long.class)))
        .thenReturn(2L);
    when(jdbcTemplate.queryForObject(contains("account_totals"), eq(Long.class)))
        .thenReturn(3L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unbalanced voucher");
    assertThat(verifier.status()).containsEntry("state", "blocked").containsEntry("issueCount", 5L);
    verify(jdbcTemplate).update(contains("UPDATE money_migration_runs SET state = ?"),
        eq("BLOCKED"), any(), eq(5L), any());
  }

  @Test
  void blocksHistoricalJournalRowsWithoutVoucherNumbers() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(
        contains("WHERE voucher_number IS NULL OR btrim(voucher_number) = ''"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without a voucher number");
    assertThat(verifier.status()).containsEntry("state", "blocked").containsEntry("issueCount", 1L);
    verify(jdbcTemplate).update(contains("UPDATE money_migration_runs SET state = ?"),
        eq("BLOCKED"), any(), eq(1L), any());
  }

  @Test
  void blocksHistoricalJournalRowsWithoutVoucherDates() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("FROM journal_entries WHERE voucher_date IS NULL"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without a voucher date");
    assertThat(verifier.status()).containsEntry("state", "blocked").containsEntry("issueCount", 1L);
  }

  @Test
  void blocksHistoricalJournalRowsWithoutAccountNumbers() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE account_number IS NULL OR btrim(account_number) = ''"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without an account number");
    assertThat(verifier.status()).containsEntry("state", "blocked").containsEntry("issueCount", 1L);
  }

  @Test
  void blocksVouchersWithMultipleAccountingDates() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("HAVING COUNT(DISTINCT voucher_date) > 1"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("inconsistent dates");
    assertThat(verifier.status()).containsEntry("state", "blocked").containsEntry("issueCount", 1L);
  }

  @Test
  void blocksMissingHistoricalVoucherRegistryRow() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("LEFT JOIN journal_voucher_registry registry"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing from the registry");
    assertThat(verifier.status()).containsEntry("state", "blocked").containsEntry("issueCount", 1L);
  }

  @Test
  void blocksOrphanedVoucherRegistryRow() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("FROM journal_voucher_registry registry WHERE NOT EXISTS"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("orphaned voucher");
    assertThat(verifier.status()).containsEntry("state", "blocked").containsEntry("issueCount", 1L);
  }

  @Test
  void blocksBalancedJournalWithBothSidesPositiveOnOneRow() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'supplier-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("WHERE migration_key = 'customer-payment-exact-v1'"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("OR (debit > 0 AND credit > 0)"), eq(Long.class)))
        .thenReturn(1L);
    when(jdbcTemplate.queryForObject(contains("JOIN pg_trigger"), eq(Long.class))).thenReturn(32L);
    when(jdbcTemplate.queryForObject(contains("FROM currencies"), eq(Integer.class))).thenReturn(2);

    MoneyMigrationVerifier verifier = new MoneyMigrationVerifier(jdbcTemplate, true, 26L);

    org.assertj.core.api.Assertions.assertThatThrownBy(verifier::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid debit/credit row");
    assertThat(verifier.status()).containsEntry("state", "blocked").containsEntry("issueCount", 1L);
  }
}
