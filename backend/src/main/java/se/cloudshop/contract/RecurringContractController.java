package se.cloudshop.contract;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.customer.Customer;
import se.cloudshop.customer.CustomerRepository;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.product.Product;
import se.cloudshop.product.ProductService;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@RestController
public class RecurringContractController {

  private final AuthHeader authHeader;
  private final RecurringContractRepository recurringContractRepository;
  private final CustomerRepository customerRepository;
  private final ProductService productService;
  private final OrderRepository orderRepository;
  private final AccountingService accountingService;
  private final SettingsService settingsService;

  public RecurringContractController(
      AuthHeader authHeader,
      RecurringContractRepository recurringContractRepository,
      CustomerRepository customerRepository,
      ProductService productService,
      OrderRepository orderRepository,
      AccountingService accountingService,
      SettingsService settingsService
  ) {
    this.authHeader = authHeader;
    this.recurringContractRepository = recurringContractRepository;
    this.customerRepository = customerRepository;
    this.productService = productService;
    this.orderRepository = orderRepository;
    this.accountingService = accountingService;
    this.settingsService = settingsService;
  }

  @GetMapping("/contracts")
  public List<RecurringContract> getContracts(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return recurringContractRepository.findAll();
  }

  @PostMapping("/contracts")
  @ResponseStatus(HttpStatus.CREATED)
  public RecurringContract createContract(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateRecurringContractRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    Customer customer = customerRepository.findById(request.customerId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found."));
    Product product = productService.findById(request.serviceId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service not found."));

    int quantity = request.quantity() == null ? 1 : request.quantity();
    if (quantity <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be at least 1.");
    }

    String interval = normalizeInterval(request.interval());
    LocalDate nextInvoiceDate = request.nextInvoiceDate() == null ? LocalDate.now() : request.nextInvoiceDate();

    return recurringContractRepository.save(new RecurringContract(
        customer.getId(),
        customer.getName(),
        product.getId(),
        product.getName(),
        quantity,
        interval,
        nextInvoiceDate
    ));
  }

  @PutMapping("/contracts/{id}/status")
  public RecurringContract updateContractStatus(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody UpdateRecurringContractStatusRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    RecurringContract contract = recurringContractRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found."));
    contract.setActive(request.active());
    return recurringContractRepository.save(contract);
  }

  @DeleteMapping("/contracts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteContract(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    recurringContractRepository.deleteById(id);
  }

  @PostMapping("/contracts/{id}/invoice")
  public Order createContractInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);

    RecurringContract contract = recurringContractRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found."));

    if (!contract.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract is paused.");
    }

    Customer customer = customerRepository.findById(contract.getCustomerId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found."));
    Product product = productService.findById(contract.getServiceId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service not found."));

    accountingService.requireUnlockedAccountingDate(LocalDate.now());

    Order savedOrder = orderRepository.save(new Order(customer, product, Instant.now(), contract.getQuantity()));
    AppSettings settings = settingsService.getSettings();
    savedOrder.setInvoiceNumber("F-" + savedOrder.getInvoiceDate().getYear() + "-" + String.format("%04d", savedOrder.getId()));
    savedOrder.setPaymentTermsDays(settings.getPaymentTermsDays() <= 0 ? 30 : settings.getPaymentTermsDays());
    savedOrder.setDueDate(savedOrder.getInvoiceDate().plusDays(savedOrder.getPaymentTermsDays()));
    savedOrder.setOcrNumber(settings.getDefaultOcr());
    savedOrder.setPlusGiro(settings.getPlusGiro());
    savedOrder.setPaymentRecipient(settings.getPaymentRecipient());
    savedOrder = orderRepository.save(savedOrder);
    accountingService.createInvoiceEntries(savedOrder);

    contract.setLastInvoiceNumber(savedOrder.getInvoiceNumber());
    contract.setNextInvoiceDate(nextDate(contract.getNextInvoiceDate(), contract.getInterval()));
    recurringContractRepository.save(contract);

    return savedOrder;
  }

  private String normalizeInterval(String interval) {
    if ("quarterly".equalsIgnoreCase(interval)) {
      return "quarterly";
    }
    if ("yearly".equalsIgnoreCase(interval)) {
      return "yearly";
    }
    return "monthly";
  }

  private LocalDate nextDate(LocalDate date, String interval) {
    LocalDate baseDate = date == null ? LocalDate.now() : date;
    if ("quarterly".equals(interval)) {
      return baseDate.plusMonths(3);
    }
    if ("yearly".equals(interval)) {
      return baseDate.plusMonths(12);
    }
    return baseDate.plusMonths(1);
  }
}
