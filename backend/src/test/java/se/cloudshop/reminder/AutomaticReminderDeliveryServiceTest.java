package se.cloudshop.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import se.cloudshop.customer.Customer;
import se.cloudshop.email.InvoiceReminderEmailService;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.product.Product;

class AutomaticReminderDeliveryServiceTest {

  private final OrderRepository orders = mock(OrderRepository.class);
  private final InvoiceReminderEmailService emails = mock(InvoiceReminderEmailService.class);
  private final AutomaticReminderDeliveryService service = new AutomaticReminderDeliveryService(orders, emails);

  @Test
  void sendsFiveDayReminderAndDoesNotDuplicateIt() throws Exception {
    LocalDate today = LocalDate.of(2026, 9, 16);
    Order invoice = invoice(today.plusDays(5));
    setId(invoice, 42L);
    when(orders.findById(42L)).thenReturn(Optional.of(invoice));

    var first = deliver(invoice, today, false);
    var second = deliver(invoice, today, false);

    assertThat(first).isEqualTo(AutomaticReminderDeliveryService.DeliveryResult.SENT);
    assertThat(second).isEqualTo(AutomaticReminderDeliveryService.DeliveryResult.NOT_ELIGIBLE);
    verify(emails).sendReminder(invoice);
    verify(orders, times(2)).lockById(42L);
  }

  @Test
  void retriesSkippedReminderAndRecoversAPreviouslyMissedRun() throws Exception {
    LocalDate today = LocalDate.of(2026, 9, 16);
    Order invoice = invoice(today.plusDays(3));
    setId(invoice, 43L);
    when(orders.findById(43L)).thenReturn(Optional.of(invoice));
    doThrow(new IllegalStateException("SMTP unavailable"))
        .doNothing()
        .when(emails).sendReminder(invoice);

    var skipped = deliver(invoice, today, false);
    var recovered = deliver(invoice, today, false);

    assertThat(skipped).isEqualTo(AutomaticReminderDeliveryService.DeliveryResult.SKIPPED);
    assertThat(recovered).isEqualTo(AutomaticReminderDeliveryService.DeliveryResult.SENT);
    assertThat(invoice.hasReminder("AUTO_EMAIL_5_DAYS", "SKIPPED")).isTrue();
    assertThat(invoice.hasReminder("AUTO_EMAIL_5_DAYS", "SENT")).isTrue();
    verify(emails, times(2)).sendReminder(invoice);
  }

  @Test
  void doesNotSendBeforeReminderWindow() throws Exception {
    LocalDate today = LocalDate.of(2026, 9, 16);
    Order invoice = invoice(today.plusDays(6));
    setId(invoice, 44L);
    when(orders.findById(44L)).thenReturn(Optional.of(invoice));

    assertThat(deliver(invoice, today, false))
        .isEqualTo(AutomaticReminderDeliveryService.DeliveryResult.NOT_ELIGIBLE);
    verify(emails, times(0)).sendReminder(invoice);
  }

  private AutomaticReminderDeliveryService.DeliveryResult deliver(Order invoice, LocalDate today, boolean overdue) {
    return service.deliver(
        invoice.getId(),
        today,
        5,
        "AUTO_EMAIL_5_DAYS",
        overdue,
        3,
        "AUTO_OVERDUE_EMAIL_3_DAYS"
    );
  }

  private Order invoice(LocalDate dueDate) {
    Customer customer = new Customer("Ali", "ali@example.com", null, null, null, null, null);
    Order invoice = new Order(customer, new Product("PT", "Training", 1000), Instant.now());
    invoice.setStatus("SENT");
    invoice.setDueDate(dueDate);
    return invoice;
  }

  private void setId(Order invoice, Long id) throws Exception {
    Field field = Order.class.getDeclaredField("id");
    field.setAccessible(true);
    field.set(invoice, id);
  }
}
