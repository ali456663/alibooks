package se.cloudshop.order;

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
public class ReceivablesReportController {

  private final AuthHeader authHeader;
  private final ReceivablesReportService receivablesReportService;
  private final AuditService auditService;

  public ReceivablesReportController(AuthHeader authHeader, ReceivablesReportService receivablesReportService, AuditService auditService) {
    this.authHeader = authHeader;
    this.receivablesReportService = receivablesReportService;
    this.auditService = auditService;
  }

  @GetMapping("/receivables/aging")
  public ReceivablesAgingReport getReceivablesAgingReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return receivablesReportService.createAgingReport(asOf);
  }

  @GetMapping("/receivables/aging/export")
  public ResponseEntity<byte[]> exportReceivablesAgingReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
  ) {
    authHeader.requireValidToken(authorizationHeader);

    ReceivablesAgingReport report = receivablesReportService.createAgingReport(asOf);
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks kundreskontra aldringsanalys\n");
    csv.append("Avstamningsdatum,").append(report.asOf()).append("\n");
    csv.append("Oppna fakturor,").append(report.invoiceCount()).append("\n");
    csv.append("Totalt utestaende,").append(report.totalOutstanding()).append("\n");
    csv.append("Ej forfallet,").append(report.notDueOutstanding()).append("\n");
    csv.append("Forfallet,").append(report.overdueOutstanding()).append("\n");
    csv.append("Forfaller inom 5 dagar,").append(report.dueSoonOutstanding()).append("\n");
    csv.append("Utan forfallodatum,").append(report.noDueDateOutstanding()).append("\n");
    csv.append("\n");
    csv.append("Grupp,Antal,Summa,Aldsta forfallodatum\n");
    for (ReceivablesAgingBucket bucket : report.buckets()) {
      csv.append(escape(bucket.title())).append(",");
      csv.append(bucket.invoiceCount()).append(",");
      csv.append(bucket.totalRemaining()).append(",");
      csv.append(bucket.oldestDueDate() == null ? "" : bucket.oldestDueDate()).append("\n");
    }
    csv.append("\n");
    csv.append("Faktura,Kund,E-post,Fakturadatum,Forfallodatum,Status,Total,Betalt,Kvar,Dagar forfallen,Grupp,Paminnelse rekommenderas\n");
    for (ReceivablesAgingInvoice invoice : report.invoices()) {
      csv.append(escape(invoice.invoiceNumber())).append(",");
      csv.append(escape(invoice.customerName())).append(",");
      csv.append(escape(invoice.customerEmail())).append(",");
      csv.append(invoice.invoiceDate() == null ? "" : invoice.invoiceDate()).append(",");
      csv.append(invoice.dueDate() == null ? "" : invoice.dueDate()).append(",");
      csv.append(escape(invoice.status())).append(",");
      csv.append(invoice.totalAmount()).append(",");
      csv.append(invoice.paidAmount()).append(",");
      csv.append(invoice.remainingAmount()).append(",");
      csv.append(invoice.daysOverdue()).append(",");
      csv.append(escape(invoice.bucketTitle())).append(",");
      csv.append(invoice.reminderRecommended() ? "Ja" : "Nej").append("\n");
    }

    auditService.record(
        "export",
        "receivables_aging",
        String.valueOf(report.asOf()),
        "receivables_aging_exported",
        String.valueOf(report.asOf()),
        "Receivables aging exported. Outstanding: " + report.totalOutstanding()
            + ". Overdue: " + report.overdueOutstanding() + ".",
        report.invoiceCount(),
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=kundreskontra-aging.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String escape(String value) {
    return CsvEscaper.escape(value);
  }
}
