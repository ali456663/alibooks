package se.cloudshop.admin;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.accounting.JournalEntryRepository;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.customer.CustomerRepository;
import se.cloudshop.expense.ExpenseRepository;
import se.cloudshop.order.OrderRepository;

@RestController
public class TestDataController {

  private final AuthHeader authHeader;
  private final JournalEntryRepository journalEntryRepository;
  private final OrderRepository orderRepository;
  private final ExpenseRepository expenseRepository;
  private final CustomerRepository customerRepository;

  public TestDataController(
      AuthHeader authHeader,
      JournalEntryRepository journalEntryRepository,
      OrderRepository orderRepository,
      ExpenseRepository expenseRepository,
      CustomerRepository customerRepository
  ) {
    this.authHeader = authHeader;
    this.journalEntryRepository = journalEntryRepository;
    this.orderRepository = orderRepository;
    this.expenseRepository = expenseRepository;
    this.customerRepository = customerRepository;
  }

  @DeleteMapping("/test-data")
  @Transactional
  public void clearTestData(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    journalEntryRepository.deleteAll();
    orderRepository.deleteAll();
    expenseRepository.deleteAll();
    customerRepository.deleteAll();
  }
}
