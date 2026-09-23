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
    verify(jdbcTemplate).execute(contains("CREATE TRIGGER supplier_invoice_payments_money_shadow_sync"));
    verify(jdbcTemplate).execute(contains("expenses ADD COLUMN IF NOT EXISTS total_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("card_purchases ADD COLUMN IF NOT EXISTS total_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS bank_reconciliation_entries_bank_row_id_idx"));
    verify(jdbcTemplate).execute(contains("stripe_payouts ADD COLUMN IF NOT EXISTS net_amount_minor bigint"));
    verify(jdbcTemplate).execute(contains("vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay_minor bigint"));
    verify(jdbcTemplate).execute(contains("journal_entries ADD COLUMN IF NOT EXISTS debit_minor bigint"));
    verify(jdbcTemplate).execute(contains("journal_entries ADD COLUMN IF NOT EXISTS credit_minor bigint"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS currencies"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE OR REPLACE FUNCTION sync_money_shadow_fields"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TRIGGER journal_entries_money_shadow_sync"));
    verify(jdbcTemplate, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS money_migration_ledger"));
    verify(jdbcTemplate, times(8)).execute(contains("INSERT INTO money_migration_ledger"));
    org.assertj.core.api.Assertions.assertThat(statements)
        .anyMatch(statement -> statement.contains("CAST(ROUND(CAST(total_amount AS numeric) * 100, 0) AS bigint)"));
  }
}
