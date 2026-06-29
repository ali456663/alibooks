package se.cloudshop.accounting;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class AccountingExportController {

  private final AuthHeader authHeader;
  private final AccountingService accountingService;

  public AccountingExportController(AuthHeader authHeader, AccountingService accountingService) {
    this.authHeader = authHeader;
    this.accountingService = accountingService;
  }

  @GetMapping("/journal-entries/export")
  public ResponseEntity<byte[]> exportJournalEntries(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);

    List<JournalEntry> entries = accountingService.findAllEntries();
    StringBuilder csv = new StringBuilder();
    csv.append("Verifikat,Datum,Konto,Kontonamn,Beskrivning,Debet,Kredit\n");

    for (JournalEntry entry : entries) {
      csv.append(escape(entry.getVoucherNumber())).append(",");
      csv.append(escape(entry.getVoucherDate() == null ? "" : entry.getVoucherDate().toString())).append(",");
      csv.append(escape(entry.getAccountNumber())).append(",");
      csv.append(escape(entry.getAccountName())).append(",");
      csv.append(escape(entry.getDescription())).append(",");
      csv.append(entry.getDebit()).append(",");
      csv.append(entry.getCredit()).append("\n");
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=journal-entries.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/profit-and-loss/export")
  public ResponseEntity<byte[]> exportProfitAndLoss(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);

    ProfitAndLossReport report = accountingService.createProfitAndLossReport();
    StringBuilder csv = new StringBuilder();
    csv.append("Rapport,Rad,Konto,Kontonamn,Belopp\n");
    appendReportLines(csv, "Resultatrapport", "Intakter", report.revenue());
    appendReportLines(csv, "Resultatrapport", "Kostnader", report.expenses());
    csv.append("Resultatrapport,Totalt,,Intakter,").append(report.totalRevenue()).append("\n");
    csv.append("Resultatrapport,Totalt,,Kostnader,").append(report.totalExpenses()).append("\n");
    csv.append("Resultatrapport,Resultat,,Resultat,").append(report.result()).append("\n");

    return csvResponse("profit-and-loss.csv", csv);
  }

  @GetMapping("/balance-report/export")
  public ResponseEntity<byte[]> exportBalanceReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);

    BalanceReport report = accountingService.createBalanceReport();
    StringBuilder csv = new StringBuilder();
    csv.append("Rapport,Rad,Konto,Kontonamn,Belopp\n");
    appendReportLines(csv, "Balansrapport", "Tillgangar", report.assets());
    appendReportLines(csv, "Balansrapport", "Skulder och eget kapital", report.liabilitiesAndEquity());
    csv.append("Balansrapport,Totalt,,Tillgangar,").append(report.totalAssets()).append("\n");
    csv.append("Balansrapport,Totalt,,Skulder och eget kapital,").append(report.totalLiabilitiesAndEquity()).append("\n");
    csv.append("Balansrapport,Skillnad,,Skillnad,").append(report.difference()).append("\n");

    return csvResponse("balance-report.csv", csv);
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
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }

    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
