package se.cloudshop.invoice;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;

@RestController
public class InvoicePdfController {

  private final AuthHeader authHeader;
  private final OrderRepository orderRepository;
  private final InvoiceOriginalService invoiceOriginalService;

  public InvoicePdfController(
      AuthHeader authHeader,
      OrderRepository orderRepository,
      InvoiceOriginalService invoiceOriginalService
  ) {
    this.authHeader = authHeader;
    this.orderRepository = orderRepository;
    this.invoiceOriginalService = invoiceOriginalService;
  }

  @GetMapping("/invoices/{id}/pdf")
  public ResponseEntity<byte[]> getInvoicePdf(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order invoice = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
    var document = invoiceOriginalService.read(invoice);
    String filename = (invoice.getInvoiceNumber() == null ? "invoice-" + invoice.getId() : invoice.getInvoiceNumber()) + ".pdf";
    ContentDisposition contentDisposition = ContentDisposition.inline()
        .filename(safeDownloadFilename(filename), StandardCharsets.UTF_8)
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Invoice-Document", document.source())
        .header("X-Invoice-SHA256", document.sha256())
        .contentType(MediaType.APPLICATION_PDF)
        .body(document.pdf());
  }

  private String safeDownloadFilename(String filename) {
    String clean = filename == null ? "" : filename.trim()
        .replace('\r', '_')
        .replace('\n', '_')
        .replace('\\', '_')
        .replace('/', '_');
    return clean.isBlank() ? "invoice.pdf" : clean;
  }
}
