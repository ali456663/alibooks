package se.cloudshop.supplier;

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
public class PayablesReportController {

  private final AuthHeader authHeader;
  private final PayablesReportService payablesReportService;
  private final AuditService auditService;

  public PayablesReportController(AuthHeader authHeader, PayablesReportService payablesReportService, AuditService auditService) {
    this.authHeader = authHeader;
    this.payablesReportService = payablesReportService;
    this.auditService = auditService;
  }

  @GetMapping("/payables/aging")
  public PayablesAgingReport getPayablesAgingReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return payablesReportService.createAgingReport(asOf);
  }

  @GetMapping("/payables/aging/export")
  public ResponseEntity<byte[]> exportPayablesAgingReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
  ) {
    authHeader.requireValidToken(authorizationHeader);

    PayablesAgingReport report = payablesReportService.createAgingReport(asOf);
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks leverantorsreskontra aldringsanalys\n");
    csv.append("Avstamningsdatum,").append(report.asOf()).append("\n");
    csv.append("Oppna leverantorsfakturor,").append(report.invoiceCount()).append("\n");
    csv.append("Totalt att betala,").append(report.totalOutstanding()).append("\n");
    csv.append("Ej forfallet,").append(report.notDueOutstanding()).append("\n");
    csv.append("Forfallet,").append(report.overdueOutstanding()).append("\n");
    csv.append("Forfaller inom 5 dagar,").append(report.dueSoonOutstanding()).append("\n");
    csv.append("Utan forfallodatum,").append(report.noDueDateOutstanding()).append("\n");
    csv.append("Ingaende moms i oppna fakturor,").append(report.inputVatOutstanding()).append("\n");
    csv.append("\n");
    csv.append("Grupp,Antal,Summa,Aldsta forfallodatum\n");
    for (PayablesAgingBucket bucket : report.buckets()) {
      csv.append(escape(bucket.title())).append(",");
      csv.append(bucket.invoiceCount()).append(",");
      csv.append(bucket.totalRemaining()).append(",");
      csv.append(bucket.oldestDueDate() == null ? "" : bucket.oldestDueDate()).append("\n");
    }
    csv.append("\n");
    csv.append("Leverantor,E-post,Org/personnummer,Fakturadatum,Forfallodatum,Status,Referens,Beskrivning,Kategori,Netto,Moms,Totalt,Kvar,Dagar forfallen,Grupp,Betalning rekommenderas\n");
    for (PayablesAgingInvoice invoice : report.invoices()) {
      csv.append(escape(invoice.supplierName())).append(",");
      csv.append(escape(invoice.supplierEmail())).append(",");
      csv.append(escape(invoice.supplierOrgNumber())).append(",");
      csv.append(invoice.invoiceDate() == null ? "" : invoice.invoiceDate()).append(",");
      csv.append(invoice.dueDate() == null ? "" : invoice.dueDate()).append(",");
      csv.append(escape(invoice.status())).append(",");
      csv.append(escape(invoice.reference())).append(",");
      csv.append(escape(invoice.description())).append(",");
      csv.append(escape(invoice.category())).append(",");
      csv.append(invoice.netAmount()).append(",");
      csv.append(invoice.vatAmount()).append(",");
      csv.append(invoice.totalAmount()).append(",");
      csv.append(invoice.remainingAmount()).append(",");
      csv.append(invoice.daysOverdue()).append(",");
      csv.append(escape(invoice.bucketTitle())).append(",");
      csv.append(invoice.paymentRecommended() ? "Ja" : "Nej").append("\n");
    }

    auditService.record(
        "export",
        "payables_aging",
        String.valueOf(report.asOf()),
        "payables_aging_exported",
        String.valueOf(report.asOf()),
        "Payables aging exported. Outstanding: " + report.totalOutstanding()
            + ". Overdue: " + report.overdueOutstanding() + ".",
        report.invoiceCount(),
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leverantorsreskontra-aging.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String escape(String value) {
    return CsvEscaper.escape(value);
  }
}
