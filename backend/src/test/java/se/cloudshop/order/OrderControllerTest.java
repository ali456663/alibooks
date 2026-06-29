package se.cloudshop.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.customer.CustomerRepository;
import se.cloudshop.email.InvoiceEmailService;
import se.cloudshop.email.InvoiceReminderEmailService;
import se.cloudshop.product.ProductService;
import se.cloudshop.settings.SettingsService;

class OrderControllerTest {

  private final ProductService productService = mock(ProductService.class);
  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final CustomerRepository customerRepository = mock(CustomerRepository.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final SettingsService settingsService = mock(SettingsService.class);
  private final InvoiceReminderEmailService invoiceReminderEmailService = mock(InvoiceReminderEmailService.class);
  private final InvoiceEmailService invoiceEmailService = mock(InvoiceEmailService.class);
  private final OrderController orderController = new OrderController(
      productService,
      authHeader,
      orderRepository,
      customerRepository,
      accountingService,
      settingsService,
      invoiceReminderEmailService,
      invoiceEmailService
  );

  @Test
  void createOrderRequiresJwtToken() {
    CreateOrderRequest request = new CreateOrderRequest("Ali", null, 1L, 1);

    assertThatThrownBy(() -> orderController.createOrder(null, request))
        .isInstanceOf(ResponseStatusException.class);
  }
}
