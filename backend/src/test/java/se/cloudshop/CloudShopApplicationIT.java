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
import se.cloudshop.accounting.AccountingController;
import se.cloudshop.accounting.CreateManualJournalEntryRequest;
import se.cloudshop.accounting.CreateManualJournalEntryLineRequest;
import se.cloudshop.accounting.CreateManualMultiLineJournalEntryRequest;
import se.cloudshop.accounting.CreateOpeningBalanceRequest;
import se.cloudshop.accounting.CreateCorrectionJournalEntryRequest;
import java.util.List;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.accounting.JournalEntryRepository;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.JwtService;
import se.cloudshop.auth.AuthRequest;
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
import se.cloudshop.supplier.Supplier;
import se.cloudshop.supplier.SupplierInvoice;
import se.cloudshop.supplier.SupplierRepository;
import se.cloudshop.supplier.SupplierInvoiceRepository;
import se.cloudshop.supplier.SupplierController;
import se.cloudshop.supplier.CreateSupplierInvoiceRequest;
import se.cloudshop.supplier.UpdateSupplierInvoiceStatusRequest;
import se.cloudshop.supplier.CancelSupplierInvoiceRequest;

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
  @Autowired AccountingController accountingController;
  @Autowired se.cloudshop.accounting.AccountingPeriodLockService periodLocks;
  @Autowired SettingsService settings;
  @Autowired se.cloudshop.bank.BankReconciliationController bankController;
  @Autowired se.cloudshop.bank.BankReconciliationEntryRepository bankRows;
  @Autowired StripePaymentService stripe;
  @Autowired StripeWebhookEventRepository stripeEvents;
  @Autowired SupplierRepository suppliers;
  @Autowired SupplierInvoiceRepository supplierInvoices;
  @Autowired SupplierController supplierController;
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
    reset(audit, invoiceEmails);
    jdbc.execute("DROP TRIGGER IF EXISTS reject_test_credit ON journal_entries");
    jdbc.execute("ALTER TABLE invoice_originals DROP CONSTRAINT IF EXISTS reject_test_original");
    jdbc.execute("TRUNCATE app_users, invoice_originals, journal_entries, customer_orders, expenses, audit_events, stripe_webhook_events, supplier_invoices, suppliers, bank_reconciliation_entries RESTART IDENTITY CASCADE");
    jdbc.update("UPDATE app_settings SET accounting_locked_through_date = NULL, accounting_method = 'INVOICE_METHOD'");
    settings.getSettings();
    jdbc.update("UPDATE app_settings SET company_name = ?, company_address = ?, company_postal_code = ?, company_city = ?, company_organization_number = ?, vat_registration_number = ?",
        "Integration Test Seller", "Test Street 1", "111 22", "Stockholm", "556000-0000", "SE556000000001");
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
  void productionOwnerRegistrationCanOnlyBeUsedOncePerWorkspace() {
    AuthRequest ownerRequest = new AuthRequest("owner@example.invalid", "StrongTestPassword123");
    var first = http.postForEntity("/auth/register", ownerRequest, String.class);
    var second = http.postForEntity("/auth/register", new AuthRequest("second@example.invalid", "StrongTestPassword123"), String.class);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Long.class)).isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/profit-and-loss", "/balance-report", "/trial-balance", "/general-ledger",
      "/vat-report", "/vat-control", "/journal-integrity", "/account-sign-control",
      "/profit-and-loss/export", "/balance-report/export", "/trial-balance/export", "/general-ledger/export",
      "/sie/export", "/sie/receipt/export", "/bank-reconciliations/report", "/bank-reconciliations/report/export"})
  void oversizedReportsReturnExplicitErrorWithoutExportOrBookkeepingWrites(String path) throws Exception {
    for (int i = 0; i < 2; i++) {
      accounting.createManualEntry(new CreateManualJournalEntryRequest(LocalDate.now(), "Report limit test",
          "1930", "3041", 1_500_000_000));
      accounting.createManualEntry(new CreateManualJournalEntryRequest(LocalDate.now(), "VAT limit test",
          "1930", "2611", 1_500_000_000));
    }
    long journalCount = journal.count();
    long auditCount = jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class);
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var response = http.exchange(path, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    var error = new ObjectMapper().readTree(response.getBody());
    assertThat(error.path("code").asText()).isEqualTo("REPORT_AMOUNT_LIMIT");
    assertThat(error.path("message").asText()).contains("Rapporten har stoppats");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    assertThat(journal.count()).isEqualTo(journalCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class)).isEqualTo(auditCount);
  }

  @Test
  void realProfitReportReturnsLossWithoutTurningItIntoPositiveIncome() throws Exception {
    accounting.createManualEntry(new CreateManualJournalEntryRequest(LocalDate.now(), "Expense test", "5420", "1930", 300));
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var response = http.exchange("/profit-and-loss", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(new ObjectMapper().readTree(response.getBody()).path("result").asInt()).isEqualTo(-300);
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
  void incompleteSellerProfileCannotIssueOrBookInvoice() {
    Long id = draft().getId();
    jdbc.update("UPDATE app_settings SET company_city = ''");

    assertThatThrownBy(() -> invoices.markInvoiceAsSent(authorization, id))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("registered address");

    assertThat(journal.count()).isZero();
    assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo("DRAFT");
  }

  @Test
  void invoiceOverSimplifiedInvoiceLimitNeedsBuyerAddressBeforeBooking() {
    Long id = invoices.createInvoice(authorization,
        new CreateOrderRequest("Test customer", null, product.getId(), 41)).getId();

    assertThatThrownBy(() -> invoices.markInvoiceAsSent(authorization, id))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
        .hasMessageContaining("4,000 SEK");

    Order rejected = orders.findById(id).orElseThrow();
    assertThat(rejected.getStatus()).isEqualTo("DRAFT");
    assertThat(journal.findByInvoice(rejected)).isEmpty();
    assertThat(originalRows.findById(id)).isEmpty();
  }

  @Test
  void invoiceOverSimplifiedInvoiceLimitCanBeIssuedWithCompleteBuyerAddress() {
    var customer = customers.save(new se.cloudshop.customer.Customer(
        "Test business buyer", "buyer@example.invalid", "", "Buyer Street 1", "", "111 22", "Stockholm"));
    Long id = invoices.createInvoice(authorization,
        new CreateOrderRequest(customer.getName(), customer.getId(), product.getId(), 41)).getId();

    Order issued = invoices.markInvoiceAsSent(authorization, id);

    assertThat(issued.getStatus()).isEqualTo("SENT");
    assertThat(journal.findByInvoice(issued)).hasSize(3);
    assertThat(issued.isDocumentSnapshotAvailable()).isTrue();
    assertBalanced();
  }

  @Test
  void invoiceAtSimplifiedInvoiceLimitCanBeIssuedWithoutBuyerAddress() {
    Long id = invoices.createInvoice(authorization,
        new CreateOrderRequest("Test customer", null, product.getId(), 32)).getId();

    Order issued = invoices.markInvoiceAsSent(authorization, id);

    assertThat(issued.getTotalAmount()).isEqualTo(4_000);
    assertThat(issued.getStatus()).isEqualTo("SENT");
    assertThat(journal.findByInvoice(issued)).hasSize(3);
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
  void closingFirstRejectsWaitingPostingEvenWithCachedSettings() throws Exception {
    afterUncommittedWrite(
        () -> periodLocks.closePeriod(LocalDate.now(), authorization),
        () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
          assertThat(settings.getSettings().getAccountingLockedThroughDate()).isNull();
          assertThatThrownBy(() -> accounting.createManualEntry(periodRaceEntry(LocalDate.now())))
              .isInstanceOf(ResponseStatusException.class).hasMessageContaining("last till och med");
          status.setRollbackOnly();
        }));
    assertThat(journal.count()).isZero();
    assertThat(settings.getSettings().getAccountingLockedThroughDate()).isEqualTo(LocalDate.now());
  }

  @Test
  void postingFirstIsIncludedInWaitingPeriodCloseChecks() throws Exception {
    afterUncommittedWrite(
        () -> accounting.createManualEntry(periodRaceEntry(LocalDate.now())),
        () -> assertThatThrownBy(() -> periodLocks.closePeriod(LocalDate.now(), authorization))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("attest"));
    assertThat(journal.count()).isEqualTo(2);
    assertThat(settings.getSettings().getAccountingLockedThroughDate()).isNull();
  }

  @Test
  void postingInOpenPeriodWaitsForCloseThenSucceeds() throws Exception {
    afterUncommittedWrite(
        () -> periodLocks.closePeriod(LocalDate.now().minusDays(1), authorization),
        () -> accounting.createManualEntry(periodRaceEntry(LocalDate.now())));
    assertThat(journal.count()).isEqualTo(2);
    assertThat(settings.getSettings().getAccountingLockedThroughDate()).isEqualTo(LocalDate.now().minusDays(1));
    assertBalanced();
  }

  @Test
  void concurrentCloseCannotMoveDateBackwards() throws Exception {
    afterUncommittedWrite(
        () -> periodLocks.closePeriod(LocalDate.now(), authorization),
        () -> assertThatThrownBy(() -> periodLocks.closePeriod(LocalDate.now().minusDays(1), authorization))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("redan last"));
    assertThat(settings.getSettings().getAccountingLockedThroughDate()).isEqualTo(LocalDate.now());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE event_action = 'period_locked'", Long.class)).isEqualTo(1);
  }

  @Test
  void failedCloseAuditRollsBackDateAndReleasesWriteLock() {
    failAudit();
    assertThatThrownBy(() -> periodLocks.closePeriod(LocalDate.now(), authorization)).isInstanceOf(IllegalStateException.class);
    assertThat(settings.getSettings().getAccountingLockedThroughDate()).isNull();
    reset(audit);
    accounting.createManualEntry(periodRaceEntry(LocalDate.now()));
    assertThat(journal.count()).isEqualTo(2);
  }

  @Test
  void waitingSettingsUpdateCannotOverwriteNewPeriodLock() throws Exception {
    var requested = settings.getSettings();
    requested.setCompanyName("Concurrent settings change");
    afterUncommittedWrite(
        () -> periodLocks.closePeriod(LocalDate.now(), authorization),
        () -> assertThatThrownBy(() -> settings.updateSettings(requested))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not regular settings"));
    assertThat(settings.getSettings().getAccountingLockedThroughDate()).isEqualTo(LocalDate.now());
    assertThat(settings.getSettings().getCompanyName()).isNotEqualTo("Concurrent settings change");
  }

  @Test
  void accountingWriteLockRequiresAnEnclosingTransaction() {
    assertThatThrownBy(settings::lockSettingsForAccounting)
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  private CreateManualJournalEntryRequest periodRaceEntry(LocalDate date) {
    return new CreateManualJournalEntryRequest(date, "Period concurrency test", "1930", "2018", 100);
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

  @Test
  void concurrentCorrectionRequestsCreateOnlyOneCorrectionVoucher() throws Exception {
    String originalVoucher = accounting.createManualEntry(new CreateManualJournalEntryRequest(
        LocalDate.now(), "Correction concurrency test", "1930", "2018", 100)).getFirst().getVoucherNumber();
    var executor = Executors.newFixedThreadPool(2);
    var correctionCreated = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var secondStarted = new CountDownLatch(1);
    try {
      var first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
        accounting.createCorrectionEntry(originalVoucher, new CreateCorrectionJournalEntryRequest(LocalDate.now()));
        correctionCreated.countDown();
        try {
          if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(exception);
        }
        return null;
      }));
      assertThat(correctionCreated.await(15, TimeUnit.SECONDS)).isTrue();
      var second = executor.submit(() -> {
        secondStarted.countDown();
        try {
          accounting.createCorrectionEntry(originalVoucher, new CreateCorrectionJournalEntryRequest(LocalDate.now()));
          return false;
        } catch (ResponseStatusException exception) {
          return exception.getStatusCode() == HttpStatus.CONFLICT;
        }
      });
      assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();
      first.get(15, TimeUnit.SECONDS);
      assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
      List<JournalEntry> correctionEntries = journal.findByCorrectionOfVoucherNumber(originalVoucher);
      assertThat(correctionEntries).hasSize(2);
      assertThat(correctionEntries.stream().map(JournalEntry::getVoucherNumber).distinct().count()).isEqualTo(1);
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
        new MarkInvoicePaidRequest(LocalDate.now(), null, "remaining"));
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
        new MarkInvoiceRefundRequest(LocalDate.now(), null, "remaining"));
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
  @ValueSource(booleans = {false, true})
  void wrappedVoucherSumCannotPassBalanceCheck(boolean openingBalance) {
    List<CreateManualJournalEntryLineRequest> lines = List.of(
        new CreateManualJournalEntryLineRequest("1930", Integer.MAX_VALUE, 0),
        new CreateManualJournalEntryLineRequest("1930", Integer.MAX_VALUE, 0),
        new CreateManualJournalEntryLineRequest("1930", 102, 0),
        new CreateManualJournalEntryLineRequest("2018", 0, 100));
    assertThatThrownBy(() -> {
      if (openingBalance) accounting.createOpeningBalanceEntry(new CreateOpeningBalanceRequest(LocalDate.now(), "Test", lines));
      else accounting.createManualMultiLineEntry(new CreateManualMultiLineJournalEntryRequest(LocalDate.now(), "Test", lines));
    }).isInstanceOf(ResponseStatusException.class).hasMessageContaining("balance debit and credit");
    assertThat(journal.count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oversizedBalancedVoucherIsRejectedBeforeWriting(boolean openingBalance) {
    List<CreateManualJournalEntryLineRequest> lines = List.of(
        new CreateManualJournalEntryLineRequest("1930", Integer.MAX_VALUE, 0),
        new CreateManualJournalEntryLineRequest("1930", 1, 0),
        new CreateManualJournalEntryLineRequest("2018", 0, Integer.MAX_VALUE),
        new CreateManualJournalEntryLineRequest("2018", 0, 1));
    assertThatThrownBy(() -> {
      if (openingBalance) accounting.createOpeningBalanceEntry(new CreateOpeningBalanceRequest(LocalDate.now(), "Test", lines));
      else accounting.createManualMultiLineEntry(new CreateManualMultiLineJournalEntryRequest(LocalDate.now(), "Test", lines));
    }).isInstanceOf(ResponseStatusException.class).hasMessageContaining("total exceeds the supported limit");
    assertThat(journal.count()).isZero();
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

  @ParameterizedTest
  @ValueSource(strings = {"0.5", "100.99", "-1", "2147483648"})
  void expenseRejectsInvalidNetWithoutAnyWrites(String netJson) {
    assertThat(http.postForEntity("/expenses", jsonRequest("{\"description\":\"Test\",\"netAmount\":" + netJson + ",\"vatAmount\":0}"), String.class)
        .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
    assertThat(journal.count()).isZero();
    assertThat(auditCount()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.5", "25.99", "-1", "2147483647"})
  void expenseRejectsInvalidVatOrOverflowWithoutAnyWrites(String vatJson) {
    assertThat(http.postForEntity("/expenses", jsonRequest("{\"description\":\"Test\",\"netAmount\":100,\"vatAmount\":" + vatJson + "}"), String.class)
        .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
    assertThat(journal.count()).isZero();
    assertThat(auditCount()).isZero();
  }

  @Test
  void expenseAcceptsExplicitZeroVatAndBooksExactAmount() {
    assertThat(http.postForEntity("/expenses", jsonRequest("{\"expenseDate\":\"" + LocalDate.now() + "\",\"description\":\"Test\",\"netAmount\":100,\"vatAmount\":0}"), String.class)
        .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(jdbc.queryForObject("SELECT total_amount FROM expenses", Integer.class)).isEqualTo(100);
    assertBalanced();
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 2})
  void invoiceOverflowDoesNotCreateDraftOrAudit(int quantity) {
    product.setPrice(1_000_000_000);
    products.save(product);
    String body = "{\"customerName\":\"Test\",\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}";
    assertThat(http.postForEntity("/invoices", jsonRequest(body), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(orders.count()).isZero();
    assertThat(journal.count()).isZero();
    assertThat(auditCount()).isZero();
  }

  @Test
  void fractionalInvoiceQuantityIsNotSilentlyTruncated() {
    String body = "{\"customerName\":\"Test\",\"productId\":" + product.getId() + ",\"quantity\":1.9}";
    assertThat(http.postForEntity("/invoices", jsonRequest(body), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(orders.count()).isZero();
    assertThat(journal.count()).isZero();
  }

  @Test
  void largeCashInvoiceBooksCorrectVatInPostgresAndReversesOnRefund() {
    jdbc.update("UPDATE app_settings SET accounting_method = 'CASH_METHOD'");
    product.setPrice(100_000);
    products.save(product);
    Long id = invoices.markInvoiceAsSent(authorization, draftWithBuyerAddress().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 100_000, "first"));
    invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 25_000, "last"));
    assertThat(jdbc.queryForObject("SELECT sum(credit) FROM journal_entries WHERE account_number='2611'", Long.class)).isEqualTo(25_000);
    invoices.createCreditInvoice(authorization, id);
    invoices.markInvoiceRefunded(authorization, id, new MarkInvoiceRefundRequest(LocalDate.now(), 125_000, "refund"));
    assertThat(jdbc.queryForObject("SELECT sum(debit) FROM journal_entries WHERE account_number='2611'", Long.class)).isEqualTo(25_000);
    assertBalanced();
  }

  private Long refundableInvoice() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 125, "paid"));
    invoices.createCreditInvoice(authorization, id);
    return id;
  }

  @Test
  void creditUsesHistoricInvoiceEvenIfProductPriceNowExceedsLimit() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    product.setPrice(Integer.MAX_VALUE);
    products.save(product);
    Order credit = invoices.createCreditInvoice(authorization, id);
    assertThat(credit.getNetAmount()).isEqualTo(-100);
    assertThat(credit.getVatAmount()).isEqualTo(-25);
    assertThat(credit.getTotalAmount()).isEqualTo(-125);
    assertBalanced();
  }

  @Test
  void concurrentSupplierPartialPaymentsPreserveBothAmounts() throws Exception {
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    afterUncommittedWrite(() -> paySupplier(id, 50, "first"), () -> paySupplier(id, 75, "second"));
    SupplierInvoice saved = supplierInvoices.findById(id).orElseThrow();
    assertThat(saved.getPaidAmount()).isEqualTo(125);
    assertThat(saved.getStatus()).isEqualTo("paid");
    assertThat(saved.getPaymentHistory().lines().count()).isEqualTo(2);
    assertThat(journal.count()).isEqualTo(7);
    assertBalanced();
  }

  @Test
  void concurrentSupplierDuplicatePaymentDoesNotBookTwice() throws Exception {
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    afterUncommittedWrite(() -> paySupplier(id, 50, "same"), () ->
        assertThatThrownBy(() -> paySupplier(id, 50, "same")).hasMessageContaining("already registered"));
    assertThat(supplierInvoices.findById(id).orElseThrow().getPaidAmount()).isEqualTo(50);
    assertThat(journal.count()).isEqualTo(5);
    assertBalanced();
  }

  @Test
  void concurrentSupplierPaymentsCannotExceedRemainingAmount() throws Exception {
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    afterUncommittedWrite(() -> paySupplier(id, 100, "first"), () ->
        assertThatThrownBy(() -> paySupplier(id, 50, "second")).hasMessageContaining("remaining amount"));
    assertThat(supplierInvoices.findById(id).orElseThrow().getPaidAmount()).isEqualTo(100);
    assertThat(journal.count()).isEqualTo(5);
  }

  @Test
  void concurrentSupplierCancellationCannotEraseCommittedPayment() throws Exception {
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    afterUncommittedWrite(() -> paySupplier(id, 50, "first"), () ->
        assertThatThrownBy(() -> supplierController.cancelSupplierInvoice(authorization, id, null))
            .hasMessageContaining("cannot be cancelled"));
    assertThat(supplierInvoices.findById(id).orElseThrow().getStatus()).isEqualTo("partial");
    assertThat(journal.count()).isEqualTo(5);
  }

  @Test
  void concurrentSupplierPaymentCannotReactivateCancelledInvoice() throws Exception {
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    afterUncommittedWrite(() -> supplierController.cancelSupplierInvoice(
            authorization, id, new CancelSupplierInvoiceRequest(LocalDate.now())), () ->
        assertThatThrownBy(() -> paySupplier(id, 50, "late")).hasMessageContaining("cannot be reactivated"));
    assertThat(supplierInvoices.findById(id).orElseThrow().getStatus()).isEqualTo("cancelled");
    assertThat(supplierInvoices.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(journal.count()).isEqualTo(6);
    assertBalanced();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void supplierPaymentFailureRollsBackBalanceJournalHistoryAndAudit(boolean auditFailure) {
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    long auditBefore = auditCount();
    if (auditFailure) failAudit(); else rejectCreditRows();
    assertThatThrownBy(() -> paySupplier(id, 50, "rollback")).isInstanceOf(RuntimeException.class);
    SupplierInvoice saved = supplierInvoices.findById(id).orElseThrow();
    assertThat(saved.getPaidAmount()).isZero();
    assertThat(saved.getPaymentHistory()).isEmpty();
    assertThat(journal.count()).isEqualTo(3);
    assertThat(auditCount()).isEqualTo(auditBefore);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void supplierInvoiceFromLockedPeriodCanBePaidOnOpenDate(boolean cashMethod) {
    if (cashMethod) jdbc.update("UPDATE app_settings SET accounting_method = 'CASH_METHOD'");
    LocalDate yesterday = LocalDate.now().minusDays(1);
    Long id = supplierInvoice(125, yesterday).getId();
    settings.lockAccountingThroughDate(yesterday);
    paySupplier(id, 125, "today");
    assertThat(supplierInvoices.findById(id).orElseThrow().getPaidAmount()).isEqualTo(125);
    assertThat(jdbc.queryForObject("SELECT sum(credit) FROM journal_entries WHERE account_number = '1930'", Long.class)).isEqualTo(125);
    assertBalanced();
  }

  @Test
  void cashSupplierPaymentCannotBeCancelledOrMovedBackToBooked() {
    jdbc.update("UPDATE app_settings SET accounting_method = 'CASH_METHOD'");
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    paySupplier(id, 50, "first");
    assertThatThrownBy(() -> supplierController.cancelSupplierInvoice(authorization, id, null)).hasMessageContaining("cannot be cancelled");
    assertThatThrownBy(() -> supplierController.updateSupplierInvoiceStatus(authorization, id,
        new UpdateSupplierInvoiceStatusRequest("booked", null, null, ""))).hasMessageContaining("Keep its payment status");
    assertThat(supplierInvoices.findById(id).orElseThrow().getStatus()).isEqualTo("partial");
    assertThat(journal.count()).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/receivables/aging", "/receivables/aging/export", "/payables/aging", "/payables/aging/export", "/supplier-invoices/export"})
  void oversizedOutstandingBalancesCannotReturnSuccessfulReportOrExport(String path) throws Exception {
    for (int i = 0; i < 2; i++) {
      if (path.startsWith("/receivables")) {
        product.setPrice(1_200_000_000);
        products.save(product);
        invoices.markInvoiceAsSent(authorization, draftWithBuyerAddress().getId());
      } else {
        supplierInvoice(1_500_000_000, LocalDate.now());
      }
    }
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    long before = auditCount();
    var response = http.exchange(path, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(new ObjectMapper().readTree(response.getBody()).path("code").asText()).isEqualTo("REPORT_AMOUNT_LIMIT");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    assertThat(auditCount()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/receivables/aging", "/receivables/aging/export", "/payables/aging", "/payables/aging/export"})
  void historicalBalancesAndCsvUsePaymentDatesInsteadOfCurrentPaidStatus(String path) throws Exception {
    LocalDate issued = LocalDate.now().minusDays(10);
    if (path.startsWith("/receivables")) {
      Long id = draft().getId();
      jdbc.update("UPDATE customer_orders SET invoice_date = ? WHERE id = ?", issued, id);
      invoices.markInvoiceAsSent(authorization, id);
      invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(issued.plusDays(2), 50, "first"));
      invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(issued.plusDays(6), 75, "last"));
    } else {
      Long id = supplierInvoice(125, issued).getId();
      supplierController.updateSupplierInvoiceStatus(authorization, id,
          new UpdateSupplierInvoiceStatusRequest("paid", issued.plusDays(2), 50, "first"));
      supplierController.updateSupplierInvoiceStatus(authorization, id,
          new UpdateSupplierInvoiceStatusRequest("paid", issued.plusDays(6), 75, "last"));
    }
    long journalCount = journal.count();
    long events = auditCount();
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var response = http.exchange(path + "?asOf=" + issued.plusDays(2), org.springframework.http.HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    if (path.endsWith("/export")) {
      assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
      assertThat(response.getBody()).contains("Avstamningsdatum," + issued.plusDays(2));
      assertThat(response.getBody()).contains(path.startsWith("/receivables") ? "Totalt utestaende,75" : "Totalt att betala,75");
      assertThat(auditCount()).isEqualTo(events + 1);
    } else {
      var body = new ObjectMapper().readTree(response.getBody());
      assertThat(body.path("totalOutstanding").asInt()).isEqualTo(75);
      assertThat(body.path("invoiceCount").asInt()).isEqualTo(1);
      assertThat(body.path("invoices").get(0).path("remainingAmount").asInt()).isEqualTo(75);
      assertThat(auditCount()).isEqualTo(events);
    }
    assertThat(journal.count()).isEqualTo(journalCount);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/receivables/aging", "/receivables/aging/export", "/payables/aging", "/payables/aging/export"})
  void incompletePaymentHistoryStopsReportAndExportWithoutSideEffects(String path) throws Exception {
    if (path.startsWith("/receivables")) {
      Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
      jdbc.update("UPDATE customer_orders SET paid_amount = 50, status = 'PARTIALLY_PAID' WHERE id = ?", id);
    } else {
      Long id = supplierInvoice(125, LocalDate.now()).getId();
      jdbc.update("UPDATE supplier_invoices SET paid_amount = 50, status = 'partial' WHERE id = ?", id);
    }
    long journalCount = journal.count();
    long events = auditCount();
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var response = http.exchange(path, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(new ObjectMapper().readTree(response.getBody()).path("code").asText()).isEqualTo("SETTLEMENT_HISTORY_INCOMPLETE");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    assertThat(auditCount()).isEqualTo(events);
    assertThat(journal.count()).isEqualTo(journalCount);
  }

  @Test
  void cashSupplierInvoiceCannotBeDeletedOrCancelledWithoutADate() {
    jdbc.update("UPDATE app_settings SET accounting_method = 'CASH_METHOD'");
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    assertThatThrownBy(() -> supplierController.deleteSupplierInvoice(authorization, id))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("must be retained");
    assertThatThrownBy(() -> supplierController.updateSupplierInvoiceStatus(authorization, id,
        new UpdateSupplierInvoiceStatusRequest("cancelled", LocalDate.now(), null, "")))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cancellation endpoint");
    assertThat(supplierInvoices.findById(id)).isPresent();
    supplierController.cancelSupplierInvoice(authorization, id, new CancelSupplierInvoiceRequest(LocalDate.now()));
    assertThat(supplierInvoices.findById(id).orElseThrow().getCancelledAt()).isEqualTo(LocalDate.now());
  }

  @Test
  void multilineSupplierPaymentReferenceCannotCorruptHistoryOrWriteJournal() {
    Long id = supplierInvoice(125, LocalDate.now()).getId();
    long events = auditCount();
    long journalCount = journal.count();
    assertThatThrownBy(() -> paySupplier(id, 50, "reference\nsecond line"))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("single line");
    assertThat(supplierInvoices.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(journal.count()).isEqualTo(journalCount);
    assertThat(auditCount()).isEqualTo(events);
  }

  @Test
  void subledgerControlRequiresAuthentication() {
    assertThat(http.getForEntity("/subledger-control", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void realPartialCustomerAndSupplierPaymentsReconcileWithoutWritingData() throws Exception {
    Long customerId = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, customerId, new MarkInvoicePaidRequest(LocalDate.now(), 50, "first"));
    Long supplierId = supplierInvoice(125, LocalDate.now()).getId();
    paySupplier(supplierId, 50, "first");
    long events = auditCount();
    long entries = journal.count();
    var body = subledgerControl();
    assertThat(body.path("status").asText()).isEqualTo("MATCHED");
    for (var account : body.path("accounts")) {
      assertThat(account.path("ledgerBalance").asInt()).isEqualTo(75);
      assertThat(account.path("subledgerBalance").asInt()).isEqualTo(75);
      assertThat(account.path("difference").asInt()).isZero();
    }
    assertThat(auditCount()).isEqualTo(events);
    assertThat(journal.count()).isEqualTo(entries);
  }

  @Test
  void mislinkedPaymentIsCaughtEvenWhenGrandTotalsMatchAndBlocksPeriodClose() throws Exception {
    Long first = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    Long second = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, first, new MarkInvoicePaidRequest(LocalDate.now(), 50, "first"));
    jdbc.update("UPDATE journal_entries SET invoice_id = ? WHERE invoice_id = ? AND account_number = '1510' AND credit = 50", second, first);
    var body = subledgerControl();
    assertThat(body.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
    assertThat(body.path("accounts").get(0).path("difference").asInt()).isZero();
    assertThat(body.path("accounts").get(0).path("differences").size()).isEqualTo(2);
    assertThat(periodLocks.checkPeriod(LocalDate.now()).blockers()).anyMatch(message -> message.contains("Reskontra och huvudbok"));
    assertThatThrownBy(() -> periodLocks.closePeriod(LocalDate.now(), authorization)).isInstanceOf(ResponseStatusException.class);
    assertThat(settings.getSettings().getAccountingLockedThroughDate()).isNull();
  }

  @Test
  void cashMethodIsExplicitlyNotAnAutomaticSubledgerMatch() throws Exception {
    jdbc.update("UPDATE app_settings SET accounting_method = 'CASH_METHOD'");
    invoices.markInvoiceAsSent(authorization, draft().getId());
    var body = subledgerControl();
    assertThat(body.path("status").asText()).isEqualTo("UNSUPPORTED_METHOD");
    assertThat(body.path("accounts").get(0).path("difference").isNull()).isTrue();
    assertThat(periodLocks.checkPeriod(LocalDate.now()).blockers()).anyMatch(message -> message.contains("stodjer inte vald bokforingsmetod"));
  }

  @Test
  void unlinkedOffsettingManualEntriesNeverBecomeGreen() throws Exception {
    accounting.createManualEntry(new CreateManualJournalEntryRequest(LocalDate.now(), "test", "1510", "3041", 50));
    accounting.createManualEntry(new CreateManualJournalEntryRequest(LocalDate.now(), "test", "3041", "1510", 50));
    var body = subledgerControl();
    assertThat(body.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
    assertThat(body.path("accounts").get(0).path("difference").asInt()).isZero();
    assertThat(body.path("accounts").get(0).path("unlinkedEntryCount").asInt()).isEqualTo(2);
  }

  @Test
  void creditedCustomerRefundRemainsForReviewUntilRefundIsBooked() throws Exception {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.markInvoiceAsPaid(authorization, id, new MarkInvoicePaidRequest(LocalDate.now(), 50, "first"));
    invoices.createCreditInvoice(authorization, id);
    assertThat(subledgerControl().path("status").asText()).isEqualTo("REVIEW_REQUIRED");
    invoices.markInvoiceRefunded(authorization, id, new MarkInvoiceRefundRequest(LocalDate.now(), 50, "refund"));
    assertThat(subledgerControl().path("status").asText()).isEqualTo("MATCHED");
  }

  private com.fasterxml.jackson.databind.JsonNode subledgerControl() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var response = http.exchange("/subledger-control?asOf=" + LocalDate.now(), org.springframework.http.HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return new ObjectMapper().readTree(response.getBody());
  }

  private SupplierInvoice supplierInvoice(int total, LocalDate invoiceDate) {
    Supplier supplier = suppliers.save(new Supplier("Test supplier", "supplier@example.invalid", "", "", ""));
    return supplierController.createSupplierInvoice(authorization, new CreateSupplierInvoiceRequest(
        supplier.getId(), invoiceDate, LocalDate.now().plusDays(30), "Test invoice", "test-ref", total, total / 5, "5420"));
  }

  private void paySupplier(Long id, int amount, String reference) {
    supplierController.updateSupplierInvoiceStatus(authorization, id,
        new UpdateSupplierInvoiceStatusRequest("paid", LocalDate.now(), amount, reference));
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

  @Test
  void bankPaymentAndHistoryCommitTogether() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.registerBankPayment(authorization, id, bankPayment("bank-test", 50));
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(50);
    assertThat(bankRows.findAll()).singleElement().satisfies(row -> {
      assertThat(row.getAmount()).isEqualTo(50);
      assertThat(row.getStatus()).isEqualTo("booked");
      assertThat(row.getMatchLabel()).isEqualTo("Invoice " + id);
    });
    assertThat(journal.count()).isEqualTo(5);
    assertBalanced();
  }

  @Test
  void manualVoucherAuditFailureRollsBackAllJournalLines() {
    failAudit();

    assertThatThrownBy(() -> accountingController.createManualJournalEntry(
        authorization,
        new CreateManualJournalEntryRequest(
            LocalDate.now(),
            "Manual audit rollback",
            "5420",
            "1930",
            100
        )
    )).isInstanceOf(RuntimeException.class);

    assertThat(journal.count()).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM audit_events WHERE event_action = 'manual_created'",
        Long.class
    )).isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void bankAuditOrJournalFailureRollsBackEverything(boolean auditFailure) {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    if (auditFailure) {
      doThrow(new IllegalStateException("Simulated bank audit failure")).when(audit).record(
          org.mockito.ArgumentMatchers.eq("bank"), anyString(), any(), anyString(), anyString(), anyString(), anyInt(), anyString());
    } else rejectCreditRows();
    assertThatThrownBy(() -> invoices.registerBankPayment(authorization, id, bankPayment("bank-fail", 50))).isInstanceOf(RuntimeException.class);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
    assertThat(bankRows.count()).isZero();
    assertThat(journal.count()).isEqualTo(3);
  }

  @Test
  void bankRowCannotBeReusedForAnotherInvoice() {
    Long first = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    Long second = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.registerBankPayment(authorization, first, bankPayment("same-bank", 50));
    assertThatThrownBy(() -> invoices.registerBankPayment(authorization, second, bankPayment("same-bank", 50)))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(orders.findById(second).orElseThrow().getPaidAmount()).isZero();
    assertThat(bankRows.count()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -10, 126})
  void bankPaymentDoesNotClampInvalidAmount(int amount) {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    assertThatThrownBy(() -> invoices.registerBankPayment(authorization, id, bankPayment("invalid-amount", amount)))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(bankRows.count()).isZero();
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void bankBookingMustMatchFullAmountAndDate(boolean wrongDate) {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    var row = bankRow("mismatch", wrongDate ? 50 : 51);
    var request = new MarkInvoicePaidRequest(wrongDate ? LocalDate.now().plusDays(1) : LocalDate.now(), 50, "ref", row);
    assertThatThrownBy(() -> invoices.registerBankPayment(authorization, id, request)).isInstanceOf(ResponseStatusException.class);
    assertThat(bankRows.count()).isZero();
    assertThat(journal.count()).isEqualTo(3);
  }

  @Test
  void bankExpenseAndHistoryCommitTogetherAndCannotBeRepeated() {
    var request = bankExpense("expense-row");
    var expense = expenses.createBankExpense(authorization, request);
    assertThat(bankRows.findAll()).singleElement().satisfies(row -> {
      assertThat(row.getAmount()).isEqualTo(-125);
      assertThat(row.getMatchLabel()).isEqualTo("Expense " + expense.getId());
    });
    assertThatThrownBy(() -> expenses.createBankExpense(authorization, request)).isInstanceOf(ResponseStatusException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isEqualTo(1);
    assertThat(bankRows.count()).isEqualTo(1);
    assertBalanced();
  }

  @Test
  void bankExpenseAuditFailureDoesNotLeaveAnExpenseOrHistory() {
    doThrow(new IllegalStateException("Simulated bank audit failure")).when(audit).record(
        org.mockito.ArgumentMatchers.eq("bank"), anyString(), any(), anyString(), anyString(), anyString(), anyInt(), anyString());
    assertThatThrownBy(() -> expenses.createBankExpense(authorization, bankExpense("expense-fail"))).isInstanceOf(RuntimeException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
    assertThat(bankRows.count()).isZero();
    assertThat(journal.count()).isZero();
  }

  @Test
  void bankRowsRespectPeriodLockAndCannotFakeBookedHistory() {
    settings.lockAccountingThroughDate(LocalDate.now());
    assertThatThrownBy(() -> expenses.createBankExpense(authorization, bankExpense("locked"))).isInstanceOf(ResponseStatusException.class);
    var row = new se.cloudshop.bank.CreateBankReconciliationEntryRequest("fake", LocalDate.now(), "Test", "ref", 50, "payment", "booked", "fake");
    assertThatThrownBy(() -> bankController.createBankReconciliation(authorization, row)).isInstanceOf(ResponseStatusException.class);
    assertThat(bankRows.count()).isZero();
    assertThat(journal.count()).isZero();
  }

  @Test
  void skippedBankRowMustBeRestoredBeforeBookingAndCannotBeRemovedInClosedPeriod() {
    var row = new se.cloudshop.bank.CreateBankReconciliationEntryRequest("skipped", LocalDate.now(), "Test", "ref", -125, "expense", "skipped", "");
    bankController.createBankReconciliation(authorization, row);
    assertThatThrownBy(() -> expenses.createBankExpense(authorization, bankExpense("skipped"))).isInstanceOf(ResponseStatusException.class);
    bankController.removeSkippedBankReconciliation(authorization, "skipped");
    expenses.createBankExpense(authorization, bankExpense("skipped"));
    bankController.removeSkippedBankReconciliation(authorization, "skipped");
    assertThat(bankRows.count()).isEqualTo(1);
    bankController.createBankReconciliation(authorization, new se.cloudshop.bank.CreateBankReconciliationEntryRequest("closed-skip", LocalDate.now(), "Test", "ref", -125, "expense", "skipped", ""));
    settings.lockAccountingThroughDate(LocalDate.now());
    assertThatThrownBy(() -> bankController.removeSkippedBankReconciliation(authorization, "closed-skip")).isInstanceOf(ResponseStatusException.class);
    assertThat(bankRows.count()).isEqualTo(2);
  }

  @Test
  void bankEndpointsRequireAuthenticationAndExplicitBankRow() throws Exception {
    assertThat(http.postForEntity("/bank-import/expenses", jsonRequest("{}"), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(http.postForEntity("/bank-import/invoices/1/paid", jsonRequest("{}"), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    for (String route : List.of("/bank-import/expenses", "/bank-import/invoices/1/paid", "/bank-reconciliations/1/journal-link")) {
      var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(http.getRootUri() + route))
          .header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
          .timeout(java.time.Duration.ofSeconds(10)).build();
      assertThat(java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
    }
    assertThat(bankRows.count()).isZero();
  }

  private se.cloudshop.bank.BankImportRow bankRow(String key, int amount) {
    return new se.cloudshop.bank.BankImportRow(key, LocalDate.now(), "Test", "ref", amount);
  }

  @Test
  void concurrentBankRowOnDifferentInvoicesBooksOnlyOnce() throws Exception {
    Long first = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    Long second = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    afterUncommittedWrite(() -> invoices.registerBankPayment(authorization, first, bankPayment("race", 50)),
        () -> assertThatThrownBy(() -> invoices.registerBankPayment(authorization, second, bankPayment("race", 50)))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)));
    assertThat(bankRows.count()).isEqualTo(1);
    assertThat(orders.findById(second).orElseThrow().getPaidAmount()).isZero();
    assertThat(journal.count()).isEqualTo(8);
  }

  @Test
  void concurrentBankExpenseCreatesOnlyOneExpense() throws Exception {
    afterUncommittedWrite(() -> expenses.createBankExpense(authorization, bankExpense("expense-race")),
        () -> assertThatThrownBy(() -> expenses.createBankExpense(authorization, bankExpense("expense-race")))
            .isInstanceOf(ResponseStatusException.class));
    assertThat(bankRows.count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isEqualTo(1);
    assertBalanced();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "bad id", "../escape"})
  void bankRowRequiresValidIdentity(String key) {
    assertThatThrownBy(() -> expenses.createBankExpense(authorization, bankExpense(key))).isInstanceOf(ResponseStatusException.class);
    assertThat(bankRows.count()).isZero();
    assertThat(journal.count()).isZero();
  }

  private MarkInvoicePaidRequest bankPayment(String key, int amount) {
    return new MarkInvoicePaidRequest(LocalDate.now(), amount, "ref", bankRow(key, amount));
  }

  @Test
  void bankHttpEndpointBindsAtomicRowAndRejectsRetry() {
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    String body = "{\"paymentDate\":\"" + LocalDate.now() + "\",\"paidAmount\":50,\"paymentReference\":\"ref\","
        + "\"bankRow\":{\"bankRowId\":\"http-row\",\"date\":\"" + LocalDate.now() + "\",\"amount\":50,\"description\":\"Test\",\"reference\":\"ref\"}}";
    assertThat(http.postForEntity("/bank-import/invoices/" + id + "/paid", jsonRequest(body), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.postForEntity("/bank-import/invoices/" + id + "/paid", jsonRequest(body), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(bankRows.count()).isEqualTo(1);
    assertThat(orders.findById(id).orElseThrow().getPaidAmount()).isEqualTo(50);
  }

  @Test
  void bankExpenseRejectsWrongAccountAndMissingDate() {
    assertThatThrownBy(() -> expenses.createBankExpense(authorization,
        new CreateExpenseRequest(LocalDate.now(), "Test", 100, 25, "5420", "2018", bankRow("account", -125))))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> expenses.createBankExpense(authorization,
        new CreateExpenseRequest(null, "Test", 100, 25, "5420", "1930", bankRow("date", -125))))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(bankRows.count()).isZero();
    assertThat(journal.count()).isZero();
  }

  @Test
  void failedBankAuditReleasesRowForSuccessfulRetry() {
    failAudit();
    assertThatThrownBy(() -> expenses.createBankExpense(authorization, bankExpense("retry"))).isInstanceOf(RuntimeException.class);
    reset(audit);
    expenses.createBankExpense(authorization, bankExpense("retry"));
    assertThat(bankRows.count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isEqualTo(1);
  }

  private CreateExpenseRequest bankExpense(String key) {
    return new CreateExpenseRequest(LocalDate.now(), "Test", 100, 25, "5420", "1930", bankRow(key, -125));
  }

  @Autowired se.cloudshop.bank.BankJournalMatchService bankMatches;
  @Autowired se.cloudshop.bank.BankReconciliationService bankReport;

  @ParameterizedTest
  @ValueSource(strings = {"INVOICE_METHOD", "CASH_METHOD"})
  void atomicBankPaymentStoresExactJournalLink(String method) {
    jdbc.update("UPDATE app_settings SET accounting_method = ?", method);
    Long id = invoices.markInvoiceAsSent(authorization, draft().getId()).getId();
    invoices.registerBankPayment(authorization, id, bankPayment("linked-payment", 50));
    var row = bankRows.findAll().getFirst();
    assertThat(row.getJournalEntryId()).isNotNull();
    var entry = journal.findById(row.getJournalEntryId()).orElseThrow();
    assertThat(entry.getAccountNumber()).isEqualTo("1930");
    assertThat(entry.getDebit()).isEqualTo(50);
    assertThat(entry.getVoucherDate()).isEqualTo(row.getBankDate());
    assertThat(bankReport.createReport(null, null).criticalIssueCount()).isZero();
  }

  @Test
  void atomicExpenseLinkUsesCreditRowAndCannotBeDeleted() {
    expenses.createBankExpense(authorization, bankExpense("linked-expense"));
    var row = bankRows.findAll().getFirst();
    var entry = journal.findById(row.getJournalEntryId()).orElseThrow();
    assertThat(entry.getCredit()).isEqualTo(125);
    assertThat(entry.getAccountNumber()).isEqualTo("1930");
    assertThat(bankReport.createReport(null, null).criticalIssueCount()).isZero();
    assertThatThrownBy(() -> jdbc.update("DELETE FROM journal_entries WHERE id = ?", entry.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(journal.existsById(entry.getId())).isTrue();
  }

  @Test
  void explicitLegacyMatchDoesNotBookMoneyAgainAndIsAudited() {
    var row = legacyBankRow("legacy-match", 50);
    long journalId = manualBankRow(LocalDate.now(), "1930", 50);
    var candidates = bankMatches.candidates(row.getId());
    assertThat(candidates).singleElement().satisfies(candidate -> assertThat(candidate.id()).isEqualTo(journalId));
    long count = journal.count();
    assertThat(bankReport.createReport(null, null).criticalIssueCount()).isPositive();
    bankMatches.match(row.getId(), journalId, authorization);
    assertThat(journal.count()).isEqualTo(count);
    assertThat(bankReport.createReport(null, null).criticalIssueCount()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE event_action = 'bank_journal_linked'", Long.class)).isEqualTo(1);
    assertThatThrownBy(() -> bankMatches.match(row.getId(), journalId, authorization)).isInstanceOf(ResponseStatusException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"wrong-date", "wrong-amount", "wrong-account", "missing", "locked", "skipped", "duplicate-bank", "null-id"})
  void unsafeManualBankLinksAreRejected(String problem) {
    var row = legacyBankRow("legacy-reject", 50);
    long journalId = manualBankRow(problem.equals("wrong-date") ? LocalDate.now().minusDays(1) : LocalDate.now(),
        problem.equals("wrong-account") ? "1580" : "1930", problem.equals("wrong-amount") ? 51 : 50);
    if (problem.equals("locked")) jdbc.update("UPDATE app_settings SET accounting_locked_through_date = ?", LocalDate.now());
    if (problem.equals("skipped")) jdbc.update("UPDATE bank_reconciliation_entries SET status = 'skipped' WHERE id = ?", row.getId());
    if (problem.equals("duplicate-bank")) legacyBankRow("legacy-reject", 50);
    Long target = problem.equals("null-id") ? null : problem.equals("missing") ? Long.MAX_VALUE : journalId;
    long count = journal.count();
    assertThatThrownBy(() -> bankMatches.match(row.getId(), target, authorization)).isInstanceOf(ResponseStatusException.class);
    assertThat(bankRows.findById(row.getId()).orElseThrow().getJournalEntryId()).isNull();
    assertThat(journal.count()).isEqualTo(count);
  }

  @Test
  void manualMatchAuditFailureRollsBackLink() {
    var row = legacyBankRow("legacy-audit", 50);
    long journalId = manualBankRow(LocalDate.now(), "1930", 50);
    failAudit();
    assertThatThrownBy(() -> bankMatches.match(row.getId(), journalId, authorization)).isInstanceOf(IllegalStateException.class);
    assertThat(bankRows.findById(row.getId()).orElseThrow().getJournalEntryId()).isNull();
  }

  @Test
  void concurrentLegacyLinksCannotReuseJournalEntry() throws Exception {
    var first = legacyBankRow("legacy-first", 50);
    var second = legacyBankRow("legacy-second", 50);
    long journalId = manualBankRow(LocalDate.now(), "1930", 50);
    afterUncommittedWrite(() -> bankMatches.match(first.getId(), journalId, authorization), () ->
        assertThatThrownBy(() -> bankMatches.match(second.getId(), journalId, authorization)).isInstanceOf(ResponseStatusException.class));
    assertThat(bankRows.findById(first.getId()).orElseThrow().getJournalEntryId()).isEqualTo(journalId);
    assertThat(bankRows.findById(second.getId()).orElseThrow().getJournalEntryId()).isNull();
    assertThat(bankMatches.candidates(second.getId())).isEmpty();
    assertThatThrownBy(() -> jdbc.update("UPDATE bank_reconciliation_entries SET journal_entry_id = ? WHERE id = ?", journalId, second.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private se.cloudshop.bank.BankReconciliationEntry legacyBankRow(String key, int amount) {
    return bankRows.save(new se.cloudshop.bank.BankReconciliationEntry(new se.cloudshop.bank.CreateBankReconciliationEntryRequest(
        key, LocalDate.now(), "Legacy test", "ref", amount, "csv", "booked", "Legacy")));
  }

  @Test
  void legacyLinkMigrationIsAdditiveAndRepeatable() throws Exception {
    var statements = java.nio.file.Files.readAllLines(java.nio.file.Path.of("src/main/java/se/cloudshop/config/DatabaseSchemaPatch.java"))
        .stream().filter(line -> line.contains("jdbcTemplate.execute(") && line.contains("journal_entry_id"))
        .map(line -> line.substring(line.indexOf("execute(\"") + 9, line.lastIndexOf("\");"))).toList();
    assertThat(statements).hasSize(3);
    new TransactionTemplate(transactions).executeWithoutResult(status -> {
      // Connection-local temporary tables shadow only this test's tables and disappear at commit.
      jdbc.execute("CREATE TEMP TABLE journal_entries (id bigint PRIMARY KEY) ON COMMIT DROP");
      jdbc.execute("CREATE TEMP TABLE bank_reconciliation_entries (id bigint PRIMARY KEY, amount integer) ON COMMIT DROP");
      jdbc.update("INSERT INTO journal_entries VALUES (1)");
      jdbc.update("INSERT INTO bank_reconciliation_entries VALUES (1, 50)");
      statements.forEach(jdbc::execute);
      statements.forEach(jdbc::execute);
      assertThat(jdbc.queryForObject("SELECT amount FROM bank_reconciliation_entries WHERE id = 1", Integer.class)).isEqualTo(50);
      assertThat(jdbc.queryForObject("SELECT journal_entry_id FROM bank_reconciliation_entries WHERE id = 1", Long.class)).isNull();
      assertThat(jdbc.update("UPDATE bank_reconciliation_entries SET journal_entry_id = 1 WHERE id = 1")).isEqualTo(1);
    });
  }

  @Test
  void bankLinkHttpContractRequiresAuthAndReturnsOnlyLinkIdentity() throws Exception {
    var row = legacyBankRow("http-link", 50);
    long journalId = manualBankRow(LocalDate.now(), "1930", 50);
    String prefix = "/bank-reconciliations/" + row.getId();
    assertThat(http.getForEntity(prefix + "/journal-candidates", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var candidates = http.exchange(prefix + "/journal-candidates", org.springframework.http.HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertThat(candidates.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(new ObjectMapper().readTree(candidates.getBody()).get(0).path("id").asLong()).isEqualTo(journalId);
    var response = http.postForEntity(prefix + "/journal-link", jsonRequest("{\"journalEntryId\":" + journalId + "}"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var body = new ObjectMapper().readTree(response.getBody());
    assertThat(body.path("journalEntryId").asLong()).isEqualTo(journalId);
    assertThat(body.has("journalEntry")).isFalse();
    assertThat(http.postForEntity(prefix + "/journal-link", jsonRequest("{\"journalEntryId\":" + journalId + "}"), String.class)
        .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  private long manualBankRow(LocalDate date, String account, int amount) {
    accounting.createManualEntry(new CreateManualJournalEntryRequest(date, "Legacy match test", account, "3041", amount));
    return journal.findAll().stream().filter(entry -> account.equals(entry.getAccountNumber())).findFirst().orElseThrow().getId();
  }

  private Order draft() {
    return invoices.createInvoice(authorization, new CreateOrderRequest("Test customer", null, product.getId(), 1));
  }

  private Order draftWithBuyerAddress() {
    var buyer = customers.save(new se.cloudshop.customer.Customer(
        "Test business buyer", "buyer@example.invalid", "", "Buyer Street 1", "", "111 22", "Stockholm"));
    return invoices.createInvoice(authorization,
        new CreateOrderRequest(buyer.getName(), buyer.getId(), product.getId(), 1));
  }

  @Autowired se.cloudshop.invoice.InvoicePdfService invoicePdf;
  @Autowired se.cloudshop.invoice.InvoiceOriginalService invoiceOriginals;
  @Autowired se.cloudshop.invoice.InvoiceOriginalRepository originalRows;
  @org.springframework.boot.test.mock.mockito.MockBean se.cloudshop.email.InvoiceEmailService invoiceEmails;
  @Autowired se.cloudshop.contract.RecurringContractController contracts;
  @Autowired se.cloudshop.contract.RecurringContractRepository contractRows;
  @Autowired se.cloudshop.customer.CustomerRepository customers;

  @Test
  void issuedOriginalSurvivesPaymentAndSettingsChangesByteForByte() {
    Order issued = invoices.markInvoiceAsSent(authorization, draft().getId());
    var original = invoiceOriginals.read(issued);
    assertThat(original.source()).isEqualTo("original");
    assertThat(originalRows.findById(issued.getId()).orElseThrow().verifiedPdf()).isEqualTo(original.pdf());
    invoices.markInvoiceAsPaid(authorization, issued.getId(), new MarkInvoicePaidRequest(LocalDate.now(), 50, "test"));
    jdbc.update("UPDATE products SET name = 'Changed after issuance' WHERE id = ?", product.getId());
    var after = invoiceOriginals.read(orders.findById(issued.getId()).orElseThrow());
    assertThat(after.pdf()).isEqualTo(original.pdf());
    assertThat(after.sha256()).isEqualTo(original.sha256());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var response = http.exchange("/invoices/" + issued.getId() + "/pdf", org.springframework.http.HttpMethod.GET,
        new HttpEntity<>(headers), byte[].class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("X-Invoice-Document")).isEqualTo("original");
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).isEqualTo(original.pdf());
  }

  @Test
  void creditHasSeparateArchivedOriginal() {
    Order issued = invoices.markInvoiceAsSent(authorization, draft().getId());
    var original = invoiceOriginals.read(issued);
    Order credit = invoices.createCreditInvoice(authorization, issued.getId());
    assertThat(originalRows.count()).isEqualTo(2);
    assertThat(invoiceOriginals.read(credit).source()).isEqualTo("original");
    assertThat(invoiceOriginals.read(credit).sha256()).isNotEqualTo(original.sha256());
    assertThat(invoiceOriginals.read(orders.findById(issued.getId()).orElseThrow()).pdf()).isEqualTo(original.pdf());
  }

  @Test
  void repeatedMarkSentDoesNotRebookOrReplaceOriginal() {
    Order issued = invoices.markInvoiceAsSent(authorization, draft().getId());
    var original = invoiceOriginals.read(issued);
    long auditCount = jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class);
    long journalCount = journal.count();
    invoices.markInvoiceAsSent(authorization, issued.getId());
    assertThat(originalRows.count()).isEqualTo(1);
    assertThat(invoiceOriginals.read(issued).pdf()).isEqualTo(original.pdf());
    assertThat(journal.count()).isEqualTo(journalCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class)).isEqualTo(auditCount);
  }

  @Test
  void archiveFailureRollsBackIssuanceAndPreventsSmtp() {
    Order draft = draft();
    jdbc.execute("ALTER TABLE invoice_originals ADD CONSTRAINT reject_test_original CHECK (false) NOT VALID");
    assertThatThrownBy(() -> invoices.sendInvoiceEmail(authorization, draft.getId())).isInstanceOf(RuntimeException.class);
    assertThat(orders.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo("DRAFT");
    assertThat(journal.count()).isZero();
    assertThat(originalRows.count()).isZero();
    org.mockito.Mockito.verify(invoiceEmails, org.mockito.Mockito.never()).sendInvoice(any());
  }

  @Test
  void auditFailureDoesNotLeaveAnOrphanOriginal() {
    Order draft = draft();
    failAudit();
    assertThatThrownBy(() -> invoices.markInvoiceAsSent(authorization, draft.getId())).isInstanceOf(RuntimeException.class);
    assertThat(originalRows.count()).isZero();
    assertThat(journal.count()).isZero();
    assertThat(orders.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo("DRAFT");
  }

  @Test
  void oldIssuedInvoiceWithoutOriginalIsNotBackfilled() {
    Order legacy = draft();
    jdbc.update("UPDATE customer_orders SET status = 'SENT' WHERE id = ?", legacy.getId());
    var read = invoiceOriginals.read(orders.findById(legacy.getId()).orElseThrow());
    assertThat(read.source()).isEqualTo("reconstructed");
    assertThat(originalRows.count()).isZero();
  }

  @Test
  void corruptedOriginalReturnsConflictNotRegeneratedPdf() {
    Order issued = invoices.markInvoiceAsSent(authorization, draft().getId());
    jdbc.update("UPDATE invoice_originals SET sha256 = 'corrupted' WHERE invoice_id = ?", issued.getId());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var response = http.exchange("/invoices/" + issued.getId() + "/pdf", org.springframework.http.HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    assertThat(originalRows.count()).isEqualTo(1);
  }

  @Test
  void originalForeignKeyPreventsInvoiceDeletion() {
    Order issued = invoices.markInvoiceAsSent(authorization, draft().getId());
    assertThatThrownBy(() -> jdbc.update("DELETE FROM customer_orders WHERE id = ?", issued.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(originalRows.count()).isEqualTo(1);
  }

  @Test
  void contractInvoiceAndSnapshotRollbackTogetherWhenAuditFails() {
    var customer = customers.save(new se.cloudshop.customer.Customer("Contract test", "test@example.invalid", "", "", "", "", ""));
    var contract = contracts.createContract(authorization, new se.cloudshop.contract.CreateRecurringContractRequest(
        customer.getId(), product.getId(), 1, "monthly", LocalDate.now()));
    failAudit();
    assertThatThrownBy(() -> contracts.createContractInvoice(authorization, contract.getId())).isInstanceOf(IllegalStateException.class);
    assertThat(orders.count()).isZero();
    assertThat(contractRows.findById(contract.getId()).orElseThrow().getNextInvoiceDate()).isEqualTo(LocalDate.now());
    reset(audit);
    var invoice = contracts.createContractInvoice(authorization, contract.getId());
    assertThat(orders.findById(invoice.getId()).orElseThrow().getDocumentSnapshot().customerName()).isEqualTo("Contract test");
    assertThat(contractRows.findById(contract.getId()).orElseThrow().getNextInvoiceDate()).isEqualTo(LocalDate.now().plusMonths(1));
  }

  @Test
  void oversizedContractDoesNotPersistWrappedAmount() {
    var customer = customers.save(new se.cloudshop.customer.Customer("Contract limit", "test@example.invalid", "", "", "", "", ""));
    long before = contractRows.count();
    assertThatThrownBy(() -> contracts.createContract(authorization, new se.cloudshop.contract.CreateRecurringContractRequest(
        customer.getId(), product.getId(), Integer.MAX_VALUE, "monthly", LocalDate.now())))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(contractRows.count()).isEqualTo(before);
  }

  @Test
  void invoiceIsBookedAndIssuedBeforeEmailTransport() {
    var draft = draft();
    org.mockito.Mockito.doAnswer(call -> {
      Order invoice = call.getArgument(0);
      assertThat(invoice.getStatus()).isEqualTo("SENT");
      assertThat(invoice.getRemainingAmount()).isEqualTo(125);
      assertThat(invoice.getDocumentSnapshot()).isNotNull();
      assertThat(journal.count()).isEqualTo(3);
      return null;
    }).when(invoiceEmails).sendInvoice(any());
    invoices.sendInvoiceEmail(authorization, draft.getId());
    org.mockito.Mockito.verify(invoiceEmails).sendInvoice(any());
    assertThat(orders.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo("SENT");
  }

  @Test
  void smtpFailureRollsBackDraftIssuanceAndBookkeeping() {
    var draft = draft();
    long auditCount = jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class);
    doThrow(new org.springframework.mail.MailSendException("Simulated SMTP failure")).when(invoiceEmails).sendInvoice(any());
    assertThatThrownBy(() -> invoices.sendInvoiceEmail(authorization, draft.getId())).isInstanceOf(org.springframework.mail.MailSendException.class);
    assertThat(orders.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo("DRAFT");
    assertThat(journal.count()).isZero();
    assertThat(originalRows.count()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class)).isEqualTo(auditCount);
  }

  @Test
  void journalFailurePreventsEmailTransport() {
    var draft = draft();
    rejectCreditRows();
    assertThatThrownBy(() -> invoices.sendInvoiceEmail(authorization, draft.getId())).isInstanceOf(RuntimeException.class);
    org.mockito.Mockito.verify(invoiceEmails, org.mockito.Mockito.never()).sendInvoice(any());
    assertThat(journal.count()).isZero();
    assertThat(orders.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo("DRAFT");
  }

  @Test
  void auditFailureAfterEmailTransportLeavesNoSentState() {
    var draft = draft();
    failAudit();
    assertThatThrownBy(() -> invoices.sendInvoiceEmail(authorization, draft.getId())).isInstanceOf(RuntimeException.class);
    org.mockito.Mockito.verify(invoiceEmails).sendInvoice(any());
    assertThat(journal.count()).isZero();
    assertThat(orders.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo("DRAFT");
    assertThat(orders.findById(draft.getId()).orElseThrow().hasReminder("INVOICE_EMAIL", "SENT")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"SENT", "PAID", "CREDIT"})
  void resendingIssuedInvoiceNeverCreatesNewJournalEntries(String type) {
    Order invoice = invoices.markInvoiceAsSent(authorization, draft().getId());
    if (type.equals("PAID")) invoice = invoices.markInvoiceAsPaid(authorization, invoice.getId(), new MarkInvoicePaidRequest(LocalDate.now(), 125, "test"));
    if (type.equals("CREDIT")) invoice = invoices.createCreditInvoice(authorization, invoice.getId());
    long journalCount = journal.count();
    String status = invoice.getStatus();
    invoices.sendInvoiceEmail(authorization, invoice.getId());
    assertThat(journal.count()).isEqualTo(journalCount);
    assertThat(orders.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(status);
  }

  @Test
  void lockedDraftNeverReachesEmailTransport() {
    var draft = draft();
    settings.lockAccountingThroughDate(LocalDate.now());
    assertThatThrownBy(() -> invoices.sendInvoiceEmail(authorization, draft.getId())).isInstanceOf(ResponseStatusException.class);
    org.mockito.Mockito.verify(invoiceEmails, org.mockito.Mockito.never()).sendInvoice(any());
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.NullAndEmptySource
  void unknownInvoiceStatusCannotBeEmailed(String status) {
    var draft = draft();
    jdbc.update("UPDATE customer_orders SET status = ? WHERE id = ?", status, draft.getId());
    assertThatThrownBy(() -> invoices.sendInvoiceEmail(authorization, draft.getId())).isInstanceOf(ResponseStatusException.class);
    org.mockito.Mockito.verify(invoiceEmails, org.mockito.Mockito.never()).sendInvoice(any());
  }

  @Test
  void invoiceDocumentSnapshotSurvivesDatabaseReloadAndRegistryChanges() {
    Order created = draft();
    var snapshot = created.getDocumentSnapshot();
    assertThat(snapshot).isNotNull();
    assertThat(jdbc.queryForObject("SELECT document_snapshot FROM customer_orders WHERE id = ?", String.class, created.getId()))
        .contains("Test customer", "Integration test service");
    jdbc.update("UPDATE products SET name = 'Changed service', price = 999 WHERE id = ?", product.getId());
    Order reloaded = orders.findById(created.getId()).orElseThrow();
    assertThat(reloaded.getDocumentSnapshot()).isEqualTo(snapshot);
    assertThat(reloaded.getProduct().getName()).isEqualTo("Changed service");
    assertThat(reloaded.getNetAmount()).isEqualTo(100);
    invoices.markInvoiceAsSent(authorization, created.getId());
    var credit = invoices.createCreditInvoice(authorization, created.getId());
    var creditSnapshot = orders.findById(credit.getId()).orElseThrow().getDocumentSnapshot();
    assertThat(creditSnapshot).isNotEqualTo(snapshot);
    assertThat(creditSnapshot.creditedInvoiceNumber()).isEqualTo(created.getInvoiceNumber());
    assertThat(credit.getTotalAmount()).isEqualTo(-125);
  }

  @Test
  void invoiceExportIncludesExplicitMinorUnitColumns() {
    draft();
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);

    var response = http.exchange("/invoices/export", org.springframework.http.HttpMethod.GET,
        new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("NettoMinor,MomsMinor,TotaltMinor", "10000,2500,12500");
  }

  @Test
  void reconstructedPdfReadDoesNotBackfillLegacyInvoice() {
    var invoice = draft();
    jdbc.update("UPDATE customer_orders SET document_snapshot = NULL WHERE id = ?", invoice.getId());
    assertThat(invoicePdf.createInvoicePdf(orders.findById(invoice.getId()).orElseThrow())).isNotEmpty();
    assertThat(jdbc.queryForObject("SELECT document_snapshot FROM customer_orders WHERE id = ?", String.class, invoice.getId())).isNull();
  }

  @Test
  void invoiceExportOverflowReturns422WithoutSuccessAuditOrCsv() {
    draft();
    draft();
    jdbc.update("UPDATE customer_orders SET net_amount = 1200000000, vat_amount = 300000000, total_amount = 1500000000");
    long auditCount = jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class);
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    var response = http.exchange("/invoices/export", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody()).contains("REPORT_AMOUNT_LIMIT");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class)).isEqualTo(auditCount);
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
