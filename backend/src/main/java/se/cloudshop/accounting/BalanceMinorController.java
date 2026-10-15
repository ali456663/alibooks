package se.cloudshop.accounting;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
public class BalanceMinorController {
  private final BalanceMinorService service;
  private final AuthHeader authHeader;
  private final AuditService audit;

  public BalanceMinorController(BalanceMinorService service, AuthHeader authHeader, AuditService audit) {
    this.service = service;
    this.authHeader = authHeader;
    this.audit = audit;
  }

  @GetMapping("/v2/balance-report")
  public BalanceMinorReport report(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    authHeader.requireValidToken(authorization);
    return service.create(to);
  }

  @GetMapping("/v2/balance-report/export")
  public ResponseEntity<byte[]> export(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    BalanceMinorReport report = report(authorization, to);
    if (report.differenceMinor() != 0) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Balansrapporten har differens i oren. Exporten har stoppats.");
    }
    StringBuilder csv = new StringBuilder("AsOfDate,Currency,MinorUnitExponent,Category,Account,AccountName,AmountMinor\n");
    String prefix = (to == null ? "" : to) + "," + report.currencyCode() + "," + report.minorUnitExponent() + ",";
    for (BalanceMinorReport.Line line : report.assets()) {
      appendLine(csv, prefix, "Asset", line);
    }
    for (BalanceMinorReport.Line line : report.liabilitiesAndEquity()) {
      appendLine(csv, prefix, "LiabilityOrEquity", line);
    }
    csv.append(prefix).append("TOTAL_ASSETS,,,").append(report.totalAssetsMinor()).append('\n');
    csv.append(prefix).append("TOTAL_LIABILITIES_AND_EQUITY,,,")
        .append(report.totalLiabilitiesAndEquityMinor()).append('\n');
    csv.append(prefix).append("DIFFERENCE,,,").append(report.differenceMinor()).append('\n');
    int accountCount = report.assets().size() + report.liabilitiesAndEquity().size();
    audit.record("export", "balance_report", to == null ? "" : to.toString(),
        "balance_report_minor_exported", to == null ? "" : to.toString(),
        "Balance report exported in minor units (SEK, exponent 2). Accounts: " + accountCount,
        accountCount, authorization);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=balance-report-minor.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static void appendLine(StringBuilder csv, String prefix, String category,
      BalanceMinorReport.Line line) {
    csv.append(prefix).append(category).append(',')
        .append(CsvEscaper.escape(line.accountNumber())).append(',')
        .append(CsvEscaper.escape(line.accountName())).append(',')
        .append(line.amountMinor()).append('\n');
  }
}
