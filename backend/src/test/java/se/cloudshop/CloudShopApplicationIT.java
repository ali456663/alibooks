package se.cloudshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.accounting.CreateManualJournalEntryRequest;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.accounting.JournalEntryRepository;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.JwtService;
import se.cloudshop.expense.CreateExpenseRequest;
import se.cloudshop.expense.ExpenseController;
import se.cloudshop.order.CreateOrderRequest;
import se.cloudshop.order.MarkInvoicePaidRequest;
import se.cloudshop.order.MarkInvoiceRefundRequest;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderController;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.product.Product;
import se.cloudshop.product.ProductRepository;
import se.cloudshop.settings.SettingsService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CloudShopApplicationIT {
  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrderController invoices;
  @Autowired ExpenseController expenses;
  @Autowired OrderRepository orders;
  @Autowired ProductRepository products;
  @Autowired JournalEntryRepository journal;
  @Autowired AccountingService accounting;
  @Autowired SettingsService settings;
  @Autowired JwtService jwt;
  @Autowired PlatformTransactionManager transactions;
  @SpyBean AuditService audit;
  private String authorization;
  private Product product;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    String url = System.getenv("TEST_DATABASE_URL");
    if (url == null || !url.matches("jdbc:postgresql://[^/]+/alibooks_test")) {
      throw new IllegalStateException("Integration tests require an isolated TEST_DATABASE_URL ending in /alibooks_test.");
    }
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("app.schema-patch.enabled", () -> "true");
    registry.add("app.invoice-reminders.cron", () -> "-");
    registry.add("jwt.secret", () -> "integration-only-secret-with-no-production-access");
    registry.add("spring.mail.host", () -> "");
    registry.add("stripe.secret-key", () -> "");
    registry.add("stripe.webhook-secret", () -> "");
  }

  @BeforeEach
  void prepare() {
    reset(audit);
    jdbc.execute("DROP TRIGGER IF EXISTS reject_test_credit ON journal_entries");
    jdbc.execute("TRUNCATE journal_entries, customer_orders, expenses, audit_events RESTART IDENTITY CASCADE");
    jdbc.update("UPDATE app_settings SET accounting_locked_through_date = NULL, accounting_method = 'INVOICE_METHOD'");
    settings.getSettings();
    product = products.save(new Product("Integration test service", "Test only", 100));
    authorization = "Bearer " + jwt.createToken("integration@example.invalid");
  }

  @Test
  void applicationStartsWithRealDatabaseAndHttpServer() {
    assertThat(http.getForEntity("/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class)).isPositive();
  }

  @Test
  void invoicesRequireAuthenticationOverHttp() {
    assertThat(http.getForEntity("/invoices", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void draftCreationPersistsNumberButDoesNotBook() {
    Order invoice = draft();
    assertThat(orders.findById(invoice.getId()).orElseThrow().getInvoiceNumber()).isNotBlank();
    assertThat(journal.count()).isZero();
  }

  @Test
  void sentInvoicePersistsBalancedVoucherAndStatus() {
    Order invoice = invoices.markInvoiceAsSent(authorization, draft().getId());
    assertThat(invoice.getStatus()).isEqualTo("SENT");
    assertThat(journal.count()).isEqualTo(3);
    assertBalanced();
  }

  @Test
  void markingSentTwiceDoesNotDoubleBook() {
    Long id = draft().getId();
    invoices.markInvoiceAsSent(authorization, id);
    invoices.markInvoiceAsSent(authorization, id);
    assertThat(journal.count()).isEqualTo(3);
  }

  @Test
  void failedJournalInsertRollsBackWholeVoucherAndInvoiceStatus() {
    Long id = draft().getId();
    rejectCreditRows();
    assertThatThrownBy(() -> invoices.markInvoiceAsSent(authorization, id)).isInstanceOf(RuntimeException.class);
    assertThat(journal.count()).isZero();
    assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo("DRAFT");
  }

  @Test
  void failedManualVoucherDoesNotLeaveDebitRow() {
    rejectCreditRows();
    assertThatThrownBy(() -> accounting.createManualEntry(new CreateManualJournalEntryRequest(
        LocalDate.now(), "Failure test", "1930", "2018", 100)))
        .isInstanceOf(RuntimeException.class);
    assertThat(journal.count()).isZero();
  }

  @Test
  void failedExpenseBookingDoesNotLeaveExpenseOrJournalRows() {
    rejectCreditRows();
    assertThatThrownBy(() -> expenses.createExpense(authorization, new CreateExpenseRequest(
        LocalDate.now(), "Failure test", 100, 25, "5420", "1930")))
        .isInstanceOf(RuntimeException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
    assertThat(journal.count()).isZero();
  }

  @Test
  void failedAuditRollsBackDraftCreation() {
    failAudit();
    assertThatThrownBy(this::draft).isInstanceOf(IllegalStateException.class);
    assertThat(orders.count()).isZero();
  }

  @Test
  void failedAuditRollsBackPaymentAndItsJournalRows() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    failAudit();
    assertThatThrownBy(() -> invoices.markInvoiceAsPaid(authorization, id,
        new MarkInvoicePaidRequest(LocalDate.now(), 50, "test-payment")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(journal.count()).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_payments", Long.class)).isZero();
  }

  @Test
  void partialAndFinalPaymentsPersistAndBalance() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    Order partial = invoices.markInvoiceAsPaid(authorization, id,
        new MarkInvoicePaidRequest(LocalDate.now(), 50, "partial"));
    assertThat(partial.getRemainingAmount()).isEqualTo(75);
    assertThat(partial.getStatus()).isEqualTo("PARTIALLY_PAID");
    Order paid = invoices.markInvoiceAsPaid(authorization, id,
        new MarkInvoicePaidRequest(LocalDate.now(), 75, "final"));
    assertThat(paid.getRemainingAmount()).isZero();
    assertThat(paid.getStatus()).isEqualTo("PAID");
    assertBalanced();
  }

  @Test
  void duplicatePaymentIsRejectedWithoutAdditionalBooking() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    MarkInvoicePaidRequest payment = new MarkInvoicePaidRequest(LocalDate.now(), 50, "same");
    invoices.markInvoiceAsPaid(authorization, id, payment);
    long count = journal.count();
    assertThatThrownBy(() -> invoices.markInvoiceAsPaid(authorization, id, payment))
        .hasMessageContaining("already registered");
    assertThat(journal.count()).isEqualTo(count);
  }

  @Test
  void failedAuditRollsBackCreditInvoiceAndOriginalStatus() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    failAudit();
    assertThatThrownBy(() -> invoices.createCreditInvoice(authorization, id)).isInstanceOf(IllegalStateException.class);
    assertThat(orders.count()).isEqualTo(1);
    assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo("SENT");
    assertThat(journal.count()).isEqualTo(3);
  }

  @Test
  void lockedDateRejectsPostingWithoutPartialWrites() {
    Long id = draft().getId();
    settings.lockAccountingThroughDate(LocalDate.now());
    assertThatThrownBy(() -> invoices.markInvoiceAsSent(authorization, id)).isInstanceOf(RuntimeException.class);
    assertThat(journal.count()).isZero();
    assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo("DRAFT");
  }

  @Test
  void overpaymentIsRejectedWithoutAdditionalBooking() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    assertThatThrownBy(() -> invoices.markInvoiceAsPaid(authorization, id,
        new MarkInvoicePaidRequest(LocalDate.now(), 126, "too much")))
        .hasMessageContaining("greater than remaining amount");
    assertThat(journal.count()).isEqualTo(3);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
  }

  @Test
  void failedAuditDoesNotDeleteDraft() {
    Long id = draft().getId();
    failAudit();
    assertThatThrownBy(() -> invoices.deleteInvoice(authorization, id)).isInstanceOf(IllegalStateException.class);
    assertThat(orders.existsById(id)).isTrue();
  }

  @Test
  void bookedInvoiceCannotBeDeleted() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    assertThatThrownBy(() -> invoices.deleteInvoice(authorization, id)).hasMessageContaining("Only draft invoices");
    assertThat(orders.existsById(id)).isTrue();
    assertThat(journal.count()).isEqualTo(3);
  }

  @Test
  void creditInvoiceReversesOriginalAccountBalances() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.createCreditInvoice(authorization, id);
    assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo("CREDITED");
    assertThat(journal.count()).isEqualTo(6);
    assertThat(jdbc.queryForList("SELECT sum(debit - credit) AS balance FROM journal_entries GROUP BY account_number", Long.class))
        .allMatch(balance -> balance == 0);
  }

  @Test
  void failedAuditRollsBackRefund() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 125, "paid"));
    invoices.createCreditInvoice(authorization, id);
    long count = journal.count();
    failAudit();
    assertThatThrownBy(() -> invoices.markInvoiceRefunded(authorization, id,
        new MarkInvoiceRefundRequest(LocalDate.now(), 125, "refund"))).isInstanceOf(IllegalStateException.class);
    assertThat(orders.findById(id).orElseThrow().getRefundedAmount()).isZero();
    assertThat(journal.count()).isEqualTo(count);
  }

  @Test
  void concurrentVouchersWaitForCommitAndReceiveDistinctNumbers() throws Exception {
    var executor = Executors.newFixedThreadPool(2);
    var allocated = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var request = new CreateManualJournalEntryRequest(LocalDate.now(), "Concurrent test", "1930", "2018", 100);
    try {
      var first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
        String number = accounting.createManualEntry(request).getFirst().getVoucherNumber();
        allocated.countDown();
        try {
          if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(exception);
        }
        return number;
      }));
      assertThat(allocated.await(15, TimeUnit.SECONDS)).isTrue();
      var second = executor.submit(() -> {
        started.countDown();
        return accounting.createManualEntry(request).getFirst().getVoucherNumber();
      });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();
      assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
      assertThat(journal.count()).isEqualTo(4);
      assertBalanced();
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Order draft() {
    return invoices.createInvoice(authorization, new CreateOrderRequest("Test customer", null, product.getId(), 1));
  }

  private void assertBalanced() {
    assertThat(journal.findAll().stream().mapToLong(JournalEntry::getDebit).sum())
        .isEqualTo(journal.findAll().stream().mapToLong(JournalEntry::getCredit).sum());
  }

  private void failAudit() {
    doThrow(new IllegalStateException("Simulated audit failure")).when(audit)
        .record(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyInt(), anyString());
  }

  private void rejectCreditRows() {
    // Fail in PostgreSQL after the first debit insert, not in a mocked repository.
    jdbc.execute("CREATE OR REPLACE FUNCTION reject_test_credit() RETURNS trigger LANGUAGE plpgsql AS $$ "
        + "BEGIN IF NEW.credit > 0 THEN RAISE EXCEPTION 'Simulated journal write failure'; END IF; RETURN NEW; END $$");
    jdbc.execute("CREATE TRIGGER reject_test_credit BEFORE INSERT ON journal_entries "
        + "FOR EACH ROW EXECUTE FUNCTION reject_test_credit()");
  }
}
