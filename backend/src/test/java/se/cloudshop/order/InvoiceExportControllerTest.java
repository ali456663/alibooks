package se.cloudshop.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.product.Product;

class InvoiceExportControllerTest {

  @Test
  void refusesInvoiceExportWhenMinorShadowContainsOre() {
    OrderRepository orderRepository = mock(OrderRepository.class);
    InvoiceExportController controller = new InvoiceExportController(
        orderRepository,
        new AuthHeader(new JwtService("test_secret")),
        mock(AuditService.class)
    );
    Order invoice = new Order("Customer", new Product("Consulting", "Service", 1000), Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(invoice, "totalAmountMinor", 1_000_050L);
    when(orderRepository.findAll()).thenReturn(List.of(invoice));

    assertThatThrownBy(() -> controller.exportInvoices("Bearer " + new JwtService("test_secret").createToken("test@example.invalid")))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
          org.assertj.core.api.Assertions.assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
          org.assertj.core.api.Assertions.assertThat(exception.getReason()).contains("Fakturaexporten", "oren");
        });
  }
}
