package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.cloudshop.audit.AuditService;
import se.cloudshop.bank.BankReconciliationIssue;
import se.cloudshop.bank.BankReconciliationReport;
import se.cloudshop.bank.BankReconciliationService;
import se.cloudshop.expense.ExpenseRepository;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.order.ReceivablesAgingReport;
import se.cloudshop.order.ReceivablesReportService;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;
import se.cloudshop.supplier.PayablesAgingReport;
import se.cloudshop.supplier.PayablesReportService;

class AccountingPeriodLockServiceTest {

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"REVIEW_REQUIRED", "UNSUPPORTED_METHOD"})
  void unresolvedSubledgerControlPreventsLocking(String status) {
    LocalDate date = LocalDate.now();
    mockCleanProfessionalControls(date);
    when(subledgerControlService.createReport(date)).thenReturn(new SubledgerControlReport(date, "INVOICE_METHOD", status, List.of()));
    assertThat(periodLockService.checkPeriod(date).blockers()).anyMatch(message -> message.contains("reskontra") || message.contains("Reskontra"));
    assertThatThrownBy(() -> periodLockService.closePeriod(date, "test")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    verify(settingsService, never()).lockAccountingThroughDate(any());
  }

  @Test
  void wrappedVoucherTotalsCannotPassPeriodClose() {
    LocalDate date = LocalDate.now();
    mockCleanProfessionalControls(date);
    Account bank = new Account("1930", "Bank");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "M-1", Integer.MAX_VALUE, 0, "Test", date),
        new JournalEntry(null, bank, "M-1", Integer.MAX_VALUE, 0, "Test", date),
        new JournalEntry(null, bank, "M-1", 102, 0, "Test", date),
        new JournalEntry(null, bank, "M-1", 0, 100, "Test", date)
    ));
    PeriodCloseCheckResult result = periodLockService.checkPeriod(date);
    assertThat(result.unbalancedVoucherCount()).isEqualTo(1);
    assertThat(result.readyToLock()).isFalse();
    assertThat(result.blockers()).anyMatch(message -> message.contains("obalanserade"));
    assertThatThrownBy(() -> periodLockService.closePeriod(date, "test"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    verify(settingsService, never()).lockAccountingThroughDate(any());
  }

  @Test
  void lateVoucherAmountsCannotWrapDuringPeriodClose() {
    LocalDate date = LocalDate.now().minusDays(50);
    Account bank = new Account("1930", "Bank");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "M-1", Integer.MAX_VALUE, 0, "Test", date),
        new JournalEntry(null, bank, "M-1", 1, 0, "Test", date)
    ));
    assertThatThrownBy(() -> periodLockService.closePeriod(LocalDate.now(), "test"))
        .isInstanceOf(ReportAmounts.LimitExceeded.class);
    verify(settingsService, never()).lockAccountingThroughDate(any());
  }

  @Test
  void lateVoucherOreStopsPeriodCloseInsteadOfRounding() {
    LocalDate date = LocalDate.now().minusDays(50);
    Account bank = new Account("1930", "Bank");
    JournalEntry entry = new JournalEntry(null, bank, "M-1", 125, 0, "Test", date);
    ReflectionTestUtils.setField(entry, "debitMinor", 12_550L);
    when(journalEntryRepository.findAll()).thenReturn(List.of(entry));

    assertThatThrownBy(() -> periodLockService.closePeriod(LocalDate.now(), "test"))
        .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode().value()).isEqualTo(422));
    verify(settingsService, never()).lockAccountingThroughDate(any());
  }

  @Test
  void periodCloseUsesMinorUnitsWhenCheckingVoucherBalance() {
    LocalDate date = LocalDate.now().minusDays(1);
    Account bank = new Account("1930", "Bank");
    Account sales = new Account("3041", "Forsaljning");
    JournalEntry debit = new JournalEntry(null, bank, "M-ORE", 125, 0, "Bank", date);
    JournalEntry credit = new JournalEntry(null, sales, "M-ORE", 0, 125, "Sales", date);
    ReflectionTestUtils.setField(debit, "debitMinor", 12_550L);
    ReflectionTestUtils.setField(credit, "creditMinor", 12_500L);
    when(journalEntryRepository.findAll()).thenReturn(List.of(debit, credit));
    mockCleanProfessionalControls(date);

    PeriodCloseCheckResult result = periodLockService.checkPeriod(date);

    assertThat(result.unbalancedVoucherCount()).isEqualTo(1);
    assertThat(result.readyToLock()).isFalse();
    assertThat(result.blockers()).anyMatch(message -> message.contains("obalanserade verifikat"));
  }

  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
  private final SettingsService settingsService = mock(SettingsService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final BankReconciliationService bankReconciliationService = mock(BankReconciliationService.class);
  private final ReceivablesReportService receivablesReportService = mock(ReceivablesReportService.class);
  private final PayablesReportService payablesReportService = mock(PayablesReportService.class);
  private final VoucherApprovalRepository voucherApprovalRepository = mock(VoucherApprovalRepository.class);
  private final SubledgerControlService subledgerControlService = mock(SubledgerControlService.class);
  private final AccountingPeriodLockService periodLockService = new AccountingPeriodLockService(
      journalEntryRepository,
      orderRepository,
      expenseRepository,
      settingsService,
      auditService,
      accountingService,
      bankReconciliationService,
      receivablesReportService,
      payablesReportService,
      voucherApprovalRepository,
      subledgerControlService
  );

  @BeforeEach
  void setUp() {
    when(subledgerControlService.createReport(any())).thenReturn(new SubledgerControlReport(LocalDate.now(), "INVOICE_METHOD", "MATCHED", List.of()));
    when(settingsService.getSettings()).thenReturn(AppSettings.defaults());
    when(journalEntryRepository.findAll()).thenReturn(List.of());
    when(voucherApprovalRepository.findAll()).thenReturn(List.of());
    when(orderRepository.findAll()).thenReturn(List.of());
    when(expenseRepository.findAll()).thenReturn(List.of());
    when(bankReconciliationService.createReport(isNull(), any())).thenReturn(new BankReconciliationReport(
        null,
        LocalDate.now(),
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of()
    ));
    when(receivablesReportService.createAgingReport(any())).thenReturn(new ReceivablesAgingReport(
        LocalDate.now(),
        0,
        0,
        0,
        0,
        0,
        0,
        List.of(),
        List.of()
    ));
    when(payablesReportService.createAgingReport(any())).thenReturn(new PayablesAgingReport(
        LocalDate.now(),
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of(),
        List.of()
    ));
    when(accountingService.createVatFilingProofReportsThroughDate(any())).thenReturn(List.of());
    when(accountingService.createAccountSignControlReport(isNull(), any())).thenReturn(new AccountSignControlReport(
        null,
        LocalDate.now(),
        Instant.EPOCH,
        0,
        0,
        0,
        0,
        List.of()
    ));
    when(accountingService.createJournalIntegrityReport(isNull(), any())).thenReturn(new JournalIntegrityReport(
        null,
        LocalDate.now(),
        Instant.EPOCH,
        0,
        0,
        0,
        0,
        0,
        0,
        "",
        "",
        "A".repeat(64),
        List.of()
    ));
  }

  @Test
  void checkPeriodBlocksLockWhenProfessionalControlsHaveCriticalIssues() {
    LocalDate lockDate = LocalDate.now().minusDays(1);

    when(accountingService.createBalanceReport(lockDate)).thenReturn(new BalanceReport(
        lockDate,
        List.of(),
        List.of(),
        1000,
        900,
        100
    ));
    when(accountingService.createTrialBalanceReport(null, lockDate)).thenReturn(new TrialBalanceReport(
        null,
        lockDate,
        List.of(),
        0,
        0,
        1000,
        900,
        1000,
        900,
        100
    ));
    when(accountingService.createVoucherControlReport(null, lockDate)).thenReturn(new VoucherControlReport(
        null,
        lockDate,
        2,
        4,
        1,
        1,
        0,
        0,
        0,
        1,
        0,
        1,
        2,
        List.of()
    ));
    when(accountingService.createVatControlReport(null, lockDate)).thenReturn(new VatControlReport(
        null,
        lockDate,
        2,
        1000,
        100,
        250,
        -150,
        0,
        0,
        0,
        100,
        1,
        1,
        List.of()
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isFalse();
    assertThat(result.balanceDifference()).isEqualTo(100);
    assertThat(result.trialBalanceDifference()).isEqualTo(100);
    assertThat(result.voucherCriticalIssueCount()).isEqualTo(1);
    assertThat(result.vatCriticalIssueCount()).isEqualTo(1);
    assertThat(result.sieExportReady()).isFalse();
    assertThat(result.blockers())
        .anyMatch(blocker -> blocker.contains("Balansrapporten"))
        .anyMatch(blocker -> blocker.contains("Saldobalansen"))
        .anyMatch(blocker -> blocker.contains("kritiska verifikationspunkter"))
        .anyMatch(blocker -> blocker.contains("kritiska momspunkter"));
    assertThat(result.warnings())
        .anyMatch(warning -> warning.contains("verifikationsvarningar"))
        .anyMatch(warning -> warning.contains("momsvarningar"))
        .anyMatch(warning -> warning.contains("SIE-export"));
  }

  @Test
  void checkPeriodBlocksLockWhenVatFilingProofChainIsIncomplete() {
    LocalDate lockDate = LocalDate.now().minusDays(1);

    when(accountingService.createBalanceReport(lockDate)).thenReturn(new BalanceReport(
        lockDate,
        List.of(),
        List.of(),
        0,
        0,
        0
    ));
    when(accountingService.createTrialBalanceReport(null, lockDate)).thenReturn(new TrialBalanceReport(
        null,
        lockDate,
        List.of(),
        0,
        0,
        0,
        0,
        0,
        0,
        0
    ));
    when(accountingService.createVoucherControlReport(null, lockDate)).thenReturn(new VoucherControlReport(
        null,
        lockDate,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of()
    ));
    when(accountingService.createVatControlReport(null, lockDate)).thenReturn(new VatControlReport(
        null,
        lockDate,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of()
    ));
    when(accountingService.createVatFilingProofReportsThroughDate(lockDate)).thenReturn(List.of(
        new VatFilingProofReport(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            "PAID",
            150,
            true,
            true,
            true,
            false,
            false,
            List.of(new VatFilingProofVoucher("MOMS-1", LocalDate.of(2026, 7, 31), "VAT settlement 2026-07-01 - 2026-07-31", 250, 250)),
            List.of(),
            "VAT filing is marked as paid/refunded but the payment voucher is missing."
        )
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isFalse();
    assertThat(result.vatProofIncompleteCount()).isEqualTo(1);
    assertThat(result.vatProofSettlementMissingCount()).isZero();
    assertThat(result.vatProofPaymentMissingCount()).isEqualTo(1);
    assertThat(result.blockers())
        .anyMatch(blocker -> blocker.contains("momsdeklarerade perioder saknar komplett beviskedja"));
  }

  @Test
  void checkPeriodBlocksLockWhenJournalIntegrityHasProblems() {
    LocalDate lockDate = LocalDate.now().minusDays(1);

    when(accountingService.createBalanceReport(lockDate)).thenReturn(new BalanceReport(
        lockDate,
        List.of(),
        List.of(),
        0,
        0,
        0
    ));
    when(accountingService.createTrialBalanceReport(null, lockDate)).thenReturn(new TrialBalanceReport(
        null,
        lockDate,
        List.of(),
        0,
        0,
        0,
        0,
        0,
        0,
        0
    ));
    when(accountingService.createVoucherControlReport(null, lockDate)).thenReturn(new VoucherControlReport(
        null,
        lockDate,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of()
    ));
    when(accountingService.createVatControlReport(null, lockDate)).thenReturn(new VatControlReport(
        null,
        lockDate,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of()
    ));
    when(accountingService.createJournalIntegrityReport(null, lockDate)).thenReturn(new JournalIntegrityReport(
        null,
        lockDate,
        Instant.EPOCH,
        2,
        1,
        100,
        90,
        10,
        1,
        "B".repeat(64),
        "C".repeat(64),
        "D".repeat(64),
        List.of()
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isFalse();
    assertThat(result.journalIntegrityDifference()).isEqualTo(10);
    assertThat(result.journalIntegrityMissingEvidenceCount()).isEqualTo(1);
    assertThat(result.blockers())
        .anyMatch(blocker -> blocker.contains("integritetskontroll har differens"))
        .anyMatch(blocker -> blocker.contains("saknar tydlig kallkoppling"));
  }

  @Test
  void checkPeriodBlocksLockWhenCriticalAccountSignsAreNegative() {
    LocalDate lockDate = LocalDate.now().minusDays(1);
    Account bank = new Account("1930", "Foretagskonto");
    Account employeeTax = new Account("2710", "Personalskatt");
    Account stripeReceivable = new Account("1580", "Stripe-fordran");
    Account sales = new Account("3041", "Forsaljning");

    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, employeeTax, "M-1", 500, 0, "Wrong employee tax sign", lockDate),
        new JournalEntry(null, bank, "M-1", 0, 500, "Wrong bank sign", lockDate),
        new JournalEntry(null, sales, "M-2", 100, 0, "Stripe sign control offset", lockDate),
        new JournalEntry(null, stripeReceivable, "M-2", 0, 100, "Wrong Stripe receivable sign", lockDate)
    ));
    mockCleanProfessionalControls(lockDate);
    when(accountingService.createAccountSignControlReport(null, lockDate)).thenReturn(new AccountSignControlReport(
        null,
        lockDate,
        Instant.EPOCH,
        3,
        3,
        2,
        1,
        List.of(
            new AccountSignControlLine("1930", "Foretagskonto", "debit", -500, "critical", "critical", true, "1930 Foretagskonto har ovantat minussaldo -500 SEK."),
            new AccountSignControlLine("2710", "Personalskatt", "credit", -500, "critical", "critical", true, "2710 Personalskatt har ovantat minussaldo -500 SEK."),
            new AccountSignControlLine("1580", "Stripe-fordran", "debit", -100, "warning", "warning", false, "1580 Stripe-fordran har ovantat minussaldo -100 SEK.")
        )
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isFalse();
    assertThat(result.blockers())
        .anyMatch(blocker -> blocker.contains("1930"))
        .anyMatch(blocker -> blocker.contains("2710"));
    assertThat(result.warnings())
        .anyMatch(warning -> warning.contains("1580"));
  }

  @Test
  void checkPeriodBlocksLockWhenBankReconciliationHasCriticalDifference() {
    LocalDate lockDate = LocalDate.now().minusDays(1);
    mockCleanProfessionalControls(lockDate);
    when(bankReconciliationService.createReport(null, lockDate)).thenReturn(new BankReconciliationReport(
        null,
        lockDate,
        2,
        2,
        2,
        1,
        1000,
        750,
        250,
        1,
        1,
        List.of(
            new BankReconciliationIssue("critical", "bank_reconciliation_difference", null, "", 250, "Bank differs."),
            new BankReconciliationIssue("warning", "skipped_bank_row", lockDate, "SKIP-1", 100, "Skipped bank row.")
        )
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isFalse();
    assertThat(result.bankReconciliationDifference()).isEqualTo(250);
    assertThat(result.bankReconciliationCriticalIssueCount()).isEqualTo(1);
    assertThat(result.bankReconciliationWarningIssueCount()).isEqualTo(1);
    assertThat(result.blockers())
        .anyMatch(blocker -> blocker.contains("kritiska bankavstamningspunkter"));
    assertThat(result.warnings())
        .anyMatch(warning -> warning.contains("bankavstamningsvarningar"));
  }

  @Test
  void checkPeriodBlocksLockWhenVouchersAreNotApproved() {
    LocalDate lockDate = LocalDate.now().minusDays(1);
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning");
    mockCleanProfessionalControls(lockDate);
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-2026-0001", 1250, 0, "Invoice paid", lockDate),
        new JournalEntry(null, sales, "F-2026-0001", 0, 1250, "Invoice paid", lockDate),
        new JournalEntry(null, bank, "F-2026-0002", 500, 0, "Invoice paid", lockDate),
        new JournalEntry(null, sales, "F-2026-0002", 0, 500, "Invoice paid", lockDate),
        new JournalEntry(null, bank, "F-2026-0003", 700, 0, "Invoice paid", lockDate),
        new JournalEntry(null, sales, "F-2026-0003", 0, 700, "Invoice paid", lockDate)
    ));
    when(voucherApprovalRepository.findAll()).thenReturn(List.of(
        new VoucherApproval("F-2026-0001", "approved", "Looks good", "AliBooks"),
        new VoucherApproval("F-2026-0002", "pending", "Needs receipt check", "AliBooks")
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isFalse();
    assertThat(result.voucherApprovedCount()).isEqualTo(1);
    assertThat(result.voucherPendingApprovalCount()).isEqualTo(1);
    assertThat(result.voucherMissingApprovalCount()).isEqualTo(1);
    assertThat(result.voucherBlockedApprovalCount()).isZero();
    assertThat(result.blockers())
        .anyMatch(blocker -> blocker.contains("saknar attest"))
        .anyMatch(blocker -> blocker.contains("vantar pa attest"));
  }

  @Test
  void checkPeriodWarnsWhenReceivablesRemainOpen() {
    LocalDate lockDate = LocalDate.now().minusDays(1);
    mockCleanProfessionalControls(lockDate);
    when(receivablesReportService.createAgingReport(lockDate)).thenReturn(new ReceivablesAgingReport(
        lockDate,
        2,
        1500,
        400,
        1100,
        400,
        0,
        List.of(),
        List.of()
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isTrue();
    assertThat(result.receivablesInvoiceCount()).isEqualTo(2);
    assertThat(result.receivablesTotalOutstanding()).isEqualTo(1500);
    assertThat(result.receivablesOverdueOutstanding()).isEqualTo(1100);
    assertThat(result.receivablesDueSoonOutstanding()).isEqualTo(400);
    assertThat(result.warnings())
        .anyMatch(warning -> warning.contains("oppna kundfakturor"))
        .anyMatch(warning -> warning.contains("Forfallna kundfordringar"));
  }

  @Test
  void checkPeriodWarnsWhenPayablesRemainOpen() {
    LocalDate lockDate = LocalDate.now().minusDays(1);
    mockCleanProfessionalControls(lockDate);
    when(payablesReportService.createAgingReport(lockDate)).thenReturn(new PayablesAgingReport(
        lockDate,
        3,
        2400,
        700,
        1700,
        700,
        0,
        480,
        List.of(),
        List.of()
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isTrue();
    assertThat(result.payablesInvoiceCount()).isEqualTo(3);
    assertThat(result.payablesTotalOutstanding()).isEqualTo(2400);
    assertThat(result.payablesOverdueOutstanding()).isEqualTo(1700);
    assertThat(result.payablesDueSoonOutstanding()).isEqualTo(700);
    assertThat(result.warnings())
        .anyMatch(warning -> warning.contains("oppna leverantorsfakturor"))
        .anyMatch(warning -> warning.contains("Forfallna leverantorsskulder"));
  }

  @Test
  void checkPeriodWarnsWhenVoucherIsBookedLongAfterVoucherDate() {
    LocalDate oldVoucherDate = LocalDate.now().minusDays(50);
    LocalDate lockDate = LocalDate.now();
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning");
    mockCleanProfessionalControls(lockDate);
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "M-55", 500, 0, "Late bookkeeping", oldVoucherDate),
        new JournalEntry(null, sales, "M-55", 0, 500, "Late bookkeeping", oldVoucherDate)
    ));

    PeriodCloseCheckResult result = periodLockService.checkPeriod(lockDate);

    assertThat(result.readyToLock()).isFalse();
    assertThat(result.lateBookedVoucherCount()).isEqualTo(1);
    assertThat(result.longestBookingLagDays()).isGreaterThanOrEqualTo(49);
    assertThat(result.lateBookedVouchers())
        .hasSize(1)
        .first()
        .satisfies(voucher -> {
          assertThat(voucher.voucherNumber()).isEqualTo("M-55");
          assertThat(voucher.voucherDate()).isEqualTo(oldVoucherDate);
          assertThat(voucher.debit()).isEqualTo(500);
          assertThat(voucher.credit()).isEqualTo(500);
        });
    assertThat(result.warnings())
        .anyMatch(warning -> warning.contains("mer an 35 dagar"));
  }

  private void mockCleanProfessionalControls(LocalDate lockDate) {
    when(accountingService.createBalanceReport(lockDate)).thenReturn(new BalanceReport(
        lockDate,
        List.of(),
        List.of(),
        0,
        0,
        0
    ));
    when(accountingService.createTrialBalanceReport(null, lockDate)).thenReturn(new TrialBalanceReport(
        null,
        lockDate,
        List.of(),
        0,
        0,
        0,
        0,
        0,
        0,
        0
    ));
    when(accountingService.createVoucherControlReport(null, lockDate)).thenReturn(new VoucherControlReport(
        null,
        lockDate,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of()
    ));
    when(accountingService.createVatControlReport(null, lockDate)).thenReturn(new VatControlReport(
        null,
        lockDate,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of()
    ));
  }
}
