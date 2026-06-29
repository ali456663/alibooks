package se.cloudshop.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
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

  public OrderController(
      ProductService productService,
      AuthHeader authHeader,
      OrderRepository orderRepository,
      CustomerRepository customerRepository,
      AccountingService accountingService,
      SettingsService settingsService,
      InvoiceReminderEmailService invoiceReminderEmailService,
      InvoiceEmailService invoiceEmailService
  ) {
    this.productService = productService;
    this.authHeader = authHeader;
    this.orderRepository = orderRepository;
    this.customerRepository = customerRepository;
    this.accountingService = accountingService;
    this.settingsService = settingsService;
    this.invoiceReminderEmailService = invoiceReminderEmailService;
    this.invoiceEmailService = invoiceEmailService;
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
  @ResponseStatus(HttpStatus.CREATED)
  public Order createOrder(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateOrderRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    Product product = productService.findById(request.productId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product not found."));
    int quantity = request.quantity() == null ? 1 : request.quantity();

    if (quantity <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be at least 1.");
    }

    accountingService.requireUnlockedAccountingDate(LocalDate.now());

    Order order;

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

    Order savedOrder = orderRepository.save(order);
    AppSettings settings = settingsService.getSettings();
    savedOrder.setInvoiceNumber("F-" + savedOrder.getInvoiceDate().getYear() + "-" + String.format("%04d", savedOrder.getId()));
    savedOrder.setPaymentTermsDays(settings.getPaymentTermsDays() <= 0 ? 30 : settings.getPaymentTermsDays());
    savedOrder.setDueDate(savedOrder.getInvoiceDate().plusDays(savedOrder.getPaymentTermsDays()));
    savedOrder.setOcrNumber(settings.getDefaultOcr());
    savedOrder.setPlusGiro(settings.getPlusGiro());
    savedOrder.setPaymentRecipient(settings.getPaymentRecipient());
    savedOrder = orderRepository.save(savedOrder);
    accountingService.createInvoiceEntries(savedOrder);
    return savedOrder;
  }

  @PostMapping("/invoices")
  @ResponseStatus(HttpStatus.CREATED)
  public Order createInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateOrderRequest request
  ) {
    return createOrder(authorizationHeader, request);
  }

  @PostMapping("/invoices/{id}/sent")
  public Order markInvoiceAsSent(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    order.setStatus("SENT");
    order.addReminderHistory("INVOICE_MARKED_SENT", "SAVED", order.getCustomer() == null ? null : order.getCustomer().getEmail());
    return orderRepository.save(order);
  }

  @PostMapping("/invoices/{id}/paid")
  public Order markInvoiceAsPaid(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody(required = false) MarkInvoicePaidRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    LocalDate paymentDate = request == null || request.paymentDate() == null ? LocalDate.now() : request.paymentDate();
    int paidAmount = request == null || request.paidAmount() == null || request.paidAmount() <= 0
        ? order.getRemainingAmount()
        : request.paidAmount();
    String paymentReference = request == null ? null : request.paymentReference();

    if (paidAmount > order.getRemainingAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid amount cannot be greater than remaining amount.");
    }

    accountingService.createPaymentEntries(order, paymentDate, paidAmount);
    order.registerPayment(paymentDate, paidAmount, paymentReference);
    Order savedOrder = orderRepository.save(order);
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
  public Order sendInvoiceEmail(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    invoiceEmailService.sendInvoice(order);
    order.setStatus("SENT");
    order.addReminderHistory("INVOICE_EMAIL", "SENT", order.getCustomer() == null ? null : order.getCustomer().getEmail());
    return orderRepository.save(order);
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
  public Order sendInvoiceReminderEmail(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    invoiceReminderEmailService.sendReminder(order);
    order.addReminder("EMAIL", "SENT", order.getCustomer() == null ? null : order.getCustomer().getEmail());
    return orderRepository.save(order);
  }

  @DeleteMapping("/invoices/{id}")
  public void deleteInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    accountingService.deleteEntriesForInvoice(order);
    orderRepository.delete(order);
  }

  @PostMapping("/invoices/{id}/credit")
  public Order createCreditInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Order original = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    if ("DRAFT".equals(original.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Draft invoices should be deleted instead of credited.");
    }

    if ("CREDITED".equals(original.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice is already credited.");
    }

    accountingService.requireUnlockedAccountingDate(LocalDate.now());

    Order creditInvoice = original.getCustomer() != null
        ? new Order(original.getCustomer(), original.getProduct(), Instant.now(), original.getQuantity())
        : new Order(original.getCustomerName(), original.getProduct(), Instant.now(), original.getQuantity());
    creditInvoice.setCreditInvoice(true);
    creditInvoice.setCreditedInvoiceId(original.getId());
    creditInvoice.setAmounts(
        -Math.abs(original.getNetAmount()),
        -Math.abs(original.getVatAmount()),
        -Math.abs(original.getTotalAmount())
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

    return savedCreditInvoice;
  }
}
