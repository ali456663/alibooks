package se.cloudshop.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.product.Product;

class InvoicePdfControllerTest {

  private final JwtService jwtService = new JwtService("test_secret");
  private final AuthHeader authHeader = new AuthHeader(jwtService);
  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final InvoicePdfService invoicePdfService = mock(InvoicePdfService.class);
  private final InvoicePdfController controller = new InvoicePdfController(
      authHeader,
      orderRepository,
      invoicePdfService
  );

  @Test
  void invoicePdfUsesSafeContentDispositionFilename() {
    Order invoice = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now());
    invoice.setInvoiceNumber("F-2026-0001\r\n../evil");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(invoicePdfService.createInvoicePdf(invoice)).thenReturn("%PDF".getBytes());

    ResponseEntity<byte[]> response = controller.getInvoicePdf("Bearer " + token(), 1L);

    String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(contentDisposition).contains("inline");
    assertThat(contentDisposition).doesNotContain("\r").doesNotContain("\n").doesNotContain("/");
    assertThat(contentDisposition).contains("F-2026-0001__.._evil.pdf");
  }

  private String token() {
    return jwtService.createToken("ali@example.com");
  }
}
