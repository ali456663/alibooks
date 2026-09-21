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

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void rejectsBucketOrReportOverflow(boolean sameBucket) {
    Product product = new Product("Test", "Test", 1000);
    Order first = invoice(1L, "F-1", product, LocalDate.now().plusDays(2), "SENT");
    Order second = invoice(2L, "F-2", product, sameBucket ? first.getDueDate() : LocalDate.now().minusDays(60), "SENT");
    first.setAmounts(1_500_000_000, 0, 1_500_000_000);
    second.setAmounts(1_500_000_000, 0, 1_500_000_000);
    when(orderRepository.findAll()).thenReturn(List.of(first, second));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> receivablesReportService.createAgingReport(LocalDate.now()))
        .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
  }

  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final ReceivablesReportService receivablesReportService = new ReceivablesReportService(orderRepository);

  @Test
  void reconstructsPaidInvoiceBeforePartialAndFinalPayment() {
    Order order = invoice(1L, "F-1", new Product("Test", "Test", 1000), LocalDate.of(2026, 4, 10), "SENT");
    order.registerPayment(LocalDate.of(2026, 4, 5), 500, "first");
    order.registerPayment(LocalDate.of(2026, 4, 20), 750, "last");
    when(orderRepository.findAll()).thenReturn(List.of(order));
    assertThat(receivablesReportService.createAgingReport(LocalDate.of(2026, 3, 31)).invoiceCount()).isZero();
    assertThat(receivablesReportService.createAgingReport(LocalDate.of(2026, 4, 4)).totalOutstanding()).isEqualTo(1250);
    var report = receivablesReportService.createAgingReport(LocalDate.of(2026, 4, 5));
    assertThat(report.totalOutstanding()).isEqualTo(750);
    assertThat(report.invoices()).singleElement().satisfies(row -> {
      assertThat(row.paidAmount()).isEqualTo(500);
      assertThat(row.status()).isEqualTo("PARTIALLY_PAID");
      assertThat(row.reminderRecommended()).isFalse();
    });
    assertThat(receivablesReportService.createAgingReport(LocalDate.of(2026, 4, 20)).invoiceCount()).isZero();
  }

  @Test
  void creditRemovesOriginalOnlyFromCreditDateWithoutReopeningOnRefund() {
    Order original = invoice(1L, "F-1", new Product("Test", "Test", 1000), LocalDate.of(2026, 4, 10), "SENT");
    original.registerPayment(LocalDate.of(2026, 4, 5), 500, "first");
    original.setStatus("CREDITED");
    Order credit = Order.draftFromInvoiceSnapshot(original, Instant.now());
    credit.setCreditInvoice(true);
    credit.setStatus("SENT");
    credit.setCreditedInvoiceId(1L);
    credit.setAmounts(-1000, -250, -1250);
    org.springframework.test.util.ReflectionTestUtils.setField(credit, "invoiceDate", LocalDate.of(2026, 4, 15));
    original.registerRefund(LocalDate.of(2026, 4, 20), 500, "refund");
    when(orderRepository.findAll()).thenReturn(List.of(original, credit));
    assertThat(receivablesReportService.createAgingReport(LocalDate.of(2026, 4, 14)).totalOutstanding()).isEqualTo(750);
    assertThat(receivablesReportService.createAgingReport(LocalDate.of(2026, 4, 15)).totalOutstanding()).isZero();
    assertThat(receivablesReportService.createAgingReport(LocalDate.of(2026, 4, 20)).totalOutstanding()).isZero();
  }

  @Test
  void refusesCreditedInvoiceWithoutCreditEvidence() {
    Order original = invoice(1L, "F-1", new Product("Test", "Test", 1000), LocalDate.of(2026, 4, 10), "CREDITED");
    when(orderRepository.findAll()).thenReturn(List.of(original));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> receivablesReportService.createAgingReport(LocalDate.of(2026, 4, 10)))
        .isInstanceOf(se.cloudshop.accounting.SettlementSnapshot.HistoryIncomplete.class);
  }

  @Test
  void stopsWhenReceivableContainsOreLegacyReportCannotRepresent() {
    Order order = invoice(1L, "F-ORE", new Product("Test", "Test", 1000), LocalDate.of(2026, 4, 10), "SENT");
    org.springframework.test.util.ReflectionTestUtils.setField(order, "totalAmountMinor", 12550L);
    when(orderRepository.findAll()).thenReturn(List.of(order));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> receivablesReportService.createAgingReport(LocalDate.of(2026, 4, 10)))
        .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode().value()).isEqualTo(422));
  }

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
    org.springframework.test.util.ReflectionTestUtils.setField(order, "invoiceDate", LocalDate.of(2026, 4, 1));
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
