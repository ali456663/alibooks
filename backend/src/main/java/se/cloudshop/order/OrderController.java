package se.cloudshop.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import se.cloudshop.audit.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.customer.Customer;
import se.cloudshop.customer.CustomerRepository;
import se.cloudshop.email.InvoiceEmailService;
import se.cloudshop.email.InvoiceReminderEmailService;
import se.cloudshop.product.Product;
import se.cloudshop.product.ProductService;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@RestController
public class OrderController {

  private final ProductService productService;
  private final AuthHeader authHeader;
  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final AccountingService accountingService;
  private final SettingsService settingsService;
  private final InvoiceReminderEmailService invoiceReminderEmailService;
  private final InvoiceEmailService invoiceEmailService;
  private final AuditService auditService;
  private final se.cloudshop.bank.BankImportBookingService bankImport;
  private final se.cloudshop.invoice.InvoiceOriginalService invoiceOriginals;

  public OrderController(
      ProductService productService,
      AuthHeader authHeader,
      OrderRepository orderRepository,
      CustomerRepository customerRepository,
      AccountingService accountingService,
      SettingsService settingsService,
      InvoiceReminderEmailService invoiceReminderEmailService,
      InvoiceEmailService invoiceEmailService,
      AuditService auditService,
      se.cloudshop.bank.BankImportBookingService bankImport,
      se.cloudshop.invoice.InvoiceOriginalService invoiceOriginals
  ) {
    this.productService = productService;
    this.authHeader = authHeader;
    this.orderRepository = orderRepository;
    this.customerRepository = customerRepository;
    this.accountingService = accountingService;
    this.settingsService = settingsService;
    this.invoiceReminderEmailService = invoiceReminderEmailService;
    this.invoiceEmailService = invoiceEmailService;
    this.auditService = auditService;
    this.bankImport = bankImport;
    this.invoiceOriginals = invoiceOriginals;
  }

  @GetMapping("/orders")
  public List<Order> getOrders(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return orderRepository.findAll();
  }

  @GetMapping("/invoices")
  public List<Order> getInvoices(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return orderRepository.findAll();
  }

