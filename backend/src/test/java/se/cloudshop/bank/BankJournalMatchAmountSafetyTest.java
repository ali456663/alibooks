package se.cloudshop.bank;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.accounting.Account;
import se.cloudshop.accounting.JournalEntry;
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

  @Test
  void manuallySelectedJournalMustMatchBankDateAndSignedAmount() {
    LocalDate bankDate = LocalDate.of(2026, 9, 21);
    BankReconciliationEntry row = new BankReconciliationEntry(new CreateBankReconciliationEntryRequest(
        "bank-row-2", bankDate, "Bank payment", "txn-2", 100, "csv", "booked", "Bank payment"));
    JournalEntry wrongDate = new JournalEntry(
        null,
        new Account("1930", "Bank"),
        "B-2",
        100,
        0,
        "Different date",
        bankDate.plusDays(1));
    ReflectionTestUtils.setField(row, "id", 1L);
    ReflectionTestUtils.setField(wrongDate, "id", 2L);
    when(rows.findById(1L)).thenReturn(Optional.of(row));
    when(rows.findAllByBankRowId("bank-row-2")).thenReturn(List.of(row));
    when(rows.findAll()).thenReturn(List.of(row));
    when(journal.findById(2L)).thenReturn(Optional.of(wrongDate));

    assertThatThrownBy(() -> service.match(1L, 2L, "Bearer token"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    verify(rows, never()).saveAndFlush(any(BankReconciliationEntry.class));
  }
}
