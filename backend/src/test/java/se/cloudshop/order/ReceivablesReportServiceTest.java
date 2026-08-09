package se.cloudshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.cloudshop.customer.Customer;
import se.cloudshop.product.Product;

class ReceivablesReportServiceTest {

  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final ReceivablesReportService receivablesReportService = new ReceivablesReportService(orderRepository);

  @Test
  void createsAgingReportForOpenReceivablesOnly() {
    Product product = new Product("PT", "Training", 1000);
    Order notDue = invoice(1L, "F-2026-0001", product, LocalDate.of(2026, 8, 20), "SENT");
    Order overdue = invoice(2L, "F-2026-0002", product, LocalDate.of(2026, 6, 10), "SENT");
    Order partial = invoice(3L, "F-2026-0003", product, LocalDate.of(2026, 5, 15), "PARTIALLY_PAID");
    partial.registerPayment(partial.getInvoiceDate(), 500, "Bank");
    Order paid = invoice(4L, "F-2026-0004", product, LocalDate.of(2026, 6, 1), "SENT");
    paid.registerPayment(paid.getInvoiceDate(), paid.getTotalAmount(), "Bank");
    Order draft = invoice(5L, "F-2026-0005", product, LocalDate.of(2026, 8, 1), "DRAFT");

    when(orderRepository.findAll()).thenReturn(List.of(notDue, overdue, partial, paid, draft));

    ReceivablesAgingReport report = receivablesReportService.createAgingReport(LocalDate.of(2026, 7, 27));

    assertThat(report.invoiceCount()).isEqualTo(3);
    assertThat(report.totalOutstanding()).isEqualTo(3250);
    assertThat(report.notDueOutstanding()).isEqualTo(1250);
    assertThat(report.overdueOutstanding()).isEqualTo(2000);
    assertThat(report.buckets()).extracting(ReceivablesAgingBucket::key)
        .containsExactly("no-due-date", "not-due", "overdue-1-30", "overdue-31-60", "overdue-61-90", "overdue-90-plus");
    assertThat(report.buckets()).filteredOn(bucket -> "overdue-31-60".equals(bucket.key()))
        .singleElement()
        .extracting(ReceivablesAgingBucket::totalRemaining)
        .isEqualTo(1250);
    assertThat(report.buckets()).filteredOn(bucket -> "overdue-61-90".equals(bucket.key()))
        .singleElement()
        .extracting(ReceivablesAgingBucket::totalRemaining)
        .isEqualTo(750);
    assertThat(report.invoices()).extracting(ReceivablesAgingInvoice::invoiceNumber)
        .containsExactly("F-2026-0003", "F-2026-0002", "F-2026-0001");
  }

  private Order invoice(Long id, String invoiceNumber, Product product, LocalDate dueDate, String status) {
    Customer customer = new Customer("Ali Wafa", "ali@example.com", "20010203-6598", "Byvagen 56", "0700000000", "12345", "Sodertalje");
    Order order = new Order(customer, product, Instant.parse("2026-07-01T10:00:00Z"));
    setOrderId(order, id);
    order.setInvoiceNumber(invoiceNumber);
    order.setDueDate(dueDate);
    order.setStatus(status);
    order.setAmounts(1000, 250, 1250);
    return order;
  }

  private void setOrderId(Order order, Long id) {
    try {
      Field field = Order.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(order, id);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
