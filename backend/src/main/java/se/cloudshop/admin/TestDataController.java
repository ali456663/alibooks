package se.cloudshop.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.customer.CustomerRepository;
import se.cloudshop.expense.ExpenseRepository;
import se.cloudshop.order.OrderRepository;

@RestController
public class TestDataController {

  private final AuthHeader authHeader;
  private final JdbcTemplate jdbcTemplate;
  private final OrderRepository orderRepository;
  private final ExpenseRepository expenseRepository;
  private final CustomerRepository customerRepository;
  private final boolean testDataResetEnabled;

  public TestDataController(
      AuthHeader authHeader,
      JdbcTemplate jdbcTemplate,
      OrderRepository orderRepository,
      ExpenseRepository expenseRepository,
      CustomerRepository customerRepository,
      @Value("${app.test-data-reset.enabled:false}") boolean testDataResetEnabled
  ) {
    this.authHeader = authHeader;
    this.jdbcTemplate = jdbcTemplate;
    this.orderRepository = orderRepository;
    this.expenseRepository = expenseRepository;
    this.customerRepository = customerRepository;
    this.testDataResetEnabled = testDataResetEnabled;
  }

  @DeleteMapping("/test-data")
  @Transactional
  public void clearTestData(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestHeader(value = "X-AliBooks-Confirm-Reset", required = false) String confirmation
  ) {
    authHeader.requireValidToken(authorizationHeader);
    if (!testDataResetEnabled) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Test data reset is disabled. Set APP_TEST_DATA_RESET_ENABLED=true only in a local test environment."
      );
    }
    if (!"DELETE_ALIBOOKS_TEST_DATA".equals(confirmation)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Missing reset confirmation header."
      );
    }
    jdbcTemplate.execute("TRUNCATE TABLE journal_entries, journal_voucher_registry, voucher_evidence_contents, voucher_evidence CASCADE");
    orderRepository.deleteAll();
    expenseRepository.deleteAll();
    customerRepository.deleteAll();
  }
}
