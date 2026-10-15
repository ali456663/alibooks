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
public class JournalMinorController {
  private final JournalMinorService service;
  private final AuthHeader authHeader;
  private final AuditService audit;

  public JournalMinorController(JournalMinorService service, AuthHeader authHeader, AuditService audit) {
    this.service = service;
    this.authHeader = authHeader;
    this.audit = audit;
  }

  @GetMapping("/v2/journal-entries")
  public JournalMinorReport report(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    authHeader.requireValidToken(authorization);
    return service.create(from, to);
  }

  @GetMapping("/v2/journal-entries/export")
  public ResponseEntity<byte[]> export(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    JournalMinorReport report = report(authorization, from, to);
    if (!report.voucherDifferences().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Grundboken kan inte exporteras: ett eller flera verifikat ar obalanserade i valt intervall.");
    }
    StringBuilder csv = new StringBuilder("PeriodFrom,PeriodTo,Currency,MinorUnitExponent,JournalEntryId,VoucherDate,VoucherNumber,CreatedAt,Account,AccountName,Description,CorrectionOfVoucherNumber,VoucherEvidenceId,DebitMinor,CreditMinor,DifferenceMinor\n");
    String prefix = (from == null ? "" : from) + "," + (to == null ? "" : to) + ",SEK,2,";
    for (JournalMinorReport.Entry entry : report.entries()) {
      csv.append(prefix).append(CsvEscaper.escape(entry.journalEntryId())).append(',')
          .append(entry.voucherDate()).append(',').append(CsvEscaper.escape(entry.voucherNumber())).append(',')
          .append(entry.createdAt() == null ? "" : entry.createdAt()).append(',')
          .append(CsvEscaper.escape(entry.accountNumber())).append(',')
          .append(CsvEscaper.escape(entry.accountName())).append(',')
          .append(CsvEscaper.escape(entry.description())).append(',')
          .append(CsvEscaper.escape(entry.correctionOfVoucherNumber())).append(',')
          .append(entry.voucherEvidenceId() == null ? "" : entry.voucherEvidenceId()).append(',')
          .append(entry.debitMinor()).append(',').append(entry.creditMinor()).append(",\n");
    }
    csv.append(prefix).append("TOTAL,,,,,,,,,")
        .append(report.debitTotalMinor()).append(',').append(report.creditTotalMinor()).append(',')
        .append(report.differenceMinor()).append('\n');
    String period = (from == null ? "" : from) + " - " + (to == null ? "" : to);
    audit.record("export", "journal_entries", period, "journal_minor_exported", period,
        "Journal exported in registration order and minor units (SEK, exponent 2). Entries: "
            + report.entries().size(), report.entries().size(), authorization);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=journal-entries-minor.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }
}
