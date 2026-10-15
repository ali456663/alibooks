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

class JournalMinorIT {
  private static SingleConnectionDataSource dataSource;
  private static JdbcTemplate jdbc;
  private static String schema;
  private static JournalMinorService service;
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
    schema = "minor_journal_test_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    jdbc.execute("SET search_path TO " + schema);
    jdbc.execute("CREATE TABLE currencies (currency_code varchar(3) PRIMARY KEY, minor_unit_exponent integer)");
    jdbc.execute("INSERT INTO currencies VALUES ('SEK', 2)");
    jdbc.execute("CREATE TABLE journal_entries (id bigserial PRIMARY KEY, voucher_date date, voucher_number text, "
        + "created_at timestamp with time zone, account_number text, account_name text, description text, "
        + "correction_of_voucher_number text, voucher_evidence_id bigint, debit numeric, credit numeric, "
        + "debit_minor bigint, credit_minor bigint, currency_code text)");
    service = new JournalMinorService(jdbc);
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
  void returnsRegistrationOrderRatherThanVoucherDateAndPreservesOre() {
    row(TO, 1, 0);
    row(FROM, 0, 1);
    row(TO.plusDays(1), 100, 0);
    JournalMinorReport report = service.create(FROM, TO);
    assertThat(report.entries()).extracting(JournalMinorReport.Entry::journalEntryId)
        .containsExactly("1", "2");
    assertThat(report.entries()).extracting(JournalMinorReport.Entry::voucherDate)
        .containsExactly(TO, FROM);
    assertThat(report.debitTotalMinor()).isEqualTo(1);
    assertThat(report.creditTotalMinor()).isEqualTo(1);
    assertThat(report.differenceMinor()).isZero();
    assertThat(report.voucherDifferences()).isEmpty();
    assertThat(service.create(null, null).entries()).hasSize(3);
  }

  @Test
  void reportsOneOreDifferenceAndEmptyPeriodWithoutRounding() {
    row(FROM, 101, 0);
    row(FROM, 0, 100);
    assertThat(service.create(FROM, TO).differenceMinor()).isEqualTo(1);
    assertThat(service.create(FROM, TO).voucherDifferences())
        .containsExactly(new JournalMinorReport.VoucherDifference("M-1", 1));
    assertThat(service.create(FROM.plusYears(1), null).entries()).isEmpty();
  }

  @Test
  void revealsOffsettingUnbalancedVouchersEvenWhenGrandTotalIsZero() {
    row("M-1", FROM, 101, 0);
    row("M-2", FROM, 0, 101);
    JournalMinorReport report = service.create(FROM, TO);
    assertThat(report.differenceMinor()).isZero();
    assertThat(report.voucherDifferences()).containsExactly(
        new JournalMinorReport.VoucherDifference("M-1", 101),
        new JournalMinorReport.VoucherDifference("M-2", -101));
  }

  @ParameterizedTest
  @ValueSource(strings = {"debit_minor = NULL", "debit_minor = debit_minor + 1", "debit = NULL",
      "voucher_date = NULL", "voucher_number = ' '", "currency_code = 'EUR'", "account_number = ' '",
      "debit_minor = -1, debit = -0.01", "credit_minor = 1, credit = 0.01"})
  void refusesIncompleteOrContradictoryRows(String corruption) {
    row(FROM, 125050, 0);
    jdbc.execute("UPDATE journal_entries SET " + corruption);
    assertUnprocessable();
  }

  @Test
  void refusesWrongExponentOrUnsafeJsonInteger() {
    jdbc.execute("UPDATE currencies SET minor_unit_exponent = 0");
    assertUnprocessable();
    jdbc.execute("UPDATE currencies SET minor_unit_exponent = 2");
    row(FROM, 9_007_199_254_740_991L, 0);
    assertThat(service.create(FROM, TO).debitTotalMinor()).isEqualTo(9_007_199_254_740_991L);
    row(FROM, 1, 0);
    assertUnprocessable();
  }

  @Test
  void rejectsReversedPeriod() {
    assertThatThrownBy(() -> service.create(TO, FROM)).isInstanceOfSatisfying(
        ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  private static void assertUnprocessable() {
    assertThatThrownBy(() -> service.create(FROM, TO)).isInstanceOfSatisfying(
        ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  private static void row(LocalDate date, long debit, long credit) {
    row("M-1", date, debit, credit);
  }

  private static void row(String voucherNumber, LocalDate date, long debit, long credit) {
    jdbc.update("INSERT INTO journal_entries (voucher_date, voucher_number, account_number, account_name, "
        + "debit, credit, debit_minor, credit_minor, currency_code) VALUES (?, ?, '1930', 'Bank', ?, ?, ?, ?, 'SEK')",
        java.sql.Date.valueOf(date), voucherNumber,
        BigDecimal.valueOf(debit, 2), BigDecimal.valueOf(credit, 2), debit, credit);
  }
}
