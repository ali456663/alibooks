package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import se.cloudshop.order.*;
import se.cloudshop.product.Product;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.AppSettingsRepository;
import se.cloudshop.supplier.*;

class SubledgerControlServiceTest {
  private final JournalEntryRepository journal = mock(JournalEntryRepository.class);
  private final AppSettingsRepository settings = mock(AppSettingsRepository.class);
  private final ReceivablesReportService receivables = mock(ReceivablesReportService.class);
  private final PayablesReportService payables = mock(PayablesReportService.class);
  private final SubledgerControlService service = new SubledgerControlService(journal, settings, receivables, payables);
  private final LocalDate date = LocalDate.of(2026, 7, 31);

  @BeforeEach
  void prepare() {
    when(settings.findById(1L)).thenReturn(Optional.of(AppSettings.defaults()));
    when(journal.findAll()).thenReturn(List.of());
    expectedCustomers();
    when(payables.createAgingReport(any())).thenReturn(new PayablesAgingReport(date, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of()));
  }

  @Test
  void emptyDatabaseIsNotReportedAsVerifiedBookkeeping() {
    assertThat(service.createReport(date).status()).isEqualTo("NO_DATA");
  }

  @Test
  void matchesReceivableAfterPartialPayment() {
    expectedCustomers(row(1, 75));
    when(journal.findAll()).thenReturn(List.of(customerEntry(1, 125, 0, date), customerEntry(1, 0, 50, date)));
    var report = service.createReport(date);
    assertThat(report.status()).isEqualTo("MATCHED");
    assertThat(report.accounts().get(0).ledgerBalance()).isEqualTo(75);
    assertThat(report.accounts().get(0).difference()).isZero();
  }

  @Test
  void creditorAccountUsesCreditMinusDebit() {
    SupplierInvoice invoice = new SupplierInvoice();
    ReflectionTestUtils.setField(invoice, "id", 2L);
    PayablesAgingInvoice row = mock(PayablesAgingInvoice.class);
    when(row.invoiceId()).thenReturn(2L);
    when(row.remainingAmount()).thenReturn(75);
    when(payables.createAgingReport(any())).thenReturn(new PayablesAgingReport(date, 1, 75, 75, 0, 0, 0, 0, List.of(), List.of(row)));
    when(journal.findAll()).thenReturn(List.of(
        new JournalEntry(null, null, invoice, new Account("2440", "Payables"), "L-1", 0, 125, "test", date),
        new JournalEntry(null, null, invoice, new Account("2440", "Payables"), "LB-1", 50, 0, "test", date)));
    assertThat(service.createReport(date).status()).isEqualTo("MATCHED");
    assertThat(service.createReport(date).accounts().get(1).ledgerBalance()).isEqualTo(75);
  }

  @Test
  void offsettingInvoiceErrorsCannotHideBehindMatchingGrandTotal() {
    expectedCustomers(row(1, 75), row(2, 50));
    when(journal.findAll()).thenReturn(List.of(customerEntry(1, 50, 0, date), customerEntry(2, 75, 0, date)));
    var report = service.createReport(date);
    assertThat(report.status()).isEqualTo("REVIEW_REQUIRED");
    assertThat(report.accounts().get(0).difference()).isZero();
    assertThat(report.accounts().get(0).differences()).extracting(SubledgerControlReport.InvoiceDifference::difference)
        .containsExactly(-25, 25);
  }

  @Test
  void unlinkedRowsRequireReviewEvenWhenTheirNetIsZero() {
    when(journal.findAll()).thenReturn(List.of(
        new JournalEntry(null, new Account("1510", "Receivables"), "M-1", 50, 0, "manual", date),
        new JournalEntry(null, new Account("1510", "Receivables"), "M-2", 0, 50, "manual", date)));
    var account = service.createReport(date).accounts().get(0);
    assertThat(account.status()).isEqualTo("REVIEW_REQUIRED");
    assertThat(account.unlinkedEntryCount()).isEqualTo(2);
    assertThat(account.difference()).isZero();
  }

