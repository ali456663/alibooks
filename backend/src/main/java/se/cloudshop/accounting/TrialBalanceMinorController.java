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
public class TrialBalanceMinorController {
  private final TrialBalanceMinorService service;
  private final AuthHeader authHeader;
  private final AuditService audit;

  public TrialBalanceMinorController(TrialBalanceMinorService service, AuthHeader authHeader, AuditService audit) {
    this.service = service;
    this.authHeader = authHeader;
    this.audit = audit;
  }

  @GetMapping("/v2/trial-balance")
  public TrialBalanceMinorReport report(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    authHeader.requireValidToken(authorization);
    return service.create(from, to);
  }

  @GetMapping("/v2/trial-balance/export")
  public ResponseEntity<byte[]> export(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    TrialBalanceMinorReport report = report(authorization, from, to);
    StringBuilder csv = new StringBuilder("PeriodFrom,PeriodTo,Currency,MinorUnitExponent,Account,AccountName,OpeningDebitMinor,OpeningCreditMinor,PeriodDebitMinor,PeriodCreditMinor,ClosingDebitMinor,ClosingCreditMinor,DifferenceMinor\n");
    String prefix = (from == null ? "" : from) + "," + (to == null ? "" : to) + ",SEK,2,";
    for (TrialBalanceMinorReport.Line line : report.lines()) {
      csv.append(prefix).append(CsvEscaper.escape(line.accountNumber())).append(',')
          .append(CsvEscaper.escape(line.accountName())).append(',')
          .append(line.openingDebitMinor()).append(',').append(line.openingCreditMinor()).append(',')
          .append(line.periodDebitMinor()).append(',').append(line.periodCreditMinor()).append(',')
          .append(line.closingDebitMinor()).append(',').append(line.closingCreditMinor()).append(",\n");
    }
    csv.append(prefix).append("TOTAL,,")
        .append(report.openingDebitTotalMinor()).append(',').append(report.openingCreditTotalMinor()).append(',')
        .append(report.periodDebitTotalMinor()).append(',').append(report.periodCreditTotalMinor()).append(',')
        .append(report.closingDebitTotalMinor()).append(',').append(report.closingCreditTotalMinor()).append(',')
        .append(report.differenceMinor()).append('\n');
    String period = (from == null ? "" : from) + " - " + (to == null ? "" : to);
    audit.record("export", "trial_balance", period, "trial_balance_minor_exported", period,
        "Trial balance exported in minor units (SEK, exponent 2). Accounts: " + report.lines().size(),
        report.lines().size(), authorization);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trial-balance-minor.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }
}
