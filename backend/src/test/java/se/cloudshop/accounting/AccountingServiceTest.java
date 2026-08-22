package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import se.cloudshop.bank.BankReconciliationReport;
import se.cloudshop.bank.BankReconciliationService;
import se.cloudshop.expense.Expense;
import se.cloudshop.order.Order;
import se.cloudshop.order.ReceivablesAgingReport;
import se.cloudshop.order.ReceivablesReportService;
import se.cloudshop.product.Product;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;
import se.cloudshop.supplier.PayablesAgingReport;
import se.cloudshop.supplier.PayablesReportService;
import se.cloudshop.supplier.Supplier;
import se.cloudshop.supplier.SupplierInvoice;

class AccountingServiceTest {

  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final StripePayoutRepository stripePayoutRepository = mock(StripePayoutRepository.class);
  private final VoucherNumberService voucherNumberService = mock(VoucherNumberService.class);
  private final SettingsService settingsService = mock(SettingsService.class);
  private final VatFilingRepository vatFilingRepository = mock(VatFilingRepository.class);
  private final BankReconciliationService bankReconciliationService = mock(BankReconciliationService.class);
  private final ReceivablesReportService receivablesReportService = mock(ReceivablesReportService.class);
  private final PayablesReportService payablesReportService = mock(PayablesReportService.class);
  private final VoucherApprovalRepository voucherApprovalRepository = mock(VoucherApprovalRepository.class);
  private final AccountingService accountingService = new AccountingService(
      accountRepository,
      journalEntryRepository,
      stripePayoutRepository,
      voucherNumberService,
      settingsService,
      vatFilingRepository,
      bankReconciliationService,
      receivablesReportService,
      payablesReportService,
      voucherApprovalRepository
  );

