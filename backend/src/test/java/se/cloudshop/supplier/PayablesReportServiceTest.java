package se.cloudshop.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayablesReportServiceTest {

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void rejectsBucketOrReportOverflow(boolean sameBucket) {
    Supplier supplier = supplier("Test");
    SupplierInvoice first = invoice(1L, supplier, LocalDate.now().plusDays(2), 1_500_000_000, 0, "unpaid");
    SupplierInvoice second = invoice(2L, supplier, sameBucket ? first.getDueDate() : LocalDate.now().minusDays(60), 1_500_000_000, 0, "unpaid");
    when(supplierInvoiceRepository.findAll()).thenReturn(List.of(first, second));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> payablesReportService.createAgingReport(LocalDate.now()))
        .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
  }

  private final SupplierInvoiceRepository supplierInvoiceRepository = mock(SupplierInvoiceRepository.class);
  private final se.cloudshop.audit.AuditEventRepository auditRepository = mock(se.cloudshop.audit.AuditEventRepository.class);
  private final PayablesReportService payablesReportService = new PayablesReportService(supplierInvoiceRepository, auditRepository);

  @Test
  void reconstructsSupplierPaymentsAndCancellationByDate() {
    SupplierInvoice paid = invoice(1L, supplier("Test"), LocalDate.of(2026, 7, 10), 1250, 250, "unpaid");
    paid.registerPayment(LocalDate.of(2026, 7, 5), 500, "first");
    paid.registerPayment(LocalDate.of(2026, 7, 20), 750, "last");
    SupplierInvoice cancelled = invoice(2L, supplier("Test"), LocalDate.of(2026, 7, 10), 100, 20, "unpaid");
    cancelled.markCancelled(LocalDate.of(2026, 7, 15), "R-1");
    when(supplierInvoiceRepository.findAll()).thenReturn(List.of(paid, cancelled));
    assertThat(payablesReportService.createAgingReport(LocalDate.of(2026, 6, 30)).invoiceCount()).isZero();
    assertThat(payablesReportService.createAgingReport(LocalDate.of(2026, 7, 4)).totalOutstanding()).isEqualTo(1350);
    assertThat(payablesReportService.createAgingReport(LocalDate.of(2026, 7, 5)).totalOutstanding()).isEqualTo(850);
    var report = payablesReportService.createAgingReport(LocalDate.of(2026, 7, 15));
    assertThat(report.totalOutstanding()).isEqualTo(750);
    assertThat(report.invoices()).singleElement().satisfies(row -> {
      assertThat(row.status()).isEqualTo("partial");
      assertThat(row.paymentRecommended()).isFalse();
    });
    assertThat(payablesReportService.createAgingReport(LocalDate.of(2026, 7, 20)).invoiceCount()).isZero();
  }

  @Test
  void refusesLegacyPaidAmountWithoutDatedHistory() {
    SupplierInvoice invoice = invoice(1L, supplier("Test"), LocalDate.of(2026, 7, 10), 1250, 250, "partial");
    setField(invoice, "paidAmount", 500);
    when(supplierInvoiceRepository.findAll()).thenReturn(List.of(invoice));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> payablesReportService.createAgingReport(LocalDate.of(2026, 7, 10)))
        .isInstanceOf(se.cloudshop.accounting.SettlementSnapshot.HistoryIncomplete.class);
  }

  @Test
  void refusesHistoricalReportWhenOlderVersionsDeletedSupplierInvoices() {
    when(auditRepository.existsByEntityTypeAndAction("supplier_invoice", "deleted")).thenReturn(true);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> payablesReportService.createAgingReport(LocalDate.now().minusDays(1)))
        .isInstanceOf(se.cloudshop.accounting.SettlementSnapshot.HistoryIncomplete.class);
  }

  @Test
  void createsAgingReportForOpenSupplierInvoicesOnly() {
    Supplier supplier = supplier("Adobe");
    SupplierInvoice notDue = invoice(1L, supplier, LocalDate.of(2026, 7, 30), 1250, 250, "unpaid");
    SupplierInvoice overdue = invoice(2L, supplier, LocalDate.of(2026, 6, 10), 625, 125, "prepared");
    SupplierInvoice oldOverdue = invoice(3L, supplier, LocalDate.of(2026, 4, 10), 1000, 200, "unpaid");
    SupplierInvoice paid = invoice(4L, supplier, LocalDate.of(2026, 6, 1), 500, 100, "paid");
    SupplierInvoice bookedUnpaid = invoice(5L, supplier, LocalDate.of(2026, 6, 1), 700, 140, "booked");
    SupplierInvoice cancelled = invoice(6L, supplier, LocalDate.of(2026, 6, 1), 900, 180, "cancelled");
    SupplierInvoice partial = invoice(7L, supplier, LocalDate.of(2026, 7, 30), 1000, 200, "partial");
    partial.registerPayment(LocalDate.of(2026, 7, 2), 400, "test");
    paid.registerPayment(LocalDate.of(2026, 7, 2), paid.getTotalAmount(), "test");
    cancelled.markCancelled(LocalDate.of(2026, 7, 2), "");

    when(supplierInvoiceRepository.findAll()).thenReturn(List.of(notDue, overdue, oldOverdue, paid, bookedUnpaid, cancelled, partial));

    PayablesAgingReport report = payablesReportService.createAgingReport(LocalDate.of(2026, 7, 27));

    assertThat(report.invoiceCount()).isEqualTo(5);
    assertThat(report.totalOutstanding()).isEqualTo(4175);
    assertThat(report.notDueOutstanding()).isEqualTo(1850);
    assertThat(report.overdueOutstanding()).isEqualTo(2325);
    assertThat(report.dueSoonOutstanding()).isEqualTo(1850);
    assertThat(report.inputVatOutstanding()).isEqualTo(915);
    assertThat(report.buckets()).extracting(PayablesAgingBucket::key)
        .containsExactly("no-due-date", "not-due", "overdue-1-30", "overdue-31-60", "overdue-61-90", "overdue-90-plus");
    assertThat(report.buckets()).filteredOn(bucket -> "overdue-31-60".equals(bucket.key()))
        .singleElement()
        .extracting(PayablesAgingBucket::totalRemaining)
        .isEqualTo(1325);
    assertThat(report.buckets()).filteredOn(bucket -> "overdue-90-plus".equals(bucket.key()))
        .singleElement()
        .extracting(PayablesAgingBucket::totalRemaining)
        .isEqualTo(1000);
    assertThat(report.invoices()).extracting(PayablesAgingInvoice::invoiceId)
        .containsExactly(3L, 5L, 2L, 1L, 7L);
  }

  private Supplier supplier(String name) {
    Supplier supplier = new Supplier(name, "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    setField(supplier, "id", 1L);
    return supplier;
  }

  private SupplierInvoice invoice(Long id, Supplier supplier, LocalDate dueDate, int totalAmount, int vatAmount, String status) {
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        dueDate,
        "Programvara",
        "OCR-" + id,
        totalAmount,
        vatAmount,
        "5420"
    );
    setField(invoice, "id", id);
    setField(invoice, "status", status);
    return invoice;
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
