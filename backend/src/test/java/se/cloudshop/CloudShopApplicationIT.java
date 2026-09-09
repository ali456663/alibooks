package se.cloudshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.LocalDate;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
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
import se.cloudshop.payment.StripePaymentService;
import se.cloudshop.payment.StripeWebhookEventRepository;

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
  @Autowired StripePaymentService stripe;
  @Autowired StripeWebhookEventRepository stripeEvents;
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
    registry.add("stripe.webhook-secret", () -> "integration-only-signing-secret");
  }

  @BeforeEach
  void prepare() {
    reset(audit);
    jdbc.execute("DROP TRIGGER IF EXISTS reject_test_credit ON journal_entries");
    jdbc.execute("TRUNCATE journal_entries, customer_orders, expenses, audit_events, stripe_webhook_events RESTART IDENTITY CASCADE");
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

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
  void invalidPaymentAmountLeavesInvoiceAndJournalUnchanged(int amount) {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    long auditCount = auditCount();
    assertThatThrownBy(() -> invoices.markInvoiceAsPaid(authorization, id,
        new MarkInvoicePaidRequest(LocalDate.now(), amount, "invalid")))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(journal.count()).isEqualTo(3);
    assertThat(auditCount()).isEqualTo(auditCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_payments", Long.class)).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
  void invalidRefundAmountLeavesInvoiceAndJournalUnchanged(int amount) {
    Long id = refundableInvoice();
    long entries = journal.count();
    long auditCount = auditCount();
    assertThatThrownBy(() -> invoices.markInvoiceRefunded(authorization, id,
        new MarkInvoiceRefundRequest(LocalDate.now(), amount, "invalid")))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThat(orders.findById(id).orElseThrow().getRefundedAmount()).isZero();
    assertThat(journal.count()).isEqualTo(entries);
    assertThat(auditCount()).isEqualTo(auditCount);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void omittedPaymentAmountUsesOnlyRemainingBalance(boolean omitBody) {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 50, "first"));
    invoices.markInvoiceAsPaid(authorization, id,
        omitBody ? null : new MarkInvoicePaidRequest(null, null, "remaining"));
    Order saved = orders.findById(id).orElseThrow();
    assertThat(saved.getPaidAmount()).isEqualTo(125);
    assertThat(saved.getStatus()).isEqualTo("PAID");
    assertThat(saved.getPayments()).extracting(payment -> payment.getAmount()).containsExactlyInAnyOrder(50, 75);
    assertThat(journal.count()).isEqualTo(7);
    assertBalanced();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void omittedRefundAmountUsesOnlyRefundableBalance(boolean omitBody) {
    Long id = refundableInvoice();
    invoices.markInvoiceRefunded(authorization, id, new MarkInvoiceRefundRequest(LocalDate.now(), 50, "first"));
    invoices.markInvoiceRefunded(authorization, id,
        omitBody ? null : new MarkInvoiceRefundRequest(null, null, "remaining"));
    Order saved = orders.findById(id).orElseThrow();
    assertThat(saved.getRefundedAmount()).isEqualTo(125);
    assertThat(saved.getRefundableAmount()).isZero();
    assertThat(journal.count()).isEqualTo(12);
    assertBalanced();
  }

  @Test
  void paymentBeforeInvoiceDateLeavesNoWrites() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    assertThatThrownBy(() -> invoices.markInvoiceAsPaid(authorization, id,
        new MarkInvoicePaidRequest(LocalDate.now().minusDays(1), 50, "early")))
        .hasMessageContaining("before invoice date");
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(journal.count()).isEqualTo(3);
  }

  @Test
  void refundBeforeInvoiceDateLeavesNoWrites() {
    Long id = refundableInvoice();
    long entries = journal.count();
    assertThatThrownBy(() -> invoices.markInvoiceRefunded(authorization, id,
        new MarkInvoiceRefundRequest(LocalDate.now().minusDays(1), 50, "early")))
        .hasMessageContaining("before invoice date");
    assertThat(orders.findById(id).orElseThrow().getRefundedAmount()).isZero();
    assertThat(journal.count()).isEqualTo(entries);
  }

  @Test
  void concurrentPartialPaymentsKeepBothAmounts() throws Exception {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    afterUncommittedWrite(
        () -> invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 50, "first")),
        () -> invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 75, "second")));
    Order saved = orders.findById(id).orElseThrow();
    assertThat(saved.getPaidAmount()).isEqualTo(125);
    assertThat(saved.getPayments()).hasSize(2);
    assertThat(saved.getStatus()).isEqualTo("PAID");
    assertThat(journal.count()).isEqualTo(7);
    assertBalanced();
  }

  @Test
  void concurrentDuplicatePaymentIsRejectedAfterFirstCommit() throws Exception {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    var payment = new MarkInvoicePaidRequest(LocalDate.now(), 50, "same");
    afterUncommittedWrite(
        () -> invoices.markInvoiceAsPaid(authorization, id, payment),
        () -> assertThatThrownBy(() -> invoices.markInvoiceAsPaid(authorization, id, payment))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)));
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(50);
    assertThat(orders.findById(id).orElseThrow().getPayments()).hasSize(1);
    assertThat(journal.count()).isEqualTo(5);
    assertBalanced();
  }

  @Test
  void concurrentPaymentsCannotExceedInvoiceBalance() throws Exception {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    afterUncommittedWrite(
        () -> invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 75, "first")),
        () -> assertThatThrownBy(() -> invoices.markInvoiceAsPaid(authorization, id,
            new MarkInvoicePaidRequest(LocalDate.now(), 75, "second")))
            .hasMessageContaining("greater than remaining amount"));
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(75);
    assertThat(journal.count()).isEqualTo(5);
    assertBalanced();
  }

  @Test
  void concurrentRefundsCannotExceedPaidAmount() throws Exception {
    Long id = refundableInvoice();
    long entries = journal.count();
    afterUncommittedWrite(
        () -> invoices.markInvoiceRefunded(authorization, id, new MarkInvoiceRefundRequest(LocalDate.now(), 75, "first")),
        () -> assertThatThrownBy(() -> invoices.markInvoiceRefunded(authorization, id,
            new MarkInvoiceRefundRequest(LocalDate.now(), 75, "second")))
            .hasMessageContaining("greater than refundable amount"));
    assertThat(orders.findById(id).orElseThrow().getRefundedAmount()).isEqualTo(75);
    assertThat(journal.count()).isEqualTo(entries + 2);
    assertBalanced();
  }

  @Test
  void concurrentCreditRequestsCreateOnlyOneCreditInvoice() throws Exception {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    afterUncommittedWrite(
        () -> invoices.createCreditInvoice(authorization, id),
        () -> assertThatThrownBy(() -> invoices.createCreditInvoice(authorization, id))
            .hasMessageContaining("already credited"));
    assertThat(orders.count()).isEqualTo(2);
    assertThat(journal.count()).isEqualTo(6);
    assertBalanced();
  }

  @Test
  void paymentForMissingInvoiceReturnsNotFoundWithoutWrites() {
    assertThatThrownBy(() -> invoices.markInvoiceAsPaid(authorization, Long.MAX_VALUE, null))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    assertThat(journal.count()).isZero();
    assertThat(auditCount()).isZero();
  }

  private long auditCount() {
    return jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.5", "50.9", "\"50\"", "true", "{}", "[]", "2147483648"})
  void httpPaymentRejectsCoercionWithoutBooking(String amountJson) {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    assertThat(http.postForEntity("/invoices/" + id + "/paid",
        jsonRequest("{\"paidAmount\":" + amountJson + "}"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(journal.count()).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.5", "50.9", "\"50\"", "true", "{}", "[]", "2147483648"})
  void httpRefundRejectsCoercionWithoutBooking(String amountJson) {
    Long id = refundableInvoice();
    long entries = journal.count();
    assertThat(http.postForEntity("/invoices/" + id + "/refund",
        jsonRequest("{\"refundAmount\":" + amountJson + "}"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(orders.findById(id).orElseThrow().getRefundedAmount()).isZero();
    assertThat(journal.count()).isEqualTo(entries);
  }

  private HttpEntity<String> jsonRequest(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private Long refundableInvoice() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 125, "paid"));
    invoices.createCreditInvoice(authorization, id);
    return id;
  }

  private void afterUncommittedWrite(Runnable firstWrite, Runnable secondWrite) throws Exception {
    var executor = Executors.newFixedThreadPool(2);
    var written = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    try {
      var first = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
        firstWrite.run();
        written.countDown();
        try {
          if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(exception);
        }
      }));
      assertThat(written.await(15, TimeUnit.SECONDS)).isTrue();
      var second = executor.submit(() -> {
        started.countDown();
        secondWrite.run();
      });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void stripeBooksActualPartialAmountToClearingAccount() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    sendStripe(stripeEvent(id, "evt_partial", "cs_partial", 5000));
    Order saved = orders.findById(id).orElseThrow();
    assertThat(saved.getPaidAmount()).isEqualTo(50);
    assertThat(saved.getRemainingAmount()).isEqualTo(75);
    assertThat(jdbc.queryForObject("SELECT sum(debit) FROM journal_entries WHERE account_number = '1580'", Long.class)).isEqualTo(50);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_entries WHERE account_number = '1930'", Long.class)).isZero();
    assertThat(stripeEvents.existsById("checkout:cs_partial")).isTrue();
    assertBalanced();
  }

  @Test
  void stripeCashMethodUsesClearingAccountAndInvoiceTax() {
    jdbc.update("UPDATE app_settings SET accounting_method = 'CASH_METHOD'");
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    sendStripe(stripeEvent(id, "evt_cash", "cs_cash", 12500));
    assertThat(journal.count()).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT sum(debit) FROM journal_entries WHERE account_number = '1580'", Long.class)).isEqualTo(125);
    assertThat(jdbc.queryForObject("SELECT sum(credit) FROM journal_entries WHERE account_number = '2611'", Long.class)).isEqualTo(25);
    assertBalanced();
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -100, 5001, 214748364800L, Long.MAX_VALUE})
  void stripeRejectsUnsupportedAmountsWithoutWrites(long amount) {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    assertThatThrownBy(() -> sendStripe(stripeEvent(id, "evt_bad_amount", "cs_bad_amount", amount)))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    assertUnpaidStripeInvoice(id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"eur", "usd", ""})
  void stripeRejectsWrongOrMissingCurrency(String currency) {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    ObjectNode event = stripeEvent(id, "evt_currency", "cs_currency", 5000);
    stripeSession(event).put("currency", currency);
    assertThatThrownBy(() -> sendStripe(event)).hasMessageContaining("Only SEK");
    assertUnpaidStripeInvoice(id);
  }

  @Test
  void unpaidCheckoutWaitsForAsynchronousSuccess() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    ObjectNode event = stripeEvent(id, "evt_pending", "cs_delayed", 12500);
    stripeSession(event).put("payment_status", "unpaid");
    sendStripe(event);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(stripeEvents.existsById("checkout:cs_delayed")).isFalse();
    ObjectNode success = stripeEvent(id, "evt_success", "cs_delayed", 12500);
    success.put("type", "checkout.session.async_payment_succeeded");
    sendStripe(success);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(125);
    assertThat(journal.count()).isEqualTo(5);
  }

  @Test
  void repeatedEventsAndDifferentEventsForSameSessionDoNotDoubleBook() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    ObjectNode event = stripeEvent(id, "evt_first", "cs_once", 5000);
    sendStripe(event);
    sendStripe(event);
    event.put("id", "evt_second");
    event.put("type", "checkout.session.async_payment_succeeded");
    sendStripe(event);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(50);
    assertThat(orders.findById(id).orElseThrow().getPayments()).hasSize(1);
    assertThat(journal.count()).isEqualTo(5);
  }

  @Test
  void stripeAndManualPaymentSerializeAgainstSameInvoice() throws Exception {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    afterUncommittedWrite(
        () -> invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 50, "bank")),
        () -> sendStripe(stripeEvent(id, "evt_concurrent", "cs_concurrent", 7500)));
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(125);
    assertThat(orders.findById(id).orElseThrow().getPayments()).hasSize(2);
    assertThat(journal.count()).isEqualTo(7);
    assertBalanced();
  }

  @Test
  void concurrentStripeEventsForSameSessionOnlyBookOnce() throws Exception {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    afterUncommittedWrite(
        () -> sendStripe(stripeEvent(id, "evt_concurrent_first", "cs_same", 5000)),
        () -> sendStripe(stripeEvent(id, "evt_concurrent_second", "cs_same", 5000)));
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(50);
    assertThat(journal.count()).isEqualTo(5);
  }

  @Test
  void staleStripeAmountIsNotClampedToRemainingBalance() throws Exception {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    afterUncommittedWrite(
        () -> invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 50, "bank")),
        () -> assertThatThrownBy(() -> sendStripe(stripeEvent(id, "evt_stale", "cs_stale", 12500)))
            .hasMessageContaining("Reconcile manually"));
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(50);
    assertThat(stripeEvents.count()).isZero();
    assertThat(journal.count()).isEqualTo(5);
  }

  @Test
  void stripeDatabaseFailureRollsBackAndCanBeRetried() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    ObjectNode event = stripeEvent(id, "evt_retry", "cs_retry", 5000);
    rejectCreditRows();
    assertThatThrownBy(() -> sendStripe(event)).isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(ResponseStatusException.class);
    assertUnpaidStripeInvoice(id);
    jdbc.execute("DROP TRIGGER reject_test_credit ON journal_entries");
    sendStripe(event);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(50);
    assertThat(journal.count()).isEqualTo(5);
  }

  @Test
  void externalStripeSaleRequiresReviewedInvoiceInsteadOfAssumingVat() {
    ObjectNode event = stripeEvent(1L, "evt_external", "cs_external", 12500);
    stripeSession(event).remove("metadata");
    stripeSession(event).put("client_reference_id", "1");
    assertThatThrownBy(() -> sendStripe(event)).hasMessageContaining("External sales require reviewed tax");
    assertThat(journal.count()).isZero();
    assertThat(stripeEvents.count()).isZero();
  }

  @Test
  void invalidStripeSignatureCannotWriteAnything() {
    ObjectNode event = stripeEvent(1L, "evt_forged", "cs_forged", 12500);
    assertThatThrownBy(() -> stripe.handleWebhook(event.toString(), "t=1,v1=invalid"))
        .hasMessageContaining("Invalid Stripe webhook");
    assertThat(journal.count()).isZero();
    assertThat(stripeEvents.count()).isZero();
  }

  @Test
  void checkoutSessionUpdateDoesNotOverwriteRecordedPayment() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 50, "bank"));
    orders.updateStripeCheckoutSessionId(id, "cs_new");
    Order saved = orders.findById(id).orElseThrow();
    assertThat(saved.getPaidAmount()).isEqualTo(50);
    assertThat(saved.getStatus()).isEqualTo("PARTIALLY_PAID");
    assertThat(saved.getPayments()).hasSize(1);
  }

  private void assertUnpaidStripeInvoice(Long id) {
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(journal.count()).isEqualTo(3);
    assertThat(stripeEvents.count()).isZero();
  }

  private ObjectNode stripeEvent(Long invoiceId, String eventId, String sessionId, long amount) {
    ObjectNode event = new ObjectMapper().createObjectNode();
    event.put("id", eventId);
    event.put("object", "event");
    event.put("type", "checkout.session.completed");
    event.put("created", Instant.now().getEpochSecond());
    ObjectNode session = event.putObject("data").putObject("object");
    session.put("id", sessionId);
    session.put("object", "checkout.session");
    session.put("mode", "payment");
    session.put("payment_status", "paid");
    session.put("currency", "sek");
    session.put("amount_total", amount);
    session.putObject("metadata").put("invoiceId", invoiceId.toString());
    return event;
  }

  private ObjectNode stripeSession(ObjectNode event) {
    return (ObjectNode) event.path("data").path("object");
  }

  private void sendStripe(ObjectNode event) {
    String payload = event.toString();
    long timestamp = Instant.now().getEpochSecond();
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec("integration-only-signing-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
      stripe.handleWebhook(payload, "t=" + timestamp + ",v1=" + signature);
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
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
