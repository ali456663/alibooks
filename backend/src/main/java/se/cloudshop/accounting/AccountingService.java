package se.cloudshop.accounting;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.expense.Expense;
import se.cloudshop.order.Order;
import se.cloudshop.settings.SettingsService;

@Service
public class AccountingService {

  private final AccountRepository accountRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final StripePayoutRepository stripePayoutRepository;
  private final VoucherNumberService voucherNumberService;
  private final SettingsService settingsService;

  public AccountingService(
      AccountRepository accountRepository,
      JournalEntryRepository journalEntryRepository,
      StripePayoutRepository stripePayoutRepository,
      VoucherNumberService voucherNumberService,
      SettingsService settingsService
  ) {
    this.accountRepository = accountRepository;
    this.journalEntryRepository = journalEntryRepository;
    this.stripePayoutRepository = stripePayoutRepository;
    this.voucherNumberService = voucherNumberService;
    this.settingsService = settingsService;
  }

  public void createInvoiceEntries(Order invoice) {
    requireUnlockedAccountingDate(invoice.getInvoiceDate());

    String voucherNumber = voucherNumberService.nextVoucherNumber("F");
    Account receivables = account("1510");
    Account sales = account("3041");
    Account outputVat = account("2611");

    journalEntryRepository.save(new JournalEntry(
        invoice,
        receivables,
        voucherNumber,
        invoice.getTotalAmount(),
        0,
        "Invoice created",
        invoice.getInvoiceDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        sales,
        voucherNumber,
        0,
        invoice.getNetAmount(),
        "Invoice created",
        invoice.getInvoiceDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        outputVat,
        voucherNumber,
        0,
        invoice.getVatAmount(),
        "Invoice created",
        invoice.getInvoiceDate()
    ));
  }

  public void createPaymentEntries(Order invoice) {
    createPaymentEntries(invoice, LocalDate.now(), invoice.getTotalAmount());
  }

  public void createPaymentEntries(Order invoice, LocalDate paymentDate, int paidAmount) {
    if ("PAID".equals(invoice.getStatus())) {
      return;
    }

    LocalDate voucherDate = paymentDate == null ? LocalDate.now() : paymentDate;
    requireUnlockedAccountingDate(voucherDate);

    int amount = paidAmount <= 0 ? invoice.getTotalAmount() : paidAmount;

    Account bank = account("1930");
    Account receivables = account("1510");
    String voucherNumber = voucherNumberService.nextVoucherNumber("B");

    journalEntryRepository.save(new JournalEntry(
        invoice,
        bank,
        voucherNumber,
        amount,
        0,
        "Invoice paid",
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        receivables,
        voucherNumber,
        0,
        amount,
        "Invoice paid",
        voucherDate
    ));
  }

  @Transactional
  public void createStripeExternalSaleEntries(int totalAmount, String stripeReference, LocalDate paymentDate) {
    String reference = stripeReference == null ? "" : stripeReference.trim();
    if (!reference.isBlank() && stripeWebsiteSaleReferenceExists(reference)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe website sale reference is already booked.");
    }

    if (totalAmount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe amount must be greater than zero.");
    }

    LocalDate voucherDate = paymentDate == null ? LocalDate.now() : paymentDate;
    requireUnlockedAccountingDate(voucherDate);

    int netAmount = Math.round(totalAmount / 1.25f);
    int vatAmount = totalAmount - netAmount;
    String description = "Stripe website sale";
    if (!reference.isBlank()) {
      description += " " + reference;
    }

    String voucherNumber = voucherNumberService.nextVoucherNumber("S");
    Account stripeReceivable = account("1580");
    Account sales = account("3041");
    Account outputVat = account("2611");

    journalEntryRepository.save(new JournalEntry(
        null,
        stripeReceivable,
        voucherNumber,
        totalAmount,
        0,
        description,
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        null,
        sales,
        voucherNumber,
        0,
        netAmount,
        description,
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        null,
        outputVat,
        voucherNumber,
        0,
        vatAmount,
        description,
        voucherDate
    ));
  }

