package se.cloudshop.accounting;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.export.CsvEscaper;

@RestController
public class ProfitAndLossMinorController {
  private final ProfitAndLossMinorService service;
  private final AuthHeader authHeader;
  private final AuditService audit;

  public ProfitAndLossMinorController(ProfitAndLossMinorService service, AuthHeader authHeader, AuditService audit) {
    this.service = service;
    this.authHeader = authHeader;
    this.audit = audit;
  }

  @GetMapping("/v2/profit-and-loss")
  public ProfitAndLossMinorReport report(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    authHeader.requireValidToken(authorization);
    return service.create(from, to);
  }

  @GetMapping("/v2/profit-and-loss/export")
  public ResponseEntity<byte[]> export(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    ProfitAndLossMinorReport report = report(authorization, from, to);
    StringBuilder csv = new StringBuilder("PeriodFrom,PeriodTo,Currency,MinorUnitExponent,Category,Account,AccountName,AmountMinor\n");
    String prefix = (from == null ? "" : from) + "," + (to == null ? "" : to) + ","
        + report.currencyCode() + "," + report.minorUnitExponent() + ",";
    for (ProfitAndLossMinorReport.Line line : report.revenue()) {
      appendLine(csv, prefix, "Revenue", line);
    }
    for (ProfitAndLossMinorReport.Line line : report.expenses()) {
      appendLine(csv, prefix, "Expense", line);
    }
    csv.append(prefix).append("TOTAL_REVENUE,,,").append(report.totalRevenueMinor()).append('\n');
    csv.append(prefix).append("TOTAL_EXPENSES,,,").append(report.totalExpensesMinor()).append('\n');
    csv.append(prefix).append("RESULT,,,").append(report.resultMinor()).append('\n');
    String period = (from == null ? "" : from) + " - " + (to == null ? "" : to);
    int accountCount = report.revenue().size() + report.expenses().size();
    audit.record("export", "profit_and_loss", period, "profit_and_loss_minor_exported", period,
        "Profit and loss exported in minor units (SEK, exponent 2). Accounts: " + accountCount,
        accountCount, authorization);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=profit-and-loss-minor.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static void appendLine(StringBuilder csv, String prefix, String category,
      ProfitAndLossMinorReport.Line line) {
    csv.append(prefix).append(category).append(',')
        .append(CsvEscaper.escape(line.accountNumber())).append(',')
        .append(CsvEscaper.escape(line.accountName())).append(',')
        .append(line.amountMinor()).append('\n');
  }
}
