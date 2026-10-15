package se.cloudshop.config;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
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
  // Deliberately remains false until every API, write path and report uses minor units.
  private static final boolean API_MINOR_UNIT_AUTHORITATIVE = false;
  private static final List<RequiredTrigger> REQUIRED_TRIGGERS = List.of(
      new RequiredTrigger("products", "products_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("products", "a_products_money_integrity_guard", "guard_product_money_integrity"),
      new RequiredTrigger("customer_orders", "customer_orders_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("customer_orders", "a_customer_orders_money_integrity_guard", "guard_customer_order_money_integrity"),
      new RequiredTrigger("invoice_payments", "invoice_payments_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("invoice_payments", "a_invoice_payment_money_integrity_guard", "guard_customer_payment_money_integrity"),
      new RequiredTrigger("supplier_invoices", "supplier_invoices_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("supplier_invoices", "a_supplier_invoices_money_integrity_guard", "guard_operational_money_integrity"),
      new RequiredTrigger("supplier_invoice_payments", "supplier_invoice_payments_money_shadow_sync", "sync_supplier_payment_shadow_fields"),
      new RequiredTrigger("expenses", "expenses_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("expenses", "a_expenses_money_integrity_guard", "guard_operational_money_integrity"),
      new RequiredTrigger("card_purchases", "card_purchases_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("card_purchases", "a_card_purchases_money_integrity_guard", "guard_operational_money_integrity"),
      new RequiredTrigger("stripe_payouts", "stripe_payouts_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("stripe_payouts", "a_stripe_payouts_money_integrity_guard", "guard_operational_money_integrity"),
      new RequiredTrigger("bank_reconciliation_entries", "bank_reconciliation_entries_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("bank_reconciliation_entries", "a_bank_reconciliation_entries_money_integrity_guard", "guard_operational_money_integrity"),
      new RequiredTrigger("vat_filings", "vat_filings_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("vat_filings", "a_vat_filings_money_integrity_guard", "guard_operational_money_integrity"),
      new RequiredTrigger("journal_entries", "journal_entries_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("journal_entries", "a_journal_money_integrity_guard", "guard_journal_money_integrity"),
      new RequiredTrigger("journal_entries", "b_journal_posted_immutable_guard", "protect_posted_journal_entry"),
      new RequiredTrigger("journal_entries", "journal_voucher_reservation_guard", "reserve_journal_voucher", 7, false),
      new RequiredTrigger("journal_entries", "journal_voucher_balance_guard", "require_balanced_journal_voucher", 5, true),
      new RequiredTrigger("journal_voucher_registry", "journal_voucher_registry_immutable", "protect_journal_voucher_registry", 27, false),
      new RequiredTrigger("invoice_originals", "invoice_originals_immutable", "protect_invoice_original", 27, false),
      new RequiredTrigger("audit_events", "audit_events_immutable", "protect_audit_event", 27, false),
      new RequiredTrigger("audit_events", "audit_events_no_truncate", "protect_audit_event", 34, false),
      new RequiredTrigger("owner_transactions", "owner_transactions_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("owner_transactions", "a_owner_transactions_money_integrity_guard", "guard_operational_money_integrity"),
      new RequiredTrigger("audit_events", "audit_events_money_shadow_sync", "sync_money_shadow_fields"),
      new RequiredTrigger("audit_events", "a_audit_events_money_integrity_guard", "guard_operational_money_integrity")
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
  private static final List<String> MONEY_TABLES = List.of(
      "products", "customer_orders", "invoice_payments", "supplier_invoices",
      "expenses", "card_purchases", "stripe_payouts", "bank_reconciliation_entries",
      "vat_filings", "supplier_invoice_payments", "journal_entries", "owner_transactions",
      "audit_events");
  private static final List<String> REQUIRED_CHECK_NAMES = requiredCheckNames();

  private final JdbcTemplate jdbcTemplate;
  private final boolean verifyOnStartup;
  private final long maxFullCheckAgeHours;
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
    this.jdbcTemplate = jdbcTemplate;
    this.verifyOnStartup = verifyOnStartup;
    if (maxFullCheckAgeHours <= 0) {
      throw new IllegalArgumentException("app.money-migration.max-full-check-age-hours must be positive");
    }
    this.maxFullCheckAgeHours = maxFullCheckAgeHours;
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

    if (hasFreshVerifiedCheckpoint()) {
      verifyCheckpoint();
      return;
    }

    runFullVerification();
  }

  private void runFullVerification() {
    status = Map.of(
        "state", "verifying",
        "currency", CURRENCY,
        "verified", false,
        "readyForAuthoritativeCutover", false
    );
    UUID runId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO money_migration_runs (run_id, migration_key, mode, state) VALUES (?, ?, 'full', 'RUNNING')",
        runId, MIGRATION_KEY);
    int minorUnitExponent = minorUnitExponent();
    long minorUnitFactor = BigInteger.TEN.pow(minorUnitExponent).longValueExact();
    List<String> failures = new ArrayList<>();
    long issueCount = 0L;
    long checkedRows = 0L;
    long fractionalLegacyIssues = 0L;

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
      long fractionalLegacy = API_MINOR_UNIT_AUTHORITATIVE ? 0L
          : count("SELECT COUNT(*) FROM " + column.table() + " WHERE " + column.legacyColumn()
              + " IS NOT NULL AND CAST(" + column.legacyColumn() + " AS numeric) "
              + "<> TRUNC(CAST(" + column.legacyColumn() + " AS numeric))");
      long rows = count("SELECT COUNT(*) FROM " + column.table());
      checkedRows = Math.addExact(checkedRows, rows);
      fractionalLegacyIssues = Math.addExact(fractionalLegacyIssues, fractionalLegacy);
      long totalIssues = Math.addExact(missingMinor,
          Math.addExact(orphanMinor, Math.addExact(roundingRequired, Math.addExact(mismatch, fractionalLegacy))));
      issueCount = Math.addExact(issueCount, totalIssues);
      recordCheck(runId, "shadow:" + column.table() + "." + column.legacyColumn(), rows,
          totalIssues, "legacy and bigint shadow must exist together and match without implicit truncation");
      recordIssues(runId, column, "MISSING_MINOR", column.minorColumn() + " is null", minorUnitFactor);
      recordIssues(runId, column, "LEGACY_MISSING", column.legacyColumn() + " is null while the shadow exists", minorUnitFactor);
      recordIssues(runId, column, "ROUNDING_REQUIRED", "legacy value requires explicit minor-unit rounding", minorUnitFactor);
      recordIssues(runId, column, "MINOR_MISMATCH", "legacy value * " + minorUnitFactor + " differs from bigint shadow", minorUnitFactor);
      if (fractionalLegacy > 0) {
        recordIssues(runId, column, "FRACTIONAL_LEGACY", "legacy value has oren while the API still reads whole kronor", minorUnitFactor);
      }
      if (totalIssues > 0) {
        failures.add(column.table() + "." + column.legacyColumn() + " has "
            + totalIssues + " non-exact row(s)");
      }
    }

    recordCheck(runId, "legacy:whole-krona", checkedRows, fractionalLegacyIssues,
        "all legacy money columns must contain whole kronor until API and booking flows use minor units");

    for (String table : MONEY_TABLES) {
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

    long missingVoucherNumbers = count("SELECT COUNT(*) FROM journal_entries "
        + "WHERE voucher_number IS NULL OR btrim(voucher_number) = ''");
    recordCheck(runId, "journal:voucher-number", count("SELECT COUNT(*) FROM journal_entries"),
        missingVoucherNumbers, "every journal row must have a voucher number");
    if (missingVoucherNumbers > 0) {
      issueCount = Math.addExact(issueCount, missingVoucherNumbers);
      failures.add("journal_entries has " + missingVoucherNumbers + " row(s) without a voucher number");
    }

    long missingVoucherDates = count("SELECT COUNT(*) FROM journal_entries WHERE voucher_date IS NULL");
    recordCheck(runId, "journal:voucher-date", count("SELECT COUNT(*) FROM journal_entries"),
        missingVoucherDates, "historical voucher dates must be documented, never replaced with today's date");
    if (missingVoucherDates > 0) {
      issueCount = Math.addExact(issueCount, missingVoucherDates);
      failures.add("journal_entries has " + missingVoucherDates + " row(s) without a voucher date");
    }

    long missingAccounts = count("SELECT COUNT(*) FROM journal_entries "
        + "WHERE account_number IS NULL OR btrim(account_number) = ''");
    recordCheck(runId, "journal:account-number", count("SELECT COUNT(*) FROM journal_entries"),
        missingAccounts, "every journal row must have an account number");
    if (missingAccounts > 0) {
      issueCount = Math.addExact(issueCount, missingAccounts);
      failures.add("journal_entries has " + missingAccounts + " row(s) without an account number");
    }

    long mixedVoucherDates = count("SELECT COUNT(*) FROM (SELECT voucher_number FROM journal_entries "
        + "WHERE voucher_number IS NOT NULL AND btrim(voucher_number) <> '' "
        + "GROUP BY voucher_number HAVING COUNT(DISTINCT voucher_date) > 1) mixed_dates");
    recordCheck(runId, "journal:voucher-date-consistency",
        count("SELECT COUNT(DISTINCT voucher_number) FROM journal_entries"), mixedVoucherDates,
        "all rows of a voucher must have the same accounting date");
    if (mixedVoucherDates > 0) {
      issueCount = Math.addExact(issueCount, mixedVoucherDates);
      failures.add("journal_entries has " + mixedVoucherDates + " voucher(s) with inconsistent dates");
    }

    long unsealedVouchers = count("SELECT COUNT(*) FROM journal_voucher_registry WHERE NOT sealed");
    recordCheck(runId, "journal:voucher-seal", count("SELECT COUNT(*) FROM journal_voucher_registry"),
        unsealedVouchers, "committed vouchers must be sealed");
    if (unsealedVouchers > 0) {
      issueCount = Math.addExact(issueCount, unsealedVouchers);
      failures.add("journal_voucher_registry has " + unsealedVouchers + " unsealed voucher(s)");
    }
    long missingRegistry = count("SELECT COUNT(*) FROM (SELECT DISTINCT voucher_number FROM journal_entries "
        + "WHERE voucher_number IS NOT NULL AND btrim(voucher_number) <> '') vouchers "
        + "LEFT JOIN journal_voucher_registry registry ON registry.voucher_number = vouchers.voucher_number "
        + "WHERE registry.voucher_number IS NULL");
    recordCheck(runId, "journal:voucher-registry", count("SELECT COUNT(DISTINCT voucher_number) FROM journal_entries"),
        missingRegistry, "every posted voucher must have a registry row");
    if (missingRegistry > 0) {
      issueCount = Math.addExact(issueCount, missingRegistry);
      failures.add("journal_entries has " + missingRegistry + " voucher(s) missing from the registry");
    }
    long orphanRegistry = count("SELECT COUNT(*) FROM journal_voucher_registry registry "
        + "WHERE NOT EXISTS (SELECT 1 FROM journal_entries entry "
        + "WHERE entry.voucher_number = registry.voucher_number)");
    recordCheck(runId, "journal:orphan-voucher-registry", count("SELECT COUNT(*) FROM journal_voucher_registry"),
        orphanRegistry, "every sealed voucher number must have posted journal rows");
    if (orphanRegistry > 0) {
      issueCount = Math.addExact(issueCount, orphanRegistry);
      failures.add("journal_voucher_registry has " + orphanRegistry + " orphaned voucher(s)");
    }

    long invalidJournalRows = count("SELECT COUNT(*) FROM journal_entries "
        + "WHERE debit IS NULL OR credit IS NULL OR debit < 0 OR credit < 0 "
        + "OR (debit > 0 AND credit > 0)");
    recordCheck(runId, "journal:row-shape", count("SELECT COUNT(*) FROM journal_entries"),
        invalidJournalRows, "journal rows need nonnegative debit and credit on separate sides");
    if (invalidJournalRows > 0) {
      issueCount = Math.addExact(issueCount, invalidJournalRows);
      failures.add("journal_entries has " + invalidJournalRows + " invalid debit/credit row(s)");
    }

    long unbalancedVouchers = count("SELECT COUNT(*) FROM (SELECT voucher_number FROM journal_entries "
        + "WHERE voucher_number IS NOT NULL AND voucher_number <> '' GROUP BY voucher_number "
        + "HAVING COALESCE(SUM(debit_minor), 0) <> COALESCE(SUM(credit_minor), 0)) balanced");
    recordCheck(runId, "journal:voucher-balance", count("SELECT COUNT(DISTINCT voucher_number) FROM journal_entries "
        + "WHERE voucher_number IS NOT NULL AND voucher_number <> ''"), unbalancedVouchers,
        "debit_minor must equal credit_minor per voucher");
    if (unbalancedVouchers > 0) {
      issueCount = Math.addExact(issueCount, unbalancedVouchers);
      failures.add("journal_entries has " + unbalancedVouchers + " unbalanced voucher(s)");
    }

    long validMoneyTriggers = triggerCount();
    long invalidMoneyTriggers = REQUIRED_TRIGGERS.size() - validMoneyTriggers;
    issueCount = Math.addExact(issueCount, invalidMoneyTriggers);
    recordCheck(runId, "database:money-shadow-triggers", REQUIRED_TRIGGERS.size(), invalidMoneyTriggers,
        "all required money and voucher triggers must be enabled with the expected events and deferral");
    recordCheck(runId, "database:operational-money-guard", REQUIRED_TRIGGERS.size(), invalidMoneyTriggers,
        "operational money tables must reject contradictory legacy and minor-unit values before shadow synchronization");
    if (invalidMoneyTriggers > 0) {
      failures.add("database has " + invalidMoneyTriggers + " missing, disabled, or misconfigured money shadow trigger(s)");
    }

    long supplierGuard = count("SELECT COUNT(*) FROM money_migration_ledger "
        + "WHERE migration_key = 'supplier-payment-exact-v1'");
    recordCheck(runId, "database:supplier-payment-exact-guard", 1, supplierGuard == 1 ? 0 : 1,
        "supplier payment trigger must reject fractional and contradictory legacy/minor amounts");
    if (supplierGuard != 1) {
      issueCount = Math.addExact(issueCount, 1);
      failures.add("supplier payment exact-money guard is not installed");
    }

    long customerGuard = count("SELECT COUNT(*) FROM money_migration_ledger "
        + "WHERE migration_key = 'customer-payment-exact-v1'");
    recordCheck(runId, "database:customer-payment-exact-guard", 1, customerGuard == 1 ? 0 : 1,
        "customer payment trigger must reject contradictory legacy/minor amounts");
    if (customerGuard != 1) {
      issueCount = Math.addExact(issueCount, 1);
      failures.add("customer payment exact-money guard is not installed");
    }

    long accountMismatches = count("SELECT COUNT(*) FROM (SELECT account_number FROM journal_entries "
        + "WHERE account_number IS NOT NULL GROUP BY account_number "
        + "HAVING COALESCE(SUM(CAST(debit AS numeric) * " + minorUnitFactor + " - CAST(credit AS numeric) * " + minorUnitFactor + "), 0) "
        + "<> COALESCE(SUM(debit_minor - credit_minor), 0)) account_totals");
    recordCheck(runId, "journal:account-total-reconciliation", count("SELECT COUNT(DISTINCT account_number) "
        + "FROM journal_entries WHERE account_number IS NOT NULL"), accountMismatches,
        "legacy account totals * 100 must equal minor account totals");
    if (accountMismatches > 0) {
      issueCount = Math.addExact(issueCount, accountMismatches);
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
    jdbcTemplate.update("UPDATE money_migration_runs SET state = ?, finished_at = CURRENT_TIMESTAMP, checked_rows = ?, issue_count = ? WHERE run_id = ?",
        verified ? "VERIFIED" : "BLOCKED", checkedRows, issueCount, runId);
    putCutoverStatus(fullStatus);
    status = Map.copyOf(fullStatus);
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
    Map<String, Object> current = status;
    if (!Boolean.TRUE.equals(current.get("verified"))) {
      return current;
    }
    try {
      Instant checkedAt = Instant.parse((String) current.get("checkedAt"));
      if (!Instant.now().isAfter(checkedAt.plus(maxFullCheckAgeHours, ChronoUnit.HOURS))) {
        return current;
      }
    } catch (RuntimeException exception) {
      log.warn("Money migration checkpoint timestamp is unavailable; blocking readiness.", exception);
    }
    Map<String, Object> expired = new LinkedHashMap<>(current);
    expired.put("state", "stale-checkpoint");
    expired.put("verified", false);
    expired.put("readyForAuthoritativeCutover", false);
    expired.put("cutoverEligible", false);
    return Map.copyOf(expired);
  }

  private boolean hasFreshVerifiedCheckpoint() {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM (SELECT state, issue_count, finished_at FROM money_migration_runs "
            + "WHERE migration_key = ? AND mode = 'full' ORDER BY started_at DESC LIMIT 1) latest_run "
            + "WHERE state = 'VERIFIED' AND issue_count = 0 AND finished_at >= "
            + "CURRENT_TIMESTAMP - (? * INTERVAL '1 hour') "
            + "AND finished_at >= (SELECT applied_at FROM money_migration_ledger "
            + "WHERE migration_key = 'journal-money-guard-v1') "
            + "AND finished_at >= (SELECT applied_at FROM money_migration_ledger "
            + "WHERE migration_key = 'journal-posted-immutable-v1') "
            + "AND finished_at >= (SELECT applied_at FROM money_migration_ledger "
            + "WHERE migration_key = 'journal-voucher-balance-v1') "
            + "AND finished_at >= (SELECT applied_at FROM money_migration_ledger "
            + "WHERE migration_key = 'journal-voucher-seal-v1') "
            + "AND finished_at >= (SELECT applied_at FROM money_migration_ledger "
            + "WHERE migration_key = 'supplier-payment-exact-v1') "
            + "AND finished_at >= (SELECT applied_at FROM money_migration_ledger "
            + "WHERE migration_key = 'customer-payment-exact-v1') "
            + "AND finished_at >= (SELECT applied_at FROM money_migration_ledger "
            + "WHERE migration_key = 'customer-order-exact-v1') "
            + "AND finished_at >= (SELECT applied_at FROM money_migration_ledger "
            + "WHERE migration_key = 'product-money-guard-v1')",
        Long.class, MIGRATION_KEY, maxFullCheckAgeHours);
    return count != null && count > 0 && triggerCount() == REQUIRED_TRIGGERS.size()
        && latestFullRunHasRequiredChecks();
  }

  private boolean latestFullRunHasRequiredChecks() {
    String expected = String.join(",", REQUIRED_CHECK_NAMES.stream()
        .map(name -> "('" + name + "')")
        .toList());
    Long missing = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM (VALUES " + expected + ") AS required(check_name) "
            + "CROSS JOIN (SELECT run_id FROM money_migration_runs WHERE migration_key = ? AND mode = 'full' "
            + "ORDER BY started_at DESC LIMIT 1) latest_run "
            + "LEFT JOIN money_migration_checks check_row ON check_row.run_id = latest_run.run_id "
            + "AND check_row.migration_key = ? AND check_row.check_name = required.check_name "
            + "AND check_row.status = 'PASS' AND check_row.issue_count = 0 "
            + "WHERE check_row.check_name IS NULL",
        Long.class, MIGRATION_KEY, MIGRATION_KEY);
    return missing != null && missing == 0;
  }

  private static List<String> requiredCheckNames() {
    List<String> names = new ArrayList<>();
    for (MoneyColumn column : MONEY_COLUMNS) {
      names.add("shadow:" + column.table() + "." + column.legacyColumn());
    }
    names.add("legacy:whole-krona");
    for (String table : MONEY_TABLES) {
      names.add("currency:" + table);
    }
    names.addAll(List.of(
        "journal:voucher-number", "journal:voucher-date", "journal:account-number",
        "journal:voucher-date-consistency", "journal:voucher-seal", "journal:voucher-registry",
        "journal:orphan-voucher-registry", "journal:row-shape", "journal:voucher-balance",
        "database:money-shadow-triggers", "database:operational-money-guard", "database:supplier-payment-exact-guard",
        "database:customer-payment-exact-guard", "journal:account-total-reconciliation"));
    return List.copyOf(names);
  }

  private void verifyCheckpoint() {
    Map<String, Object> checkpoint = jdbcTemplate.queryForMap(
        "SELECT run_id::text AS run_id, checked_rows, issue_count, finished_at "
            + "FROM money_migration_runs WHERE migration_key = ? AND mode = 'full' "
            + "AND state = 'VERIFIED' AND issue_count = 0 ORDER BY started_at DESC LIMIT 1",
        MIGRATION_KEY);
    Map<String, Object> checkpointStatus = new LinkedHashMap<>();
    checkpointStatus.put("state", "verified-checkpoint");
    checkpointStatus.put("currency", CURRENCY);
    checkpointStatus.put("minorUnitExponent", minorUnitExponent());
    checkpointStatus.put("verified", true);
    checkpointStatus.put("runId", String.valueOf(checkpoint.get("run_id")));
    checkpointStatus.put("checkedRows", ((Number) checkpoint.get("checked_rows")).longValue());
    checkpointStatus.put("issueCount", ((Number) checkpoint.get("issue_count")).longValue());
    if (!(checkpoint.get("finished_at") instanceof Timestamp finishedAt)) {
      throw new IllegalStateException("Money migration checkpoint timestamp is unavailable");
    }
    checkpointStatus.put("checkedAt", finishedAt.toInstant().toString());
    checkpointStatus.put("checkpointMaxAgeHours", maxFullCheckAgeHours);
    putCutoverStatus(checkpointStatus);
    status = Map.copyOf(checkpointStatus);
  }

  private long triggerCount() {
    String requirements = String.join(",", REQUIRED_TRIGGERS.stream()
        .map(trigger -> {
          List<String> arguments = triggerArguments(trigger);
          String argumentHex = arguments.isEmpty() ? "" : HexFormat.of().formatHex(
              (String.join("\0", arguments) + "\0").getBytes(StandardCharsets.UTF_8));
          return "('" + trigger.table() + "','" + trigger.name() + "','" + trigger.function()
              + "'," + trigger.eventMask() + "," + trigger.deferred() + ","
              + arguments.size() + ",'" + argumentHex + "')";
        })
        .toList());
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM (VALUES " + requirements + ") AS expected(table_name, trigger_name, function_name, event_mask, is_deferred, argument_count, argument_hex) "
            + "JOIN pg_class relation ON relation.relname = expected.table_name "
            + "JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace "
            + "JOIN pg_trigger trigger_row ON trigger_row.tgrelid = relation.oid "
            + "AND trigger_row.tgname = expected.trigger_name AND NOT trigger_row.tgisinternal "
            + "JOIN pg_proc trigger_function ON trigger_function.oid = trigger_row.tgfoid "
            + "JOIN pg_namespace function_namespace ON function_namespace.oid = trigger_function.pronamespace "
            + "WHERE namespace.nspname = current_schema() "
            + "AND function_namespace.nspname = current_schema() "
            + "AND trigger_function.proname = expected.function_name "
            + "AND trigger_row.tgenabled IN ('O', 'A') "
            + "AND (trigger_row.tgtype & expected.event_mask) = expected.event_mask "
            + "AND trigger_row.tgnargs = expected.argument_count "
            + "AND encode(trigger_row.tgargs, 'hex') = expected.argument_hex "
            + "AND (NOT expected.is_deferred OR (trigger_row.tgdeferrable "
            + "AND trigger_row.tginitdeferred AND trigger_row.tgconstraint <> 0))",
        Long.class);
    return count == null ? 0L : count;
  }

  private static List<String> triggerArguments(RequiredTrigger trigger) {
    if (!"guard_operational_money_integrity".equals(trigger.function())
        && !"guard_customer_order_money_integrity".equals(trigger.function())) {
      return List.of();
    }
    List<String> arguments = new ArrayList<>();
    for (MoneyColumn column : MONEY_COLUMNS) {
      if (column.table().equals(trigger.table())) {
        arguments.add(column.legacyColumn());
        arguments.add(column.minorColumn());
      }
    }
    if (arguments.isEmpty()) {
      throw new IllegalStateException("Money guard has no field arguments: " + trigger.name());
    }
    return List.copyOf(arguments);
  }

  private void putCutoverStatus(Map<String, Object> target) {
    int streak = zeroIssueFullCheckStreak();
    boolean restoreVerified = verifiedBackupRestore();
    boolean eligible = streak >= 7 && restoreVerified && API_MINOR_UNIT_AUTHORITATIVE;
    target.put("zeroIssueFullCheckStreak", streak);
    target.put("requiredZeroIssueFullChecks", 7);
    target.put("verifiedBackupRestore", restoreVerified);
    target.put("apiMinorUnitAuthoritative", API_MINOR_UNIT_AUTHORITATIVE);
    target.put("cutoverEligible", eligible);
    target.put("readyForAuthoritativeCutover", eligible);
    target.put("cutoverBlockers", cutoverBlockers(streak, restoreVerified));
  }

  private int zeroIssueFullCheckStreak() {
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          "SELECT (COALESCE(finished_at, started_at) AT TIME ZONE 'UTC')::date AS check_day, "
              + "BOOL_AND(state = 'VERIFIED' AND issue_count = 0 AND finished_at IS NOT NULL) AS clean, "
              + "BOOL_OR(finished_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 hour')) AS fresh "
              + "FROM money_migration_runs WHERE migration_key = ? AND mode = 'full' "
              + "AND COALESCE(finished_at, started_at) >= (SELECT applied_at FROM money_migration_ledger "
              + "WHERE migration_key = 'journal-money-guard-v1') "
              + "AND COALESCE(finished_at, started_at) >= (SELECT applied_at FROM money_migration_ledger "
              + "WHERE migration_key = 'journal-posted-immutable-v1') "
              + "AND COALESCE(finished_at, started_at) >= (SELECT applied_at FROM money_migration_ledger "
              + "WHERE migration_key = 'journal-voucher-balance-v1') "
              + "AND COALESCE(finished_at, started_at) >= (SELECT applied_at FROM money_migration_ledger "
              + "WHERE migration_key = 'journal-voucher-seal-v1') "
              + "AND COALESCE(finished_at, started_at) >= (SELECT applied_at FROM money_migration_ledger "
              + "WHERE migration_key = 'supplier-payment-exact-v1') "
              + "AND COALESCE(finished_at, started_at) >= (SELECT applied_at FROM money_migration_ledger "
              + "WHERE migration_key = 'customer-payment-exact-v1') "
              + "AND COALESCE(finished_at, started_at) >= (SELECT applied_at FROM money_migration_ledger "
              + "WHERE migration_key = 'customer-order-exact-v1') "
              + "AND COALESCE(finished_at, started_at) >= (SELECT applied_at FROM money_migration_ledger "
              + "WHERE migration_key = 'product-money-guard-v1') "
              + "GROUP BY check_day ORDER BY check_day DESC LIMIT 7",
          maxFullCheckAgeHours, MIGRATION_KEY);
      if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("fresh"))) {
        return 0;
      }
      int streak = 0;
      LocalDate expectedDay = checkDay(rows.getFirst().get("check_day"));
      for (Map<String, Object> row : rows) {
        if (!Boolean.TRUE.equals(row.get("clean")) || !expectedDay.equals(checkDay(row.get("check_day")))) {
          break;
        }
        streak++;
        expectedDay = expectedDay.minusDays(1);
      }
      return streak;
    } catch (RuntimeException exception) {
      log.warn("Could not read money migration check streak; blocking cutover.", exception);
      return 0;
    }
  }

  private LocalDate checkDay(Object value) {
    if (value instanceof java.sql.Date date) return date.toLocalDate();
    if (value instanceof LocalDate day) return day;
    throw new IllegalStateException("Money migration check day is unavailable.");
  }

  private boolean verifiedBackupRestore() {
    try {
      Long count = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM money_migration_ledger restore "
              + "JOIN money_migration_ledger journal_guard ON journal_guard.migration_key = 'journal-money-guard-v1' "
              + "JOIN money_migration_ledger journal_immutable ON journal_immutable.migration_key = 'journal-posted-immutable-v1' "
              + "JOIN money_migration_ledger journal_balance ON journal_balance.migration_key = 'journal-voucher-balance-v1' "
              + "JOIN money_migration_ledger journal_seal ON journal_seal.migration_key = 'journal-voucher-seal-v1' "
              + "JOIN money_migration_ledger supplier_guard ON supplier_guard.migration_key = 'supplier-payment-exact-v1' "
              + "JOIN money_migration_ledger customer_guard ON customer_guard.migration_key = 'customer-payment-exact-v1' "
              + "JOIN money_migration_ledger order_guard ON order_guard.migration_key = 'customer-order-exact-v1' "
              + "JOIN money_migration_ledger product_guard ON product_guard.migration_key = 'product-money-guard-v1' "
              + "WHERE restore.migration_key = 'backup-restore-v1' "
              + "AND restore.target_unit = 'verified-restore' "
              + "AND restore.applied_at >= journal_guard.applied_at "
              + "AND restore.applied_at >= journal_immutable.applied_at "
              + "AND restore.applied_at >= journal_balance.applied_at "
              + "AND restore.applied_at >= journal_seal.applied_at "
              + "AND restore.applied_at >= supplier_guard.applied_at "
              + "AND restore.applied_at >= customer_guard.applied_at "
              + "AND restore.applied_at >= order_guard.applied_at "
              + "AND restore.applied_at >= product_guard.applied_at",
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
      blockers.add("seven consecutive UTC days with zero-issue full checks are required");
    }
    if (!restoreVerified) {
      blockers.add("verified backup restore rehearsal is required");
    }
    if (!API_MINOR_UNIT_AUTHORITATIVE) {
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
    if (exponent == null || exponent != 2) {
      throw new IllegalStateException("SEK must use a minor-unit exponent of 2");
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
      case "FRACTIONAL_LEGACY" -> "CAST(" + column.legacyColumn() + " AS numeric) "
          + "<> TRUNC(CAST(" + column.legacyColumn() + " AS numeric))";
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

  private record RequiredTrigger(String table, String name, String function, int eventMask, boolean deferred) {
    private RequiredTrigger(String table, String name, String function) {
      this(table, name, function, 23, false);
    }
  }
}