  @BeforeEach
  void setUp() {
    when(settingsService.getSettings()).thenReturn(AppSettings.defaults());
    when(journalEntryRepository.findAll()).thenReturn(List.of());
    when(journalEntryRepository.findByInvoice(any(Order.class))).thenReturn(List.of());
    when(journalEntryRepository.findBySupplierInvoice(any(SupplierInvoice.class))).thenReturn(List.of());
    when(journalEntryRepository.findByCorrectionOfVoucherNumber(any())).thenReturn(List.of());
    when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(voucherApprovalRepository.findByVoucherNumber(any())).thenReturn(Optional.empty());
    when(voucherApprovalRepository.findAll()).thenReturn(List.of());
    when(voucherApprovalRepository.save(any(VoucherApproval.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(stripePayoutRepository.save(any(StripePayout.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(stripePayoutRepository.findByReference(any())).thenReturn(Optional.empty());
    when(vatFilingRepository.findAll()).thenReturn(List.of());
    when(vatFilingRepository.findFirstByStatusInOrderByPeriodToDesc(List.of("SUBMITTED", "PAID"))).thenReturn(Optional.empty());
    when(bankReconciliationService.createReport(any(), any())).thenReturn(new BankReconciliationReport(
        null,
        null,
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
        null,
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
        null,
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

    mockAccount("1580", "Fordran hos Stripe");
    mockAccount("1510", "Kundfordringar");
    mockAccount("1650", "Momsfordran");
    mockAccount("1930", "Foretagskonto");
    mockAccount("2012", "Avrakning for skatter och avgifter");
    mockAccount("2018", "Egna insattningar");
    mockAccount("2019", "Arets resultat");
    mockAccount("2440", "Leverantorsskulder");
    mockAccount("1630", "Skattekonto");
    mockAccount("2611", "Utgaende moms");
    mockAccount("2641", "Ingaende moms");
    mockAccount("2650", "Redovisningskonto for moms");
    mockAccount("3041", "Forsaljning tjanster 25 procent");
    mockAccount("5420", "Programvaror");
    mockAccount("6570", "Bankkostnader");
    mockAccount("8999", "Arets resultat");
  }

  @Test
  void createsArchiveYearReportFromBookkeepingEvidenceAndVatArchive() {
    Product training = new Product("PT", "Training", 1000);
    Order invoice = new Order("Ali Wafa", training, Instant.parse("2026-02-01T10:00:00Z"));
    invoice.setInvoiceNumber("F-2026-0001");
    Expense expense = new Expense(LocalDate.of(2026, 2, 2), "Programvara", 400, 100, "5420", "1930");
    expense.setReceipt("kvitto.pdf", "application/pdf", "uploads/receipts/kvitto.pdf", "abc123", Instant.parse("2026-02-02T10:00:00Z"));
    List<JournalEntry> entries = List.of(
        new JournalEntry(invoice, new Account("1510", "Kundfordringar"), "F-1", 1250, 0, "Invoice created", LocalDate.of(2026, 2, 1)),
        new JournalEntry(invoice, new Account("3041", "Forsaljning tjanster 25 %"), "F-1", 0, 1000, "Invoice created", LocalDate.of(2026, 2, 1)),
        new JournalEntry(invoice, new Account("2611", "Utgaende moms"), "F-1", 0, 250, "Invoice created", LocalDate.of(2026, 2, 1)),
        new JournalEntry(null, expense, new Account("5420", "Programvaror"), "K-1", 400, 0, "Programvara", LocalDate.of(2026, 2, 2)),
        new JournalEntry(null, expense, new Account("2641", "Ingaende moms"), "K-1", 100, 0, "Programvara", LocalDate.of(2026, 2, 2)),
        new JournalEntry(null, expense, new Account("1930", "Foretagskonto"), "K-1", 0, 500, "Programvara", LocalDate.of(2026, 2, 2))
    );
    when(journalEntryRepository.findAll()).thenReturn(entries);
    VatReport vatReport = new VatReport(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 250, 100, 150, false);
    VatFiling filing = new VatFiling(
        vatReport,
        new CreateVatFilingRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "PAID", "moms-2026", "bank-2026", LocalDate.of(2027, 2, 12), ""),
        "PAID"
    );
    when(vatFilingRepository.findAll()).thenReturn(List.of(filing));
    when(voucherApprovalRepository.findAll()).thenReturn(List.of(
        new VoucherApproval("F-1", "approved", "Checked", "AliBooks"),
        new VoucherApproval("K-1", "approved", "Checked", "AliBooks")
    ));

    ArchiveYearReport report = accountingService.createArchiveYearReport(2026);

    assertThat(report.year()).isEqualTo(2026);
    assertThat(report.retentionUntil()).isEqualTo("2033-12-31");
    assertThat(report.journalEntryCount()).isEqualTo(6);
    assertThat(report.voucherCount()).isEqualTo(2);
    assertThat(report.invoiceCount()).isEqualTo(1);
    assertThat(report.expenseCount()).isEqualTo(1);
    assertThat(report.receiptCount()).isEqualTo(1);
    assertThat(report.missingReceiptCount()).isZero();
    assertThat(report.missingReceiptHashCount()).isZero();
    assertThat(report.vatFilingCount()).isEqualTo(1);
    assertThat(report.voucherApprovedCount()).isEqualTo(2);
    assertThat(report.voucherMissingApprovalCount()).isZero();
    assertThat(report.voucherPendingApprovalCount()).isZero();
    assertThat(report.voucherBlockedApprovalCount()).isZero();
    assertThat(report.periodFingerprint()).hasSize(64);
    assertThat(report.items()).extracting(ArchiveYearItem::key).contains("journal", "receipts", "vat", "reports", "voucher-approval", "sie");
  }

  @Test
  void submittedVatFilingLocksAccountingDateInBackend() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    VatFiling submittedFiling = new VatFiling(
        new VatReport(periodFrom, periodTo, 2500, 600, 1900, false),
        new CreateVatFilingRequest(periodFrom, periodTo, "SUBMITTED", "SKV-2026-07", "", null, ""),
        "SUBMITTED"
    );
    when(vatFilingRepository.findFirstByStatusInOrderByPeriodToDesc(List.of("SUBMITTED", "PAID")))
        .thenReturn(Optional.of(submittedFiling));

    assertThatThrownBy(() -> accountingService.requireUnlockedAccountingDate(LocalDate.of(2026, 7, 15)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("Momsperioden ar deklarerad eller betald");
  }

  @Test
  void invoiceMethodBooksInvoiceWhenCreated() {
    when(voucherNumberService.nextVoucherNumber("F")).thenReturn("F-1");
    Order invoice = testInvoice(1000);

    accountingService.createInvoiceEntries(invoice);

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1510");
      assertThat(entry.getDebit()).isEqualTo(1250);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("3041");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1000);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2611");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(250);
    });
  }

  @Test
  void invoiceMethodDoesNotDuplicateExistingInvoiceBooking() {
    Order invoice = testInvoice(1000);
    when(journalEntryRepository.findByInvoice(invoice)).thenReturn(List.of(
        new JournalEntry(invoice, new Account("1510", "Kundfordringar"), "F-1", 1250, 0, "Invoice created", invoice.getInvoiceDate())
    ));

    accountingService.createInvoiceEntries(invoice);

    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
  }

  @Test
  void cashMethodDoesNotBookInvoiceWhenCreated() {
    when(settingsService.getSettings()).thenReturn(settingsWithAccountingMethod("CASH_METHOD"));
    Order invoice = testInvoice(1000);

    accountingService.createInvoiceEntries(invoice);

    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
  }

  @Test
  void cashMethodBooksSaleAndVatWhenInvoiceIsPaid() {
    when(settingsService.getSettings()).thenReturn(settingsWithAccountingMethod("CASH_METHOD"));
    when(voucherNumberService.nextVoucherNumber("B")).thenReturn("B-1");
    Order invoice = testInvoice(1000);
    invoice.setStatus("SENT");

    accountingService.createPaymentEntries(invoice, invoice.getInvoiceDate(), 625);

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isEqualTo(625);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("3041");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(500);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2611");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(125);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("B-1");
      assertThat(entry.getVoucherDate()).isEqualTo(invoice.getInvoiceDate());
    });
  }

  @Test
  void cashMethodFinalPartialPaymentBooksRemainingVatRoundingDifference() {
    when(settingsService.getSettings()).thenReturn(settingsWithAccountingMethod("CASH_METHOD"));
    when(voucherNumberService.nextVoucherNumber("B")).thenReturn("B-2");
    Order invoice = testInvoice(799);
    invoice.setStatus("SENT");
    invoice.registerPayment(invoice.getInvoiceDate(), 666, "Bank first payments");

    accountingService.createPaymentEntries(invoice, invoice.getInvoiceDate(), 333);

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(invoice.getTotalAmount()).isEqualTo(999);
    assertThat(invoice.getVatAmount()).isEqualTo(200);
    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isEqualTo(333);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("3041");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(266);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2611");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(67);
    });
  }

  @Test
  void invoiceMethodBooksCustomerRefundAgainstReceivablesAndBank() {
    when(voucherNumberService.nextVoucherNumber("AR")).thenReturn("AR-1");
    Order invoice = testInvoice(1000);
    invoice.registerPayment(invoice.getInvoiceDate(), 1250, "Bank");
    invoice.setStatus("CREDITED");

    accountingService.createRefundEntries(invoice, invoice.getInvoiceDate(), 1250);

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(2);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1510");
      assertThat(entry.getDebit()).isEqualTo(1250);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1250);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("AR-1");
      assertThat(entry.getVoucherDate()).isEqualTo(invoice.getInvoiceDate());
    });
  }

  @Test
  void cashMethodBooksCustomerRefundAgainstSalesVatAndBank() {
    when(settingsService.getSettings()).thenReturn(settingsWithAccountingMethod("CASH_METHOD"));
    when(voucherNumberService.nextVoucherNumber("AR")).thenReturn("AR-1");
    Order invoice = testInvoice(1000);
    invoice.registerPayment(invoice.getInvoiceDate(), 1250, "Bank");
    invoice.setStatus("CREDITED");

    accountingService.createRefundEntries(invoice, invoice.getInvoiceDate(), 1250);

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("3041");
      assertThat(entry.getDebit()).isEqualTo(1000);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2611");
      assertThat(entry.getDebit()).isEqualTo(250);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1250);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("AR-1");
      assertThat(entry.getVoucherDate()).isEqualTo(invoice.getInvoiceDate());
    });
  }

  @Test
  void createsVatReportForSelectedPeriodUsingNetVatAccounts() {
    Account outputVat = new Account("2611", "Utgaende moms");
    Account inputVat = new Account("2641", "Ingaende moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, outputVat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, inputVat, "K-1", 100, 0, "Expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, outputVat, "F-OLD", 0, 500, "Old invoice", LocalDate.of(2026, 6, 30))
    ));

    VatReport report = accountingService.createVatReport(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

    assertThat(report.outputVat()).isEqualTo(250);
    assertThat(report.inputVat()).isEqualTo(100);
    assertThat(report.vatToPay()).isEqualTo(150);
    assertThat(report.settled()).isFalse();
  }

  @Test
  void createsVatReportWithOriginalVatAmountsAfterSettlementVoucher() {
    Account outputVat = new Account("2611", "Utgaende moms");
    Account inputVat = new Account("2641", "Ingaende moms");
    Account vatPayable = new Account("2650", "Redovisningskonto for moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, outputVat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, inputVat, "K-1", 100, 0, "Expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, outputVat, "MOMS-1", 250, 0, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 7, 31)),
        new JournalEntry(null, inputVat, "MOMS-1", 0, 100, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 7, 31)),
        new JournalEntry(null, vatPayable, "MOMS-1", 0, 150, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 7, 31))
    ));

    VatReport report = accountingService.createVatReport(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

    assertThat(report.outputVat()).isEqualTo(250);
    assertThat(report.inputVat()).isEqualTo(100);
    assertThat(report.vatToPay()).isEqualTo(150);
    assertThat(report.settled()).isTrue();
  }

  @Test
  void createsJournalIntegrityReportForSelectedPeriod() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "M-1", 1250, 0, "Manual booking", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "M-1", 0, 1250, "Manual booking", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "OLD-1", 1, 0, "Old booking", LocalDate.of(2026, 6, 30))
    ));

    JournalIntegrityReport report = accountingService.createJournalIntegrityReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.entryCount()).isEqualTo(2);
    assertThat(report.voucherCount()).isEqualTo(1);
    assertThat(report.totalDebit()).isEqualTo(1250);
    assertThat(report.totalCredit()).isEqualTo(1250);
    assertThat(report.difference()).isZero();
    assertThat(report.periodFingerprint()).hasSize(64).matches("[0-9A-F]+");
    assertThat(report.finalChainHash()).hasSize(64).matches("[0-9A-F]+");
    assertThat(report.lines()).hasSize(2);
    assertThat(report.lines().get(1).previousChainHash()).isEqualTo(report.lines().get(0).chainHash());
  }

  @Test
  void createsAccountSignControlReportForSelectedPeriod() {
    Account bank = new Account("1930", "Foretagskonto");
    Account employeeTax = new Account("2710", "Personalskatt");
    Account stripeReceivable = new Account("1580", "Stripe-fordran");
    Account sales = new Account("3041", "Forsaljning");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, employeeTax, "M-1", 500, 0, "Wrong employee tax sign", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "M-1", 0, 500, "Wrong bank sign", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "M-2", 100, 0, "Stripe sign offset", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, stripeReceivable, "M-2", 0, 100, "Wrong Stripe sign", LocalDate.of(2026, 7, 11))
    ));

    AccountSignControlReport report = accountingService.createAccountSignControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.accountCount()).isEqualTo(12);
    assertThat(report.criticalIssueCount()).isEqualTo(2);
    assertThat(report.warningIssueCount()).isEqualTo(1);
    assertThat(report.lines())
        .anySatisfy(line -> {
          assertThat(line.accountNumber()).isEqualTo("1930");
          assertThat(line.status()).isEqualTo("critical");
          assertThat(line.balance()).isEqualTo(-500);
          assertThat(line.blocking()).isTrue();
        })
        .anySatisfy(line -> {
          assertThat(line.accountNumber()).isEqualTo("2710");
          assertThat(line.status()).isEqualTo("critical");
          assertThat(line.balance()).isEqualTo(-500);
        })
        .anySatisfy(line -> {
          assertThat(line.accountNumber()).isEqualTo("1580");
          assertThat(line.status()).isEqualTo("warning");
          assertThat(line.balance()).isEqualTo(-100);
          assertThat(line.blocking()).isFalse();
        });
  }

  @Test
  void accountSignControlUsesClosingBalanceThroughPeriodEnd() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, sales, "M-1", 700, 0, "Opening sign offset", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, bank, "M-1", 0, 700, "Negative bank before period", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, bank, "M-2", 700, 0, "Future bank correction", LocalDate.of(2026, 8, 1)),
        new JournalEntry(null, sales, "M-2", 0, 700, "Future sign offset", LocalDate.of(2026, 8, 1))
    ));

    AccountSignControlReport report = accountingService.createAccountSignControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.criticalIssueCount()).isEqualTo(1);
    assertThat(report.lines())
        .anySatisfy(line -> {
          assertThat(line.accountNumber()).isEqualTo("1930");
          assertThat(line.status()).isEqualTo("critical");
          assertThat(line.balance()).isEqualTo(-700);
          assertThat(line.message()).contains("ovantat minussaldo");
        });
  }

  @Test
  void accountSignControlBlocksSupplierDebtWithUnexpectedDebitBalance() {
    Account supplierDebt = new Account("2440", "Leverantorsskulder");
    Account expense = new Account("5420", "Programvaror");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, supplierDebt, "L-1", 900, 0, "Wrong supplier debt sign", LocalDate.of(2026, 7, 20)),
        new JournalEntry(null, expense, "L-1", 0, 900, "Supplier debt offset", LocalDate.of(2026, 7, 20))
    ));

    AccountSignControlReport report = accountingService.createAccountSignControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.criticalIssueCount()).isEqualTo(1);
    assertThat(report.lines())
        .anySatisfy(line -> {
          assertThat(line.accountNumber()).isEqualTo("2440");
          assertThat(line.expectedNature()).isEqualTo("credit");
          assertThat(line.status()).isEqualTo("critical");
          assertThat(line.blocking()).isTrue();
          assertThat(line.balance()).isEqualTo(-900);
        });
  }

  @Test
  void profitAndLossIncludesPayrollAndFinancialAccounts() {
    Account sales = new Account("3041", "Forsaljning");
    Account payroll = new Account("7010", "Lon till anstallda");
    Account yearEnd = new Account("8999", "Arets resultat");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, payroll, "L-1", 300, 0, "Payroll", LocalDate.of(2026, 7, 25)),
        new JournalEntry(null, yearEnd, "BR-1", 700, 0, "Annual result 2026", LocalDate.of(2026, 12, 31))
    ));

    ProfitAndLossReport report = accountingService.createProfitAndLossReport(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 12, 31)
    );

    assertThat(report.totalRevenue()).isEqualTo(1000);
    assertThat(report.totalExpenses()).isEqualTo(1000);
    assertThat(report.result()).isZero();
  }

  @Test
  void createsAnnualResultVoucherForSoleTraderProfit() {
    Account sales = new Account("3041", "Forsaljning");
    Account software = new Account("5420", "Programvaror");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, software, "K-1", 400, 0, "Software", LocalDate.of(2026, 7, 11))
    ));
    when(voucherNumberService.nextVoucherNumber("BR")).thenReturn("BR-1");

    List<JournalEntry> entries = accountingService.createAnnualResultVoucher(new CreateAnnualResultVoucherRequest(
        2026,
        LocalDate.of(2026, 12, 31)
    ));

    assertThat(entries).hasSize(2);
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("BR-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 12, 31));
      assertThat(entry.getDescription()).isEqualTo("Annual result 2026");
      assertThat(entry.getSourceType()).isEqualTo("annual_result");
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("8999");
      assertThat(entry.getDebit()).isEqualTo(600);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2019");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(600);
    });
  }

  @Test
  void rejectsDuplicateAnnualResultVoucherForSameYear() {
    Account sales = new Account("3041", "Forsaljning");
    Account resultAccount = new Account("2019", "Arets resultat");
    Account yearEnd = new Account("8999", "Arets resultat");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, yearEnd, "BR-1", 1000, 0, "Annual result 2026", LocalDate.of(2026, 12, 31)),
        new JournalEntry(null, resultAccount, "BR-1", 0, 1000, "Annual result 2026", LocalDate.of(2026, 12, 31))
    ));

    assertThatThrownBy(() -> accountingService.createAnnualResultVoucher(new CreateAnnualResultVoucherRequest(
        2026,
        LocalDate.of(2026, 12, 31)
    )))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("Annual result voucher already exists");

    verify(voucherNumberService, never()).nextVoucherNumber("BR");
  }

  @Test
  void createsVatControlReportAndFindsVatDifferences() {
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account software = new Account("5420", "Programvaror");
    Account outputVat = new Account("2611", "Utgaende moms");
    Account inputVat = new Account("2641", "Ingaende moms");
    Account vatPayable = new Account("2650", "Redovisningskonto for moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, outputVat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-2", 0, 1000, "Invoice wrong VAT", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, outputVat, "F-2", 0, 100, "Invoice wrong VAT", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, software, "K-1", 800, 0, "Software", LocalDate.of(2026, 7, 12)),
        new JournalEntry(null, inputVat, "K-1", 300, 0, "Software", LocalDate.of(2026, 7, 12)),
        new JournalEntry(null, outputVat, "MOMS-1", 250, 0, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 8, 12)),
        new JournalEntry(null, vatPayable, "MOMS-1", 0, 250, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 8, 12))
    ));

    VatControlReport report = accountingService.createVatControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.totalSalesNet()).isEqualTo(2000);
    assertThat(report.totalOutputVat()).isEqualTo(350);
    assertThat(report.expectedOutputVat()).isEqualTo(500);
    assertThat(report.outputVatDifference()).isEqualTo(-150);
    assertThat(report.totalPurchaseNet()).isEqualTo(800);
    assertThat(report.totalInputVat()).isEqualTo(300);
    assertThat(report.expectedMaxInputVat()).isEqualTo(200);
    assertThat(report.criticalIssueCount()).isEqualTo(2);
    assertThat(report.warningIssueCount()).isZero();
    assertThat(report.issues()).extracting(VatControlIssue::issueType)
        .contains("output_vat_difference", "input_vat_too_high");
  }

  @Test
  void vatControlWarnsWhenPurchaseIsBookedWithoutInputVat() {
    Account bank = new Account("1930", "Foretagskonto");
    Account software = new Account("5420", "Programvaror");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, software, "K-10", 1000, 0, "Software subscription", LocalDate.of(2026, 7, 14)),
        new JournalEntry(null, bank, "K-10", 0, 1000, "Software subscription", LocalDate.of(2026, 7, 14))
    ));

    VatControlReport report = accountingService.createVatControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.criticalIssueCount()).isZero();
    assertThat(report.warningIssueCount()).isEqualTo(1);
    assertThat(report.totalPurchaseNet()).isEqualTo(1000);
    assertThat(report.totalInputVat()).isZero();
    assertThat(report.expectedMaxInputVat()).isEqualTo(250);
    assertThat(report.issues()).singleElement()
        .satisfies(issue -> {
          assertThat(issue.issueType()).isEqualTo("purchase_without_input_vat");
          assertThat(issue.voucherNumber()).isEqualTo("K-10");
          assertThat(issue.difference()).isEqualTo(-250);
        });
  }

  @Test
  void createsVatSettlementVoucherForVatToPay() {
    when(voucherNumberService.nextVoucherNumber("MOMS")).thenReturn("MOMS-1");
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account software = new Account("5420", "Programvaror");
    Account outputVat = new Account("2611", "Utgaende moms");
    Account inputVat = new Account("2641", "Ingaende moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1250, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, outputVat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, software, "K-1", 400, 0, "Expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, inputVat, "K-1", 100, 0, "Expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, bank, "K-1", 0, 500, "Expense", LocalDate.of(2026, 7, 11))
    ));

    List<JournalEntry> entries = accountingService.createVatSettlementEntry(new CreateVatSettlementRequest(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        LocalDate.of(2026, 8, 12)
    ));

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2611");
      assertThat(entry.getDebit()).isEqualTo(250);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2641");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(100);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2650");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(150);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("MOMS-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 8, 12));
    });
  }

  @Test
  void createsVatSettlementVoucherForVatToReclaim() {
    when(voucherNumberService.nextVoucherNumber("MOMS")).thenReturn("MOMS-2");
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account software = new Account("5420", "Programvaror");
    Account outputVat = new Account("2611", "Utgaende moms");
    Account inputVat = new Account("2641", "Ingaende moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 500, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 400, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, outputVat, "F-1", 0, 100, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, software, "K-1", 1000, 0, "Expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, inputVat, "K-1", 250, 0, "Expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, bank, "K-1", 0, 1250, "Expense", LocalDate.of(2026, 7, 11))
    ));

    List<JournalEntry> entries = accountingService.createVatSettlementEntry(new CreateVatSettlementRequest(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        LocalDate.of(2026, 8, 12)
    ));

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2611");
      assertThat(entry.getDebit()).isEqualTo(100);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2641");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(250);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1650");
      assertThat(entry.getDebit()).isEqualTo(150);
      assertThat(entry.getCredit()).isZero();
    });
  }

  @Test
  void createsVatPaymentVoucherForPaidVatFiling() {
    when(voucherNumberService.nextVoucherNumber("MOMS")).thenReturn("MOMS-PAY-1");
    Account settlement = new Account("2650", "Redovisningskonto for moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, settlement, "MOMS-1", 0, 150, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 7, 31))
    ));
    VatFiling filing = new VatFiling(
        new VatReport(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 250, 100, 150, true),
        new CreateVatFilingRequest(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "SUBMITTED", "SKV-1", "", null, ""),
        "SUBMITTED"
    );

    List<JournalEntry> entries = accountingService.createVatFilingPaymentEntry(
        filing,
        LocalDate.of(2026, 8, 12),
        "BANK-REF"
    );

    assertThat(entries).hasSize(4);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2650");
      assertThat(entry.getDebit()).isEqualTo(150);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2012");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(150);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2012");
      assertThat(entry.getDebit()).isEqualTo(150);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(150);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("MOMS-PAY-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 8, 12));
      assertThat(entry.getDescription()).contains("BANK-REF");
      assertThat(entry.getSourceType()).isEqualTo("vat_payment");
    });
  }

  @Test
  void createsVatPaymentVoucherThroughTaxAccountForLimitedCompany() {
    AppSettings settings = AppSettings.defaults();
    settings.setCompanyType("LIMITED_COMPANY");
    when(settingsService.getSettings()).thenReturn(settings);
    when(voucherNumberService.nextVoucherNumber("MOMS")).thenReturn("MOMS-PAY-AB-1");
    Account settlement = new Account("2650", "Redovisningskonto for moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, settlement, "MOMS-1", 0, 150, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 7, 31))
    ));
    VatFiling filing = new VatFiling(
        new VatReport(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 250, 100, 150, true),
        new CreateVatFilingRequest(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "SUBMITTED", "SKV-1", "", null, ""),
        "SUBMITTED"
    );

    List<JournalEntry> entries = accountingService.createVatFilingPaymentEntry(
        filing,
        LocalDate.of(2026, 8, 12),
        "SKV-AB"
    );

    assertThat(entries).hasSize(4);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1630");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(150);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1630");
      assertThat(entry.getDebit()).isEqualTo(150);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).allSatisfy(entry -> assertThat(entry.getVoucherNumber()).isEqualTo("MOMS-PAY-AB-1"));
  }

  @Test
  void createsVatFilingProofReportForPaidPeriod() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    Account outputVat = new Account("2611", "Utgaende moms");
    Account inputVat = new Account("2641", "Ingaende moms");
    Account vatPayable = new Account("2650", "Redovisningskonto for moms");
    Account bank = new Account("1930", "Foretagskonto");
    VatFiling paidFiling = new VatFiling(
        new VatReport(periodFrom, periodTo, 250, 100, 150, true),
        new CreateVatFilingRequest(periodFrom, periodTo, "PAID", "SKV-1", "BANK-1", null, ""),
        "PAID"
    );
    when(vatFilingRepository.findByPeriodFromAndPeriodTo(periodFrom, periodTo))
        .thenReturn(Optional.of(paidFiling));
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, outputVat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, inputVat, "K-1", 100, 0, "Expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, outputVat, "MOMS-1", 250, 0, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 7, 31)),
        new JournalEntry(null, inputVat, "MOMS-1", 0, 100, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 7, 31)),
        new JournalEntry(null, vatPayable, "MOMS-1", 0, 150, "VAT settlement 2026-07-01 - 2026-07-31", LocalDate.of(2026, 7, 31)),
        new JournalEntry(null, vatPayable, "MOMS-2", 150, 0, "VAT payment 2026-07-01 - 2026-07-31 BANK-1", LocalDate.of(2026, 8, 12)),
        new JournalEntry(null, bank, "MOMS-2", 0, 150, "VAT payment 2026-07-01 - 2026-07-31 BANK-1", LocalDate.of(2026, 8, 12))
    ));

    VatFilingProofReport report = accountingService.createVatFilingProofReport(periodFrom, periodTo);

    assertThat(report.filingStatus()).isEqualTo("PAID");
    assertThat(report.expectedVatAmount()).isEqualTo(150);
    assertThat(report.settlementVoucherFound()).isTrue();
    assertThat(report.paymentVoucherRequired()).isTrue();
    assertThat(report.paymentVoucherFound()).isTrue();
    assertThat(report.completeForCurrentStatus()).isTrue();
    assertThat(report.settlementVouchers()).singleElement()
        .satisfies(voucher -> assertThat(voucher.voucherNumber()).isEqualTo("MOMS-1"));
    assertThat(report.paymentVouchers()).singleElement()
        .satisfies(voucher -> assertThat(voucher.voucherNumber()).isEqualTo("MOMS-2"));
  }

  @Test
  void rejectsVatSettlementWhenCriticalVatControlIssuesRemain() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account outputVat = new Account("2611", "Utgaende moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1100, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, outputVat, "F-1", 0, 100, "Invoice", LocalDate.of(2026, 7, 10))
    ));

    assertThatThrownBy(() -> accountingService.createVatSettlementEntry(new CreateVatSettlementRequest(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        LocalDate.of(2026, 8, 12)
    )))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("critical VAT control issues");

    verify(voucherNumberService, never()).nextVoucherNumber("MOMS");
  }

  @Test
  void rejectsVatSettlementWhenCriticalVoucherControlIssuesRemain() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account outputVat = new Account("2611", "Utgaende moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1000, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, outputVat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10))
    ));

    assertThatThrownBy(() -> accountingService.createVatSettlementEntry(new CreateVatSettlementRequest(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        LocalDate.of(2026, 8, 12)
    )))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("critical voucher control issues");

    verify(voucherNumberService, never()).nextVoucherNumber("MOMS");
  }

  @Test
  void createsTrialBalanceForSelectedPeriod() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account vat = new Account("2611", "Utgaende moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "B-OLD", 500, 0, "Opening bank", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, sales, "F-OLD", 0, 400, "Opening sale", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, vat, "F-OLD", 0, 100, "Opening VAT", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, bank, "B-1", 1250, 0, "Payment", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Sale", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, vat, "F-1", 0, 250, "VAT", LocalDate.of(2026, 7, 10))
    ));

    TrialBalanceReport report = accountingService.createTrialBalanceReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.openingDebitTotal()).isEqualTo(500);
    assertThat(report.openingCreditTotal()).isEqualTo(500);
    assertThat(report.periodDebitTotal()).isEqualTo(1250);
    assertThat(report.periodCreditTotal()).isEqualTo(1250);
    assertThat(report.closingDebitTotal()).isEqualTo(1750);
    assertThat(report.closingCreditTotal()).isEqualTo(1750);
    assertThat(report.difference()).isZero();
    assertThat(report.lines()).anySatisfy(line -> {
      assertThat(line.accountNumber()).isEqualTo("1930");
      assertThat(line.openingDebit()).isEqualTo(500);
      assertThat(line.periodDebit()).isEqualTo(1250);
      assertThat(line.closingDebit()).isEqualTo(1750);
    });
  }

  @Test
  void createsProfitAndLossForSelectedPeriodOnly() {
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account software = new Account("5420", "Programvaror");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, sales, "F-OLD", 0, 500, "Old sale", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Sale", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, software, "K-1", 300, 0, "Expense", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, software, "K-FUTURE", 700, 0, "Future expense", LocalDate.of(2026, 8, 1))
    ));

    ProfitAndLossReport report = accountingService.createProfitAndLossReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.periodFrom()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(report.periodTo()).isEqualTo(LocalDate.of(2026, 7, 31));
    assertThat(report.totalRevenue()).isEqualTo(1000);
    assertThat(report.totalExpenses()).isEqualTo(300);
    assertThat(report.result()).isEqualTo(700);
  }

  @Test
  void createsBalanceReportAsOfSelectedDate() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account vat = new Account("2611", "Utgaende moms");
    Account equity = new Account("2018", "Egna insattningar");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "E-1", 1000, 0, "Deposit", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, equity, "E-1", 0, 1000, "Deposit", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, bank, "B-1", 1250, 0, "Payment", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Sale", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, vat, "F-1", 0, 250, "VAT", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "B-FUTURE", 999, 0, "Future payment", LocalDate.of(2026, 8, 1)),
        new JournalEntry(null, sales, "F-FUTURE", 0, 999, "Future sale", LocalDate.of(2026, 8, 1))
    ));

    BalanceReport report = accountingService.createBalanceReport(LocalDate.of(2026, 7, 31));

    assertThat(report.asOfDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    assertThat(report.totalAssets()).isEqualTo(2250);
    assertThat(report.totalLiabilitiesAndEquity()).isEqualTo(2250);
    assertThat(report.difference()).isZero();
    assertThat(report.assets()).anySatisfy(line -> {
      assertThat(line.accountNumber()).isEqualTo("1930");
      assertThat(line.amount()).isEqualTo(2250);
    });
  }

  @Test
  void createsGeneralLedgerForSelectedAccountAndPeriod() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "B-OLD", 500, 0, "Opening bank", LocalDate.of(2026, 6, 30)),
        new JournalEntry(null, bank, "B-1", 1250, 0, "Payment", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "K-1", 0, 300, "Expense paid", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, bank, "B-FUTURE", 999, 0, "Future payment", LocalDate.of(2026, 8, 1)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Sale", LocalDate.of(2026, 7, 10))
    ));

    GeneralLedgerReport report = accountingService.createGeneralLedgerReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "1930"
    );

    assertThat(report.accountNumber()).isEqualTo("1930");
    assertThat(report.accountCount()).isEqualTo(1);
    assertThat(report.entryCount()).isEqualTo(2);
    assertThat(report.periodDebitTotal()).isEqualTo(1250);
    assertThat(report.periodCreditTotal()).isEqualTo(300);
    assertThat(report.accounts()).singleElement().satisfies(account -> {
      assertThat(account.accountNumber()).isEqualTo("1930");
      assertThat(account.openingBalance()).isEqualTo(500);
      assertThat(account.periodDebit()).isEqualTo(1250);
      assertThat(account.periodCredit()).isEqualTo(300);
      assertThat(account.closingBalance()).isEqualTo(1450);
      assertThat(account.entries()).extracting(GeneralLedgerEntry::balance).containsExactly(1750, 1450);
    });
  }

  @Test
  void createsSieExportForSelectedPeriod() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account vat = new Account("2611", "Utgaende moms");
    when(accountRepository.findAll()).thenReturn(List.of(bank, sales, vat));
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1250, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, vat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "F-FUTURE", 999, 0, "Future", LocalDate.of(2026, 8, 1))
    ));

    String sie = accountingService.createSieExport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(sie).contains("#SIETYP 4");
    assertThat(sie).contains("#FNAMN \"Muscle&Focus\"");
    assertThat(sie).contains("#RAR 0 20260701 20260731");
    assertThat(sie).contains("#KONTO 1930 \"Foretagskonto\"");
    assertThat(sie).contains("#VER \"A\" \"F-1\" 20260710 \"Invoice\"");
    assertThat(sie).contains("#TRANS 1930 {} 1250.00 \"Invoice\"");
    assertThat(sie).contains("#TRANS 3041 {} -1000.00 \"Invoice\"");
    assertThat(sie).doesNotContain("F-FUTURE");
  }

  @Test
  void createsSieExportReceiptForSelectedPeriod() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account vat = new Account("2611", "Utgaende moms");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1250, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, vat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "F-FUTURE", 999, 0, "Future", LocalDate.of(2026, 8, 1))
    ));

    SieExportReceipt receipt = accountingService.createSieExportReceipt(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(receipt.exportReady()).isTrue();
    assertThat(receipt.periodFrom()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(receipt.periodTo()).isEqualTo(LocalDate.of(2026, 7, 31));
    assertThat(receipt.voucherCount()).isEqualTo(1);
    assertThat(receipt.entryCount()).isEqualTo(3);
    assertThat(receipt.accountCount()).isEqualTo(3);
    assertThat(receipt.totalDebit()).isEqualTo(1250);
    assertThat(receipt.totalCredit()).isEqualTo(1250);
    assertThat(receipt.difference()).isZero();
    assertThat(receipt.criticalIssueCount()).isZero();
    assertThat(receipt.periodFingerprint()).isNotBlank();
    assertThat(receipt.exportControlHash()).isNotBlank();
  }

  @Test
  void stopsSieExportWhenCriticalVoucherIssuesRemain() {
    Account bank = new Account("1930", "Foretagskonto");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, null, 100, 100, "Missing voucher number", LocalDate.of(2026, 7, 10))
    ));

    assertThatThrownBy(() -> accountingService.createSieExport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    )).hasMessageContaining("critical voucher control issues");
  }

  @Test
  void createsAccountantPackageReportForSelectedPeriod() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account vat = new Account("2611", "Utgaende moms");
    Account equity = new Account("2018", "Egna insattningar");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "IB-1", 1000, 0, "Opening balance", LocalDate.of(2026, 1, 1)),
        new JournalEntry(null, equity, "IB-1", 0, 1000, "Opening balance", LocalDate.of(2026, 1, 1)),
        new JournalEntry(null, bank, "F-1", 1250, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, vat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10))
    ));
    when(voucherApprovalRepository.findAll()).thenReturn(List.of(
        new VoucherApproval("F-1", "approved", "Checked", "AliBooks")
    ));

    AccountantPackageReport report = accountingService.createAccountantPackageReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.companyName()).isEqualTo("Muscle&Focus");
    assertThat(report.totalRevenue()).isEqualTo(1000);
    assertThat(report.result()).isEqualTo(1000);
    assertThat(report.outputVat()).isEqualTo(250);
    assertThat(report.vatToPay()).isEqualTo(250);
    assertThat(report.balanceDifference()).isZero();
    assertThat(report.trialBalanceDifference()).isZero();
    assertThat(report.voucherCriticalIssues()).isZero();
    assertThat(report.vatCriticalIssues()).isZero();
    assertThat(report.accountSignCriticalIssues()).isZero();
    assertThat(report.accountSignWarningIssues()).isZero();
    assertThat(report.bankReconciliationDifference()).isZero();
    assertThat(report.bankReconciliationCriticalIssues()).isZero();
    assertThat(report.bankReconciliationWarningIssues()).isZero();
    assertThat(report.receivablesInvoiceCount()).isZero();
    assertThat(report.receivablesTotalOutstanding()).isZero();
    assertThat(report.receivablesOverdueOutstanding()).isZero();
    assertThat(report.receivablesDueSoonOutstanding()).isZero();
    assertThat(report.payablesInvoiceCount()).isZero();
    assertThat(report.payablesTotalOutstanding()).isZero();
    assertThat(report.payablesOverdueOutstanding()).isZero();
    assertThat(report.payablesDueSoonOutstanding()).isZero();
    assertThat(report.journalIntegrityDifference()).isZero();
    assertThat(report.journalIntegrityMissingEvidenceCount()).isZero();
    assertThat(report.journalIntegrityPeriodFingerprint()).isNotBlank();
    assertThat(report.journalIntegrityFinalChainHash()).isNotBlank();
    assertThat(report.voucherApprovedCount()).isEqualTo(1);
    assertThat(report.voucherMissingApprovalCount()).isZero();
    assertThat(report.voucherPendingApprovalCount()).isZero();
    assertThat(report.voucherBlockedApprovalCount()).isZero();
    assertThat(report.sieExportReady()).isTrue();
    assertThat(report.items()).extracting(AccountantPackageItem::area)
        .contains("Resultat och balans", "Kontotecken", "Verifikationsattest", "Bankavstamning", "Kundreskontra", "Leverantorsreskontra", "Beviskedja", "SIE", "Systemdokumentation", "Backup och underlag");
  }

  @Test
  void createsSystemDocumentationReportForAccountingFlows() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    Account vat = new Account("2611", "Utgaende moms");
    when(accountRepository.findAll()).thenReturn(List.of(bank, sales, vat));
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1250, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1000, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, vat, "F-1", 0, 250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "B-1", 1250, 0, "Invoice paid", LocalDate.of(2026, 8, 3))
    ));

    SystemDocumentationReport report = accountingService.createSystemDocumentationReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.companyName()).isEqualTo("Muscle&Focus");
    assertThat(report.accountCount()).isEqualTo(3);
    assertThat(report.voucherSeries()).containsExactly("F");
    assertThat(report.automationCount()).isGreaterThanOrEqualTo(4);
    assertThat(report.controlCount()).isGreaterThanOrEqualTo(2);
    assertThat(report.items()).extracting(SystemDocumentationItem::title)
        .contains("Fakturering", "Kostnader och kvitton", "Rattelse i stallet for radering", "Arsarkiv och verifikationsattest", "Grundbok, huvudbok, rapporter och SIE");
  }

  @Test
  void createsVoucherControlReportWithGapAndUnbalancedVoucher() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1250, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "F-3", 625, 0, "Invoice", LocalDate.of(2026, 7, 12)),
        new JournalEntry(null, sales, "F-3", 0, 625, "Invoice", LocalDate.of(2026, 7, 12)),
        new JournalEntry(null, bank, "M-1", 100, 0, "Manual mistake", LocalDate.of(2026, 7, 13)),
        new JournalEntry(null, sales, "BAD", 0, 100, "Bad number", LocalDate.of(2026, 7, 14)),
        new JournalEntry(null, bank, "F-4", 200, 0, "Outside period", LocalDate.of(2026, 8, 1))
    ));

    VoucherControlReport report = accountingService.createVoucherControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.voucherCount()).isEqualTo(4);
    assertThat(report.entryCount()).isEqualTo(6);
    assertThat(report.balancedVoucherCount()).isEqualTo(2);
    assertThat(report.unbalancedVoucherCount()).isEqualTo(2);
    assertThat(report.gapCount()).isEqualTo(1);
    assertThat(report.dateOrderIssueCount()).isZero();
    assertThat(report.invalidVoucherNumberCount()).isEqualTo(1);
    assertThat(report.criticalIssueCount()).isEqualTo(2);
    assertThat(report.warningIssueCount()).isEqualTo(3);
    assertThat(report.issues()).extracting(VoucherControlIssue::issueType)
        .contains("unbalanced_voucher", "voucher_gap", "invalid_voucher_number", "evidence_needs_review");
  }

  @Test
  void createsVoucherControlReportWithDateOrderWarning() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning tjanster 25 procent");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "F-1", 1250, 0, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "F-1", 0, 1250, "Invoice", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, bank, "F-2", 625, 0, "Invoice", LocalDate.of(2026, 7, 8)),
        new JournalEntry(null, sales, "F-2", 0, 625, "Invoice", LocalDate.of(2026, 7, 8))
    ));

    VoucherControlReport report = accountingService.createVoucherControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.dateOrderIssueCount()).isEqualTo(1);
    assertThat(report.criticalIssueCount()).isZero();
    assertThat(report.warningIssueCount()).isEqualTo(1);
    assertThat(report.issues()).anySatisfy(issue -> {
      assertThat(issue.issueType()).isEqualTo("voucher_date_order");
      assertThat(issue.voucherNumber()).isEqualTo("F-2");
      assertThat(issue.expectedNumber()).isEqualTo(1);
      assertThat(issue.actualNumber()).isEqualTo(2);
      assertThat(issue.message()).contains("F-1");
    });
  }

  @Test
  void voucherControlReportsMissingReceiptEvidenceAndReceiptHash() {
    Account expenseAccount = new Account("5420", "Programvaror");
    Account bank = new Account("1930", "Foretagskonto");
    Expense missingReceipt = new Expense(LocalDate.of(2026, 7, 10), "Adobe", 800, 200, "5420", "1930");
    Expense receiptWithoutHash = new Expense(LocalDate.of(2026, 7, 11), "Train", 200, 50, "5800", "1930");
    receiptWithoutHash.setReceipt("train.pdf", "application/pdf", "uploads/receipts/train.pdf");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, missingReceipt, expenseAccount, "K-1", 800, 0, "Adobe", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, missingReceipt, bank, "K-1", 0, 800, "Adobe", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, receiptWithoutHash, expenseAccount, "K-2", 200, 0, "Train", LocalDate.of(2026, 7, 11)),
        new JournalEntry(null, receiptWithoutHash, bank, "K-2", 0, 200, "Train", LocalDate.of(2026, 7, 11))
    ));

    VoucherControlReport report = accountingService.createVoucherControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.criticalIssueCount()).isEqualTo(1);
    assertThat(report.warningIssueCount()).isEqualTo(1);
    assertThat(report.issues()).anySatisfy(issue -> {
      assertThat(issue.issueType()).isEqualTo("evidence_missing_receipt");
      assertThat(issue.severity()).isEqualTo("critical");
      assertThat(issue.voucherNumber()).isEqualTo("K-1");
      assertThat(issue.message()).contains("missing receipt evidence");
    });
    assertThat(report.issues()).anySatisfy(issue -> {
      assertThat(issue.issueType()).isEqualTo("evidence_missing_receipt_hash");
      assertThat(issue.severity()).isEqualTo("warning");
      assertThat(issue.voucherNumber()).isEqualTo("K-2");
      assertThat(issue.message()).contains("SHA-256");
    });
  }

  @Test
  void voucherControlReportsSameVoucherNumberUsedForMultipleSources() {
    Account expenseAccount = new Account("5420", "Programvaror");
    Account bank = new Account("1930", "Foretagskonto");
    Expense adobe = new Expense(LocalDate.of(2026, 7, 10), "Adobe", 80, 20, "5420", "1930");
    adobe.setReceipt("adobe.pdf", "application/pdf", "uploads/receipts/adobe.pdf", "HASH-ADOBE", Instant.parse("2026-07-10T10:00:00Z"));
    Expense train = new Expense(LocalDate.of(2026, 7, 10), "Train", 160, 40, "5420", "1930");
    train.setReceipt("train.pdf", "application/pdf", "uploads/receipts/train.pdf", "HASH-TRAIN", Instant.parse("2026-07-10T11:00:00Z"));
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, adobe, expenseAccount, "K-7", 100, 0, "Adobe", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, adobe, bank, "K-7", 0, 100, "Adobe", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, train, expenseAccount, "K-7", 200, 0, "Train", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, train, bank, "K-7", 0, 200, "Train", LocalDate.of(2026, 7, 10))
    ));

    VoucherControlReport report = accountingService.createVoucherControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.voucherCount()).isEqualTo(1);
    assertThat(report.balancedVoucherCount()).isEqualTo(1);
    assertThat(report.reusedVoucherNumberCount()).isEqualTo(1);
    assertThat(report.criticalIssueCount()).isEqualTo(1);
    assertThat(report.issues()).anySatisfy(issue -> {
      assertThat(issue.issueType()).isEqualTo("voucher_number_reused_for_multiple_sources");
      assertThat(issue.voucherNumber()).isEqualTo("K-7");
      assertThat(issue.message()).contains("more than one source reference");
    });
  }

  @Test
  void voucherControlReportsJournalLineWithBothDebitAndCredit() {
    Account bank = new Account("1930", "Foretagskonto");
    Account sales = new Account("3041", "Forsaljning");
    when(journalEntryRepository.findAll()).thenReturn(List.of(
        new JournalEntry(null, bank, "M-20", 100, 100, "Incorrect mixed line", LocalDate.of(2026, 7, 10)),
        new JournalEntry(null, sales, "M-20", 0, 0, "Incorrect zero line", LocalDate.of(2026, 7, 10))
    ));

    VoucherControlReport report = accountingService.createVoucherControlReport(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31)
    );

    assertThat(report.criticalIssueCount()).isGreaterThanOrEqualTo(2);
    assertThat(report.issues()).anySatisfy(issue -> {
      assertThat(issue.issueType()).isEqualTo("journal_line_has_debit_and_credit");
      assertThat(issue.voucherNumber()).isEqualTo("M-20");
      assertThat(issue.message()).contains("both debit and credit");
    });
    assertThat(report.issues()).anySatisfy(issue -> {
      assertThat(issue.issueType()).isEqualTo("journal_line_zero_amount");
      assertThat(issue.voucherNumber()).isEqualTo("M-20");
      assertThat(issue.message()).contains("neither debit nor credit");
    });
  }

  @Test
  void createsOpeningBalanceVoucherWithIbSeries() {
    when(voucherNumberService.nextVoucherNumber("IB")).thenReturn("IB-1");

    List<JournalEntry> entries = accountingService.createOpeningBalanceEntry(new CreateOpeningBalanceRequest(
        LocalDate.of(2026, 1, 1),
        "Opening balance from Bokio",
        List.of(
            new CreateManualJournalEntryLineRequest("1930", 1000, 0),
            new CreateManualJournalEntryLineRequest("2018", 0, 1000)
        )
    ));

    assertThat(entries).hasSize(2);
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("IB-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 1, 1));
      assertThat(entry.getDescription()).isEqualTo("Opening balance from Bokio");
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isEqualTo(1000);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2018");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1000);
    });
  }

  @Test
  void rejectsManualMultiLineVoucherWithZeroLineBeforeSaving() {
    assertThatThrownBy(() -> accountingService.createManualMultiLineEntry(new CreateManualMultiLineJournalEntryRequest(
        LocalDate.of(2026, 7, 1),
        "Manual correction",
        List.of(
            new CreateManualJournalEntryLineRequest("1930", 1000, 0),
            new CreateManualJournalEntryLineRequest("3041", 0, 1000),
            new CreateManualJournalEntryLineRequest("2611", 0, 0)
        )
    )))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("line 3 needs either debit or credit");

    verify(voucherNumberService, never()).nextVoucherNumber("M");
    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
  }

  @Test
  void rejectsOpeningBalanceLineWithBothDebitAndCreditBeforeSaving() {
    assertThatThrownBy(() -> accountingService.createOpeningBalanceEntry(new CreateOpeningBalanceRequest(
        LocalDate.of(2026, 1, 1),
        "Opening balance from old system",
        List.of(
            new CreateManualJournalEntryLineRequest("1930", 1000, 100),
            new CreateManualJournalEntryLineRequest("2018", 0, 900)
        )
    )))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("line 1 cannot have both debit and credit");

    verify(voucherNumberService, never()).nextVoucherNumber("IB");
    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
  }

  @Test
  void createsJournalEntriesForStripeWebsiteSale() {
    when(voucherNumberService.nextVoucherNumber("S")).thenReturn("S-1");

    accountingService.createStripeExternalSaleEntries(1250, "pi_test_123", LocalDate.of(2026, 6, 29));

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1580");
      assertThat(entry.getDebit()).isEqualTo(1250);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("3041");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1000);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2611");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(250);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("S-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 6, 29));
      assertThat(entry.getDescription()).contains("pi_test_123");
    });
  }

  @Test
  void rejectsInvoicePaymentBeforeInvoiceDate() {
    Order invoice = testInvoice(1000);

    assertThatThrownBy(() -> accountingService.createPaymentEntries(
        invoice,
        invoice.getInvoiceDate().minusDays(1),
        1250
    ))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("Payment date cannot be before invoice date");
  }

  @Test
  void createsJournalEntriesForStripePayout() {
    when(voucherNumberService.nextVoucherNumber("SU")).thenReturn("SU-1");

    List<JournalEntry> entries = accountingService.createStripePayoutEntry(new CreateStripePayoutRequest(
        LocalDate.of(2026, 6, 30),
        1250,
        39,
        "po_test_123"
    ));

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isEqualTo(1211);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("6570");
      assertThat(entry.getDebit()).isEqualTo(39);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1580");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1250);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("SU-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 6, 30));
      assertThat(entry.getDescription()).contains("po_test_123");
    });
  }

  @Test
  void stripePayoutWithoutFeeDoesNotCreateZeroAmountFeeLine() {
    when(voucherNumberService.nextVoucherNumber("SU")).thenReturn("SU-2");

    List<JournalEntry> entries = accountingService.createStripePayoutEntry(new CreateStripePayoutRequest(
        LocalDate.of(2026, 7, 1),
        999,
        0,
        "po_no_fee"
    ));

    assertThat(entries).hasSize(2);
    assertThat(entries).noneSatisfy(entry -> assertThat(entry.getAccountNumber()).isEqualTo("6570"));
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getDebit() + entry.getCredit()).isGreaterThan(0);
      assertThat(entry.getVoucherNumber()).isEqualTo("SU-2");
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isEqualTo(999);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1580");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(999);
    });
  }

  @Test
  void rejectsStripePayoutWhenFeeWouldMakeBankLineZero() {
    assertThatThrownBy(() -> accountingService.createStripePayoutEntry(new CreateStripePayoutRequest(
        LocalDate.of(2026, 7, 1),
        999,
        999,
        "po_zero_bank"
    )))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("Stripe fee must be less than gross amount");

    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
  }

  @Test
  void createsExpenseEntriesLinkedToExpenseEvidence() {
    when(voucherNumberService.nextVoucherNumber("K")).thenReturn("K-1");
    Expense expense = new Expense(LocalDate.of(2026, 7, 22), "Adobe", 800, 200, "5420", "1930");

    accountingService.createExpenseEntries(expense);

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getExpense()).isSameAs(expense);
      assertThat(entry.getVoucherNumber()).isEqualTo("K-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 7, 22));
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("5420");
      assertThat(entry.getDebit()).isEqualTo(800);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2641");
      assertThat(entry.getDebit()).isEqualTo(200);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1000);
    });
  }

  @Test
  void createsSupplierInvoiceEntriesAgainstPayables() {
    when(voucherNumberService.nextVoucherNumber("L")).thenReturn("L-1");
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 22),
        LocalDate.of(2026, 8, 21),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );

    accountingService.createSupplierInvoiceEntries(invoice);

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getSupplierInvoice()).isSameAs(invoice);
      assertThat(entry.getVoucherNumber()).isEqualTo("L-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 7, 22));
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("5420");
      assertThat(entry.getDebit()).isEqualTo(1000);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2641");
      assertThat(entry.getDebit()).isEqualTo(250);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2440");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1250);
    });
  }

  @Test
  void cashMethodDoesNotBookSupplierInvoiceWhenCreated() {
    when(settingsService.getSettings()).thenReturn(settingsWithAccountingMethod("CASH_METHOD"));
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 22),
        LocalDate.of(2026, 8, 21),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );

    accountingService.createSupplierInvoiceEntries(invoice);

    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
  }

  @Test
  void createsSupplierInvoicePaymentEntriesAgainstBank() {
    when(voucherNumberService.nextVoucherNumber("LB")).thenReturn("LB-1");
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 22),
        LocalDate.of(2026, 8, 21),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );

    accountingService.createSupplierInvoicePaymentEntries(invoice, LocalDate.of(2026, 8, 1), 100, "BANK-1");

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(2);
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getSupplierInvoice()).isSameAs(invoice);
      assertThat(entry.getVoucherNumber()).isEqualTo("LB-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2440");
      assertThat(entry.getDebit()).isEqualTo(100);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(100);
      assertThat(entry.getDescription()).contains("BANK-1");
    });
  }

  @Test
  void cashMethodBooksSupplierInvoiceExpenseVatAndBankWhenPaid() {
    when(settingsService.getSettings()).thenReturn(settingsWithAccountingMethod("CASH_METHOD"));
    when(voucherNumberService.nextVoucherNumber("LB")).thenReturn("LB-1");
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 22),
        LocalDate.of(2026, 8, 21),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );

    accountingService.createSupplierInvoicePaymentEntries(invoice, LocalDate.of(2026, 8, 1), 625, "BANK-1");

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("5420");
      assertThat(entry.getDebit()).isEqualTo(500);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2641");
      assertThat(entry.getDebit()).isEqualTo(125);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(625);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getSupplierInvoice()).isSameAs(invoice);
      assertThat(entry.getVoucherNumber()).isEqualTo("LB-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 8, 1));
      assertThat(entry.getDescription()).contains("cash method");
    });
  }

  @Test
  void cashMethodFinalSupplierPartialPaymentBooksRemainingInputVatRoundingDifference() {
    when(settingsService.getSettings()).thenReturn(settingsWithAccountingMethod("CASH_METHOD"));
    when(voucherNumberService.nextVoucherNumber("LB")).thenReturn("LB-3");
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 22),
        LocalDate.of(2026, 8, 21),
        "Adobe Creative Cloud",
        "OCR-123",
        999,
        200,
        "5420"
    );
    invoice.registerPayment(LocalDate.of(2026, 8, 1), 666, "BANK-1");

    accountingService.createSupplierInvoicePaymentEntries(invoice, LocalDate.of(2026, 8, 2), 333, "BANK-2");

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("5420");
      assertThat(entry.getDebit()).isEqualTo(266);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2641");
      assertThat(entry.getDebit()).isEqualTo(67);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(333);
    });
  }

  @Test
  void supplierInvoicePaymentCannotBeGreaterThanRemainingAmount() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 22),
        LocalDate.of(2026, 8, 21),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );

    assertThatThrownBy(() -> accountingService.createSupplierInvoicePaymentEntries(invoice, LocalDate.of(2026, 8, 1), 1300, "BANK-1"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("cannot be greater than remaining amount");

    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
  }

  @Test
  void supplierInvoicePaymentWithDuplicateReferenceIsNotBookedTwice() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 22),
        LocalDate.of(2026, 8, 21),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );
    JournalEntry existingPayment = new JournalEntry(
        null,
        null,
        invoice,
        new Account("2440", "Leverantorsskulder"),
        "LB-1",
        100,
        0,
        "Supplier invoice paid: Adobe Creative Cloud BANK-1",
        LocalDate.of(2026, 8, 1)
    );
    when(journalEntryRepository.findBySupplierInvoice(invoice)).thenReturn(List.of(existingPayment));

    accountingService.createSupplierInvoicePaymentEntries(invoice, LocalDate.of(2026, 8, 1), 100, "BANK-1");

    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
  }

  @Test
  void supplierInvoicePaymentWithoutReferenceStillCreatesBookkeepingEntries() {
    when(voucherNumberService.nextVoucherNumber("LB")).thenReturn("LB-2");
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 22),
        LocalDate.of(2026, 8, 21),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );
    JournalEntry existingPayment = new JournalEntry(
        null,
        null,
        invoice,
        new Account("2440", "Leverantorsskulder"),
        "LB-1",
        100,
        0,
        "Supplier invoice paid: Adobe Creative Cloud",
        LocalDate.of(2026, 8, 1)
    );
    when(journalEntryRepository.findBySupplierInvoice(invoice)).thenReturn(List.of(existingPayment));

    accountingService.createSupplierInvoicePaymentEntries(invoice, LocalDate.of(2026, 8, 1), 100, "");

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(2);
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("LB-2");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    });
  }

  private List<JournalEntry> savedJournalEntries() {
    ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
    org.mockito.Mockito.verify(journalEntryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return new ArrayList<>(captor.getAllValues());
  }

  private void mockAccount(String number, String name) {
    when(accountRepository.findByNumber(number)).thenReturn(Optional.of(new Account(number, name)));
  }

  private Order testInvoice(int netAmount) {
    return new Order("Test Customer", new Product("Test service", "Test", netAmount), Instant.now());
  }

  private AppSettings settingsWithAccountingMethod(String accountingMethod) {
    AppSettings settings = AppSettings.defaults();
    settings.setAccountingMethod(accountingMethod);
    return settings;
  }
}
