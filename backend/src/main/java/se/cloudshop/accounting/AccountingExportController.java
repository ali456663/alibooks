package se.cloudshop.accounting;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.export.CsvEscaper;

@RestController
public class AccountingExportController {

  private final AuthHeader authHeader;
  private final AccountingService accountingService;
  private final AuditService auditService;

  public AccountingExportController(AuthHeader authHeader, AccountingService accountingService, AuditService auditService) {
    this.authHeader = authHeader;
    this.accountingService = accountingService;
    this.auditService = auditService;
  }

  @GetMapping("/journal-entries/export")
  public ResponseEntity<byte[]> exportJournalEntries(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    List<JournalEntry> entries = accountingService.findAllEntries()
        .stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), from, to))
        .toList();
    StringBuilder csv = new StringBuilder();
    csv.append("Verifikat,Serie,Lopnummer,Datum,Kalla,Kallreferens,Underlagsstatus,Rattelse av,Konto,Kontonamn,Beskrivning,Debet,Kredit,DebetMinor,KreditMinor,Integritetskod\n");

    for (JournalEntry entry : entries) {
      csv.append(escape(entry.getVoucherNumber())).append(",");
      csv.append(escape(entry.getVoucherSeries())).append(",");
      csv.append(entry.getVoucherSequenceNumber() == null ? "" : entry.getVoucherSequenceNumber()).append(",");
      csv.append(escape(entry.getVoucherDate() == null ? "" : entry.getVoucherDate().toString())).append(",");
      csv.append(escape(entry.getSourceType())).append(",");
      csv.append(escape(entry.getSourceReference())).append(",");
      csv.append(escape(entry.getEvidenceStatus())).append(",");
      csv.append(escape(entry.getCorrectionOfVoucherNumber())).append(",");
      csv.append(escape(entry.getAccountNumber())).append(",");
      csv.append(escape(entry.getAccountName())).append(",");
      csv.append(escape(entry.getDescription())).append(",");
      long debitMinor = entry.getDebitMinorValue();
      long creditMinor = entry.getCreditMinorValue();
      csv.append(reportWholeKrona(debitMinor, "journalexportens debet")).append(",");
      csv.append(reportWholeKrona(creditMinor, "journalexportens kredit")).append(",");
      csv.append(debitMinor).append(",");
      csv.append(creditMinor).append(",");
      csv.append(escape(entry.getIntegrityHash())).append("\n");
    }

    auditService.record(
        "export",
        "journal_entries",
        periodLabel(from, to),
        "journal_entries_exported",
        periodLabel(from, to),
        "Journal entries exported. Entries: " + entries.size() + ".",
        entries.size(),
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=journal-entries.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/profit-and-loss/export")
  public ResponseEntity<byte[]> exportProfitAndLoss(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    ProfitAndLossReport report = accountingService.createProfitAndLossReport(from, to);
    StringBuilder csv = new StringBuilder();
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Rapport,Rad,Konto,Kontonamn,Belopp\n");
    appendReportLines(csv, "Resultatrapport", "Intakter", report.revenue());
    appendReportLines(csv, "Resultatrapport", "Kostnader", report.expenses());
    csv.append("Resultatrapport,Totalt,,Intakter,").append(report.totalRevenue()).append("\n");
    csv.append("Resultatrapport,Totalt,,Kostnader,").append(report.totalExpenses()).append("\n");
    csv.append("Resultatrapport,Resultat,,Resultat,").append(report.result()).append("\n");

    auditService.record(
        "export",
        "profit_and_loss",
        periodLabel(from, to),
        "profit_and_loss_exported",
        periodLabel(from, to),
        "Profit and loss exported. Result: " + report.result() + ".",
        report.revenue().size() + report.expenses().size(),
        authorizationHeader
    );

    return csvResponse("profit-and-loss.csv", csv);
  }

  @GetMapping("/balance-report/export")
  public ResponseEntity<byte[]> exportBalanceReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    BalanceReport report = accountingService.createBalanceReport(to);
    StringBuilder csv = new StringBuilder();
    csv.append("Balansdag,").append(escape(to == null ? "" : to.toString())).append("\n");
    csv.append("Rapport,Rad,Konto,Kontonamn,Belopp\n");
    appendReportLines(csv, "Balansrapport", "Tillgangar", report.assets());
    appendReportLines(csv, "Balansrapport", "Skulder och eget kapital", report.liabilitiesAndEquity());
    csv.append("Balansrapport,Totalt,,Tillgangar,").append(report.totalAssets()).append("\n");
    csv.append("Balansrapport,Totalt,,Skulder och eget kapital,").append(report.totalLiabilitiesAndEquity()).append("\n");
    csv.append("Balansrapport,Skillnad,,Skillnad,").append(report.difference()).append("\n");

    auditService.record(
        "export",
        "balance_report",
        to == null ? "" : to.toString(),
        "balance_report_exported",
        to == null ? "" : to.toString(),
        "Balance report exported. Difference: " + report.difference() + ".",
        report.assets().size() + report.liabilitiesAndEquity().size(),
        authorizationHeader
    );

    return csvResponse("balance-report.csv", csv);
  }

  @GetMapping("/accountant-package/export")
  public ResponseEntity<byte[]> exportAccountantPackage(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    AccountantPackageReport report = accountingService.createAccountantPackageReport(from, to);
    auditService.record(
        "export",
        "handoff_export",
        "accountant-package",
        "accountant_package_exported",
        periodLabel(from, to),
        "Accountant package exported",
        report.journalEntryCount(),
        authorizationHeader
    );
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks redovisningspaket\n");
    csv.append("Foretag,").append(escape(report.companyName())).append("\n");
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Redo delar,").append(report.readyItemCount()).append("\n");
    csv.append("Varningar,").append(report.warningItemCount()).append("\n");
    csv.append("Kritiska,").append(report.criticalItemCount()).append("\n");
    csv.append("SIE redo,").append(report.sieExportReady() ? "Ja" : "Nej").append("\n");
    csv.append("\n");
    csv.append("Nyckeltal,Varde\n");
    csv.append("Intakter,").append(report.totalRevenue()).append("\n");
    csv.append("Kostnader,").append(report.totalExpenses()).append("\n");
    csv.append("Resultat,").append(report.result()).append("\n");
    csv.append("Tillgangar,").append(report.totalAssets()).append("\n");
    csv.append("Skulder och eget kapital,").append(report.totalLiabilitiesAndEquity()).append("\n");
    csv.append("Balansdifferens,").append(report.balanceDifference()).append("\n");
    csv.append("Utgaende moms,").append(report.outputVat()).append("\n");
    csv.append("Ingaende moms,").append(report.inputVat()).append("\n");
    csv.append("Moms att betala/fa tillbaka,").append(report.vatToPay()).append("\n");
    csv.append("Verifikat,").append(report.voucherCount()).append("\n");
    csv.append("Bokforingsrader,").append(report.journalEntryCount()).append("\n");
    csv.append("Saldobalans differens,").append(report.trialBalanceDifference()).append("\n");
    csv.append("Kritiska verifikationspunkter,").append(report.voucherCriticalIssues()).append("\n");
    csv.append("Kritiska momspunkter,").append(report.vatCriticalIssues()).append("\n");
    csv.append("Kritiska kontotecken,").append(report.accountSignCriticalIssues()).append("\n");
    csv.append("Kontotecken varningar,").append(report.accountSignWarningIssues()).append("\n");
    csv.append("Bankdifferens,").append(report.bankReconciliationDifference()).append("\n");
    csv.append("Kritiska bankavstamningspunkter,").append(report.bankReconciliationCriticalIssues()).append("\n");
    csv.append("Bankavstamningsvarningar,").append(report.bankReconciliationWarningIssues()).append("\n");
    csv.append("Oppna kundfakturor,").append(report.receivablesInvoiceCount()).append("\n");
    csv.append("Kundfordringar totalt,").append(report.receivablesTotalOutstanding()).append("\n");
    csv.append("Forfallna kundfordringar,").append(report.receivablesOverdueOutstanding()).append("\n");
    csv.append("Kundfordringar som forfaller snart,").append(report.receivablesDueSoonOutstanding()).append("\n");
    csv.append("Oppna leverantorsfakturor,").append(report.payablesInvoiceCount()).append("\n");
    csv.append("Leverantorsskulder totalt,").append(report.payablesTotalOutstanding()).append("\n");
    csv.append("Forfallna leverantorsskulder,").append(report.payablesOverdueOutstanding()).append("\n");
    csv.append("Leverantorsskulder som forfaller snart,").append(report.payablesDueSoonOutstanding()).append("\n");
    csv.append("Bokforingskedja differens,").append(report.journalIntegrityDifference()).append("\n");
    csv.append("Bokforingsrader utan tydlig kallkoppling,").append(report.journalIntegrityMissingEvidenceCount()).append("\n");
    csv.append("Bokforingskedja periodstampel,").append(escape(report.journalIntegrityPeriodFingerprint())).append("\n");
    csv.append("Bokforingskedja slutlig kedjekod,").append(escape(report.journalIntegrityFinalChainHash())).append("\n");
    csv.append("Godkanda verifikat,").append(report.voucherApprovedCount()).append("\n");
    csv.append("Verifikat utan attest,").append(report.voucherMissingApprovalCount()).append("\n");
    csv.append("Verifikat som vantar pa attest,").append(report.voucherPendingApprovalCount()).append("\n");
    csv.append("Blockerade verifikat,").append(report.voucherBlockedApprovalCount()).append("\n");
    csv.append("\n");
    csv.append("Omrade,Status,Detalj,Rekommenderad export\n");
    for (AccountantPackageItem item : report.items()) {
      csv.append(escape(item.area())).append(",");
      csv.append(escape(item.status())).append(",");
      csv.append(escape(item.detail())).append(",");
      csv.append(escape(item.recommendedExport())).append("\n");
    }

    return csvResponse("redovisningspaket-backend.csv", csv);
  }

  @GetMapping("/system-documentation/export")
  public ResponseEntity<byte[]> exportSystemDocumentation(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    SystemDocumentationReport report = accountingService.createSystemDocumentationReport(from, to);
    auditService.record(
        "export",
        "handoff_export",
        "system-documentation",
        "system_documentation_exported",
        periodLabel(from, to),
        "System documentation exported",
        report.exportCount(),
        authorizationHeader
    );
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks systemdokumentation\n");
    csv.append("Genererad,").append(escape(report.generatedAt().toString())).append("\n");
    csv.append("Foretag,").append(escape(report.companyName())).append("\n");
    csv.append("Foretagsform,").append(escape(report.companyType())).append("\n");
    csv.append("Bokforingsmetod,").append(escape(report.accountingMethod())).append("\n");
    csv.append("Momsperiod,").append(escape(report.vatReportingPeriod())).append("\n");
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Konton,").append(report.accountCount()).append("\n");
    csv.append("Verifikationsserier,").append(escape(String.join(" ", report.voucherSeries()))).append("\n");
    csv.append("Automatiska floden,").append(report.automationCount()).append("\n");
    csv.append("Kontroller,").append(report.controlCount()).append("\n");
    csv.append("Exporter,").append(report.exportCount()).append("\n");
    csv.append("\n");
    csv.append("Kategori,Titel,Beskrivning,Kalla/Kod,Kontrollpunkt,Rekommenderad export\n");
    for (SystemDocumentationItem item : report.items()) {
      csv.append(escape(item.category())).append(",");
      csv.append(escape(item.title())).append(",");
      csv.append(escape(item.detail())).append(",");
      csv.append(escape(item.source())).append(",");
      csv.append(escape(item.controlPoint())).append(",");
      csv.append(escape(item.recommendedExport())).append("\n");
    }

    return csvResponse("systemdokumentation.csv", csv);
  }

  @GetMapping("/archive-year/export")
  public ResponseEntity<byte[]> exportArchiveYear(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) Integer year
  ) {
    authHeader.requireValidToken(authorizationHeader);

    ArchiveYearReport report = accountingService.createArchiveYearReport(year);
    auditService.record(
        "export",
        "archive",
        report.year(),
        "archive_year_exported",
        String.valueOf(report.year()),
        "Archive year report exported. Period fingerprint: " + report.periodFingerprint(),
        report.journalEntryCount(),
        authorizationHeader
    );
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks arsarkivkontroll\n");
    csv.append("Ar,").append(report.year()).append("\n");
    csv.append("Period,").append(escape(report.periodFrom() + " - " + report.periodTo())).append("\n");
    csv.append("Genererad,").append(escape(report.generatedAt().toString())).append("\n");
    csv.append("Spara minst till,").append(escape(report.retentionUntil())).append("\n");
    csv.append("Score,").append(report.score()).append("\n");
    csv.append("Redo,").append(report.readyItemCount()).append("\n");
    csv.append("Varningar,").append(report.warningItemCount()).append("\n");
    csv.append("Kritiska,").append(report.criticalItemCount()).append("\n");
    csv.append("Bokforingsrader,").append(report.journalEntryCount()).append("\n");
    csv.append("Verifikat,").append(report.voucherCount()).append("\n");
    csv.append("Fakturor,").append(report.invoiceCount()).append("\n");
    csv.append("Kostnader,").append(report.expenseCount()).append("\n");
    csv.append("Kvitton,").append(report.receiptCount()).append("\n");
    csv.append("Saknade kvitton,").append(report.missingReceiptCount()).append("\n");
    csv.append("Saknade underlagskoder,").append(report.missingReceiptHashCount()).append("\n");
    csv.append("Momsperioder,").append(report.vatFilingCount()).append("\n");
    csv.append("Godkanda verifikat,").append(report.voucherApprovedCount()).append("\n");
    csv.append("Verifikat utan attest,").append(report.voucherMissingApprovalCount()).append("\n");
    csv.append("Verifikat som vantar pa attest,").append(report.voucherPendingApprovalCount()).append("\n");
    csv.append("Blockerade verifikat,").append(report.voucherBlockedApprovalCount()).append("\n");
    csv.append("Bokforingskedja balanserar,").append(report.journalBalanced() ? "Ja" : "Nej").append("\n");
    csv.append("Periodstampel,").append(escape(report.periodFingerprint())).append("\n");
    csv.append("Slutlig kedjekod,").append(escape(report.finalChainHash())).append("\n");
    csv.append("\n");
    csv.append("Nyckel,Titel,Status,Antal,Detalj,Rekommenderad export\n");
    for (ArchiveYearItem item : report.items()) {
      csv.append(escape(item.key())).append(",");
      csv.append(escape(item.title())).append(",");
      csv.append(escape(item.status())).append(",");
      csv.append(escape(item.count())).append(",");
      csv.append(escape(item.detail())).append(",");
      csv.append(escape(item.recommendedExport())).append("\n");
    }

    return csvResponse("arsarkivkontroll-" + report.year() + ".csv", csv);
  }

  @GetMapping("/vat-control/export")
  public ResponseEntity<byte[]> exportVatControl(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    VatControlReport report = accountingService.createVatControlReport(from, to);
    StringBuilder csv = new StringBuilder();
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Nyckeltal,Varde\n");
    csv.append("Verifikat,").append(report.voucherCount()).append("\n");
    csv.append("Forsaljning exkl moms,").append(report.totalSalesNet()).append("\n");
    csv.append("Utgaende moms 2611,").append(report.totalOutputVat()).append("\n");
    csv.append("Forvantad utgaende moms 25%,").append(report.expectedOutputVat()).append("\n");
    csv.append("Differens utgaende moms,").append(report.outputVatDifference()).append("\n");
    csv.append("Kostnader exkl moms,").append(report.totalPurchaseNet()).append("\n");
    csv.append("Ingaende moms 2641,").append(report.totalInputVat()).append("\n");
    csv.append("Max ingaende moms 25%,").append(report.expectedMaxInputVat()).append("\n");
    csv.append("Moms att betala/fa tillbaka,").append(report.vatToPay()).append("\n");
    csv.append("Kritiska,").append(report.criticalIssueCount()).append("\n");
    csv.append("Varningar,").append(report.warningIssueCount()).append("\n");
    csv.append("\n");
    csv.append("Status,Typ,Datum,Verifikat,Forsaljning,Utgaende moms,Forvantad utgaende moms,Kostnader,Ingaende moms,Max ingaende moms,Differens,Meddelande\n");
    for (VatControlIssue issue : report.issues()) {
      csv.append(escape(issue.severity())).append(",");
      csv.append(escape(issue.issueType())).append(",");
      csv.append(escape(issue.voucherDate() == null ? "" : issue.voucherDate().toString())).append(",");
      csv.append(escape(issue.voucherNumber())).append(",");
      csv.append(issue.salesNet()).append(",");
      csv.append(issue.outputVat()).append(",");
      csv.append(issue.expectedOutputVat()).append(",");
      csv.append(issue.purchaseNet()).append(",");
      csv.append(issue.inputVat()).append(",");
      csv.append(issue.expectedMaxInputVat()).append(",");
      csv.append(issue.difference()).append(",");
      csv.append(escape(issue.message())).append("\n");
    }

    auditService.record(
        "export",
        "vat_control",
        periodLabel(from, to),
        "vat_control_exported",
        periodLabel(from, to),
        "VAT control exported. Critical: " + report.criticalIssueCount()
            + ". Warnings: " + report.warningIssueCount() + ".",
        report.issues().size(),
        authorizationHeader
    );

    return csvResponse("moms-kontroll.csv", csv);
  }

  @GetMapping("/trial-balance/export")
  public ResponseEntity<byte[]> exportTrialBalance(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    TrialBalanceReport report = accountingService.createTrialBalanceReport(from, to);
    StringBuilder csv = new StringBuilder();
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Konto,Kontonamn,Ingaende debet,Ingaende kredit,Period debet,Period kredit,Utgaende debet,Utgaende kredit\n");
    for (TrialBalanceLine line : report.lines()) {
      csv.append(escape(line.accountNumber())).append(",");
      csv.append(escape(line.accountName())).append(",");
      csv.append(line.openingDebit()).append(",");
      csv.append(line.openingCredit()).append(",");
      csv.append(line.periodDebit()).append(",");
      csv.append(line.periodCredit()).append(",");
      csv.append(line.closingDebit()).append(",");
      csv.append(line.closingCredit()).append("\n");
    }
    csv.append("Totalt,,")
        .append(report.openingDebitTotal()).append(",")
        .append(report.openingCreditTotal()).append(",")
        .append(report.periodDebitTotal()).append(",")
        .append(report.periodCreditTotal()).append(",")
        .append(report.closingDebitTotal()).append(",")
        .append(report.closingCreditTotal()).append("\n");
    csv.append("Differens,,,,,,").append(report.difference()).append(",0\n");

    auditService.record(
        "export",
        "trial_balance",
        periodLabel(from, to),
        "trial_balance_exported",
        periodLabel(from, to),
        "Trial balance exported. Difference: " + report.difference() + ".",
        report.lines().size(),
        authorizationHeader
    );

    return csvResponse("trial-balance.csv", csv);
  }

  @GetMapping("/account-sign-control/export")
  public ResponseEntity<byte[]> exportAccountSignControl(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    AccountSignControlReport report = accountingService.createAccountSignControlReport(from, to);
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks kontoteckenkontroll\n");
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Genererad,").append(escape(report.generatedAt().toString())).append("\n");
    csv.append("Konton,").append(report.accountCount()).append("\n");
    csv.append("Kritiska,").append(report.criticalIssueCount()).append("\n");
    csv.append("Varningar,").append(report.warningIssueCount()).append("\n");
    csv.append("\n");
    csv.append("Status,Allvar,Konto,Kontonamn,Forvantat saldo,Bokfort saldo,Stoppar periodlasning,Meddelande\n");
    for (AccountSignControlLine line : report.lines()) {
      csv.append(escape(line.status())).append(",");
      csv.append(escape(line.severity())).append(",");
      csv.append(escape(line.accountNumber())).append(",");
      csv.append(escape(line.accountName())).append(",");
      csv.append(escape(line.expectedNature())).append(",");
      csv.append(line.balance()).append(",");
      csv.append(line.blocking() ? "Ja" : "Nej").append(",");
      csv.append(escape(line.message())).append("\n");
    }

    auditService.record(
        "export",
        "account_sign_control",
        periodLabel(from, to),
        "account_sign_control_exported",
        periodLabel(from, to),
        "Account sign control exported. Critical: " + report.criticalIssueCount()
            + ". Warnings: " + report.warningIssueCount() + ".",
        report.issueCount(),
        authorizationHeader
    );

    return csvResponse("kontoteckenkontroll.csv", csv);
  }

  @GetMapping("/general-ledger/export")
  public ResponseEntity<byte[]> exportGeneralLedger(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String accountNumber
  ) {
    authHeader.requireValidToken(authorizationHeader);

    GeneralLedgerReport report = accountingService.createGeneralLedgerReport(from, to, accountNumber);
    StringBuilder csv = new StringBuilder();
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Konto,Kontonamn,Radtyp,Datum,Verifikat,Kalla,Kallreferens,Underlagsstatus,Underlagskod SHA-256,Rattelse av,Beskrivning,Debet,Kredit,Lopande saldo,Integritetskod\n");
    for (GeneralLedgerAccount account : report.accounts()) {
      csv.append(escape(account.accountNumber())).append(",");
      csv.append(escape(account.accountName())).append(",");
      csv.append("Ingaende saldo,,,,,,,,,0,0,").append(account.openingBalance()).append(",\n");
      for (GeneralLedgerEntry entry : account.entries()) {
        csv.append(escape(account.accountNumber())).append(",");
        csv.append(escape(account.accountName())).append(",");
        csv.append("Transaktion,");
        csv.append(escape(entry.voucherDate() == null ? "" : entry.voucherDate().toString())).append(",");
        csv.append(escape(entry.voucherNumber())).append(",");
        csv.append(escape(entry.sourceType())).append(",");
        csv.append(escape(entry.sourceReference())).append(",");
        csv.append(escape(entry.evidenceStatus())).append(",");
        csv.append(escape(entry.evidenceHash())).append(",");
        csv.append(escape(entry.correctionOfVoucherNumber())).append(",");
        csv.append(escape(entry.description())).append(",");
        csv.append(entry.debit()).append(",");
        csv.append(entry.credit()).append(",");
        csv.append(entry.balance()).append(",");
        csv.append(escape(entry.integrityHash())).append("\n");
      }
      csv.append(escape(account.accountNumber())).append(",");
      csv.append(escape(account.accountName())).append(",");
      csv.append("Utgaende saldo,,,,,,,,,")
          .append(account.periodDebit()).append(",")
          .append(account.periodCredit()).append(",")
          .append(account.closingBalance()).append(",\n");
    }

    auditService.record(
        "export",
        "general_ledger",
        accountNumber == null || accountNumber.isBlank()
            ? periodLabel(from, to)
            : periodLabel(from, to) + " account " + accountNumber.trim(),
        "general_ledger_exported",
        periodLabel(from, to),
        "General ledger exported. Accounts: " + report.accountCount()
            + ". Entries: " + report.entryCount() + ".",
        report.entryCount(),
        authorizationHeader
    );

    return csvResponse(accountNumber == null || accountNumber.isBlank() ? "huvudbok.csv" : "huvudbok-" + accountNumber.trim() + ".csv", csv);
  }

  @GetMapping("/sie/export")
  public ResponseEntity<byte[]> exportSie(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    String sie = accountingService.createSieExport(from, to);
    SieExportReceipt receipt = accountingService.createSieExportReceipt(from, to);
    auditService.record(
        "export",
        "handoff_export",
        receipt.exportControlHash(),
        "sie_exported",
        periodLabel(receipt.periodFrom(), receipt.periodTo()),
        "SIE file exported with control hash " + receipt.exportControlHash(),
        receipt.entryCount(),
        authorizationHeader
    );
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=alibooks-sie.se")
        .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
        .body(sie.getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/sie/receipt/export")
  public ResponseEntity<byte[]> exportSieReceipt(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    SieExportReceipt receipt = accountingService.createSieExportReceipt(from, to);
    auditService.record(
        "export",
        "handoff_export",
        receipt.exportControlHash(),
        "sie_receipt_exported",
        periodLabel(receipt.periodFrom(), receipt.periodTo()),
        "SIE export receipt exported with control hash " + receipt.exportControlHash(),
        receipt.entryCount(),
        authorizationHeader
    );
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks SIE-exportkvittens\n");
    csv.append("Genererad,").append(escape(receipt.generatedAt().toString())).append("\n");
    csv.append("Foretag,").append(escape(receipt.companyName())).append("\n");
    csv.append("Period,").append(escape(receipt.periodFrom() + " - " + receipt.periodTo())).append("\n");
    csv.append("Status,").append(receipt.exportReady() ? "Redo" : "Inte redo").append("\n");
    csv.append("Meddelande,").append(escape(receipt.message())).append("\n");
    csv.append("Kontrollkod,").append(escape(receipt.exportControlHash())).append("\n");
    csv.append("\n");
    csv.append("Nyckeltal,Varde\n");
    csv.append("Verifikat,").append(receipt.voucherCount()).append("\n");
    csv.append("Bokforingsrader,").append(receipt.entryCount()).append("\n");
    csv.append("Konton,").append(receipt.accountCount()).append("\n");
    csv.append("Debet,").append(receipt.totalDebit()).append("\n");
    csv.append("Kredit,").append(receipt.totalCredit()).append("\n");
    csv.append("Differens,").append(receipt.difference()).append("\n");
    csv.append("Kritiska verifikationspunkter,").append(receipt.criticalIssueCount()).append("\n");
    csv.append("Varningar,").append(receipt.warningIssueCount()).append("\n");
    csv.append("Obalanserade verifikat,").append(receipt.unbalancedVoucherCount()).append("\n");
    csv.append("Underlagsvarningar,").append(receipt.missingEvidenceCount()).append("\n");
    csv.append("Periodstampel,").append(escape(receipt.periodFingerprint())).append("\n");
    csv.append("Slutlig kedjekod,").append(escape(receipt.finalChainHash())).append("\n");

    return csvResponse("sie-export-kvittens.csv", csv);
  }

  @GetMapping("/voucher-control/export")
  public ResponseEntity<byte[]> exportVoucherControl(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    VoucherControlReport report = accountingService.createVoucherControlReport(from, to);
    StringBuilder csv = new StringBuilder();
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Nyckeltal,Varde\n");
    csv.append("Verifikat,").append(report.voucherCount()).append("\n");
    csv.append("Bokforingsrader,").append(report.entryCount()).append("\n");
    csv.append("Balanserade verifikat,").append(report.balancedVoucherCount()).append("\n");
    csv.append("Obalanserade verifikat,").append(report.unbalancedVoucherCount()).append("\n");
    csv.append("Ateranvanda verifikationsnummer,").append(report.reusedVoucherNumberCount()).append("\n");
    csv.append("Saknade verifikationsnummer,").append(report.missingVoucherNumberCount()).append("\n");
    csv.append("Fel format,").append(report.invalidVoucherNumberCount()).append("\n");
    csv.append("Glapp,").append(report.gapCount()).append("\n");
    csv.append("Datumordningsvarningar,").append(report.dateOrderIssueCount()).append("\n");
    csv.append("Kritiska,").append(report.criticalIssueCount()).append("\n");
    csv.append("Varningar,").append(report.warningIssueCount()).append("\n");
    csv.append("\n");
    csv.append("Status,Typ,Verifikat,Serie,Forvantat,Aktuellt,Datum,Debet,Kredit,Meddelande\n");
    for (VoucherControlIssue issue : report.issues()) {
      csv.append(escape(issue.severity())).append(",");
      csv.append(escape(issue.issueType())).append(",");
      csv.append(escape(issue.voucherNumber())).append(",");
      csv.append(escape(issue.voucherSeries())).append(",");
      csv.append(issue.expectedNumber() == null ? "" : issue.expectedNumber()).append(",");
      csv.append(issue.actualNumber() == null ? "" : issue.actualNumber()).append(",");
      csv.append(escape(issue.voucherDate() == null ? "" : issue.voucherDate().toString())).append(",");
      csv.append(issue.debit()).append(",");
      csv.append(issue.credit()).append(",");
      csv.append(escape(issue.message())).append("\n");
    }

    auditService.record(
        "export",
        "voucher_control",
        periodLabel(from, to),
        "voucher_control_exported",
        periodLabel(from, to),
        "Voucher control exported. Critical: " + report.criticalIssueCount()
            + ". Warnings: " + report.warningIssueCount() + ".",
        report.issues().size(),
        authorizationHeader
    );

    return csvResponse("verifikationskontroll.csv", csv);
  }

  @GetMapping("/journal-integrity/export")
  public ResponseEntity<byte[]> exportJournalIntegrity(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);

    JournalIntegrityReport report = accountingService.createJournalIntegrityReport(from, to);
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks bokforingskedja\n");
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Genererad,").append(escape(report.generatedAt().toString())).append("\n");
    csv.append("Bokforingsrader,").append(report.entryCount()).append("\n");
    csv.append("Verifikat,").append(report.voucherCount()).append("\n");
    csv.append("Debet,").append(report.totalDebit()).append("\n");
    csv.append("Kredit,").append(report.totalCredit()).append("\n");
    csv.append("Differens,").append(report.difference()).append("\n");
    csv.append("Underlagsvarningar,").append(report.missingEvidenceCount()).append("\n");
    csv.append("Forsta kedjekod,").append(escape(report.firstChainHash())).append("\n");
    csv.append("Slutlig kedjekod,").append(escape(report.finalChainHash())).append("\n");
    csv.append("Periodstampel,").append(escape(report.periodFingerprint())).append("\n");
    csv.append("\n");
    csv.append("Nr,Rad-ID,Datum,Verifikat,Konto,Kontonamn,Beskrivning,Kalla,Kallreferens,Underlagsstatus,Underlagskod SHA-256,Debet,Kredit,Radkod,Foregaende kedjekod,Kedjekod\n");
    for (JournalIntegrityLine line : report.lines()) {
      csv.append(line.sequenceNumber()).append(",");
      csv.append(line.journalEntryId() == null ? "" : line.journalEntryId()).append(",");
      csv.append(escape(line.voucherDate() == null ? "" : line.voucherDate().toString())).append(",");
      csv.append(escape(line.voucherNumber())).append(",");
      csv.append(escape(line.accountNumber())).append(",");
      csv.append(escape(line.accountName())).append(",");
      csv.append(escape(line.description())).append(",");
      csv.append(escape(line.sourceType())).append(",");
      csv.append(escape(line.sourceReference())).append(",");
      csv.append(escape(line.evidenceStatus())).append(",");
      csv.append(escape(line.evidenceHash())).append(",");
      csv.append(line.debit()).append(",");
      csv.append(line.credit()).append(",");
      csv.append(escape(line.rowHash())).append(",");
      csv.append(escape(line.previousChainHash())).append(",");
      csv.append(escape(line.chainHash())).append("\n");
    }

    auditService.record(
        "export",
        "journal_integrity",
        report.periodFingerprint(),
        "journal_integrity_exported",
        periodLabel(from, to),
        "Journal integrity exported. Entries: " + report.entryCount()
            + ". Difference: " + report.difference()
            + ". Missing evidence: " + report.missingEvidenceCount() + ".",
        report.entryCount(),
        authorizationHeader
    );

    return csvResponse("bokforingskedja-integritet.csv", csv);
  }

  private void appendReportLines(StringBuilder csv, String reportName, String rowType, List<ReportLine> lines) {
    for (ReportLine line : lines) {
      csv.append(escape(reportName)).append(",");
      csv.append(escape(rowType)).append(",");
      csv.append(escape(line.accountNumber())).append(",");
      csv.append(escape(line.accountName())).append(",");
      csv.append(line.amount()).append("\n");
    }
  }

  private ResponseEntity<byte[]> csvResponse(String filename, StringBuilder csv) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(safeDownloadFilename(filename), StandardCharsets.UTF_8)
            .build()
            .toString())
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String safeDownloadFilename(String filename) {
    String clean = filename == null ? "" : filename.trim()
        .replace('\r', '_')
        .replace('\n', '_')
        .replace('\\', '_')
        .replace('/', '_');
    return clean.isBlank() ? "alibooks-export.csv" : clean;
  }

  private boolean isWithinPeriod(LocalDate voucherDate, LocalDate periodFrom, LocalDate periodTo) {
    if (periodFrom == null && periodTo == null) {
      return true;
    }
    if (voucherDate == null) {
      return false;
    }
    if (periodFrom != null && voucherDate.isBefore(periodFrom)) {
      return false;
    }
    return periodTo == null || !voucherDate.isAfter(periodTo);
  }

  private String periodLabel(LocalDate from, LocalDate to) {
    return (from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString());
  }

  private String escape(String value) {
    return CsvEscaper.escape(value);
  }

  private int reportWholeKrona(long amountMinor, String field) {
    if (amountMinor % 100L != 0) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Journal export contains ore in " + field + ". Reconcile the voucher before exporting.");
    }
    try {
      return Math.toIntExact(amountMinor / 100L);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Journal export amount is outside the supported whole-krona range.", exception);
    }
  }
}