  @PostMapping("/orders")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Order createOrder(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateOrderRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    Product product = productService.findById(request.productId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product not found."));
    AppSettings settings = settingsService.getSettings();
    int quantity = request.quantity() == null ? 1 : request.quantity();

    if (quantity <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be at least 1.");
    }

    accountingService.requireUnlockedAccountingDate(LocalDate.now());

    Order order;

    try {
      if (request.customerId() != null) {
        Customer customer = customerRepository.findById(request.customerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found."));
        order = new Order(customer, product, Instant.now(), quantity);
      } else {
        if (request.customerName() == null || request.customerName().isBlank()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer is required.");
        }
        order = new Order(request.customerName(), product, Instant.now(), quantity);
      }
    } catch (ArithmeticException | IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice amounts are invalid or exceed the supported limit.");
    }

    Order savedOrder = orderRepository.save(order);
    savedOrder.setInvoiceNumber("F-" + savedOrder.getInvoiceDate().getYear() + "-" + String.format("%04d", savedOrder.getId()));
    savedOrder.setPaymentTermsDays(settings.getPaymentTermsDays() <= 0 ? 30 : settings.getPaymentTermsDays());
    savedOrder.setDueDate(savedOrder.getInvoiceDate().plusDays(savedOrder.getPaymentTermsDays()));
    savedOrder.setOcrNumber(settings.getDefaultOcr());
    savedOrder.setPlusGiro(settings.getPlusGiro());
    savedOrder.setPaymentRecipient(settings.getPaymentRecipient());
    savedOrder.captureDocumentSnapshot(settings);
    savedOrder = orderRepository.save(savedOrder);
    auditService.record("invoice", "invoice", savedOrder.getId(), "created", savedOrder.getInvoiceNumber(), "Invoice created",
        accountingWholeKrona(savedOrder.getTotalAmountMinor(), savedOrder.getTotalAmount(), "fakturans totalbelopp"), authorizationHeader);
    return savedOrder;
  }

  @PostMapping("/invoices")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Order createInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateOrderRequest request
  ) {
    return createOrder(authorizationHeader, request);
  }

  @PostMapping("/invoices/{id}/sent")
  @Transactional
  public Order markInvoiceAsSent(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    orderRepository.lockById(id);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    if (order.isCreditInvoice() || "CREDITED".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credited invoices cannot be marked as sent from the original invoice flow.");
    }

    if ("PAID".equals(order.getStatus()) || "PARTIALLY_PAID".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid invoices keep their payment status.");
    }

    int accountingTotal = accountingWholeKrona(order.getTotalAmountMinor(), order.getTotalAmount(), "fakturans totalbelopp");
    if ("SENT".equals(order.getStatus())) return order;
    if (!"DRAFT".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a draft can be issued.");
    }
    accountingService.requireUnlockedAccountingDate(order.getInvoiceDate());
    AppSettings settings = settingsService.getSettings();
    order.refreshDraftIssuerSnapshot(settings);
    se.cloudshop.invoice.InvoiceIssueValidator.requireIssuable(order);
    accountingService.createInvoiceEntries(order);
    order.setStatus("SENT");
    invoiceOriginals.archiveAtIssuance(order);
    order.addReminderHistory("INVOICE_MARKED_SENT", "SAVED", order.getCustomer() == null ? null : order.getCustomer().getEmail());
    Order savedOrder = orderRepository.save(order);
    auditService.record("invoice", "invoice", savedOrder.getId(), "marked_sent", savedOrder.getInvoiceNumber(), "Invoice marked as sent", accountingTotal, authorizationHeader);
    return savedOrder;
  }

  @PostMapping("/invoices/{id}/paid")
  @Transactional
  public Order markInvoiceAsPaid(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody(required = false) MarkInvoicePaidRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    if (request != null && request.bankRow() != null) {
      if (request.paidAmount() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit bank payment amount is required.");
      bankImport.reserve(request.bankRow(), request.paymentDate(), request.paidAmount());
    }
    orderRepository.lockById(id);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    if (order.isCreditInvoice() || "CREDITED".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit invoices and credited invoices cannot receive normal customer payments.");
    }

    if ("DRAFT".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Draft invoices must be marked as sent before payment can be registered.");
    }

    if (!order.hasRemainingAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice is already fully paid.");
    }

    if (request == null || request.paymentDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date is required.");
    }
    LocalDate paymentDate = request.paymentDate();
    if (order.getInvoiceDate() != null && paymentDate.isBefore(order.getInvoiceDate())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date cannot be before invoice date.");
    }

    int paidAmount = request == null || request.paidAmount() == null
        ? order.getRemainingAmount()
        : request.paidAmount();
    String paymentReference = request.paymentReference() == null ? "" : request.paymentReference().trim();
    validatePaymentReference(paymentReference, request.bankRow() == null);

    if (paidAmount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid amount must be greater than zero.");
    }

    if (paidAmount > order.getRemainingAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid amount cannot be greater than remaining amount.");
    }

    if (order.hasPayment(paymentDate, paidAmount, paymentReference)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This payment is already registered on the invoice.");
    }

    var bankEntry = accountingService.createPaymentEntries(order, paymentDate, paidAmount);
    order.registerPayment(paymentDate, paidAmount, paymentReference);
    Order savedOrder;
    try {
      savedOrder = orderRepository.saveAndFlush(order);
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This payment is already registered on the invoice.",
          exception
      );
    }
    auditService.record("payment", "invoice", savedOrder.getId(), "payment_registered", savedOrder.getInvoiceNumber(), "Invoice payment registered", paidAmount, authorizationHeader);
    if (request != null && request.bankRow() != null) {
      bankImport.record(request.bankRow(), "invoice_payment", "Invoice " + savedOrder.getId(), bankEntry, authorizationHeader);
    }
    return savedOrder;
  }

  private void validatePaymentReference(String reference, boolean required) {
    if (required && reference.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Payment reference is required for manual payments. Use bank import when a bank row is available."
      );
    }
    if (reference.length() > 255 || reference.contains("\n") || reference.contains("\r")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment reference must be a single line of max 255 characters.");
    }
  }

