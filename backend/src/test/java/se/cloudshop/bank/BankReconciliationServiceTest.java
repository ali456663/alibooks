package se.cloudshop.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.cloudshop.accounting.Account;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.accounting.JournalEntryRepository;

class BankReconciliationServiceTest {

  private final BankReconciliationEntryRepository bankReconciliationEntryRepository = mock(BankReconciliationEntryRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final BankReconciliationService bankReconciliationService = new BankReconciliationService(
      bankReconciliationEntryRepository,
      journalEntryRepository
  );

  @Test
  void createsBalancedBankReconciliationReportForAccount1930() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "B-1", 1250, 0, "Customer payment", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "K-1", 0, 250, "Bank expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Ignored non-bank account", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "OLD-1", 99, 0, "Outside period", LocalDate.of(2026, 6, 30))
    ));
    when(bankReconciliationEntryRepository.findAll()).thenReturn(List.of(
        bankRow("row-1", LocalDate.of(2026, 7, 10), "Stripe", "txn_1", 1250, "booked"),
        bankRow("row-2", LocalDate.of(2026, 7, 11), "Bank fee", "fee_1", -250, "booked"),
        bankRow("old", LocalDate.of(2026, 6, 30), "Old", "old_1", 99, "booked")
    ));

    BankReconciliationReport report = bankReconciliationService.createReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.journalEntryCount()).isEqualTo(2);
    assertThat(report.bankRowCount()).isEqualTo(2);
    assertThat(report.bookedBankRowCount()).isEqualTo(2);
    assertThat(report.ledgerMovement()).isEqualTo(1000);
    assertThat(report.reconciledMovement()).isEqualTo(1000);
    assertThat(report.difference()).isZero();
    assertThat(report.criticalIssueCount()).isZero();
    assertThat(report.warningIssueCount()).isZero();
  }

  @Test
  void createsCriticalIssueWhenBankRowsDoNotMatchAccount1930() {
    Account bank = new Account("1930", "Foretagskonto");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "B-1", 1250, 0, "Customer payment", LocalDate.of(2026, 7, 10))
    ));
    when(bankReconciliationEntryRepository.findAll()).thenReturn(List.of(
        bankRow("row-1", LocalDate.of(2026, 7, 10), "Stripe", "txn_1", 1000, "booked"),
        bankRow("row-2", LocalDate.of(2026, 7, 11), "Review later", "skip_1", -50, "skipped")
    ));

    BankReconciliationReport report = bankReconciliationService.createReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.ledgerMovement()).isEqualTo(1250);
    assertThat(report.reconciledMovement()).isEqualTo(1000);
    assertThat(report.difference()).isEqualTo(250);
    assertThat(report.criticalIssueCount()).isEqualTo(1);
    assertThat(report.warningIssueCount()).isEqualTo(1);
    assertThat(report.issues()).extracting(BankReconciliationIssue::issueType)
        .contains("bank_reconciliation_difference", "skipped_bank_row");
  }

  private BankReconciliationEntry bankRow(
      String bankRowId,
      LocalDate date,
      String description,
      String reference,
      int amount,
      String status
  ) {
    return new BankReconciliationEntry(new CreateBankReconciliationEntryRequest(
        bankRowId,
        date,
        description,
        reference,
        amount,
        "csv",
        status,
        description
    ));
  }
}