  @Test
  void undatedRowsCannotSilentlyDisappear() {
    JournalEntry entry = customerEntry(1, 100, 0, date);
    ReflectionTestUtils.setField(entry, "voucherDate", null);
    when(journal.findAll()).thenReturn(List.of(entry));
    assertThat(service.createReport(date).accounts().get(0).undatedEntryCount()).isEqualTo(1);
    assertThat(service.createReport(date).status()).isEqualTo("REVIEW_REQUIRED");
  }

  @Test
  void futurePaymentDoesNotChangeEarlierLedgerComparison() {
    expectedCustomers(row(1, 125));
    when(journal.findAll()).thenReturn(List.of(customerEntry(1, 125, 0, date), customerEntry(1, 0, 125, date.plusDays(1))));
    assertThat(service.createReport(date).status()).isEqualTo("MATCHED");
  }

  @Test
  void creditsGroupWithOriginalAndCustomerRefundObligationIsNotCalledMatched() {
    JournalEntry credit = customerEntry(2, 0, 125, date);
    credit.getInvoice().setCreditInvoice(true);
    credit.getInvoice().setCreditedInvoiceId(1L);
    when(journal.findAll()).thenReturn(List.of(customerEntry(1, 125, 0, date), customerEntry(1, 0, 50, date), credit));
    var account = service.createReport(date).accounts().get(0);
    assertThat(account.status()).isEqualTo("REVIEW_REQUIRED");
    assertThat(account.differences()).singleElement().satisfies(row -> {
      assertThat(row.invoiceId()).isEqualTo(1L);
      assertThat(row.ledgerBalance()).isEqualTo(-50);
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"CASH_METHOD", "UNKNOWN"})
  void unsupportedMethodCannotShowAGreenComparison(String method) {
    AppSettings config = AppSettings.defaults();
    config.setAccountingMethod(method);
    when(settings.findById(1L)).thenReturn(Optional.of(config));
    var report = service.createReport(date);
    assertThat(report.status()).isEqualTo("UNSUPPORTED_METHOD");
    assertThat(report.accounts()).allSatisfy(account -> assertThat(account.difference()).isNull());
  }

  @Test
  void largeAggregateFailsInsteadOfWrapping() {
    expectedCustomers(row(1, 1_500_000_000), row(2, 1_500_000_000));
    assertThatThrownBy(() -> service.createReport(date)).isInstanceOf(ReportAmounts.LimitExceeded.class);
  }

  @Test
  void incompleteSourceReportCannotBecomeAMatchedZeroBalance() {
    when(receivables.createAgingReport(any())).thenThrow(SettlementSnapshot.incomplete());
    assertThatThrownBy(() -> service.createReport(date)).isInstanceOf(SettlementSnapshot.HistoryIncomplete.class);
  }

  @Test
  void missingSettingsAreNotCreatedByReadOnlyControl() {
    when(settings.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createReport(date)).hasMessageContaining("settings must exist");
    verify(settings, never()).save(any());
  }

  private ReceivablesAgingInvoice row(long id, int amount) {
    return new ReceivablesAgingInvoice(id, "F-" + id, "Test", "", date, date, "SENT", amount, 0, amount, 0, "not-due", "", false);
  }

  private void expectedCustomers(ReceivablesAgingInvoice... rows) {
    when(receivables.createAgingReport(any())).thenReturn(new ReceivablesAgingReport(date, rows.length, 0, 0, 0, 0, 0, List.of(), List.of(rows)));
  }

  private JournalEntry customerEntry(long id, int debit, int credit, LocalDate date) {
    Order invoice = new Order("Test", new Product("Test", "Test", 100), Instant.now());
    ReflectionTestUtils.setField(invoice, "id", id);
    return new JournalEntry(invoice, new Account("1510", "Receivables"), "F-" + id, debit, credit, "test", date);
  }
}
