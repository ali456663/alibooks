package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

class BalanceMinorIT {
  private static SingleConnectionDataSource dataSource;
  private static JdbcTemplate jdbc;
  private static String schema;
  private static BalanceMinorService service;
  private static AppSettings settings;
  private static final LocalDate JULY = LocalDate.of(2026, 7, 10);

  @BeforeAll
  static void openIsolatedSchema() throws Exception {
    String url = System.getenv("TEST_DATABASE_URL");
    if (url == null || !url.matches("jdbc:postgresql://[^/]+/alibooks_test")) {
      throw new IllegalStateException("TEST_DATABASE_URL must point to the isolated alibooks_test database.");
    }
    dataSource = new SingleConnectionDataSource(DriverManager.getConnection(url,
        System.getenv("TEST_DATABASE_USERNAME"), System.getenv("TEST_DATABASE_PASSWORD")), true);
    jdbc = new JdbcTemplate(dataSource);
    schema = "balance_minor_test_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    jdbc.execute("SET search_path TO " + schema);
    jdbc.execute("CREATE TABLE currencies (currency_code varchar(3) PRIMARY KEY, minor_unit_exponent integer)");
    jdbc.execute("INSERT INTO currencies VALUES ('SEK', 2)");
    jdbc.execute("CREATE TABLE journal_entries (id bigserial PRIMARY KEY, account_number text, account_name text, "
        + "voucher_number text, voucher_date date, description text, debit numeric, credit numeric, "
        + "debit_minor bigint, credit_minor bigint, currency_code text)");
    SettingsService settingsService = mock(SettingsService.class);
    settings = AppSettings.defaults();
    when(settingsService.getSettings()).thenReturn(settings);
    service = new BalanceMinorService(new TrialBalanceMinorService(jdbc), settingsService, jdbc);
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
    settings.setCompanyType("SOLE_TRADER");
  }

  @Test
  void carriesOreIntoEquityAndExcludesFutureEntries() {
    row("E-1", "1930", JULY.minusDays(1), 100_000, 0, "Deposit");
    row("E-1", "2018", JULY.minusDays(1), 0, 100_000, "Deposit");
    row("F-1", "1930", JULY, 125_050, 0, "Sale");
    row("F-1", "3041", JULY, 0, 125_050, "Sale");
    row("K-1", "5010", JULY, 30_001, 0, "Rent");
    row("K-1", "1930", JULY, 0, 30_001, "Rent");
    row("F-2", "1930", JULY.plusMonths(1), 999, 0, "Later");
    row("F-2", "3041", JULY.plusMonths(1), 0, 999, "Later");

    BalanceMinorReport report = service.create(JULY);
    assertThat(report.currencyCode()).isEqualTo("SEK");
    assertThat(report.minorUnitExponent()).isEqualTo(2);
    assertThat(report.totalAssetsMinor()).isEqualTo(195_049);
    assertThat(report.totalLiabilitiesAndEquityMinor()).isEqualTo(195_049);
    assertThat(report.differenceMinor()).isZero();
    assertThat(report.liabilitiesAndEquity()).anySatisfy(line -> {
      assertThat(line.accountNumber()).isEqualTo("2019");
      assertThat(line.amountMinor()).isEqualTo(95_049);
    });
  }

  @Test
  void doesNotDuplicateBookedAnnualResultAndUsesCompanyAccount() {
    settings.setCompanyType("LIMITED_COMPANY");
    row("F-1", "1930", JULY, 125_050, 0, "Sale");
    row("F-1", "3041", JULY, 0, 125_050, "Sale");
    row("BR-1", "8999", LocalDate.of(2026, 12, 31), 125_050, 0, "Annual result 2026");
    row("BR-1", "2099", LocalDate.of(2026, 12, 31), 0, 125_050, "Annual result 2026");

    assertThat(service.create(JULY).liabilitiesAndEquity()).singleElement().satisfies(line -> {
      assertThat(line.accountNumber()).isEqualTo("2099");
      assertThat(line.amountMinor()).isEqualTo(125_050);
    });
    BalanceMinorReport after = service.create(LocalDate.of(2026, 12, 31));
    assertThat(after.liabilitiesAndEquity()).singleElement().satisfies(line -> {
      assertThat(line.accountNumber()).isEqualTo("2099");
      assertThat(line.amountMinor()).isEqualTo(125_050);
    });
    assertThat(after.differenceMinor()).isZero();
  }

  @Test
  void manualVoucherWithAnnualDescriptionDoesNotSuppressResult() {
    row("F-1", "1930", JULY, 125_050, 0, "Sale");
    row("F-1", "3041", JULY, 0, 125_050, "Sale");
    row("M-1", "1930", JULY, 1_000, 0, "Annual result 2026");
    row("M-1", "2019", JULY, 0, 1_000, "Annual result 2026");
    assertThat(service.create(JULY).liabilitiesAndEquity())
        .extracting(BalanceMinorReport.Line::amountMinor).containsExactly(1_000L, 125_050L);
  }

  @Test
  void refusesCorruptedOrUnbalancedRowsInsteadOfPublishingBalance() {
    row("F-1", "1930", JULY, 125_050, 0, "Sale");
    row("F-1", "3041", JULY, 0, 125_050, "Sale");
    jdbc.execute("UPDATE journal_entries SET debit_minor = debit_minor + 1 WHERE account_number = '1930'");
    assertUnprocessable();
    jdbc.execute("UPDATE journal_entries SET debit_minor = debit_minor - 1 WHERE account_number = '1930'");
    jdbc.execute("UPDATE journal_entries SET credit = 1250.49, credit_minor = 125049 WHERE account_number = '3041'");
    assertUnprocessable();
  }

  private static void assertUnprocessable() {
    assertThatThrownBy(() -> service.create(JULY)).isInstanceOfSatisfying(
        ResponseStatusException.class,
        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  private static void row(String voucher, String account, LocalDate date, long debit, long credit,
      String description) {
    jdbc.update("INSERT INTO journal_entries (voucher_number, account_number, account_name, voucher_date, "
            + "description, debit, credit, debit_minor, credit_minor, currency_code) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SEK')",
        voucher, account, "Account " + account, java.sql.Date.valueOf(date), description,
        BigDecimal.valueOf(debit, 2), BigDecimal.valueOf(credit, 2), debit, credit);
  }
}
