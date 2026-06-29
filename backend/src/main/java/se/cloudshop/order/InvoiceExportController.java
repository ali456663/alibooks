package se.cloudshop.order;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class InvoiceExportController {

  private final OrderRepository orderRepository;
  private final AuthHeader authHeader;

  public InvoiceExportController(OrderRepository orderRepository, AuthHeader authHeader) {
    this.orderRepository = orderRepository;
    this.authHeader = authHeader;
  }

  @GetMapping("/invoices/export")
  public ResponseEntity<byte[]> exportInvoices(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);

    StringBuilder csv = new StringBuilder();
    csv.append("Id,Fakturanummer,Fakturadatum,Forfallodatum,Kund,Status,Antal,Netto,Moms,Totalt,OCR,PlusGiro,Kreditfaktura,Krediterar faktura\n");

    for (Order invoice : orderRepository.findAll()) {
      csv.append(invoice.getId()).append(",");
      csv.append(escape(invoice.getInvoiceNumber())).append(",");
      csv.append(escape(invoice.getInvoiceDate() == null ? "" : invoice.getInvoiceDate().toString())).append(",");
      csv.append(escape(invoice.getDueDate() == null ? "" : invoice.getDueDate().toString())).append(",");
      csv.append(escape(invoice.getCustomerName())).append(",");
      csv.append(escape(invoice.getStatus())).append(",");
      csv.append(invoice.getQuantity()).append(",");
      csv.append(invoice.getNetAmount()).append(",");
      csv.append(invoice.getVatAmount()).append(",");
      csv.append(invoice.getTotalAmount()).append(",");
      csv.append(escape(invoice.getOcrNumber())).append(",");
      csv.append(escape(invoice.getPlusGiro())).append(",");
      csv.append(invoice.isCreditInvoice()).append(",");
      csv.append(invoice.getCreditedInvoiceId() == null ? "" : invoice.getCreditedInvoiceId()).append("\n");
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoices.csv")
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
