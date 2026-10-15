package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.server.ResponseStatusException;

class GeneralLedgerMinorIT {
  private static SingleConnectionDataSource dataSource;
  private static JdbcTemplate jdbc;
  private static String schema;
  private static GeneralLedgerMinorService service;
  private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
  private static final LocalDate TO = LocalDate.of(2026, 7, 31);

  @BeforeAll
  static void openIsolatedSchema() throws Exception {
    String url = System.getenv("TEST_DATABASE_URL");
    if (url == null || !url.matches("jdbc:postgresql://[^/]+/alibooks_test")) {
      throw new IllegalStateException("TEST_DATABASE_URL must point to the isolated alibooks_test database.");
    }
    dataSource = new SingleConnectionDataSource(DriverManager.getConnection(url,
        System.getenv("TEST_DATABASE_USERNAME"), System.getenv("TEST_DATABASE_PASSWORD")), true);
    jdbc = new JdbcTemplate(dataSource);
    schema = "minor_ledger_test_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    jdbc.execute("SET search_path TO " + schema);
    jdbc.execute("CREATE TABLE currencies (currency_code varchar(3) PRIMARY KEY, minor_unit_exponent integer)");
    jdbc.execute("INSERT INTO currencies VALUES ('SEK', 2)");
    jdbc.execute("CREATE TABLE journal_entries (id bigserial PRIMARY KEY, account_number text, account_name text, "
        + "voucher_date date, voucher_number text, created_at timestamp with time zone, "
        + "correction_of_voucher_number text, "
        + "description text, debit numeric, credit numeric, "
        + "debit_minor bigint, credit_minor bigint, currency_code text)");
    service = new GeneralLedgerMinorService(jdbc);
  }

  @AfterAll
  static void closeIsolatedSchema() {
    try {
      if (jdbc != null && schema != null) jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    } finally {
      if (dataSource != null) dataSource.destroy();
    }
  }

  @BeforeEach
  void resetFixtures() {
    jdbc.execute("TRUNCATE journal_entries RESTART IDENTITY");
    jdbc.execute("UPDATE currencies SET minor_unit_exponent = 2");
  }

  @Test
  void preservesOreAndDateBoundariesWithRunningAndOpeningBalances() {
    row("1930", "Old name", FROM.minusDays(1), 125050, 0);
    row("1930", "New name", FROM, 1, 0);
    row("1930", "New name", TO, 0, 101);
    row("1930", "New name", TO.plusDays(1), 999, 0);
    GeneralLedgerMinorReport report = service.create(FROM, TO, null);
    assertThat(report.currencyCode()).isEqualTo("SEK");
    assertThat(report.minorUnitExponent()).isEqualTo(2);
    assertThat(report.periodDebitTotalMinor()).isEqualTo(1);
    assertThat(report.periodCreditTotalMinor()).isEqualTo(101);
    assertThat(report.accounts()).singleElement().satisfies(account -> {
      assertThat(account.accountName()).isEqualTo("New name");
      assertThat(account.openingBalanceMinor()).isEqualTo(125050);
      assertThat(account.closingBalanceMinor()).isEqualTo(124950);
      assertThat(account.entries()).extracting(GeneralLedgerMinorReport.Entry::runningBalanceMinor)
          .containsExactly(125051L, 124950L);
    });
    assertThat(service.create(FROM, TO, "1930").entryCount()).isEqualTo(2);
    assertThat(service.create(FROM, TO, "9999").accounts()).isEmpty();
    assertThat(service.create(null, null, null).entryCount()).isEqualTo(4);
  }

  @Test
  void includesOpeningOnlyAccount() {
    row("1930", "Bank", FROM.minusDays(1), 1, 0);
    assertThat(service.create(FROM, TO, null).accounts()).singleElement().satisfies(account -> {
      assertThat(account.openingBalanceMinor()).isEqualTo(1);
      assertThat(account.closingBalanceMinor()).isEqualTo(1);
      assertThat(account.entries()).isEmpty();
    });
  }

  @Test
  void keepsZeroAmountJournalLineVisibleForTraceability() {
    row("2641", "Input VAT", FROM, 0, 0);
    GeneralLedgerMinorReport report = service.create(FROM, TO, null);
    assertThat(report.accountCount()).isEqualTo(1);
    assertThat(report.entryCount()).isEqualTo(1);
    assertThat(report.accounts().getFirst().entries()).singleElement().satisfies(entry -> {
      assertThat(entry.debitMinor()).isZero();
      assertThat(entry.creditMinor()).isZero();
      assertThat(entry.runningBalanceMinor()).isZero();
    });
  }

  @Test
  void exposesRegistrationTimeAndCorrectionLinkWithoutInventingMissingHistory() {
    row("1930", "Bank", FROM, 100, 0);
    jdbc.execute("UPDATE journal_entries SET created_at = TIMESTAMPTZ '2026-07-02 09:30:00+00', "
        + "correction_of_voucher_number = 'A-0'");
    GeneralLedgerMinorReport.Entry corrected = service.create(FROM, TO, null)
        .accounts().getFirst().entries().getFirst();
    assertThat(corrected.createdAt()).isEqualTo(java.time.Instant.parse("2026-07-02T09:30:00Z"));
    assertThat(corrected.correctionOfVoucherNumber()).isEqualTo("A-0");
    jdbc.execute("UPDATE journal_entries SET created_at = NULL, correction_of_voucher_number = NULL");
    GeneralLedgerMinorReport.Entry historical = service.create(FROM, TO, null)
        .accounts().getFirst().entries().getFirst();
    assertThat(historical.createdAt()).isNull();
    assertThat(historical.correctionOfVoucherNumber()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"debit_minor = NULL", "debit_minor = debit_minor + 1", "debit = NULL",
      "voucher_date = NULL", "voucher_number = ' '", "currency_code = 'EUR'", "account_number = ' '",
      "debit_minor = -1, debit = -0.01", "credit_minor = 1, credit = 0.01"})
  void refusesIncompleteOrContradictoryRows(String corruption) {
    row("1930", "Bank", FROM, 125050, 0);
    jdbc.execute("UPDATE journal_entries SET " + corruption);
    assertUnprocessable();
  }

  @Test
  void rejectsWrongExponentAndUnsafeJsonInteger() {
    jdbc.execute("UPDATE currencies SET minor_unit_exponent = 0");
    assertUnprocessable();
    jdbc.execute("UPDATE currencies SET minor_unit_exponent = 2");
    row("1930", "Bank", FROM, 9_007_199_254_740_991L, 0);
    assertThat(service.create(FROM, TO, null).periodDebitTotalMinor()).isEqualTo(9_007_199_254_740_991L);
    row("1930", "Bank", FROM, 1, 0);
    assertUnprocessable();
  }

  @Test
  void rejectsReversedPeriod() {
    assertThatThrownBy(() -> service.create(TO, FROM, null)).isInstanceOfSatisfying(
        ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  private static void assertUnprocessable() {
    assertThatThrownBy(() -> service.create(FROM, TO, null)).isInstanceOfSatisfying(
        ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  private static void row(String number, String name, LocalDate date, long debit, long credit) {
    jdbc.update("INSERT INTO journal_entries (account_number, account_name, voucher_date, voucher_number, "
        + "description, debit, credit, debit_minor, credit_minor, currency_code) "
        + "VALUES (?, ?, ?, 'A-1', 'Test', ?, ?, ?, ?, 'SEK')",
        number, name, java.sql.Date.valueOf(date), BigDecimal.valueOf(debit, 2),
        BigDecimal.valueOf(credit, 2), debit, credit);
  }
}
