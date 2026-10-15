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

class VatMinorIT {
  private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
  private static final LocalDate TO = LocalDate.of(2026, 7, 31);
  private static SingleConnectionDataSource dataSource;
  private static JdbcTemplate jdbc;
  private static String schema;
  private static VatMinorService service;

  @BeforeAll
  static void openIsolatedSchema() throws Exception {
    String url = System.getenv("TEST_DATABASE_URL");
    if (url == null || !url.matches("jdbc:postgresql://[^/]+/alibooks_test")) {
      throw new IllegalStateException("TEST_DATABASE_URL must point to the isolated alibooks_test database.");
    }
    dataSource = new SingleConnectionDataSource(DriverManager.getConnection(url,
        System.getenv("TEST_DATABASE_USERNAME"), System.getenv("TEST_DATABASE_PASSWORD")), true);
    jdbc = new JdbcTemplate(dataSource);
    schema = "vat_minor_test_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    jdbc.execute("SET search_path TO " + schema);
    jdbc.execute("CREATE TABLE currencies (currency_code varchar(3) PRIMARY KEY, minor_unit_exponent integer)");
    jdbc.execute("INSERT INTO currencies VALUES ('SEK', 2)");
    jdbc.execute("CREATE TABLE journal_entries (id bigserial PRIMARY KEY, voucher_date date, voucher_number text, "
        + "created_at timestamp, account_number text, account_name text, description text, "
        + "correction_of_voucher_number text, voucher_evidence_id bigint, debit numeric, credit numeric, "
        + "debit_minor bigint, credit_minor bigint, currency_code text)");
    service = new VatMinorService(new JournalMinorService(jdbc), jdbc);
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
  void calculatesOreAcrossAllRatesAndExcludesSettlementVoucher() {
    row("F-1", "1930", FROM, 125_063, 0, "Sale");
    row("F-1", "3041", FROM, 0, 100_050, "Sale");
    row("F-1", "2611", FROM, 0, 25_013, "Sale");
    row("F-2", "1930", FROM, 56_000, 0, "Sale");
    row("F-2", "3042", FROM, 0, 50_000, "Sale");
    row("F-2", "2621", FROM, 0, 6_000, "Sale");
    row("F-3", "1930", FROM, 10_600, 0, "Sale");
    row("F-3", "3043", FROM, 0, 10_000, "Sale");
    row("F-3", "2631", FROM, 0, 600, "Sale");
    row("K-1", "5410", TO, 10_005, 0, "Cost");
    row("K-1", "2641", TO, 2_001, 0, "Cost");
    row("K-1", "1930", TO, 0, 12_006, "Cost");
    row("MOMS-1", "2611", TO, 25_013, 0, "VAT settlement");
    row("MOMS-1", "2621", TO, 6_000, 0, "VAT settlement");
    row("MOMS-1", "2631", TO, 600, 0, "VAT settlement");
    row("MOMS-1", "2641", TO, 0, 2_001, "VAT settlement");
    row("MOMS-1", "2650", TO, 0, 29_612, "VAT settlement");

    VatMinorReport report = service.create(FROM, TO);

    assertThat(report.currencyCode()).isEqualTo("SEK");
    assertThat(report.minorUnitExponent()).isEqualTo(2);
    assertThat(report.salesBase25Minor()).isEqualTo(100_050);
    assertThat(report.outputVat25Minor()).isEqualTo(25_013);
    assertThat(report.salesBase12Minor()).isEqualTo(50_000);
    assertThat(report.outputVat12Minor()).isEqualTo(6_000);
    assertThat(report.salesBase6Minor()).isEqualTo(10_000);
    assertThat(report.outputVat6Minor()).isEqualTo(600);
    assertThat(report.outputVatMinor()).isEqualTo(31_613);
    assertThat(report.inputVatMinor()).isEqualTo(2_001);
    assertThat(report.vatToPayMinor()).isEqualTo(29_612);
    assertThat(report.settled()).isTrue();
  }

  @Test
  void recognizesSettlementBookedAfterReportPeriod() {
    row("F-1", "1930", FROM, 125_050, 0, "Sale");
    row("F-1", "3041", FROM, 0, 100_000, "Sale");
    row("F-1", "2611", FROM, 0, 25_050, "Sale");
    row("MOMS-1", "2611", TO.plusDays(1), 25_050, 0,
        "VAT settlement " + FROM + " - " + TO);
    row("MOMS-1", "2650", TO.plusDays(1), 0, 25_050,
        "VAT settlement " + FROM + " - " + TO);

    VatMinorReport report = service.create(FROM, TO);

    assertThat(report.outputVat25Minor()).isEqualTo(25_050);
    assertThat(report.vatToPayMinor()).isEqualTo(25_050);
    assertThat(report.settled()).isTrue();
  }

  @Test
  void doesNotTreatAnUnbalancedLaterSettlementRowAsBooked() {
    row("F-1", "1930", FROM, 125_050, 0, "Sale");
    row("F-1", "3041", FROM, 0, 100_000, "Sale");
    row("F-1", "2611", FROM, 0, 25_050, "Sale");
    row("MOMS-1", "2611", TO.plusDays(1), 25_050, 0,
        "VAT settlement " + FROM + " - " + TO);

    VatMinorReport report = service.create(FROM, TO);

    assertThat(report.outputVat25Minor()).isEqualTo(25_050);
    assertThat(report.settled()).isFalse();
  }

  @Test
  void rejectsUnbalancedPeriodBeforePublishingVatBasis() {
    row("F-1", "2611", FROM, 0, 25_050, "Sale");

    assertThatThrownBy(() -> service.create(FROM, TO)).isInstanceOfSatisfying(
        ResponseStatusException.class,
        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  void rejectsContradictoryMinorShadowEvenOnNonVatAccount() {
    row("F-1", "1930", FROM, 125_050, 0, "Sale");
    row("F-1", "3041", FROM, 0, 100_000, "Sale");
    row("F-1", "2611", FROM, 0, 25_050, "Sale");
    jdbc.execute("UPDATE journal_entries SET debit_minor = debit_minor + 1 WHERE account_number = '1930'");

    assertThatThrownBy(() -> service.create(FROM, TO)).isInstanceOfSatisfying(
        ResponseStatusException.class,
        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  void rejectsUnmappedOutputVatAccountInsteadOfSilentlyOmittingIt() {
    row("F-1", "1930", FROM, 100, 0, "Sale");
    row("F-1", "2614", FROM, 0, 100, "Sale");

    assertThatThrownBy(() -> service.create(FROM, TO)).isInstanceOfSatisfying(
        ResponseStatusException.class,
        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  void rejectsUnmappedInputVatAccountInsteadOfSilentlyOmittingIt() {
    row("K-1", "2645", FROM, 100, 0, "Purchase");
    row("K-1", "1930", FROM, 0, 100, "Purchase");

    assertThatThrownBy(() -> service.create(FROM, TO)).isInstanceOfSatisfying(
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
