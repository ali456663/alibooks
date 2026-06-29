package se.cloudshop.customer;

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
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.order.OrderRepository;

@RestController
public class CustomerController {

  private final CustomerRepository customerRepository;
  private final AuthHeader authHeader;
  private final OrderRepository orderRepository;

  public CustomerController(CustomerRepository customerRepository, AuthHeader authHeader, OrderRepository orderRepository) {
    this.customerRepository = customerRepository;
    this.authHeader = authHeader;
    this.orderRepository = orderRepository;
  }

  @GetMapping("/customers")
  public List<Customer> getCustomers(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return customerRepository.findAll();
  }

  @PostMapping("/customers")
  @ResponseStatus(HttpStatus.CREATED)
  public Customer createCustomer(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateCustomerRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    validateCustomer(request);

    return customerRepository.save(new Customer(
        request.name(),
        request.email(),
        request.personalNumber(),
        request.address(),
        request.phone(),
        request.postalCode(),
        request.city()
    ));
  }

  @PutMapping("/customers/{id}")
  public Customer updateCustomer(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody CreateCustomerRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    validateCustomer(request);

    Customer customer = customerRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
    customer.updateFrom(request);
    return customerRepository.save(customer);
  }

  @PostMapping("/customers/{id}/archive")
  public Customer archiveCustomer(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Customer customer = customerRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
    customer.setArchived(true);
    return customerRepository.save(customer);
  }

  @PostMapping("/customers/{id}/restore")
  public Customer restoreCustomer(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Customer customer = customerRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
    customer.setArchived(false);
    return customerRepository.save(customer);
  }

  @DeleteMapping("/customers/{id}")
  public void deleteCustomer(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Customer customer = customerRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));

    if (!orderRepository.findByCustomer(customer).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has invoices and should be archived instead of deleted.");
    }

    customerRepository.delete(customer);
  }

  private void validateCustomer(CreateCustomerRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer name is required.");
    }

    String normalizedName = request.name().trim();

    if (normalizedName.length() < 5 || !normalizedName.contains(" ")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a real full name, for example Ali Wafa.");
    }

    if (!normalizedName.matches("^[A-Za-zÅÄÖåäö\\s'-]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name may only contain letters, spaces, hyphen and apostrophe.");
    }

    if (request.email() == null || !request.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
    }

    if (!emailMatchesName(normalizedName, request.email())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email should match the customer's name, for example Ali Wafa can use ali.wafa@gmail.com.");
    }

    if (request.personalNumber() == null || !request.personalNumber().matches("^\\d{8}-\\d{4}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personal number must use format YYYYMMDD-XXXX, for example 20010203-6598.");
    }

    if (request.address() == null || request.address().trim().length() < 5 || !request.address().matches(".*\\d.*")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address must include street and number, for example Byvagen 56.");
    }

    if (request.postalCode() == null || !request.postalCode().matches("^\\d{3}\\s?\\d{2}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Postal code must use Swedish format, for example 123 45.");
    }

    if (request.city() == null || !request.city().matches("^[A-Za-zÅÄÖåäö\\s'-]{2,}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "City must contain at least two letters.");
    }

    if (request.phone() != null && !request.phone().isBlank() && !request.phone().matches("^[+\\d][\\d\\s-]{6,}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
    }
  }

  private boolean emailMatchesName(String name, String email) {
    String localPart = normalize(email.substring(0, email.indexOf("@")));
    String[] nameParts = normalize(name).split("\\s+");

    for (String part : nameParts) {
      if (part.length() >= 3 && localPart.contains(part)) {
        return true;
      }
    }

    return false;
  }

  private String normalize(String value) {
    return value.toLowerCase()
        .replace("å", "a")
        .replace("ä", "a")
        .replace("ö", "o")
        .replaceAll("[^a-z0-9\\s]", " ");
  }
}