  public List<JournalEntry> createStripeWebsiteSaleEntry(CreateStripeWebsiteSaleRequest request) {
    createStripeExternalSaleEntries(request.totalAmount(), request.reference(), request.saleDate());
    String reference = request.reference() == null ? "" : request.reference().trim();
    return journalEntryRepository.findAll().stream()
        .filter(entry -> entry.getDescription() != null && entry.getDescription().startsWith("Stripe website sale"))
        .filter(entry -> reference.isBlank() || entry.getDescription().contains(reference))
        .toList();
  }

  @Transactional
  public List<JournalEntry> createStripePayoutEntry(CreateStripePayoutRequest request) {
    String reference = request.reference() == null ? "" : request.reference().trim();
    if (!reference.isBlank() && stripePayoutRepository.findByReference(reference).isPresent()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe payout reference is already booked.");
    }

    if (request.grossAmount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe gross amount must be greater than zero.");
    }

    if (request.feeAmount() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe fee cannot be negative.");
    }

    if (request.feeAmount() > request.grossAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe fee cannot be greater than gross amount.");
    }

    LocalDate voucherDate = request.payoutDate() == null ? LocalDate.now() : request.payoutDate();
    requireUnlockedAccountingDate(voucherDate);

    int netPayout = request.grossAmount() - request.feeAmount();
    String description = "Stripe payout";
    if (!reference.isBlank()) {
      description += " " + reference;
    }

    String voucherNumber = voucherNumberService.nextVoucherNumber("SU");
    Account bank = account("1930");
    Account stripeReceivable = account("1580");
    Account bankFees = account("6570");

    JournalEntry bankEntry = journalEntryRepository.save(new JournalEntry(
        null,
        bank,
        voucherNumber,
        netPayout,
        0,
        description,
        voucherDate
    ));
    JournalEntry feeEntry = journalEntryRepository.save(new JournalEntry(
        null,
        bankFees,
        voucherNumber,
        request.feeAmount(),
        0,
        description + " fee",
        voucherDate
    ));
    JournalEntry stripeEntry = journalEntryRepository.save(new JournalEntry(
        null,
        stripeReceivable,
        voucherNumber,
        0,
        request.grossAmount(),
        description,
        voucherDate
    ));

    stripePayoutRepository.save(new StripePayout(
        voucherDate,
        request.grossAmount(),
        request.feeAmount(),
        reference,
        voucherNumber
    ));

    return List.of(bankEntry, feeEntry, stripeEntry);
  }

  public List<StripePayout> findStripePayouts() {
    return stripePayoutRepository.findAll();
  }

  public void createExpenseEntries(Expense expense) {
    requireUnlockedAccountingDate(expense.getExpenseDate());

    String voucherNumber = voucherNumberService.nextVoucherNumber("K");
    Account expenseAccount = account(expense.getCategory());
    Account inputVat = account("2641");
    Account paidFrom = account(expense.getPaidFrom());

    journalEntryRepository.save(new JournalEntry(
        null,
        expenseAccount,
        voucherNumber,
        expense.getNetAmount(),
        0,
        "Expense: " + expense.getDescription(),
        expense.getExpenseDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        null,
        inputVat,
        voucherNumber,
        expense.getVatAmount(),
        0,
        "Expense VAT: " + expense.getDescription(),
        expense.getExpenseDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        null,
        paidFrom,
        voucherNumber,
        0,
        expense.getTotalAmount(),
        "Expense paid: " + expense.getDescription(),
        expense.getExpenseDate()
    ));
  }

