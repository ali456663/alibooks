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
public class GeneralLedgerMinorController {
  private final GeneralLedgerMinorService service;
  private final AuthHeader authHeader;
  private final AuditService audit;

  public GeneralLedgerMinorController(GeneralLedgerMinorService service, AuthHeader authHeader, AuditService audit) {
    this.service = service;
    this.authHeader = authHeader;
    this.audit = audit;
  }

  @GetMapping("/v2/general-ledger")
  public GeneralLedgerMinorReport report(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String accountNumber) {
    authHeader.requireValidToken(authorization);
    return service.create(from, to, accountNumber);
  }

  @GetMapping("/v2/general-ledger/export")
  public ResponseEntity<byte[]> export(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String accountNumber) {
    GeneralLedgerMinorReport report = report(authorization, from, to, accountNumber);
    StringBuilder csv = new StringBuilder("PeriodFrom,PeriodTo,Currency,MinorUnitExponent,Account,AccountName,JournalEntryId,VoucherDate,VoucherNumber,CreatedAt,CorrectionOfVoucherNumber,Description,OpeningBalanceMinor,DebitMinor,CreditMinor,RunningBalanceMinor,ClosingBalanceMinor\n");
    String prefix = (from == null ? "" : from) + "," + (to == null ? "" : to) + ",SEK,2,";
    for (GeneralLedgerMinorReport.Account account : report.accounts()) {
      String accountColumns = CsvEscaper.escape(account.accountNumber()) + ","
          + CsvEscaper.escape(account.accountName()) + ",";
      if (account.entries().isEmpty()) {
        csv.append(prefix).append(accountColumns).append("-,,,,,,")
            .append(account.openingBalanceMinor()).append(",0,0,,")
            .append(account.closingBalanceMinor()).append('\n');
      }
      for (GeneralLedgerMinorReport.Entry entry : account.entries()) {
        csv.append(prefix).append(accountColumns).append(CsvEscaper.escape(entry.journalEntryId())).append(',')
            .append(entry.voucherDate()).append(',').append(CsvEscaper.escape(entry.voucherNumber())).append(',')
            .append(entry.createdAt() == null ? "" : entry.createdAt()).append(',')
            .append(CsvEscaper.escape(entry.correctionOfVoucherNumber())).append(',')
            .append(CsvEscaper.escape(entry.description())).append(',')
            .append(account.openingBalanceMinor()).append(',').append(entry.debitMinor()).append(',')
            .append(entry.creditMinor()).append(',').append(entry.runningBalanceMinor()).append(',')
            .append(account.closingBalanceMinor()).append('\n');
      }
    }
    audit.record("export", "general_ledger", (from == null ? "" : from) + " - " + (to == null ? "" : to),
        "general_ledger_minor_exported", report.selectedAccountNumber() == null ? "all" : report.selectedAccountNumber(),
        "General ledger exported in minor units (SEK, exponent 2). Entries: " + report.entryCount(),
        report.entryCount(), authorization);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=general-ledger-minor.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }
}
