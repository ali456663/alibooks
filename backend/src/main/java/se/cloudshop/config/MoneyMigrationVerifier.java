package se.cloudshop.config;

import java.time.Instant;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Verifies the additive money migration before the application starts serving data.
 *
 * <p>The legacy columns are deliberately retained in this phase. This verifier makes
 * the migration auditable and fails closed on any row where the bigint shadow, currency
 * code, or journal balance is not exact. It does not silently repair bookkeeping data.
 */
@Component
@Order(10)
public class MoneyMigrationVerifier implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(MoneyMigrationVerifier.class);
  private static final String CURRENCY = "SEK";
  private static final String MIGRATION_KEY = "core-minor-v2-verified";
  private static final List<String> REQUIRED_TRIGGER_NAMES = List.of(
      "products_money_shadow_sync",
      "customer_orders_money_shadow_sync",
      "invoice_payments_money_shadow_sync",
      "supplier_invoices_money_shadow_sync",
      "supplier_invoice_payments_money_shadow_sync",
      "expenses_money_shadow_sync",
      "card_purchases_money_shadow_sync",
      "stripe_payouts_money_shadow_sync",
      "bank_reconciliation_entries_money_shadow_sync",
      "vat_filings_money_shadow_sync",
      "journal_entries_money_shadow_sync",
      "owner_transactions_money_shadow_sync",
      "audit_events_money_shadow_sync"
  );

  private static final List<MoneyColumn> MONEY_COLUMNS = List.of(
      new MoneyColumn("products", "price", "price_minor"),
      new MoneyColumn("products", "discount_price", "discount_price_minor"),
      new MoneyColumn("customer_orders", "ordinary_price", "ordinary_price_minor"),
      new MoneyColumn("customer_orders", "discount_amount", "discount_amount_minor"),
      new MoneyColumn("customer_orders", "net_amount", "net_amount_minor"),
      new MoneyColumn("customer_orders", "vat_amount", "vat_amount_minor"),
      new MoneyColumn("customer_orders", "total_amount", "total_amount_minor"),
      new MoneyColumn("customer_orders", "paid_amount", "paid_amount_minor"),
      new MoneyColumn("customer_orders", "refunded_amount", "refunded_amount_minor"),
      new MoneyColumn("invoice_payments", "amount", "amount_minor"),
      new MoneyColumn("supplier_invoices", "total_amount", "total_amount_minor"),
      new MoneyColumn("supplier_invoices", "vat_amount", "vat_amount_minor"),
      new MoneyColumn("supplier_invoices", "net_amount", "net_amount_minor"),
      new MoneyColumn("supplier_invoices", "paid_amount", "paid_amount_minor"),
      new MoneyColumn("supplier_invoice_payments", "amount", "amount_minor"),
      new MoneyColumn("expenses", "net_amount", "net_amount_minor"),
      new MoneyColumn("expenses", "vat_amount", "vat_amount_minor"),
      new MoneyColumn("expenses", "total_amount", "total_amount_minor"),
      new MoneyColumn("card_purchases", "net_amount", "net_amount_minor"),
      new MoneyColumn("card_purchases", "vat_amount", "vat_amount_minor"),
      new MoneyColumn("card_purchases", "total_amount", "total_amount_minor"),
      new MoneyColumn("stripe_payouts", "gross_amount", "gross_amount_minor"),
      new MoneyColumn("stripe_payouts", "fee_amount", "fee_amount_minor"),
      new MoneyColumn("stripe_payouts", "net_amount", "net_amount_minor"),
      new MoneyColumn("bank_reconciliation_entries", "amount", "amount_minor"),
      new MoneyColumn("vat_filings", "output_vat", "output_vat_minor"),
      new MoneyColumn("vat_filings", "input_vat", "input_vat_minor"),
      new MoneyColumn("vat_filings", "vat_to_pay", "vat_to_pay_minor"),
      new MoneyColumn("journal_entries", "debit", "debit_minor"),
      new MoneyColumn("journal_entries", "credit", "credit_minor"),
      new MoneyColumn("owner_transactions", "amount", "amount_minor"),
      new MoneyColumn("audit_events", "amount", "amount_minor")
  );

  private final JdbcTemplate jdbcTemplate;
  private final boolean verifyOnStartup;
  private final long maxFullCheckAgeHours;
  private final boolean apiMinorUnitAuthoritative;
  private volatile Map<String, Object> status = Map.of(
      "state", "not-verified",
      "currency", CURRENCY,
      "verified", false,
      "readyForAuthoritativeCutover", false
  );

  @Autowired
  public MoneyMigrationVerifier(
      JdbcTemplate jdbcTemplate,
      @Value("${app.money-migration.verify-on-startup:true}") boolean verifyOnStartup,
      @Value("${app.money-migration.max-full-check-age-hours:26}") long maxFullCheckAgeHours) {
    this(jdbcTemplate, verifyOnStartup, maxFullCheckAgeHours, false);
  }

  public MoneyMigrationVerifier(
      JdbcTemplate jdbcTemplate,
      boolean verifyOnStartup,
      long maxFullCheckAgeHours,
      @Value("${app.money-migration.authoritative:false}") boolean apiMinorUnitAuthoritative) {
    this.jdbcTemplate = jdbcTemplate;
    this.verifyOnStartup = verifyOnStartup;
    if (maxFullCheckAgeHours <= 0) {
      throw new IllegalArgumentException("app.money-migration.max-full-check-age-hours must be positive");
    }
    this.maxFullCheckAgeHours = maxFullCheckAgeHours;
    this.apiMinorUnitAuthoritative = apiMinorUnitAuthoritative;
  }

  @Override
  public void run(String... args) {
    if (!verifyOnStartup) {
      status = Map.of(
          "state", "disabled",
          "currency", CURRENCY,
          "verified", false,
          "readyForAuthoritativeCutover", false,
          "zeroIssueFullCheckStreak", 0,
          "requiredZeroIssueFullChecks", 7,
          "verifiedBackupRestore", false,
          "apiMinorUnitAuthoritative", false,
          "cutoverEligible", false,
          "cutoverBlockers", List.of("money migration verification is disabled")
      );
      return;
    }

    ensureControlTables();

    if (hasFreshVerifiedCheckpoint()) {
      verifyCheckpoint();
      return;
    }

    runFullVerification();
  }

  private void ensureControlTables() {
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS money_migration_issues ("
        + "id bigserial PRIMARY KEY, run_id uuid, migration_key varchar(128) NOT NULL, "
        + "entity_table varchar(128) NOT NULL, entity_id varchar(128) NOT NULL, "
        + "field_name varchar(128) NOT NULL, issue_code varchar(64) NOT NULL, "
        + "source_value text, target_value text, details text, "
        + "first_seen_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "last_seen_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    jdbcTemplate.execute("ALTER TABLE money_migration_issues ADD COLUMN IF NOT EXISTS run_id uuid");
    jdbcTemplate.execute("ALTER TABLE money_migration_issues DROP CONSTRAINT IF EXISTS money_migration_issues_migration_key_entity_table_entity_id_field_name_issue_code_key");
    jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS money_migration_issues_key_idx "
        + "ON money_migration_issues(migration_key, last_seen_at)");
    jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS money_migration_issues_run_idx "
        + "ON money_migration_issues(run_id, id)");
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS money_migration_runs ("
        + "run_id uuid PRIMARY KEY, migration_key varchar(128) NOT NULL, mode varchar(32) NOT NULL, "
        + "state varchar(32) NOT NULL, started_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "finished_at timestamp with time zone, checked_rows bigint NOT NULL DEFAULT 0, "
        + "issue_count bigint NOT NULL DEFAULT 0, error_details text)");
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS money_migration_checks ("
        + "migration_key varchar(128) NOT NULL, check_name varchar(128) NOT NULL, run_id uuid, "
        + "status varchar(32) NOT NULL, row_count bigint NOT NULL DEFAULT 0, "
        + "issue_count bigint NOT NULL DEFAULT 0, details text, "
        + "checked_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "PRIMARY KEY (migration_key, check_name))");
    jdbcTemplate.execute("ALTER TABLE money_migration_checks ADD COLUMN IF NOT EXISTS run_id uuid");
  }

  private void runFullVerification() {

    UUID runId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO money_migration_runs (run_id, migration_key, mode, state) VALUES (?, ?, 'full', 'RUNNING')",
        runId, MIGRATION_KEY);
    int minorUnitExponent = minorUnitExponent();
    long minorUnitFactor = BigInteger.TEN.pow(minorUnitExponent).longValueExact();
    List<String> failures = new ArrayList<>();
    long issueCount = 0L;
    long checkedRows = 0L;

    for (MoneyColumn column : MONEY_COLUMNS) {
      long missingMinor = count("SELECT COUNT(*) FROM " + column.table()
          + " WHERE " + column.legacyColumn() + " IS NOT NULL AND " + column.minorColumn() + " IS NULL");
      long orphanMinor = count("SELECT COUNT(*) FROM " + column.table()
          + " WHERE " + column.legacyColumn() + " IS NULL AND " + column.minorColumn() + " IS NOT NULL");
      long roundingRequired = count("SELECT COUNT(*) FROM " + column.table()
          + " WHERE " + column.legacyColumn() + " IS NOT NULL AND CAST(" + column.legacyColumn()
          + " AS numeric) * " + minorUnitFactor + " <> ROUND(CAST(" + column.legacyColumn()
          + " AS numeric) * " + minorUnitFactor + ", 0)");
      long mismatch = count("SELECT COUNT(*) FROM " + column.table()
          + " WHERE " + column.legacyColumn() + " IS NOT NULL AND " + column.minorColumn()
          + " IS NOT NULL AND " + column.minorColumn() + " <> ROUND(CAST(" + column.legacyColumn()
          + " AS numeric) * " + minorUnitFactor + ", 0)");
      long rows = count("SELECT COUNT(*) FROM " + column.table());
      checkedRows = Math.addExact(checkedRows, rows);
      long totalIssues = Math.addExact(missingMinor,
          Math.addExact(orphanMinor, Math.addExact(roundingRequired, mismatch)));
      issueCount = Math.addExact(issueCount, totalIssues);
      recordCheck(runId, "shadow:" + column.table() + "." + column.legacyColumn(), rows,
          totalIssues, "legacy and bigint shadow must exist together and match without implicit truncation");
      recordIssues(runId, column, "MISSING_MINOR", column.minorColumn() + " is null", minorUnitFactor);
      recordIssues(runId, column, "LEGACY_MISSING", column.legacyColumn() + " is null while the shadow exists", minorUnitFactor);
      recordIssues(runId, column, "ROUNDING_REQUIRED", "legacy value requires explicit minor-unit rounding", minorUnitFactor);
      recordIssues(runId, column, "MINOR_MISMATCH", "legacy value * " + minorUnitFactor + " differs from bigint shadow", minorUnitFactor);
      if (totalIssues > 0) {
        failures.add(column.table() + "." + column.legacyColumn() + " has "
            + totalIssues + " non-exact row(s)");
      }
    }

    for (String table : List.of("products", "customer_orders", "invoice_payments", "supplier_invoices",
      "expenses", "card_purchases", "stripe_payouts", "bank_reconciliation_entries", "vat_filings",
        "supplier_invoice_payments",
        "journal_entries", "owner_transactions", "audit_events")) {
      long wrongCurrency = count("SELECT COUNT(*) FROM " + table
          + " WHERE currency_code IS NULL OR currency_code <> '" + CURRENCY + "'");
      long rows = count("SELECT COUNT(*) FROM " + table);
      issueCount = Math.addExact(issueCount, wrongCurrency);
      recordCheck(runId, "currency:" + table, rows, wrongCurrency, "currency_code must be SEK");
      if (wrongCurrency > 0) {
        recordIssues(runId, table, "currency_code", "CURRENCY_MISMATCH", wrongCurrency);
        failures.add(table + " has " + wrongCurrency + " row(s) without currency SEK");
      }
    }

    long unbalancedVouchers = count("SELECT COUNT(*) FROM (SELECT voucher_number FROM journal_entries "
        + "WHERE voucher_number IS NOT NULL AND voucher_number <> '' GROUP BY voucher_number "
        + "HAVING COALESCE(SUM(debit_minor), 0) <> COALESCE(SUM(credit_minor), 0)) balanced");
    recordCheck(runId, "journal:voucher-balance", count("SELECT COUNT(DISTINCT voucher_number) FROM journal_entries "
        + "WHERE voucher_number IS NOT NULL AND voucher_number <> ''"), unbalancedVouchers,
        "debit_minor must equal credit_minor per voucher");
    if (unbalancedVouchers > 0) {
      failures.add("journal_entries has " + unbalancedVouchers + " unbalanced voucher(s)");
    }

    long accountMismatches = count("SELECT COUNT(*) FROM (SELECT account_number FROM journal_entries "
        + "WHERE account_number IS NOT NULL GROUP BY account_number "
        + "HAVING COALESCE(SUM(CAST(debit AS numeric) * " + minorUnitFactor + " - CAST(credit AS numeric) * " + minorUnitFactor + "), 0) "
        + "<> COALESCE(SUM(debit_minor - credit_minor), 0)) account_totals");
    recordCheck(runId, "journal:account-total-reconciliation", count("SELECT COUNT(DISTINCT account_number) "
        + "FROM journal_entries WHERE account_number IS NOT NULL"), accountMismatches,
        "legacy account totals * 100 must equal minor account totals");
    if (accountMismatches > 0) {
      failures.add("journal_entries has " + accountMismatches + " account total mismatch(es)");
    }

    boolean verified = failures.isEmpty();
    if (verified) {
      jdbcTemplate.update("INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) "
          + "VALUES (?, 'minor-unit-verified-shadow', 'minor-authoritative-ready') "
          + "ON CONFLICT (migration_key) DO UPDATE SET applied_at = CURRENT_TIMESTAMP",
          MIGRATION_KEY);
    }

    Map<String, Object> fullStatus = new LinkedHashMap<>();
    fullStatus.put("state", verified ? "verified-shadow" : "blocked");
    fullStatus.put("currency", CURRENCY);
    fullStatus.put("minorUnitExponent", minorUnitExponent);
    fullStatus.put("verified", verified);
    fullStatus.put("runId", runId.toString());
    fullStatus.put("checkedRows", checkedRows);
    fullStatus.put("issueCount", issueCount);
    fullStatus.put("checkedAt", Instant.now().toString());
    fullStatus.put("failures", List.copyOf(failures));
    putCutoverStatus(fullStatus);
    status = Map.copyOf(fullStatus);

    jdbcTemplate.update("UPDATE money_migration_runs SET state = ?, finished_at = CURRENT_TIMESTAMP, checked_rows = ?, issue_count = ? WHERE run_id = ?",
        verified ? "VERIFIED" : "BLOCKED", checkedRows, issueCount, runId);
    if (!verified) {
      throw new IllegalStateException("Money migration verification failed: " + String.join("; ", failures));
    }
  }

  @Scheduled(cron = "${app.money-migration.full-check-cron:0 0 2 * * *}", zone = "${app.time-zone:Europe/Stockholm}")
  public void scheduledFullVerification() {
    try {
      if (!verifyOnStartup) {
        throw new IllegalStateException("Money migration verification is disabled");
      }
      ensureControlTables();
      runFullVerification();
    } catch (RuntimeException exception) {
      Map<String, Object> failedStatus = new LinkedHashMap<>(status);
      failedStatus.put("state", "blocked");
      failedStatus.put("verified", false);
      failedStatus.put("readyForAuthoritativeCutover", false);
      failedStatus.put("verificationError", exception.getClass().getSimpleName());
      status = Map.copyOf(failedStatus);
      log.error("Scheduled money migration verification failed closed.", exception);
    }
  }

  public Map<String, Object> status() {
    return status;
  }

  private boolean hasFreshVerifiedCheckpoint() {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM money_migration_runs WHERE migration_key = ? AND mode = 'full' "
            + "AND state = 'VERIFIED' AND issue_count = 0 AND finished_at >= "
            + "CURRENT_TIMESTAMP - (? * INTERVAL '1 hour')",
        Long.class, MIGRATION_KEY, maxFullCheckAgeHours);
    return count != null && count > 0 && triggerCount() == REQUIRED_TRIGGER_NAMES.size();
  }

  private void verifyCheckpoint() {
    Map<String, Object> checkpoint = jdbcTemplate.queryForMap(
        "SELECT run_id::text AS run_id, checked_rows, issue_count, finished_at "
            + "FROM money_migration_runs WHERE migration_key = ? AND mode = 'full' "
            + "AND state = 'VERIFIED' AND issue_count = 0 ORDER BY finished_at DESC LIMIT 1",
        MIGRATION_KEY);
    Map<String, Object> checkpointStatus = new LinkedHashMap<>();
    checkpointStatus.put("state", "verified-checkpoint");
    checkpointStatus.put("currency", CURRENCY);
    checkpointStatus.put("minorUnitExponent", minorUnitExponent());
    checkpointStatus.put("verified", true);
    checkpointStatus.put("runId", String.valueOf(checkpoint.get("run_id")));
    checkpointStatus.put("checkedRows", ((Number) checkpoint.get("checked_rows")).longValue());
    checkpointStatus.put("issueCount", ((Number) checkpoint.get("issue_count")).longValue());
    checkpointStatus.put("checkedAt", String.valueOf(checkpoint.get("finished_at")));
    checkpointStatus.put("checkpointMaxAgeHours", maxFullCheckAgeHours);
    putCutoverStatus(checkpointStatus);
    status = Map.copyOf(checkpointStatus);
  }

  private long triggerCount() {
    String sqlNames = String.join(",", REQUIRED_TRIGGER_NAMES.stream().map(name -> "'" + name + "'").toList());
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM pg_trigger WHERE NOT tgisinternal AND tgname IN (" + sqlNames + ")",
        Long.class);
    return count == null ? 0L : count;
  }

  private void putCutoverStatus(Map<String, Object> target) {
    int streak = zeroIssueFullCheckStreak();
    boolean restoreVerified = verifiedBackupRestore();
    boolean eligible = streak >= 7 && restoreVerified && apiMinorUnitAuthoritative;
    target.put("zeroIssueFullCheckStreak", streak);
    target.put("requiredZeroIssueFullChecks", 7);
    target.put("verifiedBackupRestore", restoreVerified);
    target.put("apiMinorUnitAuthoritative", apiMinorUnitAuthoritative);
    target.put("cutoverEligible", eligible);
    target.put("readyForAuthoritativeCutover", eligible);
    target.put("cutoverBlockers", cutoverBlockers(streak, restoreVerified));
  }

  private int zeroIssueFullCheckStreak() {
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          "SELECT state, issue_count FROM money_migration_runs WHERE migration_key = ? "
              + "AND mode = 'full' ORDER BY finished_at DESC NULLS LAST LIMIT 7",
          MIGRATION_KEY);
      int streak = 0;
      for (Map<String, Object> row : rows) {
        Object state = row.get("state");
        Object issueCount = row.get("issue_count");
        if (!"VERIFIED".equals(state) || !(issueCount instanceof Number number) || number.longValue() != 0L) {
          break;
        }
        streak++;
      }
      return streak;
    } catch (RuntimeException exception) {
      log.warn("Could not read money migration check streak; blocking cutover.", exception);
      return 0;
    }
  }

  private boolean verifiedBackupRestore() {
    try {
      Long count = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM money_migration_ledger WHERE migration_key = 'backup-restore-v1' "
              + "AND target_unit = 'verified-restore'",
          Long.class);
      return count != null && count > 0;
    } catch (RuntimeException exception) {
      log.warn("Could not read verified backup restore evidence; blocking cutover.", exception);
      return false;
    }
  }

  private List<String> cutoverBlockers(int streak, boolean restoreVerified) {
    List<String> blockers = new ArrayList<>();
    if (streak < 7) {
      blockers.add("seven consecutive zero-issue full checks are required");
    }
    if (!restoreVerified) {
      blockers.add("verified backup restore rehearsal is required");
    }
    if (!apiMinorUnitAuthoritative) {
      blockers.add("API and booking flows are not yet minor-unit authoritative");
    }
    return List.copyOf(blockers);
  }

  private long count(String sql) {
    Long result = jdbcTemplate.queryForObject(sql, Long.class);
    return result == null ? 0L : result;
  }

  private int minorUnitExponent() {
    Integer exponent = jdbcTemplate.queryForObject(
        "SELECT minor_unit_exponent FROM currencies WHERE currency_code = '" + CURRENCY + "'", Integer.class);
    if (exponent == null || exponent < 0 || exponent > 9) {
      throw new IllegalStateException("No valid minor-unit exponent configured for " + CURRENCY);
    }
    return exponent;
  }

  private void recordCheck(UUID runId, String name, long rows, long issues, String details) {
    jdbcTemplate.update("INSERT INTO money_migration_checks "
        + "(migration_key, check_name, run_id, status, row_count, issue_count, details, checked_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
        + "ON CONFLICT (migration_key, check_name) DO UPDATE SET status = EXCLUDED.status, "
        + "row_count = EXCLUDED.row_count, issue_count = EXCLUDED.issue_count, "
        + "details = EXCLUDED.details, run_id = EXCLUDED.run_id, checked_at = EXCLUDED.checked_at",
        MIGRATION_KEY, name, runId, issues == 0 ? "PASS" : "FAIL", rows, issues, details);
  }

  private void recordIssues(UUID runId, MoneyColumn column, String issueCode, String details, long factor) {
    String predicate = switch (issueCode) {
      case "MISSING_MINOR" -> column.minorColumn() + " IS NULL";
      case "LEGACY_MISSING" -> column.legacyColumn() + " IS NULL AND " + column.minorColumn() + " IS NOT NULL";
      case "ROUNDING_REQUIRED" -> "CAST(" + column.legacyColumn() + " AS numeric) * " + factor
          + " <> ROUND(CAST(" + column.legacyColumn() + " AS numeric) * " + factor + ", 0)";
      default -> column.minorColumn() + " IS NOT NULL AND " + column.minorColumn() + " <> ROUND(CAST("
          + column.legacyColumn() + " AS numeric) * " + factor + ", 0)";
    };
    String sourcePredicate = "LEGACY_MISSING".equals(issueCode)
        ? predicate
        : column.legacyColumn() + " IS NOT NULL AND (" + predicate + ")";
    jdbcTemplate.update("INSERT INTO money_migration_issues "
        + "(run_id, migration_key, entity_table, entity_id, field_name, issue_code, source_value, target_value, details) "
        + "SELECT ?, ?, ?, CAST(id AS varchar), ?, ?, CAST(" + column.legacyColumn() + " AS text), "
        + "CAST(" + column.minorColumn() + " AS text), ? FROM " + column.table() + " WHERE "
        + sourcePredicate,
        runId, MIGRATION_KEY, column.table(), column.legacyColumn(), issueCode, details);
  }

  private void recordIssues(UUID runId, String table, String field, String issueCode, long expectedCount) {
    if (expectedCount == 0) {
      return;
    }
    jdbcTemplate.update("INSERT INTO money_migration_issues "
        + "(run_id, migration_key, entity_table, entity_id, field_name, issue_code, source_value, target_value, details) "
        + "SELECT ?, ?, ?, CAST(id AS varchar), ?, ?, NULL, currency_code, ? FROM " + table
        + " WHERE currency_code IS NULL OR currency_code <> ? "
        , runId, MIGRATION_KEY, table, field, issueCode, "currency_code must be SEK", CURRENCY);
  }

  private record MoneyColumn(String table, String legacyColumn, String minorColumn) {
  }
}