  public void createCreditInvoiceEntries(Order creditInvoice) {
    requireUnlockedAccountingDate(creditInvoice.getInvoiceDate());

    String voucherNumber = voucherNumberService.nextVoucherNumber("KR");
    Account receivables = account("1510");
    Account sales = account("3041");
    Account outputVat = account("2611");

    journalEntryRepository.save(new JournalEntry(
        creditInvoice,
        sales,
        voucherNumber,
        Math.abs(creditInvoice.getNetAmount()),
        0,
        "Credit invoice",
        creditInvoice.getInvoiceDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        creditInvoice,
        outputVat,
        voucherNumber,
        Math.abs(creditInvoice.getVatAmount()),
        0,
        "Credit invoice",
        creditInvoice.getInvoiceDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        creditInvoice,
        receivables,
        voucherNumber,
        0,
        Math.abs(creditInvoice.getTotalAmount()),
        "Credit invoice",
        creditInvoice.getInvoiceDate()
    ));
  }

  public List<JournalEntry> findAllEntries() {
    return journalEntryRepository.findAll();
  }

  public List<JournalEntry> createManualEntry(CreateManualJournalEntryRequest request) {
    if (request.description() == null || request.description().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
    }

    if (request.amount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero.");
    }

    if (request.debitAccountNumber() == null || request.debitAccountNumber().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit account is required.");
    }

    if (request.creditAccountNumber() == null || request.creditAccountNumber().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit account is required.");
    }

    if (request.debitAccountNumber().equals(request.creditAccountNumber())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit and credit account must be different.");
    }

    LocalDate voucherDate = request.voucherDate() == null ? LocalDate.now() : request.voucherDate();
    requireUnlockedAccountingDate(voucherDate);

    String voucherNumber = voucherNumberService.nextVoucherNumber("M");
    Account debitAccount = account(request.debitAccountNumber());
    Account creditAccount = account(request.creditAccountNumber());
    JournalEntry debitEntry = journalEntryRepository.save(new JournalEntry(
        null,
        debitAccount,
        voucherNumber,
        request.amount(),
        0,
        request.description(),
        voucherDate
    ));
    JournalEntry creditEntry = journalEntryRepository.save(new JournalEntry(
        null,
        creditAccount,
        voucherNumber,
        0,
        request.amount(),
        request.description(),
        voucherDate
    ));

    return List.of(debitEntry, creditEntry);
  }

  @Transactional
  public List<JournalEntry> createCorrectionEntry(String voucherNumber, CreateCorrectionJournalEntryRequest request) {
    if (voucherNumber == null || voucherNumber.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher number is required.");
    }

    List<JournalEntry> originalEntries = journalEntryRepository.findByVoucherNumber(voucherNumber);
    if (originalEntries.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher not found.");
    }

    LocalDate voucherDate = request == null || request.voucherDate() == null ? LocalDate.now() : request.voucherDate();
    requireUnlockedAccountingDate(voucherDate);

    String correctionVoucherNumber = voucherNumberService.nextVoucherNumber("R");
    return originalEntries.stream()
        .map(entry -> {
          JournalEntry correctionEntry = new JournalEntry(
              null,
              account(entry.getAccountNumber()),
              correctionVoucherNumber,
              entry.getCredit(),
              entry.getDebit(),
              "Correction of " + voucherNumber + ": " + entry.getDescription(),
              voucherDate
          );
          correctionEntry.setCorrectionOfVoucherNumber(voucherNumber);
          return journalEntryRepository.save(correctionEntry);
        })
        .toList();
  }

  @Transactional
  public void deleteEntriesForInvoice(Order invoice) {
    journalEntryRepository.findByInvoice(invoice).forEach(entry -> requireUnlockedAccountingDate(entry.getVoucherDate()));
    journalEntryRepository.deleteByInvoice(invoice);
  }

  public VatReport createVatReport() {
    List<JournalEntry> entries = journalEntryRepository.findAll();
    int outputVat = entries.stream()
        .filter(entry -> entry.getAccountNumber().equals("2611"))
        .mapToInt(JournalEntry::getCredit)
        .sum();
    int inputVat = entries.stream()
        .filter(entry -> entry.getAccountNumber().equals("2641"))
        .mapToInt(JournalEntry::getDebit)
        .sum();

    return new VatReport(outputVat, inputVat, outputVat - inputVat);
  }

