package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Random;
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

class TrialBalanceMinorIT {
  private static SingleConnectionDataSource dataSource;
  private static JdbcTemplate jdbc;
  private static String schema;
  private static TrialBalanceMinorService service;
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
    schema = "minor_report_test_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    jdbc.execute("SET search_path TO " + schema);
    jdbc.execute("CREATE TABLE currencies (currency_code varchar(3) PRIMARY KEY, minor_unit_exponent integer)");
    jdbc.execute("INSERT INTO currencies VALUES ('SEK', 2)");
    // Decimal legacy columns emulate a future compatible schema while proving exact minor reads.
    jdbc.execute("CREATE TABLE journal_entries (id bigserial PRIMARY KEY, account_number text, account_name text, "
        + "voucher_date date, debit numeric, credit numeric, debit_minor bigint, credit_minor bigint, currency_code text)");
    service = new TrialBalanceMinorService(jdbc);
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
    jdbc.execute("TRUNCATE journal_entries");
    jdbc.execute("UPDATE currencies SET minor_unit_exponent = 2");
  }

  @Test
  void preservesOreAndDateBoundariesAndGroupsRenamedAccountsByNumber() {
    row("1930", "Old name", FROM.minusDays(1), 125050L, 0);
    row("1930", "New name", FROM, 1L, 0);
    row("1930", "New name", TO, 0, 101L);
    row("1930", "New name", TO.plusDays(1), 999L, 0);
    TrialBalanceMinorReport report = service.create(FROM, TO);
    assertThat(report.currencyCode()).isEqualTo("SEK");
    assertThat(report.minorUnitExponent()).isEqualTo(2);
    assertThat(report.lines()).singleElement().satisfies(line -> {
      assertThat(line.openingDebitMinor()).isEqualTo(125050L);
      assertThat(line.periodDebitMinor()).isEqualTo(1L);
      assertThat(line.periodCreditMinor()).isEqualTo(101L);
      assertThat(line.closingDebitMinor()).isEqualTo(124950L);
    });
    assertThat(report.differenceMinor()).isEqualTo(124950L);
    assertThat(service.create(null, TO).periodDebitTotalMinor()).isEqualTo(125051L);
    assertThat(service.create(null, null).periodDebitTotalMinor()).isEqualTo(126050L);
  }

  @Test
  void generatedBalancedVouchersKeepTheTrialBalanceExact() {
    Random random = new Random(721306L);
    long expectedDebit = 0;
    for (int index = 0; index < 300; index++) {
      long minor = 1L + random.nextInt(1_000_000);
      LocalDate date = FROM.plusDays(random.nextInt(40) - 5);
      boolean debitBank = random.nextBoolean();
      row("1930", "Bank", date, debitBank ? minor : 0, debitBank ? 0 : minor);
      row("3041", "Sales", date, debitBank ? 0 : minor, debitBank ? minor : 0);
      if (!date.isBefore(FROM) && !date.isAfter(TO)) expectedDebit += minor;
    }
    TrialBalanceMinorReport report = service.create(FROM, TO);
    assertThat(report.periodDebitTotalMinor()).isEqualTo(expectedDebit);
    assertThat(report.periodCreditTotalMinor()).isEqualTo(expectedDebit);
    assertThat(report.openingDebitTotalMinor()).isEqualTo(report.openingCreditTotalMinor());
    assertThat(report.closingDebitTotalMinor()).isEqualTo(report.closingCreditTotalMinor());
    assertThat(report.differenceMinor()).isZero();
  }

  @Test
  void oneOreDifferenceIsNotReportedAsBalanced() {
    row("1930", "Bank", FROM, 125050L, 0);
    row("3041", "Sales", FROM, 0, 125049L);
    assertThat(service.create(FROM, TO).differenceMinor()).isEqualTo(1L);
  }

  @Test
  void emptyReportAndWholeKronaRowsStayCompatible() {
    assertThat(service.create(FROM, TO).lines()).isEmpty();
    row("1930", "Bank", FROM, 125000L, 0);
    assertThat(service.create(FROM, TO).periodDebitTotalMinor()).isEqualTo(125000L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"debit_minor = NULL", "debit_minor = debit_minor + 1", "debit = NULL",
      "voucher_date = NULL", "currency_code = NULL", "currency_code = 'EUR'", "account_number = ' '",
      "debit_minor = -1, debit = -0.01", "credit_minor = 1, credit = 0.01"})
  void refusesIncompleteOrContradictoryRows(String corruption) {
    row("1930", "Bank", FROM, 125050L, 0);
    jdbc.execute("UPDATE journal_entries SET " + corruption);
    assertUnprocessable();
  }

  @Test
  void rejectsWrongCurrencyExponentEvenForEmptyReport() {
    jdbc.execute("UPDATE currencies SET minor_unit_exponent = 0");
    assertUnprocessable();
  }

  @Test
  void supportsJsonIntegerBoundaryAndRejectsOverflowBeforeSerialization() {
    row("1930", "Bank", FROM, 9_007_199_254_740_991L, 0);
    assertThat(service.create(FROM, TO).periodDebitTotalMinor()).isEqualTo(9_007_199_254_740_991L);
    row("1930", "Bank", FROM, 1, 0);
    assertUnprocessable();
    jdbc.execute("TRUNCATE journal_entries");
    row("1930", "Bank", FROM, Long.MAX_VALUE, 0);
    row("1930", "Bank", FROM, Long.MAX_VALUE, 0);
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

  private static void row(String number, String name, LocalDate date, long debit, long credit) {
    jdbc.update("INSERT INTO journal_entries (account_number, account_name, voucher_date, debit, credit, "
        + "debit_minor, credit_minor, currency_code) VALUES (?, ?, ?, ?, ?, ?, ?, 'SEK')",
        number, name, java.sql.Date.valueOf(date), BigDecimal.valueOf(debit, 2), BigDecimal.valueOf(credit, 2), debit, credit);
  }
}
