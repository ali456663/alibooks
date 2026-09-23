package se.cloudshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.customer.CustomerRepository;
import se.cloudshop.email.InvoiceEmailService;
import se.cloudshop.email.InvoiceReminderEmailService;
import se.cloudshop.product.Product;
import se.cloudshop.product.ProductService;
import se.cloudshop.settings.AppSettings;
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
  private final AuditService auditService = mock(AuditService.class);
  private final OrderController orderController = new OrderController(
      productService,
      authHeader,
      orderRepository,
      customerRepository,
      accountingService,
      settingsService,
      invoiceReminderEmailService,
      invoiceEmailService,
      auditService,
      mock(se.cloudshop.bank.BankImportBookingService.class),
      mock(se.cloudshop.invoice.InvoiceOriginalService.class)
  );

  @Test
  void createOrderRequiresJwtToken() {
    CreateOrderRequest request = new CreateOrderRequest("Ali", null, 1L, 1);

    assertThatThrownBy(() -> orderController.createOrder(null, request))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void createOrderLeavesInvoiceAsDraftWithoutBooking() {
    Product product = new Product("PT", "Training", 1000);
    when(productService.findById(1L)).thenReturn(Optional.of(product));
    when(settingsService.getSettings()).thenReturn(AppSettings.defaults());
    when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
        .thenAnswer(invocation -> {
          Order saved = invocation.getArgument(0);
          setOrderId(saved, 1L);
          return saved;
        });

    Order invoice = orderController.createOrder("Bearer " + authHeaderToken(), new CreateOrderRequest("Ali", null, 1L, 1));

    assertThat(invoice.getStatus()).isEqualTo("DRAFT");
    verify(accountingService, never()).createInvoiceEntries(invoice);
  }

  @Test
  void createOrderUsesVatRateConfiguredOnService() {
    Product product = new Product("PT", "Training", 1000);
    product.setVatPercent(12);
    when(productService.findById(1L)).thenReturn(Optional.of(product));
    when(settingsService.getSettings()).thenReturn(AppSettings.defaults());
    when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
        .thenAnswer(invocation -> {
          Order saved = invocation.getArgument(0);
          setOrderId(saved, 1L);
          return saved;
        });

    Order invoice = orderController.createOrder(
        "Bearer " + authHeaderToken(), new CreateOrderRequest("Ali", null, 1L, 1));

    assertThat(invoice.getVatPercent()).isEqualTo(12);
    assertThat(invoice.getVatAmount()).isEqualTo(120);
    assertThat(invoice.getTotalAmount()).isEqualTo(1120);
  }

  @Test
  void deleteInvoiceRejectsSentInvoice() {
    Order sentInvoice = new Order();
    sentInvoice.setStatus("SENT");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(sentInvoice));

    assertThatThrownBy(() -> orderController.deleteInvoice("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Only draft invoices can be deleted");

    verify(accountingService, never()).deleteEntriesForInvoice(sentInvoice);
    verify(orderRepository, never()).delete(sentInvoice);
  }

  @Test
  void markInvoicePaidRejectsAlreadyFullyPaidInvoice() {
    Order paidInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    paidInvoice.registerPayment(paidInvoice.getInvoiceDate(), paidInvoice.getTotalAmount(), "Bank");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(paidInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsPaid("Bearer " + authHeaderToken(), 1L, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invoice is already fully paid");

    verify(accountingService, never()).createPaymentEntries(paidInvoice, paidInvoice.getInvoiceDate(), paidInvoice.getTotalAmount());
  }

  @Test
  void markInvoiceSentRejectsPaidInvoice() {
    Order paidInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    paidInvoice.registerPayment(paidInvoice.getInvoiceDate(), paidInvoice.getTotalAmount(), "Bank");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(paidInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsSent("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Paid invoices keep their payment status");

    assertThat(paidInvoice.getStatus()).isEqualTo("PAID");
    verify(orderRepository, never()).save(paidInvoice);
  }

  @Test
  void markInvoiceSentBooksDraftInvoice() {
    when(settingsService.getSettings()).thenReturn(invoiceReadySettings());
    Order draftInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));
    when(orderRepository.save(draftInvoice)).thenReturn(draftInvoice);

    orderController.markInvoiceAsSent("Bearer " + authHeaderToken(), 1L);

    verify(accountingService).createInvoiceEntries(draftInvoice);
    assertThat(draftInvoice.getStatus()).isEqualTo("SENT");
  }

  @Test
  void markInvoiceSentKeepsConfiguredServiceVatRateWhenGlobalDefaultDiffers() {
    AppSettings settings = invoiceReadySettings();
    settings.setVatPercent(12);
    when(settingsService.getSettings()).thenReturn(settings);
    Product service = new Product("PT", "Training", 1000);
    service.setVatPercent(6);
    Order draftInvoice = new Order("Ali", service, java.time.Instant.now());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));
    when(orderRepository.save(draftInvoice)).thenReturn(draftInvoice);

    orderController.markInvoiceAsSent("Bearer " + authHeaderToken(), 1L);

    assertThat(draftInvoice.getStatus()).isEqualTo("SENT");
    assertThat(draftInvoice.getVatPercent()).isEqualTo(6);
    verify(accountingService).createInvoiceEntries(draftInvoice);
  }

  @Test
  void markInvoiceSentRejectsIncompleteSellerAddressBeforeBooking() {
    AppSettings settings = invoiceReadySettings();
    settings.setCompanyCity("");
    when(settingsService.getSettings()).thenReturn(settings);
    Order draftInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsSent("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("registered address");

    assertThat(draftInvoice.getStatus()).isEqualTo("DRAFT");
    verify(accountingService, never()).createInvoiceEntries(draftInvoice);
    verify(orderRepository, never()).save(draftInvoice);
  }

  @Test
  void markInvoiceSentRejectsVatInvoiceWithoutVatRegistrationNumber() {
    AppSettings settings = invoiceReadySettings();
    settings.setVatRegistrationNumber("");
    when(settingsService.getSettings()).thenReturn(settings);
    Order draftInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsSent("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("VAT registration number");

    assertThat(draftInvoice.getStatus()).isEqualTo("DRAFT");
    verify(accountingService, never()).createInvoiceEntries(draftInvoice);
  }

  @Test
  void issueRefreshesOnlyDraftSellerDetailsBeforeFreezingInvoice() {
    AppSettings previousSettings = invoiceReadySettings();
    Order draftInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    draftInvoice.captureDocumentSnapshot(previousSettings);
    AppSettings currentSettings = invoiceReadySettings();
    currentSettings.setCompanyName("Current issuer");
    currentSettings.setCompanyAddress("Current street");
    when(settingsService.getSettings()).thenReturn(currentSettings);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));
    when(orderRepository.save(draftInvoice)).thenReturn(draftInvoice);

    orderController.markInvoiceAsSent("Bearer " + authHeaderToken(), 1L);

    assertThat(draftInvoice.getDocumentSnapshot().issuerName()).isEqualTo("Current issuer");
    assertThat(draftInvoice.getDocumentSnapshot().issuerAddress()).isEqualTo("Current street");
    assertThat(draftInvoice.getDocumentSnapshot().customerName()).isEqualTo("Ali");
    verify(accountingService).createInvoiceEntries(draftInvoice);
  }

  @Test
  void markInvoicePaidRejectsDraftInvoice() {
    Order draftInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsPaid("Bearer " + authHeaderToken(), 1L, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Draft invoices must be marked as sent before payment can be registered");

    verify(accountingService, never()).createPaymentEntries(draftInvoice, draftInvoice.getInvoiceDate(), draftInvoice.getTotalAmount());
  }

  @Test
  void markInvoicePaidRejectsDuplicatePaymentOnSameInvoice() {
    Order sentInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    sentInvoice.setStatus("SENT");
    sentInvoice.registerPayment(sentInvoice.getInvoiceDate(), 100, "SWISH-123");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(sentInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsPaid(
        "Bearer " + authHeaderToken(),
        1L,
        new MarkInvoicePaidRequest(sentInvoice.getInvoiceDate(), 100, "SWISH-123")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("payment is already registered");

    verify(accountingService, never()).createPaymentEntries(sentInvoice, sentInvoice.getInvoiceDate(), 100);
    verify(orderRepository, never()).save(sentInvoice);
  }

  @Test
  void markInvoicePaidConvertsDatabaseDuplicateIntoConflict() {
    Order sentInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    sentInvoice.setStatus("SENT");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(sentInvoice));
    when(orderRepository.saveAndFlush(sentInvoice))
        .thenThrow(new DataIntegrityViolationException("invoice_payments_identity_unique"));

    assertThatThrownBy(() -> orderController.markInvoiceAsPaid(
        "Bearer " + authHeaderToken(),
        1L,
        new MarkInvoicePaidRequest(sentInvoice.getInvoiceDate(), 100, "SWISH-123")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("payment is already registered");

    verify(accountingService).createPaymentEntries(sentInvoice, sentInvoice.getInvoiceDate(), 100);
    verify(auditService, never()).record(
        org.mockito.ArgumentMatchers.eq("payment"),
        org.mockito.ArgumentMatchers.eq("invoice"),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq("payment_registered"),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.any()
    );
  }

  @Test
  void markInvoicePaidRequiresReferenceForManualPayment() {
    Order sentInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    sentInvoice.setStatus("SENT");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(sentInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsPaid(
        "Bearer " + authHeaderToken(),
        1L,
        new MarkInvoicePaidRequest(sentInvoice.getInvoiceDate(), 100, " ")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Payment reference is required");

    verify(accountingService, never()).createPaymentEntries(sentInvoice, sentInvoice.getInvoiceDate(), 100);
    verify(orderRepository, never()).save(sentInvoice);
  }

  @Test
  void markInvoicePaidRequiresExplicitPaymentDate() {
    Order sentInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    sentInvoice.setStatus("SENT");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(sentInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsPaid(
        "Bearer " + authHeaderToken(),
        1L,
        new MarkInvoicePaidRequest(null, 100, "BANK-1")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Payment date is required");

    verify(accountingService, never()).createPaymentEntries(sentInvoice, sentInvoice.getInvoiceDate(), 100);
    verify(orderRepository, never()).save(sentInvoice);
  }

  @Test
  void markInvoicePaidRejectsCreditInvoice() {
    Order creditInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    creditInvoice.setCreditInvoice(true);
    creditInvoice.setStatus("SENT");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(creditInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceAsPaid("Bearer " + authHeaderToken(), 1L, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Credit invoices and credited invoices cannot receive normal customer payments");

    verify(accountingService, never()).createPaymentEntries(creditInvoice, creditInvoice.getInvoiceDate(), creditInvoice.getTotalAmount());
  }

  @Test
  void sendInvoiceEmailKeepsPaidStatus() {
    Order paidInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    paidInvoice.registerPayment(paidInvoice.getInvoiceDate(), paidInvoice.getTotalAmount(), "Bank");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(paidInvoice));
    when(orderRepository.saveAndFlush(paidInvoice)).thenReturn(paidInvoice);

    orderController.sendInvoiceEmail("Bearer " + authHeaderToken(), 1L);

    assertThat(paidInvoice.getStatus()).isEqualTo("PAID");
    verify(invoiceEmailService).sendInvoice(paidInvoice);
  }

  @Test
  void sendInvoiceEmailDoesNotRecordSentHistoryWhenSmtpFails() {
    Order paidInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    paidInvoice.registerPayment(paidInvoice.getInvoiceDate(), paidInvoice.getTotalAmount(), "Bank");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(paidInvoice));
    when(orderRepository.saveAndFlush(paidInvoice)).thenReturn(paidInvoice);
    doThrow(new RuntimeException("SMTP unavailable")).when(invoiceEmailService).sendInvoice(paidInvoice);

    assertThatThrownBy(() -> orderController.sendInvoiceEmail("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("SMTP unavailable");

    assertThat(paidInvoice.hasReminder("INVOICE_EMAIL", "SENT")).isFalse();
    verifyNoInteractions(auditService);
  }

  @Test
  void sendInvoiceEmailRejectsDraftInvoiceInLockedPeriodBeforeSendingEmail() {
    Order draftInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Bokforingen ar last"))
        .when(accountingService)
        .requireUnlockedAccountingDate(draftInvoice.getInvoiceDate());

    assertThatThrownBy(() -> orderController.sendInvoiceEmail("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Bokforingen ar last");

    verify(invoiceEmailService, never()).sendInvoice(draftInvoice);
    verify(orderRepository, never()).save(draftInvoice);
  }

  @Test
  void sendInvoiceEmailDoesNotBookOrSendDraftWithIncompleteSellerProfile() {
    Order draftInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    when(settingsService.getSettings()).thenReturn(AppSettings.defaults());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(draftInvoice));

    assertThatThrownBy(() -> orderController.sendInvoiceEmail("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("registered address");

    assertThat(draftInvoice.getStatus()).isEqualTo("DRAFT");
    verify(accountingService, never()).createInvoiceEntries(draftInvoice);
    verify(invoiceEmailService, never()).sendInvoice(draftInvoice);
  }

  @Test
  void refundInvoiceRequiresCreditedInvoice() {
    Order paidInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    paidInvoice.registerPayment(paidInvoice.getInvoiceDate(), paidInvoice.getTotalAmount(), "Bank");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(paidInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceRefunded("Bearer " + authHeaderToken(), 1L, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Only credited invoices can be refunded");

    verify(accountingService, never()).createRefundEntries(paidInvoice, paidInvoice.getInvoiceDate(), paidInvoice.getTotalAmount());
  }

  @Test
  void refundInvoiceRegistersRefundForCreditedInvoice() {
    Order creditedInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    creditedInvoice.registerPayment(creditedInvoice.getInvoiceDate(), creditedInvoice.getTotalAmount(), "Bank");
    creditedInvoice.setStatus("CREDITED");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(creditedInvoice));
    when(orderRepository.save(creditedInvoice)).thenReturn(creditedInvoice);

    orderController.markInvoiceRefunded("Bearer " + authHeaderToken(), 1L, new MarkInvoiceRefundRequest(
        creditedInvoice.getInvoiceDate(),
        creditedInvoice.getTotalAmount(),
        "Bank refund"
    ));

    verify(accountingService).createRefundEntries(creditedInvoice, creditedInvoice.getInvoiceDate(), creditedInvoice.getTotalAmount());
    verify(orderRepository).save(creditedInvoice);
  }

  @Test
  void refundInvoiceRequiresExplicitRefundDate() {
    Order creditedInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    creditedInvoice.registerPayment(creditedInvoice.getInvoiceDate(), creditedInvoice.getTotalAmount(), "Bank");
    creditedInvoice.setStatus("CREDITED");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(creditedInvoice));

    assertThatThrownBy(() -> orderController.markInvoiceRefunded(
        "Bearer " + authHeaderToken(),
        1L,
        new MarkInvoiceRefundRequest(null, creditedInvoice.getTotalAmount(), "Bank refund")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Refund date is required");

    verify(accountingService, never()).createRefundEntries(creditedInvoice, creditedInvoice.getInvoiceDate(), creditedInvoice.getTotalAmount());
    verify(orderRepository, never()).save(creditedInvoice);
  }

  @Test
  void createCreditInvoiceRejectsCreditInvoice() {
    Order creditInvoice = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    creditInvoice.setStatus("SENT");
    creditInvoice.setCreditInvoice(true);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(creditInvoice));

    assertThatThrownBy(() -> orderController.createCreditInvoice("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Credit invoices cannot be credited again");

    verify(orderRepository, never()).save(creditInvoice);
    verify(accountingService, never()).createCreditInvoiceEntries(creditInvoice);
  }

  @Test
  void createCreditInvoiceStopsWhenOriginalMinorShadowContainsOre() {
    Order original = new Order("Ali", new Product("PT", "Training", 1000), java.time.Instant.now());
    original.setStatus("SENT");
    setOrderId(original, 1L);
    setField(original, "totalAmountMinor", 125050L);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(original));

    assertThatThrownBy(() -> orderController.createCreditInvoice("Bearer " + authHeaderToken(), 1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("innehåller ören");

    verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any(Order.class));
    verify(accountingService, never()).createCreditInvoiceEntries(org.mockito.ArgumentMatchers.any(Order.class));
  }

  private String authHeaderToken() {
    return new JwtService("test_secret").createToken("test@example.com");
  }

  private AppSettings invoiceReadySettings() {
    AppSettings settings = AppSettings.defaults();
    settings.setCompanyName("Test seller");
    settings.setCompanyAddress("Test Street 1");
    settings.setCompanyPostalCode("111 22");
    settings.setCompanyCity("Stockholm");
    settings.setCompanyOrganizationNumber("556000-0000");
    settings.setVatRegistrationNumber("SE556000000001");
    return settings;
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
