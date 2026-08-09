package se.cloudshop.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.customer.CustomerRepository;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.product.ProductService;
import se.cloudshop.settings.SettingsService;

class RecurringContractControllerTest {

  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final RecurringContractRepository recurringContractRepository = mock(RecurringContractRepository.class);
  private final CustomerRepository customerRepository = mock(CustomerRepository.class);
  private final ProductService productService = mock(ProductService.class);
  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final SettingsService settingsService = mock(SettingsService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final RecurringContractController recurringContractController = new RecurringContractController(
      authHeader,
      recurringContractRepository,
      customerRepository,
      productService,
      orderRepository,
      accountingService,
      settingsService,
      auditService
  );

  @Test
  void deleteContractArchivesInsteadOfDeleting() {
    RecurringContract contract = contract();
    setContractId(contract, 5L);
    when(recurringContractRepository.findById(5L)).thenReturn(Optional.of(contract));
    when(recurringContractRepository.save(contract)).thenReturn(contract);

    recurringContractController.deleteContract("Bearer " + authHeaderToken(), 5L);

    assertThat(contract.isArchived()).isTrue();
    assertThat(contract.isActive()).isFalse();
    assertThat(contract.getArchivedAt()).isNotNull();
    verify(recurringContractRepository, never()).deleteById(5L);
    verify(recurringContractRepository).save(contract);
    verify(auditService).record(
        eq("contract"),
        eq("recurring_contract"),
        eq(5L),
        eq("archived"),
        eq("Ali Wafa"),
        eq("Recurring contract archived instead of deleted"),
        eq(0),
        any(String.class)
    );
  }

  @Test
  void archivedContractCannotBeInvoiced() {
    RecurringContract contract = contract();
    contract.archive();
    when(recurringContractRepository.findById(5L)).thenReturn(Optional.of(contract));

    assertThatThrownBy(() -> recurringContractController.createContractInvoice(
        "Bearer " + authHeaderToken(),
        5L
    )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Archived contracts cannot be invoiced");

    verify(orderRepository, never()).save(any());
  }

  private RecurringContract contract() {
    return new RecurringContract(1L, "Ali Wafa", 2L, "PT", 1, "monthly", LocalDate.of(2026, 8, 1));
  }

  private String authHeaderToken() {
    return new JwtService("test_secret").createToken("ali@example.com");
  }

  private void setContractId(RecurringContract contract, Long id) {
    try {
      Field field = RecurringContract.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(contract, id);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
