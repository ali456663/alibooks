package se.cloudshop.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.product.Product;

class StripePaymentServiceTest {

  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final StripeWebhookEventRepository stripeWebhookEventRepository = mock(StripeWebhookEventRepository.class);
  private final StripePaymentService stripePaymentService = new StripePaymentService(
      "sk_test_dummy",
      "whsec_dummy",
      "http://localhost:5157",
      orderRepository,
      accountingService,
      stripeWebhookEventRepository
  );

  @Test
  void checkoutRejectsCreditInvoice() {
    Order creditInvoice = new Order("Ali", new Product("PT", "Training", 1000), Instant.now());
    creditInvoice.setCreditInvoice(true);
    creditInvoice.setStatus("SENT");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(creditInvoice));

    assertThatThrownBy(() -> stripePaymentService.createCheckoutSession(1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Stripe checkout cannot be created for credit invoices or credited invoices");
  }

  @Test
  void checkoutRejectsDraftInvoice() {
    Order draftInvoice = new Order("Ali", new Product("PT", "Training", 1000), Instant.now());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));

    assertThatThrownBy(() -> stripePaymentService.createCheckoutSession(1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invoice has no remaining amount to pay");
  }

  @Test
  void checkoutRejectsFullyPaidInvoice() {
    Order paidInvoice = new Order("Ali", new Product("PT", "Training", 1000), Instant.now());
    paidInvoice.registerPayment(paidInvoice.getInvoiceDate(), paidInvoice.getTotalAmount(), "Bank");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(paidInvoice));

    assertThatThrownBy(() -> stripePaymentService.createCheckoutSession(1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invoice has no remaining amount to pay");
  }

  @Test
  void checkoutRejectsOreBalanceBeforeCallingStripe() {
    Order invoice = new Order("Ali", new Product("PT", "Training", 1000), Instant.now());
    invoice.setStatus("SENT");
    ReflectionTestUtils.setField(invoice, "totalAmountMinor", 100050L);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> stripePaymentService.createCheckoutSession(1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("cannot represent an invoice balance with ore");
  }
}
