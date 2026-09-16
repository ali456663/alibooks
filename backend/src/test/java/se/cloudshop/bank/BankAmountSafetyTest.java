package se.cloudshop.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.Account;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.accounting.JournalEntryRepository;

class BankAmountSafetyTest {
  private final JournalEntryRepository journal = mock(JournalEntryRepository.class);
  private final BankReconciliationEntryRepository bank = mock(BankReconciliationEntryRepository.class);
  private final BankReconciliationService service = new BankReconciliationService(bank, journal);

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rejectsOversizedLedgerOrBankTotal(boolean ledger) {
    when(journal.findAll()).thenReturn(ledger ? List.of(entry(Integer.MAX_VALUE), entry(1)) : List.of());
    when(bank.findAll()).thenReturn(ledger ? List.of() : List.of(row(Integer.MAX_VALUE), row(1)));
    assertThatThrownBy(() -> service.createReport(null, null)).isInstanceOfSatisfying(ResponseStatusException.class,
        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  void rejectsDifferenceOverflowEvenWhenBothMovementsFit() {
    when(journal.findAll()).thenReturn(List.of(entry(Integer.MAX_VALUE)));
    when(bank.findAll()).thenReturn(List.of(row(-1)));
    assertThatThrownBy(() -> service.createReport(null, null)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void negativeMovementIsNotClampedToZero() {
    when(journal.findAll()).thenReturn(List.of(entry(-300)));
    when(bank.findAll()).thenReturn(List.of(row(-300)));
    var report = service.createReport(null, null);
    assertThat(report.ledgerMovement()).isEqualTo(-300);
    assertThat(report.reconciledMovement()).isEqualTo(-300);
    assertThat(report.difference()).isZero();
  }

  @Test
  void intermediateLongSumCanReturnToSupportedRange() {
    when(journal.findAll()).thenReturn(List.of(entry(Integer.MAX_VALUE), entry(1), entry(-1)));
    when(bank.findAll()).thenReturn(List.of(row(Integer.MAX_VALUE), row(1), row(-1)));
    assertThat(service.createReport(null, null).difference()).isZero();
    assertThat(service.createReport(null, null).ledgerMovement()).isEqualTo(Integer.MAX_VALUE);
  }

  private JournalEntry entry(int amount) {
    return new JournalEntry(null, new Account("1930", "Bank"), "M-1", Math.max(amount, 0), Math.max(-amount, 0), "Test", LocalDate.now());
  }

  private BankReconciliationEntry row(int amount) {
    return new BankReconciliationEntry(new CreateBankReconciliationEntryRequest("test", LocalDate.now(), "Test", "ref", amount, "manual", "booked", "Test"));
  }
}
