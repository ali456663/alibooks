package se.cloudshop.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

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

  @Test
  void enabledPatchCreatesAndBackfillsCoreMinorUnitShadowColumns() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    List<String> statements = new ArrayList<>();
    doAnswer(invocation -> {
      statements.add(invocation.getArgument(0, String.class));
      return null;
    }).when(jdbcTemplate).execute(anyString());

    new DatabaseSchemaPatch(jdbcTemplate, true).run();

    verify(jdbcTemplate).execute(contains("products ADD COLUMN IF NOT EXISTS price_minor bigint"));
    verify(jdbcTemplate).execute(contains("customer_orders ADD COLUMN IF NOT EXISTS total_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS amount_minor bigint"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE UNIQUE INDEX IF NOT EXISTS invoice_payments_identity_unique ON invoice_payments"));
    verify(jdbcTemplate).execute(contains("supplier_invoices ADD COLUMN IF NOT EXISTS total_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS supplier_invoice_payments"));
    verify(jdbcTemplate).execute(contains("supplier_invoice_payments ADD COLUMN IF NOT EXISTS amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("supplier_invoice_payments_identity_unique"));
    verify(jdbcTemplate).execute(contains("CREATE TRIGGER a_invoice_payment_money_integrity_guard"));
    verify(jdbcTemplate).execute(contains("CREATE OR REPLACE FUNCTION guard_customer_payment_money_integrity()"));
    verify(jdbcTemplate).execute(contains("VALUES ('customer-payment-exact-v1'"));
    verify(jdbcTemplate).execute(contains("CREATE TRIGGER supplier_invoice_payments_money_shadow_sync"));
    String supplierGuard = statements.stream()
        .filter(statement -> statement.startsWith("CREATE OR REPLACE FUNCTION sync_supplier_payment_shadow_fields()"))
        .findFirst().orElseThrow();
    org.assertj.core.api.Assertions.assertThat(supplierGuard)
        .contains("OLD.amount_minor IS DISTINCT FROM CAST(OLD.amount AS numeric) * 100")
        .contains("NEW.amount_minor IS NOT NULL AND NEW.amount_minor IS DISTINCT FROM CAST(NEW.amount AS numeric) * 100");
    org.assertj.core.api.Assertions.assertThat(statements)
        .filteredOn(statement -> statement.startsWith("UPDATE supplier_invoice_payments SET amount_minor ="))
        .singleElement()
        .asString()
        .contains("CAST(amount AS numeric) = TRUNC(CAST(amount AS numeric))");
    verify(jdbcTemplate).execute(contains("expenses ADD COLUMN IF NOT EXISTS total_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("card_purchases ADD COLUMN IF NOT EXISTS total_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS bank_reconciliation_entries_bank_row_id_idx"));
    verify(jdbcTemplate).execute(contains("stripe_payouts ADD COLUMN IF NOT EXISTS net_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay_minor bigint"));
    verify(jdbcTemplate).execute(contains("journal_entries ADD COLUMN IF NOT EXISTS debit_minor bigint"));
    verify(jdbcTemplate, times(1)).execute(contains("app_users ADD COLUMN IF NOT EXISTS auth_version bigint NOT NULL DEFAULT 0"));
    verify(jdbcTemplate).execute(contains("journal_entries ADD COLUMN IF NOT EXISTS credit_minor bigint"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS currencies"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE OR REPLACE FUNCTION sync_money_shadow_fields"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER a_customer_orders_money_integrity_guard"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER a_products_money_integrity_guard"));
    verify(jdbcTemplate, times(1)).execute(contains("VALUES ('product-money-guard-v1', 'whole-krona', 'guarded-shadow')"));
    verify(jdbcTemplate, times(1)).execute(contains("VALUES ('customer-order-exact-v1', 'whole-krona', 'guarded-shadow')"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER journal_entries_money_shadow_sync"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER a_journal_money_integrity_guard"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER b_journal_posted_immutable_guard"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE CONSTRAINT TRIGGER journal_voucher_balance_guard"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS journal_voucher_registry"));
    verify(jdbcTemplate, times(1)).execute(contains("AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-voucher-seal-v1')"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER journal_voucher_reservation_guard"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER journal_voucher_registry_immutable"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER invoice_originals_immutable BEFORE UPDATE OR DELETE"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE"));
    verify(jdbcTemplate, times(1)).execute(contains("ALTER TABLE journal_entries ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP"));
    verify(jdbcTemplate, times(1)).execute(contains("ADD CONSTRAINT journal_entry_created_at_present CHECK (created_at IS NOT NULL) NOT VALID"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS money_migration_ledger"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS money_migration_issues"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS money_migration_runs"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS money_migration_checks"));
    verify(jdbcTemplate, times(1)).execute(contains("ALTER TABLE money_migration_issues ADD COLUMN IF NOT EXISTS run_id"));
    verify(jdbcTemplate, times(1)).execute(contains("ALTER TABLE money_migration_checks ADD COLUMN IF NOT EXISTS run_id"));
    verify(jdbcTemplate, times(16)).execute(contains("INSERT INTO money_migration_ledger"));
    org.assertj.core.api.Assertions.assertThat(statements)
        .filteredOn(statement -> statement.startsWith("UPDATE products SET ")
            || statement.startsWith("UPDATE customer_orders SET "))
        .hasSize(22)
        .allMatch(statement -> statement.contains("migration_key = 'core-invoice-shadow-v1'"));
    int ledgerTable = statements.indexOf(statements.stream()
        .filter(statement -> statement.startsWith("CREATE TABLE IF NOT EXISTS money_migration_ledger"))
        .findFirst().orElseThrow());
    int legacyOrderNormalization = statements.indexOf(statements.stream()
        .filter(statement -> statement.startsWith("UPDATE customer_orders SET ordinary_price = net_amount"))
        .findFirst().orElseThrow());
    int orderShadowBackfill = statements.indexOf(statements.stream()
        .filter(statement -> statement.startsWith("UPDATE customer_orders SET ordinary_price_minor ="))
        .findFirst().orElseThrow());
    org.assertj.core.api.Assertions.assertThat(ledgerTable).isLessThan(legacyOrderNormalization);
    org.assertj.core.api.Assertions.assertThat(legacyOrderNormalization).isLessThan(orderShadowBackfill);
    org.assertj.core.api.Assertions.assertThat(statements)
        .filteredOn(statement -> statement.startsWith("UPDATE supplier_invoices SET total_amount_minor ="))
        .singleElement()
        .satisfies(statement -> org.assertj.core.api.Assertions.assertThat(statement)
            .contains("CAST(CAST(total_amount AS numeric) * 100 AS bigint)")
            .doesNotContain("ROUND("));
    org.assertj.core.api.Assertions.assertThat(statements)
        .filteredOn(statement -> statement.startsWith("UPDATE journal_entries SET debit_minor ="))
        .allMatch(statement -> statement.contains("NOT EXISTS (SELECT 1 FROM money_migration_ledger"));
    org.assertj.core.api.Assertions.assertThat(statements)
        .filteredOn(statement -> statement.startsWith("UPDATE invoice_payments SET currency_code =")
            || statement.startsWith("UPDATE invoice_payments SET amount_minor ="))
        .hasSize(2)
        .allMatch(statement -> statement.contains("migration_key = 'customer-payment-exact-v1'"));
    org.assertj.core.api.Assertions.assertThat(statements)
        .noneMatch(statement -> statement.startsWith("UPDATE journal_entries SET voucher_date ="));
    var oneTimeMarkers = java.util.Map.of(
        "supplier_invoices", "supplier-invoice-shadow-v1",
        "supplier_invoice_payments", "supplier-invoice-shadow-v1",
        "expenses", "expense-shadow-v1",
        "card_purchases", "card-purchase-shadow-v1",
        "bank_reconciliation_entries", "bank-reconciliation-shadow-v1",
        "stripe_payouts", "stripe-payout-shadow-v1",
        "vat_filings", "vat-filing-shadow-v1",
        "owner_transactions", "journal-entry-shadow-v1",
        "audit_events", "journal-entry-shadow-v1");
    for (var entry : oneTimeMarkers.entrySet()) {
      org.assertj.core.api.Assertions.assertThat(statements)
          .filteredOn(statement -> statement.startsWith("UPDATE " + entry.getKey() + " SET "))
          .isNotEmpty()
          .allMatch(statement -> statement.contains("migration_key = '" + entry.getValue() + "'"));
    }
    org.assertj.core.api.Assertions.assertThat(statements)
        .filteredOn(statement -> statement.startsWith("UPDATE supplier_invoices SET ")
            && statement.contains("_minor = CAST("))
        .hasSize(4)
        .allMatch(statement -> statement.contains(" = TRUNC(CAST(") && !statement.contains("ROUND("));
    org.assertj.core.api.Assertions.assertThat(statements)
        .filteredOn(statement -> statement.startsWith("CREATE TRIGGER a_")
            && statement.contains("EXECUTE FUNCTION guard_operational_money_integrity("))
        .hasSize(8)
        .allMatch(statement -> statement.contains("BEFORE INSERT OR UPDATE"));
  }
}
