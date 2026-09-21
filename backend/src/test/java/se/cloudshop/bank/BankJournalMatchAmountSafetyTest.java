package se.cloudshop.bank;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.accounting.JournalEntryRepository;
import se.cloudshop.audit.AuditService;

class BankJournalMatchAmountSafetyTest {

  private final BankReconciliationEntryRepository rows = mock(BankReconciliationEntryRepository.class);
  private final JournalEntryRepository journal = mock(JournalEntryRepository.class);
  private final BankJournalMatchService service = new BankJournalMatchService(
      rows,
      journal,
      mock(AccountingService.class),
      mock(AuditService.class)
  );

  @Test
  void candidatesStopWhenMinorShadowContainsOre() {
    BankReconciliationEntry row = new BankReconciliationEntry(new CreateBankReconciliationEntryRequest(
        "bank-row-1", LocalDate.of(2026, 9, 21), "Bank payment", "txn-1", 100, "csv", "booked", "Bank payment"));
    ReflectionTestUtils.setField(row, "amountMinor", 10050L);
    when(rows.findById(1L)).thenReturn(Optional.of(row));
    when(rows.findAllByBankRowId("bank-row-1")).thenReturn(List.of(row));

    assertThatThrownBy(() -> service.candidates(1L))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }
}
