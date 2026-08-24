package se.cloudshop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.product.Product;

class CustomerControllerTest {

  private final CustomerRepository customerRepository = mock(CustomerRepository.class);
  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final AuditService auditService = mock(AuditService.class);
  private final CustomerController customerController = new CustomerController(
      customerRepository,
      authHeader,
      orderRepository,
      auditService
  );

  @Test
  void createCustomerRejectsDuplicateEmail() {
    CreateCustomerRequest request = validCustomerRequest();
    when(customerRepository.existsByEmailIgnoreCase("ali.wafa@gmail.com")).thenReturn(true);

    assertThatThrownBy(() -> customerController.createCustomer("Bearer " + authHeaderToken(), request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("email already exists");

    verify(customerRepository, never()).save(any(Customer.class));
  }

  @Test
  void createCustomerRejectsDuplicatePersonalNumber() {
    CreateCustomerRequest request = validCustomerRequest();
    when(customerRepository.existsByPersonalNumber("20010203-6598")).thenReturn(true);

    assertThatThrownBy(() -> customerController.createCustomer("Bearer " + authHeaderToken(), request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("personal number already exists");

    verify(customerRepository, never()).save(any(Customer.class));
  }

  @Test
  void updateCustomerIgnoresItsOwnEmailAndPersonalNumber() {
    Customer customer = new Customer("Ali Wafa", "ali.wafa@gmail.com", "20010203-6598", "Byvagen 56", "0795565656", "123 45", "Sodertalje");
    setCustomerId(customer, 7L);
    when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
    when(customerRepository.existsByEmailIgnoreCaseAndIdNot("ali.wafa@gmail.com", 7L)).thenReturn(false);
    when(customerRepository.existsByPersonalNumberAndIdNot("20010203-6598", 7L)).thenReturn(false);
    when(customerRepository.save(customer)).thenReturn(customer);

    Customer updated = customerController.updateCustomer("Bearer " + authHeaderToken(), 7L, validCustomerRequest());

    assertThat(updated.getEmail()).isEqualTo("ali.wafa@gmail.com");
    assertThat(updated.getPersonalNumber()).isEqualTo("20010203-6598");
    verify(customerRepository).save(customer);
  }

  @Test
  void createCustomerRecordsAuditEventWithoutSensitiveDetails() {
    when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
      Customer savedCustomer = invocation.getArgument(0);
      setCustomerId(savedCustomer, 11L);
      return savedCustomer;
    });

    Customer customer = customerController.createCustomer("Bearer " + authHeaderToken(), validCustomerRequest());

    assertThat(customer.getId()).isEqualTo(11L);
    verify(auditService).record(
        eq("customer"),
        eq("customer"),
        eq(11L),
        eq("customer_created"),
        eq("Ali Wafa"),
        eq("Customer created."),
        eq(0),
        any(String.class)
    );
  }

  @Test
  void archiveCustomerRecordsAuditEvent() {
    Customer customer = new Customer("Ali Wafa", "ali.wafa@gmail.com", "20010203-6598", "Byvagen 56", "0795565656", "123 45", "Sodertalje");
    setCustomerId(customer, 12L);
    when(customerRepository.findById(12L)).thenReturn(Optional.of(customer));
    when(customerRepository.save(customer)).thenReturn(customer);

    Customer archived = customerController.archiveCustomer("Bearer " + authHeaderToken(), 12L);

    assertThat(archived.isArchived()).isTrue();
    verify(auditService).record(
        eq("customer"),
        eq("customer"),
        eq(12L),
        eq("customer_archived"),
        eq("Ali Wafa"),
        eq("Customer archived instead of deleted."),
        eq(0),
        any(String.class)
    );
  }

  @Test
  void deleteCustomerRejectsCustomerWithInvoices() {
    Customer customer = new Customer("Ali Wafa", "ali.wafa@gmail.com", "20010203-6598", "Byvagen 56", "0795565656", "123 45", "Sodertalje");
    setCustomerId(customer, 15L);
    Order invoice = new Order(customer, new Product("PT", "Training", 1000), java.time.Instant.now());
    when(customerRepository.findById(15L)).thenReturn(Optional.of(customer));
    when(orderRepository.findByCustomer(customer)).thenReturn(List.of(invoice));

    assertThatThrownBy(() -> customerController.deleteCustomer("Bearer " + authHeaderToken(), 15L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("should be archived instead of deleted");

    verify(customerRepository, never()).delete(customer);
  }

  private CreateCustomerRequest validCustomerRequest() {
    return new CreateCustomerRequest(
        "Ali Wafa",
        "ali.wafa@gmail.com",
        "20010203-6598",
        "Byvagen 56",
        "0795565656",
        "123 45",
        "Sodertalje"
    );
  }

  private String authHeaderToken() {
    return new JwtService("test_secret").createToken("ali@example.com");
  }

  private void setCustomerId(Customer customer, Long id) {
    try {
      Field field = Customer.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(customer, id);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