  public ProfitAndLossReport createProfitAndLossReport() {
    List<JournalEntry> entries = journalEntryRepository.findAll();
    List<ReportLine> revenue = reportLines(entries, "3", true);
    List<ReportLine> expenses = entries.stream()
        .filter(entry -> entry.getAccountNumber().startsWith("4")
            || entry.getAccountNumber().startsWith("5")
            || entry.getAccountNumber().startsWith("6"))
        .collect(Collectors.groupingBy(
            entry -> entry.getAccountNumber() + "|" + entry.getAccountName(),
            Collectors.summingInt(entry -> entry.getDebit() - entry.getCredit())
        ))
        .entrySet()
        .stream()
        .map(this::toReportLine)
        .toList();
    int totalRevenue = revenue.stream().mapToInt(ReportLine::amount).sum();
    int totalExpenses = expenses.stream().mapToInt(ReportLine::amount).sum();

    return new ProfitAndLossReport(revenue, expenses, totalRevenue, totalExpenses, totalRevenue - totalExpenses);
  }

  public BalanceReport createBalanceReport() {
    List<JournalEntry> entries = journalEntryRepository.findAll();
    List<ReportLine> assets = reportLines(entries, "1", false);
    List<ReportLine> liabilitiesAndEquity = reportLines(entries, "2", true);
    ProfitAndLossReport profitAndLossReport = createProfitAndLossReport();
    int result = profitAndLossReport.result();

    liabilitiesAndEquity = new java.util.ArrayList<>(liabilitiesAndEquity);
    liabilitiesAndEquity.add(yearResultLine(result));

    int totalAssets = assets.stream().mapToInt(ReportLine::amount).sum();
    int totalLiabilitiesAndEquity = liabilitiesAndEquity.stream().mapToInt(ReportLine::amount).sum();

    return new BalanceReport(
        assets,
        liabilitiesAndEquity,
        totalAssets,
        totalLiabilitiesAndEquity,
        totalAssets - totalLiabilitiesAndEquity
    );
  }

  private List<ReportLine> reportLines(List<JournalEntry> entries, String accountPrefix, boolean creditPositive) {
    return entries.stream()
        .filter(entry -> entry.getAccountNumber().startsWith(accountPrefix))
        .collect(Collectors.groupingBy(
            entry -> entry.getAccountNumber() + "|" + entry.getAccountName(),
            Collectors.summingInt(entry -> creditPositive ? entry.getCredit() - entry.getDebit() : entry.getDebit() - entry.getCredit())
        ))
        .entrySet()
        .stream()
        .map(this::toReportLine)
        .toList();
  }

  private ReportLine toReportLine(Map.Entry<String, Integer> entry) {
    String[] parts = entry.getKey().split("\\|", 2);
    return new ReportLine(parts[0], parts[1], entry.getValue());
  }

  private ReportLine yearResultLine(int result) {
    String companyType = settingsService.getSettings().getCompanyType();

    if ("LIMITED_COMPANY".equals(companyType)) {
      return new ReportLine("2099", "Arets resultat", result);
    }

    return new ReportLine("2019", "Arets resultat", result);
  }

  public void requireUnlockedAccountingDate(LocalDate voucherDate) {
    LocalDate lockedThroughDate = settingsService.getSettings().getAccountingLockedThroughDate();
    if (lockedThroughDate != null && !voucherDate.isAfter(lockedThroughDate)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Bokforingen ar last till och med " + lockedThroughDate + ". Skapa en ny korrigeringsverifikation i en olast period."
      );
    }
  }

  private Account account(String number) {
    return accountRepository.findByNumber(number)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Account " + number + " is missing."));
  }

  private boolean stripeWebsiteSaleReferenceExists(String reference) {
    return journalEntryRepository.findAll().stream()
        .anyMatch(entry -> entry.getDescription() != null
            && entry.getDescription().startsWith("Stripe website sale")
            && entry.getDescription().contains(reference));
  }
}