  @PostMapping("/bank-import/invoices/{id}/paid")
  @Transactional
  public Order registerBankPayment(@RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable Long id, @RequestBody MarkInvoicePaidRequest request) {
    authHeader.requireValidToken(authorization);
    if (request == null || request.bankRow() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank row is required.");
    return markInvoiceAsPaid(authorization, id, request);
  }

  @PostMapping("/invoices/{id}/refund")
  @Transactional
  public Order markInvoiceRefunded(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody(required = false) MarkInvoiceRefundRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    orderRepository.lockById(id);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    if (!"CREDITED".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only credited invoices can be refunded.");
    }

    if (order.getRefundableAmount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice has no refundable amount left.");
    }

    if (request == null || request.refundDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund date is required.");
    }
    LocalDate refundDate = request.refundDate();
    if (order.getInvoiceDate() != null && refundDate.isBefore(order.getInvoiceDate())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund date cannot be before invoice date.");
    }

    int refundAmount = request == null || request.refundAmount() == null
        ? order.getRefundableAmount()
        : request.refundAmount();
    String refundReference = request == null ? null : request.refundReference();

    if (refundAmount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount must be greater than zero.");
    }

    if (refundAmount > order.getRefundableAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount cannot be greater than refundable amount.");
    }

    accountingService.createRefundEntries(order, refundDate, refundAmount);
    order.registerRefund(refundDate, refundAmount, refundReference);
    Order savedOrder = orderRepository.save(order);
    auditService.record("refund", "invoice", savedOrder.getId(), "refund_registered", savedOrder.getInvoiceNumber(), "Customer refund registered", refundAmount, authorizationHeader);
    return savedOrder;
  }

  @PostMapping("/invoices/{id}/reminder")
  public Order markInvoiceReminderSent(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    order.addReminder("COPY", "SAVED", order.getCustomer() == null ? null : order.getCustomer().getEmail());
    return orderRepository.save(order);
  }

  @PostMapping("/invoices/{id}/email")
  @Transactional
  public Order sendInvoiceEmail(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    orderRepository.lockById(id);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    if ("CREDITED".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credited original invoice cannot be sent. Send the credit invoice instead.");
    }

    if (order.getStatus() == null || !java.util.Set.of("DRAFT", "SENT", "PAID", "PARTIALLY_PAID").contains(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice status does not allow sending.");
    }

    if ("DRAFT".equals(order.getStatus())) {
      if (order.isCreditInvoice()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit invoice must be issued before sending.");
      accountingService.requireUnlockedAccountingDate(order.getInvoiceDate());
      AppSettings settings = settingsService.getSettings();
      order.refreshDraftIssuerSnapshot(settings);
      se.cloudshop.invoice.InvoiceIssueValidator.requireIssuable(order);
      accountingService.createInvoiceEntries(order);
      order.setStatus("SENT");
      invoiceOriginals.archiveAtIssuance(order);
    }
    // Flush the invoice and its bookkeeping before SMTP so validation happens before the external side effect.
    // The sent-history and audit event are deliberately written only after SMTP succeeds; otherwise a failed
    // delivery would leave the invoice looking sent even though no message reached the customer.
    Order preparedOrder = orderRepository.saveAndFlush(order);
    invoiceEmailService.sendInvoice(preparedOrder);
    preparedOrder.addReminderHistory("INVOICE_EMAIL", "SENT", preparedOrder.getCustomer() == null ? null : preparedOrder.getCustomer().getEmail());
    Order savedOrder = orderRepository.saveAndFlush(preparedOrder);
    auditService.record("invoice", "invoice", savedOrder.getId(), "email_sent", savedOrder.getInvoiceNumber(), "Invoice email sent",
        accountingWholeKrona(savedOrder.getTotalAmountMinor(), savedOrder.getTotalAmount(), "fakturans totalbelopp"), authorizationHeader);
    return savedOrder;
  }

  @PostMapping("/invoices/{id}/reminder-draft")
  public Order markInvoiceReminderDraftOpened(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    order.addReminder("EMAIL_DRAFT", "OPENED", order.getCustomer() == null ? null : order.getCustomer().getEmail());
    return orderRepository.save(order);
  }

  @PostMapping("/invoices/{id}/reminder-email")
  @Transactional
  public Order sendInvoiceReminderEmail(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    orderRepository.lockById(id);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    invoiceReminderEmailService.sendReminder(order);
    order.addReminder("EMAIL", "SENT", order.getCustomer() == null ? null : order.getCustomer().getEmail());
    Order savedOrder = orderRepository.saveAndFlush(order);
    auditService.record("reminder", "invoice", savedOrder.getId(), "reminder_email_sent", savedOrder.getInvoiceNumber(), "Invoice reminder email sent", savedOrder.getRemainingAmount(), authorizationHeader);
    return savedOrder;
  }

  @DeleteMapping("/invoices/{id}")
  @Transactional
  public void deleteInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    orderRepository.lockById(id);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    if (!"DRAFT".equals(order.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Only draft invoices can be deleted. Use a credit invoice or correction for sent, paid or booked invoices."
      );
    }

    if (order.getInvoiceDate() != null) {
      accountingService.requireUnlockedAccountingDate(order.getInvoiceDate());
    }

    int accountingTotal = accountingWholeKrona(order.getTotalAmountMinor(), order.getTotalAmount(), "fakturans totalbelopp");
    accountingService.deleteEntriesForInvoice(order);
    auditService.record("invoice", "invoice", order.getId(), "draft_deleted", order.getInvoiceNumber(), "Draft invoice deleted", accountingTotal, authorizationHeader);
    orderRepository.delete(order);
  }

  @PostMapping("/invoices/{id}/credit")
  @Transactional
  public Order createCreditInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    orderRepository.lockById(id);
    Order original = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    if ("DRAFT".equals(original.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Draft invoices should be deleted instead of credited.");
    }

    if ("CREDITED".equals(original.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice is already credited.");
    }

    if (original.isCreditInvoice()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit invoices cannot be credited again.");
    }

    accountingService.requireUnlockedAccountingDate(LocalDate.now());

    int creditNet = -Math.abs(accountingWholeKrona(original.getNetAmountMinor(), original.getNetAmount(), "kreditfakturans nettobelopp"));
    int creditVat = -Math.abs(accountingWholeKrona(original.getVatAmountMinor(), original.getVatAmount(), "kreditfakturans momsbelopp"));
    int creditTotal = -Math.abs(accountingWholeKrona(original.getTotalAmountMinor(), original.getTotalAmount(), "kreditfakturans totalbelopp"));

    Order creditInvoice = Order.draftFromInvoiceSnapshot(original, Instant.now());
    creditInvoice.setCreditInvoice(true);
    creditInvoice.setCreditedInvoiceId(original.getId());
    creditInvoice.setAmounts(
        creditNet,
        creditVat,
        creditTotal
    );
    creditInvoice.setStatus("SENT");

    Order savedCreditInvoice = orderRepository.save(creditInvoice);
    savedCreditInvoice.setInvoiceNumber("K-" + savedCreditInvoice.getInvoiceDate().getYear() + "-" + String.format("%04d", savedCreditInvoice.getId()));
    savedCreditInvoice.setPaymentTermsDays(original.getPaymentTermsDays());
    savedCreditInvoice.setDueDate(original.getDueDate());
    savedCreditInvoice.setOcrNumber(original.getOcrNumber());
    savedCreditInvoice.setPlusGiro(original.getPlusGiro());
    savedCreditInvoice.setPaymentRecipient(original.getPaymentRecipient());
    savedCreditInvoice = orderRepository.save(savedCreditInvoice);

    original.setStatus("CREDITED");
    orderRepository.save(original);
    accountingService.createCreditInvoiceEntries(savedCreditInvoice);
    invoiceOriginals.archiveAtIssuance(savedCreditInvoice);
    auditService.record("invoice", "invoice", savedCreditInvoice.getId(), "credited", savedCreditInvoice.getInvoiceNumber(), "Credit invoice created for " + original.getInvoiceNumber(),
        Math.abs(creditTotal), authorizationHeader);

    return savedCreditInvoice;
  }

  private int accountingWholeKrona(Long amountMinor, int legacyAmount, String field) {
    long valueMinor;
    try {
      valueMinor = amountMinor == null ? Math.multiplyExact((long) legacyAmount, 100L) : amountMinor;
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Bokföringsspåret innehåller ett belopp utanför stödd gräns i " + field + ". Åtgärden har stoppats.", exception);
    }
    if (valueMinor % 100L != 0L) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Bokföringsspåret innehåller ören i " + field + " som den nuvarande kronrepresentationen inte kan representera. Åtgärden har stoppats.");
    }
    try {
      return Math.toIntExact(valueMinor / 100L);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Bokföringsspåret innehåller ett belopp utanför stödd gräns i " + field + ". Åtgärden har stoppats.", exception);
    }
  }
}
