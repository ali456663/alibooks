package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class AccountingExportControllerTest {

  private final JwtService jwtService = new JwtService("test_secret");
  private final AuthHeader authHeader = new AuthHeader(jwtService);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final AccountingExportController controller = new AccountingExportController(
      authHeader,
      accountingService,
      auditService
  );

  @Test
  void journalEntriesExportRecordsAuditEventWithEntryCount() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning");
    when(accountingService.findAllEntries()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1250, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "OLD-1", 1, 0, "Old invoice", LocalDate.of(2026, 6, 30))
    ));
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportJournalEntries(authorizationHeader, from, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("Verifikat,Serie,Lopnummer")
        .contains("F-1")
        .doesNotContain("OLD-1");
    verify(auditService).record(
        "export",
        "journal_entries",
        "2026-07-01 - 2026-07-31",
        "journal_entries_exported",
        "2026-07-01 - 2026-07-31",
        "Journal entries exported. Entries: 2.",
        2,
        authorizationHeader
    );
  }

  @Test
  void profitAndLossExportRecordsAuditEventWithResult() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    ProfitAndLossReport report = new ProfitAndLossReport(
        from,
        to,
        List.of(new ReportLine("3041", "Forsaljning", 2000)),
        List.of(new ReportLine("5420", "Programvaror", 500)),
        2000,
        500,
        1500
    );
    when(accountingService.createProfitAndLossReport(from, to)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportProfitAndLoss(authorizationHeader, from, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("Resultatrapport")
        .contains("1500");
    verify(auditService).record(
        "export",
        "profit_and_loss",
        "2026-07-01 - 2026-07-31",
        "profit_and_loss_exported",
        "2026-07-01 - 2026-07-31",
        "Profit and loss exported. Result: 1500.",
        2,
        authorizationHeader
    );
  }

  @Test
  void balanceReportExportRecordsAuditEventWithDifference() {
    LocalDate to = LocalDate.of(2026, 7, 31);
    BalanceReport report = new BalanceReport(
        to,
        List.of(new ReportLine("1930", "Foretagskonto", 2000)),
        List.of(new ReportLine("2010", "Eget kapital", -2000)),
        2000,
        -2000,
        0
    );
    when(accountingService.createBalanceReport(to)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportBalanceReport(authorizationHeader, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("Balansrapport")
        .contains("Foretagskonto");
    verify(auditService).record(
        "export",
        "balance_report",
        "2026-07-31",
        "balance_report_exported",
        "2026-07-31",
        "Balance report exported. Difference: 0.",
        2,
        authorizationHeader
    );
  }

  @Test
  void vatControlExportRecordsAuditEventWithIssueCounts() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    VatControlReport report = new VatControlReport(
        from,
        to,
        1,
        1000,
        100,
        250,
        -150,
        0,
        0,
        0,
        100,
        1,
        0,
        List.of(new VatControlIssue("critical", "output_vat_difference", "F-1", LocalDate.of(2026, 7, 10), 1000, 100, 250, 0, 0, 0, -150, "Wrong VAT."))
    );
    when(accountingService.createVatControlReport(from, to)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportVatControl(authorizationHeader, from, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("output_vat_difference")
        .contains("Wrong VAT.");
    verify(auditService).record(
        "export",
        "vat_control",
        "2026-07-01 - 2026-07-31",
        "vat_control_exported",
        "2026-07-01 - 2026-07-31",
        "VAT control exported. Critical: 1. Warnings: 0.",
        1,
        authorizationHeader
    );
  }

  @Test
  void trialBalanceExportRecordsAuditEventWithDifference() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    TrialBalanceReport report = new TrialBalanceReport(
        from,
        to,
        List.of(new TrialBalanceLine("1930", "Foretagskonto", 0, 0, 1250, 0, 1250, 0)),
        0,
        0,
        1250,
        1250,
        1250,
        1250,
        0
    );
    when(accountingService.createTrialBalanceReport(from, to)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportTrialBalance(authorizationHeader, from, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("Konto,Kontonamn")
        .contains("Foretagskonto");
    verify(auditService).record(
        "export",
        "trial_balance",
        "2026-07-01 - 2026-07-31",
        "trial_balance_exported",
        "2026-07-01 - 2026-07-31",
        "Trial balance exported. Difference: 0.",
        1,
        authorizationHeader
    );
  }

  @Test
  void generalLedgerExportRecordsAuditEventWithEntryCount() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    GeneralLedgerReport report = new GeneralLedgerReport(
        from,
        to,
        "1930",
        List.of(new GeneralLedgerAccount(
            "1930",
            "Foretagskonto",
            0,
            1250,
            0,
            1250,
            List.of(new GeneralLedgerEntry(
                10L,
                LocalDate.of(2026, 7, 10),
                "F-1",
                "1930",
                "Foretagskonto",
                "Invoice",
                "invoice",
                "F-2026-0001",
                "traceable",
                "",
                "",
                1250,
                0,
                1250,
                "A".repeat(64)
            ))
        )),
        1,
        1,
        1250,
        0
    );
    when(accountingService.createGeneralLedgerReport(from, to, "1930")).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportGeneralLedger(authorizationHeader, from, to, "1930");

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("Konto,Kontonamn")
        .contains("F-1");
    verify(auditService).record(
        "export",
        "general_ledger",
        "2026-07-01 - 2026-07-31 account 1930",
        "general_ledger_exported",
        "2026-07-01 - 2026-07-31",
        "General ledger exported. Accounts: 1. Entries: 1.",
        1,
        authorizationHeader
    );
  }

  @Test
  void generalLedgerExportUsesSafeContentDispositionFilename() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    String accountNumber = "1930\r\n/evil";
    GeneralLedgerReport report = new GeneralLedgerReport(
        from,
        to,
        accountNumber,
        List.of(),
        0,
        0,
        0,
        0
    );
    when(accountingService.createGeneralLedgerReport(from, to, accountNumber)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportGeneralLedger(authorizationHeader, from, to, accountNumber);

    String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(contentDisposition).contains("attachment");
    assertThat(contentDisposition).doesNotContain("\r").doesNotContain("\n").doesNotContain("/");
    assertThat(contentDisposition).contains("huvudbok-1930___evil.csv");
  }

  @Test
  void sieExportRecordsAuditEventWithControlHash() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    SieExportReceipt receipt = new SieExportReceipt(
        from,
        to,
        Instant.parse("2026-07-31T10:00:00Z"),
        "Muscle&Focus",
        true,
        "SIE export is ready for accountant handoff.",
        1,
        3,
        3,
        1250,
        1250,
        0,
        0,
        0,
        0,
        0,
        "PERIODHASH",
        "CHAINHASH",
        "CONTROLHASH"
    );
    when(accountingService.createSieExport(from, to)).thenReturn("#SIETYP 4");
    when(accountingService.createSieExportReceipt(from, to)).thenReturn(receipt);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportSie(authorizationHeader, from, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("#SIETYP 4");
    verify(auditService).record(
        "export",
        "handoff_export",
        "CONTROLHASH",
        "sie_exported",
        "2026-07-01 - 2026-07-31",
        "SIE file exported with control hash CONTROLHASH",
        3,
        authorizationHeader
    );
  }

  @Test
  void accountSignControlExportRecordsAuditEventWithIssueCounts() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    AccountSignControlReport report = new AccountSignControlReport(
        from,
        to,
        Instant.parse("2026-07-31T10:00:00Z"),
        11,
        2,
        1,
        1,
        List.of(
            new AccountSignControlLine("1930", "Foretagskonto", "debit", -500, "critical", "critical", true, "1930 Foretagskonto har ovantat minussaldo -500 SEK."),
            new AccountSignControlLine("1580", "Stripe-fordran", "debit", -100, "warning", "warning", false, "1580 Stripe-fordran har ovantat minussaldo -100 SEK.")
        )
    );
    when(accountingService.createAccountSignControlReport(from, to)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportAccountSignControl(authorizationHeader, from, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("AliBooks kontoteckenkontroll")
        .contains("Foretagskonto")
        .contains("Stripe-fordran");
    verify(auditService).record(
        "export",
        "account_sign_control",
        "2026-07-01 - 2026-07-31",
        "account_sign_control_exported",
        "2026-07-01 - 2026-07-31",
        "Account sign control exported. Critical: 1. Warnings: 1.",
        2,
        authorizationHeader
    );
  }

  @Test
  void voucherControlExportRecordsAuditEventWithIssueCounts() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    VoucherControlReport report = new VoucherControlReport(
        from,
        to,
        2,
        4,
        1,
        1,
        0,
        0,
        0,
        0,
        0,
        1,
        1,
        List.of(
            new VoucherControlIssue("critical", "unbalanced_voucher", "M-1", "M", null, 1, LocalDate.of(2026, 7, 10), 100, 0, "Voucher does not balance."),
            new VoucherControlIssue("warning", "evidence_missing_receipt_hash", "K-1", "K", null, 1, LocalDate.of(2026, 7, 11), 200, 200, "Voucher K-1 has receipt evidence without SHA-256 archive hash.")
        )
    );
    when(accountingService.createVoucherControlReport(from, to)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportVoucherControl(authorizationHeader, from, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("Nyckeltal,Varde")
        .contains("unbalanced_voucher")
        .contains("evidence_missing_receipt_hash");
    verify(auditService).record(
        "export",
        "voucher_control",
        "2026-07-01 - 2026-07-31",
        "voucher_control_exported",
        "2026-07-01 - 2026-07-31",
        "Voucher control exported. Critical: 1. Warnings: 1.",
        2,
        authorizationHeader
    );
  }

  @Test
  void journalIntegrityExportRecordsAuditEventWithFingerprint() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = LocalDate.of(2026, 7, 31);
    JournalIntegrityReport report = new JournalIntegrityReport(
        from,
        to,
        Instant.parse("2026-07-31T10:00:00Z"),
        2,
        1,
        1250,
        1250,
        0,
        0,
        "A".repeat(64),
        "B".repeat(64),
        "C".repeat(64),
        List.of(
            new JournalIntegrityLine(
                1,
                10L,
                LocalDate.of(2026, 7, 10),
                "F-1",
                "1930",
                "Foretagskonto",
                "Invoice payment",
                1250,
                0,
                "invoice",
                "F-2026-0001",
                "traceable",
                "",
                "D".repeat(64),
                "START",
                "E".repeat(64)
            )
        )
    );
    when(accountingService.createJournalIntegrityReport(from, to)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportJournalIntegrity(authorizationHeader, from, to);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("AliBooks bokforingskedja")
        .contains("C".repeat(64))
        .contains("F-1");
    verify(auditService).record(
        "export",
        "journal_integrity",
        "C".repeat(64),
        "journal_integrity_exported",
        "2026-07-01 - 2026-07-31",
        "Journal integrity exported. Entries: 2. Difference: 0. Missing evidence: 0.",
        2,
        authorizationHeader
    );
  }

  @Test
  void archiveYearExportIncludesVoucherApprovalCounts() {
    ArchiveYearReport report = new ArchiveYearReport(
        2026,
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 12, 31),
        Instant.parse("2027-01-10T10:00:00Z"),
        "2033-12-31",
        84,
        6,
        1,
        1,
        12,
        4,
        2,
        1,
        1,
        0,
        0,
        1,
        2,
        1,
        1,
        0,
        true,
        "A".repeat(64),
        "B".repeat(64),
        List.of(new ArchiveYearItem(
            "voucher-approval",
            "Verifikationsattest",
            "critical",
            "2 godkanda / 1 saknar attest",
            "1 verifikat saknar attest och 1 vantar pa attest.",
            "/voucher-approvals"
        ))
    );
    when(accountingService.createArchiveYearReport(2026)).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportArchiveYear(authorizationHeader, 2026);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("Godkanda verifikat,2")
        .contains("Verifikat utan attest,1")
        .contains("Verifikat som vantar pa attest,1")
        .contains("voucher-approval");
    verify(auditService).record(
        "export",
        "archive",
        2026,
        "archive_year_exported",
        "2026",
        "Archive year report exported. Period fingerprint: " + "A".repeat(64),
        12,
        authorizationHeader
    );
  }
}
