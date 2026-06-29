package se.cloudshop.invoice;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;

@RestController
public class InvoicePdfController {

  private final AuthHeader authHeader;
  private final OrderRepository orderRepository;
  private final InvoicePdfService invoicePdfService;

  public InvoicePdfController(
      AuthHeader authHeader,
      OrderRepository orderRepository,
      InvoicePdfService invoicePdfService
  ) {
    this.authHeader = authHeader;
    this.orderRepository = orderRepository;
    this.invoicePdfService = invoicePdfService;
  }

  @GetMapping("/invoices/{id}/pdf")
  public ResponseEntity<byte[]> getInvoicePdf(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order invoice = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
    byte[] pdf = invoicePdfService.createInvoicePdf(invoice);
    String filename = (invoice.getInvoiceNumber() == null ? "invoice-" + invoice.getId() : invoice.getInvoiceNumber()) + ".pdf";

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + filename)
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }
}

