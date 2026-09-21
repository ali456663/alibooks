package se.cloudshop.order;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.accounting.ReportAmounts;
import se.cloudshop.export.CsvEscaper;

@RestController
public class InvoiceExportController {

  private final OrderRepository orderRepository;
  private final AuthHeader authHeader;
  private final AuditService auditService;

  public InvoiceExportController(OrderRepository orderRepository, AuthHeader authHeader, AuditService auditService) {
    this.orderRepository = orderRepository;
    this.authHeader = authHeader;
    this.auditService = auditService;
  }

  @GetMapping("/invoices/export")
  public ResponseEntity<byte[]> exportInvoices(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);

    StringBuilder csv = new StringBuilder();
    csv.append("Id,Fakturanummer,Fakturadatum,Forfallodatum,Kund,Status,Antal,Netto,Moms,Totalt,NettoMinor,MomsMinor,TotaltMinor,OCR,PlusGiro,Kreditfaktura,Krediterar faktura\n");

    int exportedCount = 0;
    long totalAmountMinor = 0;
    for (Order invoice : orderRepository.findAll()) {
      csv.append(invoice.getId()).append(",");
      csv.append(escape(invoice.getInvoiceNumber())).append(",");
      csv.append(escape(invoice.getInvoiceDate() == null ? "" : invoice.getInvoiceDate().toString())).append(",");
      csv.append(escape(invoice.getDueDate() == null ? "" : invoice.getDueDate().toString())).append(",");
      csv.append(escape(invoice.getCustomerName())).append(",");
      csv.append(escape(invoice.getStatus())).append(",");
      csv.append(invoice.getQuantity()).append(",");
      long netAmountMinor = minorOrWholeKrona(invoice.getNetAmountMinor(), invoice.getNetAmount());
      long vatAmountMinor = minorOrWholeKrona(invoice.getVatAmountMinor(), invoice.getVatAmount());
      long invoiceTotalAmountMinor = minorOrWholeKrona(invoice.getTotalAmountMinor(), invoice.getTotalAmount());
      csv.append(reportWholeKrona(netAmountMinor)).append(",");
      csv.append(reportWholeKrona(vatAmountMinor)).append(",");
      csv.append(reportWholeKrona(invoiceTotalAmountMinor)).append(",");
      csv.append(netAmountMinor).append(",");
      csv.append(vatAmountMinor).append(",");
      csv.append(invoiceTotalAmountMinor).append(",");
      csv.append(escape(invoice.getOcrNumber())).append(",");
      csv.append(escape(invoice.getPlusGiro())).append(",");
      csv.append(invoice.isCreditInvoice()).append(",");
      csv.append(invoice.getCreditedInvoiceId() == null ? "" : invoice.getCreditedInvoiceId()).append("\n");
      exportedCount++;
      totalAmountMinor = Math.addExact(totalAmountMinor, invoiceTotalAmountMinor);
    }

    int totalAmount = reportWholeKrona(totalAmountMinor);

    auditService.record(
        "export",
        "invoices",
        "invoices",
        "invoices_exported",
        "invoices",
        "Invoices exported. Rows: " + exportedCount + ".",
        ReportAmounts.reportAmount(totalAmount),
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoices.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String escape(String value) {
    return CsvEscaper.escape(value);
  }

  private long minorOrWholeKrona(Long minor, int wholeKrona) {
    return minor == null ? Math.multiplyExact((long) wholeKrona, 100L) : minor;
  }

  private int reportWholeKrona(long amountMinor) {
    if (amountMinor % 100L != 0L) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "Fakturaexporten innehaller oren som den nuvarande rapportrevisionen inte kan representera.");
    }
    return ReportAmounts.reportAmount(amountMinor / 100L);
  }
}
