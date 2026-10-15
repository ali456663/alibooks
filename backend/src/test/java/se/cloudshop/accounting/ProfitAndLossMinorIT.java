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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.server.ResponseStatusException;

class ProfitAndLossMinorIT {
  private static SingleConnectionDataSource dataSource;
  private static JdbcTemplate jdbc;
  private static String schema;
  private static ProfitAndLossMinorService service;
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
    schema = "profit_loss_minor_test_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    jdbc.execute("SET search_path TO " + schema);
    jdbc.execute("CREATE TABLE currencies (currency_code varchar(3) PRIMARY KEY, minor_unit_exponent integer)");
    jdbc.execute("INSERT INTO currencies VALUES ('SEK', 2)");
    jdbc.execute("CREATE TABLE journal_entries (id bigserial PRIMARY KEY, account_number text, account_name text, "
        + "voucher_number text, voucher_date date, debit numeric, credit numeric, "
        + "debit_minor bigint, credit_minor bigint, currency_code text)");
    service = new ProfitAndLossMinorService(new TrialBalanceMinorService(jdbc));
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
  }

  @Test
  void calculatesPeriodResultFromPersistedMinorUnits() {
    row("B-0", "3041", FROM.minusDays(1), 0, 999_999);
    row("B-1", "1930", FROM, 125_050, 0);
    row("B-1", "3041", FROM, 0, 125_050);
    row("B-2", "5010", TO, 30_001, 0);
    row("B-2", "1930", TO, 0, 30_001);
    row("B-3", "3041", TO.plusDays(1), 0, 7_777);

    ProfitAndLossMinorReport report = service.create(FROM, TO);
    assertThat(report.totalRevenueMinor()).isEqualTo(125_050);
    assertThat(report.totalExpensesMinor()).isEqualTo(30_001);
    assertThat(report.resultMinor()).isEqualTo(95_049);
    assertThat(report.revenue()).singleElement().satisfies(line ->
        assertThat(line.accountNumber()).isEqualTo("3041"));
  }

  @Test
  void refusesMismatchedShadowEvenWhenIncomeAccountItselfIsValid() {
    row("B-1", "3041", FROM, 0, 125_050);
    row("B-1", "1930", FROM, 125_050, 0);
    jdbc.execute("UPDATE journal_entries SET debit_minor = debit_minor + 1 WHERE account_number = '1930'");
    assertThatThrownBy(() -> service.create(FROM, TO)).isInstanceOfSatisfying(
        ResponseStatusException.class,
        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  void refusesUnbalancedLegacyVoucherBeforePublishingResult() {
    row("B-1", "3041", FROM, 0, 125_050);
    row("B-1", "1930", FROM, 125_049, 0);

    assertThatThrownBy(() -> service.create(FROM, TO)).isInstanceOfSatisfying(
        ResponseStatusException.class,
        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  void refusesVoucherWithoutNumberBeforePublishingResult() {
    row(null, "3041", FROM, 0, 125_050);
    row(null, "1930", FROM, 125_050, 0);

    assertThatThrownBy(() -> service.create(FROM, TO)).isInstanceOfSatisfying(
        ResponseStatusException.class,
        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  private static void row(String voucher, String account, LocalDate date, long debit, long credit) {
    jdbc.update("INSERT INTO journal_entries (voucher_number, account_number, account_name, voucher_date, "
            + "debit, credit, debit_minor, credit_minor, currency_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SEK')",
        voucher, account, "Account " + account, java.sql.Date.valueOf(date),
        BigDecimal.valueOf(debit, 2), BigDecimal.valueOf(credit, 2), debit, credit);
  }
}
