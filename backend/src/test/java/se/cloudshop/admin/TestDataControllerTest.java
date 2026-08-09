package se.cloudshop.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.JournalEntryRepository;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.customer.CustomerRepository;
import se.cloudshop.expense.ExpenseRepository;
import se.cloudshop.order.OrderRepository;

class TestDataControllerTest {

  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
  private final CustomerRepository customerRepository = mock(CustomerRepository.class);

  @Test
  void rejectsTestDataResetWhenFeatureFlagIsDisabled() {
    TestDataController controller = controller(false);

    assertThatThrownBy(() -> controller.clearTestData(
        "Bearer " + authHeaderToken(),
        "DELETE_ALIBOOKS_TEST_DATA"
    )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Test data reset is disabled");

    verify(journalEntryRepository, never()).deleteAll();
    verify(orderRepository, never()).deleteAll();
    verify(expenseRepository, never()).deleteAll();
    verify(customerRepository, never()).deleteAll();
  }

  @Test
  void rejectsTestDataResetWithoutExplicitConfirmationHeader() {
    TestDataController controller = controller(true);

    assertThatThrownBy(() -> controller.clearTestData(
        "Bearer " + authHeaderToken(),
        ""
    )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Missing reset confirmation header");

    verify(journalEntryRepository, never()).deleteAll();
    verify(orderRepository, never()).deleteAll();
    verify(expenseRepository, never()).deleteAll();
    verify(customerRepository, never()).deleteAll();
  }

  @Test
  void clearsTestDataWhenEnabledAndConfirmed() {
    TestDataController controller = controller(true);

    controller.clearTestData("Bearer " + authHeaderToken(), "DELETE_ALIBOOKS_TEST_DATA");

    verify(journalEntryRepository).deleteAll();
    verify(orderRepository).deleteAll();
    verify(expenseRepository).deleteAll();
    verify(customerRepository).deleteAll();
  }

  private TestDataController controller(boolean testDataResetEnabled) {
    return new TestDataController(
        authHeader,
        journalEntryRepository,
        orderRepository,
        expenseRepository,
        customerRepository,
        testDataResetEnabled
    );
  }

  private String authHeaderToken() {
    return new JwtService("test_secret").createToken("ali@example.com");
  }
}
