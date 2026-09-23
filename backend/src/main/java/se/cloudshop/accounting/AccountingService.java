package se.cloudshop.accounting;

import static se.cloudshop.accounting.ReportAmounts.reportAmount;

import se.cloudshop.money.WholeKronaMath;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.bank.BankReconciliationReport;
import se.cloudshop.bank.BankReconciliationService;
import se.cloudshop.expense.Expense;
import se.cloudshop.order.Order;
import se.cloudshop.product.VatRate;
import se.cloudshop.order.ReceivablesAgingReport;
import se.cloudshop.order.ReceivablesReportService;
import se.cloudshop.settings.SettingsService;
import se.cloudshop.supplier.PayablesAgingReport;
import se.cloudshop.supplier.PayablesReportService;
import se.cloudshop.supplier.SupplierInvoice;

@Service
@Transactional
public class AccountingService {

  private final AccountRepository accountRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final StripePayoutRepository stripePayoutRepository;
  private final VoucherNumberService voucherNumberService;
  private final SettingsService settingsService;
  private final VatFilingRepository vatFilingRepository;
  private final BankReconciliationService bankReconciliationService;
  private final ReceivablesReportService receivablesReportService;
  private final PayablesReportService payablesReportService;
  private final VoucherApprovalRepository voucherApprovalRepository;

  public AccountingService(
      AccountRepository accountRepository,
      JournalEntryRepository journalEntryRepository,
      StripePayoutRepository stripePayoutRepository,
      VoucherNumberService voucherNumberService,
      SettingsService settingsService,
      VatFilingRepository vatFilingRepository,
      BankReconciliationService bankReconciliationService,
      ReceivablesReportService receivablesReportService,
      PayablesReportService payablesReportService,
      VoucherApprovalRepository voucherApprovalRepository
  ) {
    this.accountRepository = accountRepository;
    this.journalEntryRepository = journalEntryRepository;
    this.stripePayoutRepository = stripePayoutRepository;
    this.voucherNumberService = voucherNumberService;
    this.settingsService = settingsService;
    this.vatFilingRepository = vatFilingRepository;
    this.bankReconciliationService = bankReconciliationService;
    this.receivablesReportService = receivablesReportService;
    this.payablesReportService = payablesReportService;
    this.voucherApprovalRepository = voucherApprovalRepository;
  }

  public void createInvoiceEntries(Order invoice) {
    requireUnlockedAccountingDate(invoice.getInvoiceDate());

    if (usesCashMethod()) {
      return;
    }

    if (hasInvoiceBookingEntries(invoice)) {
      return;
    }

    int totalAmount = wholeKronaFromMinor(invoice.getTotalAmountMinor(), invoice.getTotalAmount(), "fakturans totalbelopp");
    int netAmount = wholeKronaFromMinor(invoice.getNetAmountMinor(), invoice.getNetAmount(), "fakturans nettobelopp");
    int vatAmount = wholeKronaFromMinor(invoice.getVatAmountMinor(), invoice.getVatAmount(), "fakturans momsbelopp");
    String voucherNumber = voucherNumberService.nextVoucherNumber("F");
    Account receivables = account("1510");
    Account sales = account(VatRate.salesAccount(invoice.getVatPercent()));
    Account outputVat = account(VatRate.outputVatAccount(invoice.getVatPercent()));

    journalEntryRepository.save(new JournalEntry(
        invoice,
        receivables,
        voucherNumber,
        totalAmount,
        0,
        "Invoice created",
        invoice.getInvoiceDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        sales,
        voucherNumber,
        0,
        netAmount,
        "Invoice created",
        invoice.getInvoiceDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        outputVat,
        voucherNumber,
        0,
        vatAmount,
        "Invoice created",
        invoice.getInvoiceDate()
    ));
  }

  private boolean hasInvoiceBookingEntries(Order invoice) {
    return journalEntryRepository.findByInvoice(invoice).stream()
        .anyMatch(entry -> "Invoice created".equals(entry.getDescription())
            || (entry.getVoucherNumber() != null && entry.getVoucherNumber().startsWith("F-")));
  }

  public void createPaymentEntries(Order invoice) {
    createPaymentEntries(invoice, null, invoice.getRemainingAmount());
  }

  public JournalEntry createPaymentEntries(Order invoice, LocalDate paymentDate, int paidAmount) {
    return createPaymentEntries(invoice, paymentDate, paidAmount, "1930");
  }

  public void createStripeInvoicePaymentEntries(Order invoice, LocalDate paymentDate, int paidAmount) {
    createPaymentEntries(invoice, paymentDate, paidAmount, "1580");
  }

  private JournalEntry createPaymentEntries(Order invoice, LocalDate paymentDate, int paidAmount, String receivedAccount) {
    if ("PAID".equals(invoice.getStatus())) {
      return null;
    }

    LocalDate voucherDate = requireExplicitAccountingDate(paymentDate, "Payment date is required.");
    if (invoice.getInvoiceDate() != null && voucherDate.isBefore(invoice.getInvoiceDate())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date cannot be before invoice date.");
    }

    requireUnlockedAccountingDate(voucherDate);

    if (paidAmount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid amount must be greater than zero.");
    }

    if (paidAmount > invoice.getRemainingAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid amount cannot be greater than remaining amount.");
    }

    int amount = paidAmount;

    if (usesCashMethod()) {
      return createCashMethodPaymentEntries(invoice, voucherDate, amount, receivedAccount);
    }

    Account bank = account(receivedAccount);
    Account receivables = account("1510");
    String voucherNumber = voucherNumberService.nextVoucherNumber("B");

    JournalEntry bankEntry = journalEntryRepository.save(new JournalEntry(
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
    return bankEntry;
  }

  public void createRefundEntries(Order invoice, LocalDate refundDate, int refundAmount) {
    LocalDate voucherDate = requireExplicitAccountingDate(refundDate, "Refund date is required.");
    if (invoice.getInvoiceDate() != null && voucherDate.isBefore(invoice.getInvoiceDate())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund date cannot be before invoice date.");
    }

    requireUnlockedAccountingDate(voucherDate);

    if (refundAmount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount must be greater than zero.");
    }

    if (refundAmount > invoice.getRefundableAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount cannot be greater than refundable amount.");
    }

    if (usesCashMethod()) {
      createCashMethodRefundEntries(invoice, voucherDate, refundAmount);
      return;
    }

    Account receivables = account("1510");
    Account bank = account("1930");
    String voucherNumber = voucherNumberService.nextVoucherNumber("AR");

    journalEntryRepository.save(new JournalEntry(
        invoice,
        receivables,
        voucherNumber,
        refundAmount,
        0,
        "Customer refund after credit invoice",
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        bank,
        voucherNumber,
        0,
        refundAmount,
        "Customer refund after credit invoice",
        voucherDate
    ));
  }

  @Transactional
  public void createStripeExternalSaleEntries(int totalAmount, String stripeReference, LocalDate paymentDate) {
    createStripeExternalSaleEntries(totalAmount, stripeReference, paymentDate, 25);
  }

  @Transactional
  public void createStripeExternalSaleEntries(int totalAmount, String stripeReference, LocalDate paymentDate, int vatPercent) {
    String reference = stripeReference == null ? "" : stripeReference.trim();
    if (reference.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe website sale reference is required.");
    }
    if (reference.length() > 240 || reference.contains("\n") || reference.contains("\r")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe website sale reference is invalid.");
    }
    String description = "Stripe website sale " + reference;
    journalEntryRepository.lockStripeSaleReference(reference);
    if (stripeWebsiteSaleReferenceExists(description)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe website sale reference is already booked.");
    }

    if (totalAmount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe amount must be greater than zero.");
    }
    if (!VatRate.isSupported(vatPercent)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe sale VAT rate must be 6, 12 or 25 percent.");
    }

    LocalDate voucherDate = requireExplicitAccountingDate(paymentDate, "Stripe sale date is required.");
    requireUnlockedAccountingDate(voucherDate);

    int netAmount;
    try {
      netAmount = WholeKronaMath.exactRatio(totalAmount, 100, 100 + vatPercent);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Stripe sale total cannot be split into exact whole-krona net and VAT amounts.",
          exception
      );
    }
    int vatAmount = totalAmount - netAmount;
    String voucherNumber = voucherNumberService.nextVoucherNumber("S");
    Account stripeReceivable = account("1580");
    Account sales = account(VatRate.salesAccount(vatPercent));
    Account outputVat = account(VatRate.outputVatAccount(vatPercent));

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
    createStripeExternalSaleEntries(request.totalAmount(), request.reference(), request.saleDate(), request.effectiveVatPercent());
    String reference = request.reference() == null ? "" : request.reference().trim();
    String expectedDescription = "Stripe website sale " + reference;
    return journalEntryRepository.findAll().stream()
        .filter(entry -> expectedDescription.equals(entry.getDescription()))
        .toList();
  }

  @Transactional
  public List<JournalEntry> createStripePayoutEntry(CreateStripePayoutRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe payout request is required.");
    }
    if (request.payoutDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe payout date is required.");
    }
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

    if (request.feeAmount() >= request.grossAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe fee must be less than gross amount.");
    }

    LocalDate voucherDate = request.payoutDate();
    requireUnlockedAccountingDate(voucherDate);

    int netPayout = request.grossAmount() - request.feeAmount();
    String description = "Stripe payout";
    if (!reference.isBlank()) {
      description += " " + reference;
    }

    String voucherNumber = voucherNumberService.nextVoucherNumber("SU");
    Account bank = account("1930");
    Account stripeReceivable = account("1580");
    Account bankFees = request.feeAmount() > 0 ? account("6570") : null;
    List<JournalEntry> entries = new ArrayList<>();

    JournalEntry bankEntry = journalEntryRepository.save(new JournalEntry(
        null,
        bank,
        voucherNumber,
        netPayout,
        0,
        description,
        voucherDate
    ));
    entries.add(bankEntry);

    if (request.feeAmount() > 0) {
      entries.add(journalEntryRepository.save(new JournalEntry(
          null,
          bankFees,
          voucherNumber,
          request.feeAmount(),
          0,
          description + " fee",
          voucherDate
      )));
    }

    JournalEntry stripeEntry = journalEntryRepository.save(new JournalEntry(
        null,
        stripeReceivable,
        voucherNumber,
        0,
        request.grossAmount(),
        description,
        voucherDate
    ));
    entries.add(stripeEntry);

    try {
      stripePayoutRepository.saveAndFlush(new StripePayout(
          voucherDate,
          request.grossAmount(),
          request.feeAmount(),
          reference,
          voucherNumber
      ));
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This Stripe payout reference is already registered.",
          exception
      );
    }

    return entries;
  }

  public List<StripePayout> findStripePayouts() {
    return stripePayoutRepository.findAll();
  }

  public JournalEntry createExpenseEntries(Expense expense) {
    requireUnlockedAccountingDate(expense.getExpenseDate());

    int netAmount = wholeKrona(expense.getNetAmountMinorValue(), "expense net amount");
    int vatAmount = wholeKrona(expense.getVatAmountMinorValue(), "expense VAT amount");
    int totalAmount = wholeKrona(expense.getTotalAmountMinorValue(), "expense total amount");

    String voucherNumber = voucherNumberService.nextVoucherNumber("K");
    Account expenseAccount = account(expense.getCategory());
    Account inputVat = account("2641");
    Account paidFrom = account(expense.getPaidFrom());

    journalEntryRepository.save(new JournalEntry(
        null,
        expense,
        expenseAccount,
        voucherNumber,
        netAmount,
        0,
        "Expense: " + expense.getDescription(),
        expense.getExpenseDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        null,
        expense,
        inputVat,
        voucherNumber,
        vatAmount,
        0,
        "Expense VAT: " + expense.getDescription(),
        expense.getExpenseDate()
    ));
    return journalEntryRepository.save(new JournalEntry(
        null,
        expense,
        paidFrom,
        voucherNumber,
        0,
        totalAmount,
        "Expense paid: " + expense.getDescription(),
        expense.getExpenseDate()
    ));
  }

  private static int wholeKrona(long amountMinor, String field) {
    if (amountMinor % 100L != 0L) {
      throw new IllegalArgumentException(field + " contains ore that the current accounting API cannot represent safely.");
    }
    long amount = amountMinor / 100L;
    if (amount < Integer.MIN_VALUE || amount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(field + " is outside the supported accounting range.");
    }
    return (int) amount;
  }

  public void createSupplierInvoiceEntries(SupplierInvoice supplierInvoice) {
    if (usesCashMethod()) {
      return;
    }

    if (hasSupplierInvoiceBookingEntries(supplierInvoice)) {
      return;
    }

    int netAmount = wholeKronaFromMinor(supplierInvoice.getNetAmountMinor(), supplierInvoice.getNetAmount(), "leverantörsfakturans nettobelopp");
    int vatAmount = wholeKronaFromMinor(supplierInvoice.getVatAmountMinor(), supplierInvoice.getVatAmount(), "leverantörsfakturans momsbelopp");
    int totalAmount = wholeKronaFromMinor(supplierInvoice.getTotalAmountMinor(), supplierInvoice.getTotalAmount(), "leverantörsfakturans totalbelopp");
    requireUnlockedAccountingDate(supplierInvoice.getInvoiceDate());

    String voucherNumber = voucherNumberService.nextVoucherNumber("L");
    Account expenseAccount = account(supplierInvoice.getCategory() == null || supplierInvoice.getCategory().isBlank()
        ? "5420"
        : supplierInvoice.getCategory());
    Account inputVat = account("2641");
    Account payables = account("2440");
    String descriptionPrefix = supplierInvoice.isSelfBilling() ? "Self-billing supplier invoice: " : "Supplier invoice: ";
    String description = descriptionPrefix + supplierInvoice.getDescription();

    journalEntryRepository.save(new JournalEntry(
        null,
        null,
        supplierInvoice,
        expenseAccount,
        voucherNumber,
        netAmount,
        0,
        description,
        supplierInvoice.getInvoiceDate()
    ));

    if (vatAmount > 0) {
      journalEntryRepository.save(new JournalEntry(
          null,
          null,
          supplierInvoice,
          inputVat,
          voucherNumber,
          vatAmount,
          0,
          (supplierInvoice.isSelfBilling() ? "Self-billing supplier invoice VAT: " : "Supplier invoice VAT: ") + supplierInvoice.getDescription(),
          supplierInvoice.getInvoiceDate()
      ));
    }

    journalEntryRepository.save(new JournalEntry(
        null,
        null,
        supplierInvoice,
        payables,
        voucherNumber,
        0,
        totalAmount,
        description,
        supplierInvoice.getInvoiceDate()
    ));
  }

  public void createSupplierInvoicePaymentEntries(SupplierInvoice supplierInvoice, LocalDate paymentDate) {
    createSupplierInvoicePaymentEntries(supplierInvoice, paymentDate, supplierInvoice.getRemainingAmount(), "");
  }

  public void createSupplierInvoicePaymentEntries(SupplierInvoice supplierInvoice, LocalDate paymentDate, int paidAmount, String paymentReference) {
    LocalDate voucherDate = requireExplicitAccountingDate(paymentDate, "Supplier invoice payment date is required.");
    String reference = paymentReference == null ? "" : paymentReference.trim();
    if (hasSupplierInvoicePaymentEntry(supplierInvoice, voucherDate, paidAmount, reference)) {
      return;
    }

    if (supplierInvoice.getInvoiceDate() != null && voucherDate.isBefore(supplierInvoice.getInvoiceDate())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date cannot be before supplier invoice date.");
    }

    requireUnlockedAccountingDate(voucherDate);

    if (paidAmount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier invoice payment amount must be greater than zero.");
    }

    if (paidAmount > supplierInvoice.getRemainingAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier invoice payment amount cannot be greater than remaining amount.");
    }

    if (usesCashMethod()) {
      createCashMethodSupplierInvoicePaymentEntries(supplierInvoice, voucherDate, paidAmount, reference);
      return;
    }

    String voucherNumber = voucherNumberService.nextVoucherNumber("LB");
    Account payables = account("2440");
    Account bank = account("1930");
    String description = "Supplier invoice paid: " + supplierInvoice.getDescription();
    if (!reference.isBlank()) {
      description += " " + reference;
    }

    journalEntryRepository.save(new JournalEntry(
        null,
        null,
        supplierInvoice,
        payables,
        voucherNumber,
        paidAmount,
        0,
        description,
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        null,
        null,
        supplierInvoice,
        bank,
        voucherNumber,
        0,
        paidAmount,
        description,
        voucherDate
    ));
  }

  private boolean hasSupplierInvoiceBookingEntries(SupplierInvoice supplierInvoice) {
    return journalEntryRepository.findBySupplierInvoice(supplierInvoice).stream()
        .anyMatch(entry -> "supplier_invoice".equals(entry.getSourceType()));
  }

  private boolean hasSupplierInvoicePaymentEntry(SupplierInvoice supplierInvoice, LocalDate voucherDate, int paidAmount, String reference) {
    if (reference.isBlank()) {
      return false;
    }

    long paidAmountMinor = Math.multiplyExact((long) paidAmount, 100L);
    return journalEntryRepository.findBySupplierInvoice(supplierInvoice).stream()
        .anyMatch(entry -> "supplier_invoice_payment".equals(entry.getSourceType())
            && voucherDate.equals(entry.getVoucherDate())
            && (entry.getDebitMinorValue() == paidAmountMinor || entry.getCreditMinorValue() == paidAmountMinor)
            && entry.getDescription() != null
            && entry.getDescription().contains(reference));
  }

  public boolean hasSupplierInvoiceEntries(SupplierInvoice supplierInvoice) {
    return supplierInvoice != null && !journalEntryRepository.findBySupplierInvoice(supplierInvoice).isEmpty();
  }

  @Transactional
  public List<JournalEntry> createSupplierInvoiceCancellationEntries(SupplierInvoice supplierInvoice, LocalDate cancellationDate) {
    if (supplierInvoice == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier invoice is required.");
    }

    if (wholeKronaFromMinor(supplierInvoice.getPaidAmountMinor(), supplierInvoice.getPaidAmount(),
        "leverantörsfakturans tidigare betalt") > 0
        || "paid".equals(supplierInvoice.getStatus()) || "partial".equals(supplierInvoice.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid supplier invoices cannot be cancelled without a payment correction.");
    }

    LocalDate voucherDate = requireExplicitAccountingDate(cancellationDate, "Supplier invoice cancellation date is required.");
    if (supplierInvoice.getInvoiceDate() != null && voucherDate.isBefore(supplierInvoice.getInvoiceDate())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation date cannot be before supplier invoice date.");
    }
    requireUnlockedAccountingDate(voucherDate);

    List<JournalEntry> originalEntries = journalEntryRepository.findBySupplierInvoice(supplierInvoice).stream()
        .filter(entry -> "supplier_invoice".equals(entry.getSourceType()))
        .toList();
    if (originalEntries.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier invoice has no bookkeeping entries to reverse.");
    }

    validateCorrectionAmounts(originalEntries, "leverantörsfakturans korrigeringspost");

    String originalVoucherNumber = originalEntries.get(0).getVoucherNumber();
    boolean alreadyCancelled = journalEntryRepository.findBySupplierInvoice(supplierInvoice).stream()
        .anyMatch(entry -> originalVoucherNumber != null
            && originalVoucherNumber.equals(entry.getCorrectionOfVoucherNumber()));
    if (alreadyCancelled) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier invoice is already cancelled with a correction voucher.");
    }

    String correctionVoucherNumber = voucherNumberService.nextVoucherNumber("R");
    String reference = supplierInvoice.getReference() == null || supplierInvoice.getReference().isBlank()
        ? String.valueOf(supplierInvoice.getId())
        : supplierInvoice.getReference();
    return originalEntries.stream()
        .map(entry -> {
          JournalEntry correctionEntry = new JournalEntry(
              null,
              null,
              supplierInvoice,
              account(entry.getAccountNumber()),
              correctionVoucherNumber,
              reportAccountingWholeKrona(entry.getCreditMinorValue(), "leverantörsfakturans korrigeringsdebet"),
              reportAccountingWholeKrona(entry.getDebitMinorValue(), "leverantörsfakturans korrigeringskredit"),
              "Cancellation of supplier invoice " + reference + ": " + entry.getDescription(),
              voucherDate
          );
          correctionEntry.setCorrectionOfVoucherNumber(originalVoucherNumber);
          return journalEntryRepository.save(correctionEntry);
        })
        .toList();
  }

  public void createCreditInvoiceEntries(Order creditInvoice) {
    requireUnlockedAccountingDate(creditInvoice.getInvoiceDate());

    if (usesCashMethod()) {
      return;
    }

    int netAmount = Math.abs(wholeKronaFromMinor(creditInvoice.getNetAmountMinor(), creditInvoice.getNetAmount(), "kreditfakturans nettobelopp"));
    int vatAmount = Math.abs(wholeKronaFromMinor(creditInvoice.getVatAmountMinor(), creditInvoice.getVatAmount(), "kreditfakturans momsbelopp"));
    int totalAmount = Math.abs(wholeKronaFromMinor(creditInvoice.getTotalAmountMinor(), creditInvoice.getTotalAmount(), "kreditfakturans totalbelopp"));
    String voucherNumber = voucherNumberService.nextVoucherNumber("KR");
    Account receivables = account("1510");
    Account sales = account(VatRate.salesAccount(creditInvoice.getVatPercent()));
    Account outputVat = account(VatRate.outputVatAccount(creditInvoice.getVatPercent()));

    journalEntryRepository.save(new JournalEntry(
        creditInvoice,
        sales,
        voucherNumber,
        netAmount,
        0,
        "Credit invoice",
        creditInvoice.getInvoiceDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        creditInvoice,
        outputVat,
        voucherNumber,
        vatAmount,
        0,
        "Credit invoice",
        creditInvoice.getInvoiceDate()
    ));
    journalEntryRepository.save(new JournalEntry(
        creditInvoice,
        receivables,
        voucherNumber,
        0,
        totalAmount,
        "Credit invoice",
        creditInvoice.getInvoiceDate()
    ));
  }

  public List<JournalEntry> findAllEntries() {
    return journalEntryRepository.findAll();
  }

  public List<JournalEntry> createManualEntry(CreateManualJournalEntryRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual voucher request is required.");
    }
    if (request.voucherDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher date is required.");
    }
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

    LocalDate voucherDate = request.voucherDate();
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
  public List<JournalEntry> createOwnerTransactionEntries(OwnerTransaction transaction) {
    if (transaction == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner transaction is required.");
    }

    if (transaction.getAmount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner transaction amount must be greater than zero.");
    }

    if (transaction.getDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner transaction date is required.");
    }

    if (transaction.getDebitAccount() == null || transaction.getDebitAccount().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit account is required.");
    }

    if (transaction.getCreditAccount() == null || transaction.getCreditAccount().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit account is required.");
    }

    if (transaction.getDebitAccount().equals(transaction.getCreditAccount())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit and credit account must be different.");
    }

    if ("booked".equals(transaction.getStatus()) || (transaction.getVoucherNumber() != null && !transaction.getVoucherNumber().isBlank())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner transaction is already booked.");
    }

    requireUnlockedAccountingDate(transaction.getDate());

    String voucherNumber = voucherNumberService.nextVoucherNumber("E");
    String description = transaction.getDescription() == null || transaction.getDescription().isBlank()
        ? "Owner equity transaction"
        : transaction.getDescription().trim();

    JournalEntry debitEntry = journalEntryRepository.save(new JournalEntry(
        null,
        account(transaction.getDebitAccount()),
        voucherNumber,
        transaction.getAmount(),
        0,
        description,
        transaction.getDate()
    ));
    JournalEntry creditEntry = journalEntryRepository.save(new JournalEntry(
        null,
        account(transaction.getCreditAccount()),
        voucherNumber,
        0,
        transaction.getAmount(),
        description,
        transaction.getDate()
    ));

    return List.of(debitEntry, creditEntry);
  }

  @Transactional
  public List<JournalEntry> createManualMultiLineEntry(CreateManualMultiLineJournalEntryRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual voucher request is required.");
    }
    if (request.voucherDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher date is required.");
    }
    if (request.description() == null || request.description().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
    }

    if (request.lines() == null || request.lines().size() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least two journal lines are required.");
    }

    validateJournalLines(request.lines(), "Journal");

    long totalDebit = request.lines().stream().mapToLong(CreateManualJournalEntryLineRequest::debit).sum();
    long totalCredit = request.lines().stream().mapToLong(CreateManualJournalEntryLineRequest::credit).sum();

    if (totalDebit <= 0 || totalCredit <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit and credit must be greater than zero.");
    }

    if (totalDebit != totalCredit) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher must balance debit and credit.");
    }
    if (totalDebit > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher total exceeds the supported limit.");
    }

    LocalDate voucherDate = request.voucherDate();
    requireUnlockedAccountingDate(voucherDate);

    String voucherNumber = voucherNumberService.nextVoucherNumber("M");
    return request.lines().stream()
        .map(line -> {
          return journalEntryRepository.save(new JournalEntry(
              null,
              account(line.accountNumber()),
              voucherNumber,
              line.debit(),
              line.credit(),
              request.description(),
              voucherDate
          ));
        })
        .toList();
  }

  @Transactional
  public List<JournalEntry> createOpeningBalanceEntry(CreateOpeningBalanceRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening balance request is required.");
    }
    if (request.voucherDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening balance date is required.");
    }

    String description = request.description() == null || request.description().isBlank()
        ? "Opening balance"
        : request.description().trim();

    if (request.lines() == null || request.lines().size() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least two opening balance lines are required.");
    }

    validateJournalLines(request.lines(), "Opening balance");

    long totalDebit = request.lines().stream().mapToLong(CreateManualJournalEntryLineRequest::debit).sum();
    long totalCredit = request.lines().stream().mapToLong(CreateManualJournalEntryLineRequest::credit).sum();

    if (totalDebit <= 0 || totalCredit <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening balance needs both debit and credit.");
    }

    if (totalDebit != totalCredit) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening balance must balance debit and credit.");
    }
    if (totalDebit > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening balance total exceeds the supported limit.");
    }

    LocalDate voucherDate = request.voucherDate();
    requireUnlockedAccountingDate(voucherDate);
    String voucherNumber = voucherNumberService.nextVoucherNumber("IB");

    return request.lines().stream()
        .map(line -> {
          return journalEntryRepository.save(new JournalEntry(
              null,
              account(line.accountNumber()),
              voucherNumber,
              line.debit(),
              line.credit(),
              description,
              voucherDate
          ));
        })
        .toList();
  }

  @Transactional
  public List<JournalEntry> createCorrectionEntry(String voucherNumber, CreateCorrectionJournalEntryRequest request) {
    if (voucherNumber == null || voucherNumber.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher number is required.");
    }
    if (request == null || request.voucherDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction date is required.");
    }

    List<JournalEntry> originalEntries = journalEntryRepository.findByVoucherNumberForCorrection(voucherNumber);
    if (originalEntries.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher not found.");
    }

    boolean voucherIsCorrection = originalEntries.stream()
        .anyMatch(entry -> entry.getCorrectionOfVoucherNumber() != null
            && !entry.getCorrectionOfVoucherNumber().isBlank());
    if (voucherIsCorrection) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Correction vouchers cannot be corrected again. Create a manual adjustment if another change is needed."
      );
    }

    if (!journalEntryRepository.findByCorrectionOfVoucherNumber(voucherNumber).isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Voucher already has a correction voucher."
      );
    }

    LocalDate voucherDate = request.voucherDate();
    requireUnlockedAccountingDate(voucherDate);

    validateCorrectionAmounts(originalEntries, "verifikatets korrigeringspost");

    String correctionVoucherNumber = voucherNumberService.nextVoucherNumber("R");
    List<JournalEntry> correctionEntries = originalEntries.stream()
        .map(entry -> {
          JournalEntry correctionEntry = new JournalEntry(
              null,
              account(entry.getAccountNumber()),
              correctionVoucherNumber,
              reportAccountingWholeKrona(entry.getCreditMinorValue(), "verifikatets korrigeringsdebet"),
              reportAccountingWholeKrona(entry.getDebitMinorValue(), "verifikatets korrigeringskredit"),
              "Correction of " + voucherNumber + ": " + entry.getDescription(),
              voucherDate
          );
          correctionEntry.setCorrectionOfVoucherNumber(voucherNumber);
          return journalEntryRepository.save(correctionEntry);
        })
        .toList();

    String reviewNote = "Correction of " + voucherNumber + " needs review before period close.";
    VoucherApproval approval = voucherApprovalRepository.findByVoucherNumber(correctionVoucherNumber)
        .orElseGet(() -> new VoucherApproval(correctionVoucherNumber, "pending", reviewNote, "AliBooks"));
    approval.update("pending", reviewNote, "AliBooks");
    voucherApprovalRepository.save(approval);

    return correctionEntries;
  }

  @Transactional
  public void deleteEntriesForInvoice(Order invoice) {
    journalEntryRepository.findByInvoice(invoice).forEach(entry -> requireUnlockedAccountingDate(entry.getVoucherDate()));
    journalEntryRepository.deleteByInvoice(invoice);
  }

  public VatReport createVatReport() {
    return createVatReport(null, null);
  }

  public VatReport createVatReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> entriesInPeriod = journalEntryRepository.findAll().stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .toList();
    Map<String, List<JournalEntry>> entriesByVoucher = entriesInPeriod.stream()
        .collect(Collectors.groupingBy(entry -> entry.getVoucherNumber() == null ? "" : entry.getVoucherNumber()));
    Set<String> vatSettlementVoucherKeys = entriesByVoucher.entrySet().stream()
        .filter(entry -> isVatSettlementVoucher(entry.getKey(), entry.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
    List<JournalEntry> entries = entriesInPeriod.stream()
        .filter(entry -> !vatSettlementVoucherKeys.contains(entry.getVoucherNumber() == null ? "" : entry.getVoucherNumber()))
        .toList();
    int outputVat = reportVatWholeKrona(entries.stream()
        .filter(entry -> VatRate.isOutputVatAccount(entry.getAccountNumber()))
        .mapToLong(this::journalMovementMinor)
        .sum());
    int salesBase25 = salesBase(entries, "3041");
    int salesBase12 = salesBase(entries, "3042");
    int salesBase6 = salesBase(entries, "3043");
    int outputVat25 = outputVat(entries, "2611");
    int outputVat12 = outputVat(entries, "2621");
    int outputVat6 = outputVat(entries, "2631");
    int inputVat = reportVatWholeKrona(entries.stream()
        .filter(entry -> entry.getAccountNumber().equals("2641"))
        .mapToLong(entry -> Math.subtractExact(entry.getDebitMinorValue(), entry.getCreditMinorValue()))
        .sum());

    boolean settled = hasVatSettlementForPeriod(periodFrom, periodTo) || !vatSettlementVoucherKeys.isEmpty();
    return new VatReport(periodFrom, periodTo, outputVat, inputVat, reportAmount((long) outputVat - inputVat), settled,
        salesBase25, outputVat25, salesBase12, outputVat12, salesBase6, outputVat6);
  }

  public VatControlReport createVatControlReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> entries = journalEntryRepository.findAll().stream()
        .filter(entry -> periodTo == null || isWithinPeriod(entry.getVoucherDate(), null, periodTo))
        .toList();
    Map<String, List<JournalEntry>> voucherGroups = entries.stream()
        .filter(entry -> entry.getVoucherNumber() != null && !entry.getVoucherNumber().isBlank())
        .collect(Collectors.groupingBy(JournalEntry::getVoucherNumber));
    List<VatControlIssue> issues = new ArrayList<>();
    long totalSalesNet = 0;
    long totalOutputVat = 0;
    long totalExpectedOutputVat = 0;
    long totalPurchaseNet = 0;
    long totalInputVat = 0;

    for (Map.Entry<String, List<JournalEntry>> voucherGroup : voucherGroups.entrySet()) {
      String voucherNumber = voucherGroup.getKey();
      List<JournalEntry> voucherEntries = voucherGroup.getValue();
      if (isVatSettlementVoucher(voucherNumber, voucherEntries)) {
        continue;
      }

      LocalDate voucherDate = voucherEntries.stream()
          .map(JournalEntry::getVoucherDate)
          .filter(date -> date != null)
          .min(LocalDate::compareTo)
          .orElse(null);
      int salesNet = reportAccountingWholeKrona(voucherEntries.stream()
          .filter(entry -> isSalesAccount(entry.getAccountNumber()))
          .mapToLong(this::journalMovementMinor)
          .sum(), "momsavstämningens försäljning");
      int unclassifiedSalesNet = reportAccountingWholeKrona(voucherEntries.stream()
          .filter(entry -> isSalesAccount(entry.getAccountNumber()))
          .filter(entry -> VatRate.salesRate(entry.getAccountNumber()).isEmpty())
          .mapToLong(this::journalMovementMinor)
          .sum(), "momsavstämningens oklassificerade försäljning");
      boolean hasUnclassifiedSales = voucherEntries.stream()
          .anyMatch(entry -> isSalesAccount(entry.getAccountNumber())
              && entry.getCreditMinorValue() != entry.getDebitMinorValue()
              && VatRate.salesRate(entry.getAccountNumber()).isEmpty());
      int outputVat = reportAccountingWholeKrona(voucherEntries.stream()
          .filter(entry -> VatRate.isOutputVatAccount(entry.getAccountNumber()))
          .mapToLong(this::journalMovementMinor)
          .sum(), "momsavstämningens utgående moms");
      int purchaseNet = reportAccountingWholeKrona(voucherEntries.stream()
          .filter(entry -> isPurchaseOrExpenseAccount(entry.getAccountNumber()))
          .mapToLong(entry -> Math.negateExact(journalMovementMinor(entry)))
          .sum(), "momsavstämningens inköp");
      int inputVat = reportAccountingWholeKrona(voucherEntries.stream()
          .filter(entry -> "2641".equals(entry.getAccountNumber()))
          .mapToLong(entry -> Math.negateExact(journalMovementMinor(entry)))
          .sum(), "momsavstämningens ingående moms");
      long expectedOutputVatTotal = 0;
      for (int rate : List.of(6, 12, 25)) {
        String salesAccount = VatRate.salesAccount(rate);
        String outputVatAccount = VatRate.outputVatAccount(rate);
        int rateSalesNet = accountMovement(voucherEntries, salesAccount);
        int rateOutputVat = accountMovement(voucherEntries, outputVatAccount);
        // Round once on the aggregated tax base. Rounding each journal line
        // separately can understate VAT when several small lines share a rate.
        int rateExpectedOutputVat = WholeKronaMath.roundedRatio(rateSalesNet, rate, 100);
        expectedOutputVatTotal += rateExpectedOutputVat;

        if (rateSalesNet != 0 && rateOutputVat == 0 && rateExpectedOutputVat != 0) {
          issues.add(new VatControlIssue(
              "critical", "sales_without_output_vat", voucherNumber, voucherDate,
              rateSalesNet, rateOutputVat, rateExpectedOutputVat, purchaseNet, inputVat,
              vatAt25Percent(purchaseNet), rateExpectedOutputVat,
              "Sales on account " + salesAccount + " have no output VAT on account " + outputVatAccount + "."
          ));
        } else if (rateSalesNet != 0 && Math.abs((long) rateOutputVat - rateExpectedOutputVat) > 1) {
          issues.add(new VatControlIssue(
              "critical", "output_vat_difference", voucherNumber, voucherDate,
              rateSalesNet, rateOutputVat, rateExpectedOutputVat, purchaseNet, inputVat,
              vatAt25Percent(purchaseNet), reportAmount((long) rateOutputVat - rateExpectedOutputVat),
              "Output VAT on account " + outputVatAccount + " differs from the expected amount for " + rate + "% sales."
          ));
        } else if (rateSalesNet == 0 && rateOutputVat != 0 && salesNet != 0) {
          issues.add(new VatControlIssue(
              "critical", "output_vat_difference", voucherNumber, voucherDate,
              salesNet, rateOutputVat, 0, purchaseNet, inputVat, vatAt25Percent(purchaseNet), rateOutputVat,
              "Output VAT is posted to account " + outputVatAccount + " without sales on its matching " + salesAccount + " account."
          ));
        }
      }
      int expectedOutputVat = reportAmount(expectedOutputVatTotal);
      int expectedMaxInputVat = vatAt25Percent(purchaseNet);

      totalSalesNet += salesNet;
      totalOutputVat += outputVat;
      totalExpectedOutputVat += expectedOutputVat;
      totalPurchaseNet += purchaseNet;
      totalInputVat += inputVat;

      if (hasUnclassifiedSales) {
        issues.add(new VatControlIssue(
            "critical",
            "unclassified_sales_vat",
            voucherNumber,
            voucherDate,
            unclassifiedSalesNet,
            outputVat,
            expectedOutputVat,
            purchaseNet,
            inputVat,
            expectedMaxInputVat,
            unclassifiedSalesNet,
            "Sales use an account without a configured VAT rate. Confirm the treatment and map the account before filing."
        ));
      } else if (salesNet == 0 && outputVat != 0) {
        issues.add(new VatControlIssue(
            "warning",
            "output_vat_without_sales",
            voucherNumber,
            voucherDate,
            salesNet,
            outputVat,
            expectedOutputVat,
            purchaseNet,
            inputVat,
            expectedMaxInputVat,
            outputVat,
            "Output VAT exists without sales on a 3xxx account."
        ));
      }

      if (purchaseNet >= 0 && inputVat < 0) {
        issues.add(new VatControlIssue(
            "critical",
            "negative_input_vat",
            voucherNumber,
            voucherDate,
            salesNet,
            outputVat,
            expectedOutputVat,
            purchaseNet,
            inputVat,
            expectedMaxInputVat,
            inputVat,
            "Input VAT on 2641 is negative while the purchase amount is not negative."
        ));
      } else if (purchaseNet > 0 && inputVat == 0) {
        issues.add(new VatControlIssue(
            "warning",
            "purchase_without_input_vat",
            voucherNumber,
            voucherDate,
            salesNet,
            outputVat,
            expectedOutputVat,
            purchaseNet,
            inputVat,
            expectedMaxInputVat,
            -expectedMaxInputVat,
            "Purchase or expense is booked without input VAT on 2641. Confirm VAT-free, reverse-charge or non-deductible VAT before filing."
        ));
      } else if (purchaseNet > 0 && inputVat > expectedMaxInputVat + 1) {
        issues.add(new VatControlIssue(
            "critical",
            "input_vat_too_high",
            voucherNumber,
            voucherDate,
            salesNet,
            outputVat,
            expectedOutputVat,
            purchaseNet,
            inputVat,
            expectedMaxInputVat,
            inputVat - expectedMaxInputVat,
            "Input VAT is higher than 25 percent of booked purchase/expense."
        ));
      } else if (purchaseNet == 0 && inputVat != 0) {
        issues.add(new VatControlIssue(
            "warning",
            "input_vat_without_purchase",
            voucherNumber,
            voucherDate,
            salesNet,
            outputVat,
            expectedOutputVat,
            purchaseNet,
            inputVat,
            expectedMaxInputVat,
            inputVat,
            "Input VAT exists without a purchase or expense account."
        ));
      }
    }

    int expectedOutputVat = reportAmount(totalExpectedOutputVat);
    int expectedMaxInputVat = vatAt25Percent(reportAmount(totalPurchaseNet));
    int criticalIssueCount = (int) issues.stream().filter(issue -> "critical".equals(issue.severity())).count();
    int warningIssueCount = (int) issues.stream().filter(issue -> "warning".equals(issue.severity())).count();

    return new VatControlReport(
        periodFrom,
        periodTo,
        voucherGroups.size(),
        reportAmount(totalSalesNet),
        reportAmount(totalOutputVat),
        expectedOutputVat,
        reportAmount((long) totalOutputVat - expectedOutputVat),
        reportAmount(totalPurchaseNet),
        reportAmount(totalInputVat),
        expectedMaxInputVat,
        reportAmount((long) totalOutputVat - totalInputVat),
        criticalIssueCount,
        warningIssueCount,
        issues.stream()
            .sorted(Comparator
                .comparing(VatControlIssue::severity)
                .thenComparing(issue -> issue.voucherDate() == null ? LocalDate.MAX : issue.voucherDate())
                .thenComparing(issue -> issue.voucherNumber() == null ? "" : issue.voucherNumber()))
            .toList()
    );
  }

  @Transactional
  public List<JournalEntry> createVatSettlementEntry(CreateVatSettlementRequest request) {
    if (request == null || request.periodFrom() == null || request.periodTo() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT period from and to dates are required.");
    }

    if (request.periodFrom().isAfter(request.periodTo())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT period from date cannot be after to date.");
    }

    LocalDate settlementDate = request.settlementDate() == null ? request.periodTo() : request.settlementDate();
    requireUnlockedAccountingDate(settlementDate);

    VatReport report = createVatReport(request.periodFrom(), request.periodTo());
    if (report.outputVat() == 0 && report.inputVat() == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT period is already settled or has no VAT to settle.");
    }

    VatControlReport vatControlReport = createVatControlReport(request.periodFrom(), request.periodTo());
    if (vatControlReport.criticalIssueCount() > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "VAT settlement cannot be booked while critical VAT control issues remain."
      );
    }

    VoucherControlReport voucherControlReport = createVoucherControlReport(request.periodFrom(), request.periodTo());
    if (voucherControlReport.criticalIssueCount() > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "VAT settlement cannot be booked while critical voucher control issues remain."
      );
    }

    String voucherNumber = voucherNumberService.nextVoucherNumber("MOMS");
    String description = "VAT settlement " + request.periodFrom() + " - " + request.periodTo();
    if (journalEntryRepository.findAll().stream().anyMatch(entry -> description.equals(entry.getDescription()))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT period already has a settlement voucher.");
    }

    Account outputVat25 = account("2611");
    Account outputVat12 = account("2621");
    Account outputVat6 = account("2631");
    Account inputVat = account("2641");
    Account vatPayable = account("2650");
    Account vatReceivable = account("1650");
    List<JournalEntry> entries = new ArrayList<>();

    addVatClearingEntry(entries, outputVat25, voucherNumber, report.outputVat25(), true, description, settlementDate);
    addVatClearingEntry(entries, outputVat12, voucherNumber, report.outputVat12(), true, description, settlementDate);
    addVatClearingEntry(entries, outputVat6, voucherNumber, report.outputVat6(), true, description, settlementDate);
    addVatClearingEntry(entries, inputVat, voucherNumber, report.inputVat(), false, description, settlementDate);

    if (report.vatToPay() > 0) {
      entries.add(journalEntryRepository.save(new JournalEntry(null, vatPayable, voucherNumber, 0, report.vatToPay(), description, settlementDate)));
    } else if (report.vatToPay() < 0) {
      entries.add(journalEntryRepository.save(new JournalEntry(null, vatReceivable, voucherNumber, Math.abs(report.vatToPay()), 0, description, settlementDate)));
    }

    return entries;
  }

  public boolean hasVatSettlementForPeriod(LocalDate periodFrom, LocalDate periodTo) {
    if (periodFrom == null || periodTo == null) {
      return false;
    }

    String description = "VAT settlement " + periodFrom + " - " + periodTo;
    return journalEntryRepository.findAll().stream()
        .anyMatch(entry -> description.equals(entry.getDescription()));
  }

  public VatFilingProofReport createVatFilingProofReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    VatFiling filing = vatFilingRepository.findByPeriodFromAndPeriodTo(periodFrom, periodTo).orElse(null);
    VatReport report = createVatReport(periodFrom, periodTo);
    int expectedVatAmount = filing == null ? report.vatToPay() : filing.getVatToPay();
    String filingStatus = filing == null ? "NOT_ARCHIVED" : filing.getStatus();
    String settlementDescription = "VAT settlement " + periodFrom + " - " + periodTo;
    String paymentDescription = expectedVatAmount >= 0
        ? "VAT payment " + periodFrom + " - " + periodTo
        : "VAT refund " + periodFrom + " - " + periodTo;

    List<JournalEntry> entries = journalEntryRepository.findAll();
    List<VatFilingProofVoucher> settlementVouchers = proofVouchers(entries.stream()
        .filter(entry -> settlementDescription.equals(entry.getDescription()))
        .toList());
    List<VatFilingProofVoucher> paymentVouchers = proofVouchers(entries.stream()
        .filter(entry -> entry.getDescription() != null && entry.getDescription().startsWith(paymentDescription))
        .toList());

    boolean settlementVoucherFound = !settlementVouchers.isEmpty();
    boolean paymentVoucherRequired = "PAID".equals(filingStatus) && expectedVatAmount != 0;
    boolean paymentVoucherFound = !paymentVouchers.isEmpty();
    boolean completeForCurrentStatus = settlementVoucherFound && (!paymentVoucherRequired || paymentVoucherFound);
    String message;
    if (!settlementVoucherFound) {
      message = "VAT settlement voucher is missing for this period.";
    } else if (paymentVoucherRequired && !paymentVoucherFound) {
      message = "VAT filing is marked as paid/refunded but the payment voucher is missing.";
    } else if (!"PAID".equals(filingStatus) && expectedVatAmount != 0) {
      message = "VAT settlement is booked. Payment proof is required after the period is marked as paid/refunded.";
    } else {
      message = "VAT filing proof chain is complete for the current status.";
    }

    return new VatFilingProofReport(
        periodFrom,
        periodTo,
        filingStatus,
        expectedVatAmount,
        filing != null,
        settlementVoucherFound,
        paymentVoucherRequired,
        paymentVoucherFound,
        completeForCurrentStatus,
        settlementVouchers,
        paymentVouchers,
        message
    );
  }

  public List<VatFilingProofReport> createVatFilingProofReportsThroughDate(LocalDate lockedThroughDate) {
    if (lockedThroughDate == null) {
      return List.of();
    }

    return vatFilingRepository.findAllByOrderByPeriodToDesc().stream()
        .filter(filing -> filing.getPeriodFrom() != null && filing.getPeriodTo() != null)
        .filter(filing -> !filing.getPeriodTo().isAfter(lockedThroughDate))
        .filter(filing -> !"DRAFT".equals(filing.getStatus()))
        .map(filing -> createVatFilingProofReport(filing.getPeriodFrom(), filing.getPeriodTo()))
        .toList();
  }

  @Transactional
  public List<JournalEntry> createVatFilingPaymentEntry(VatFiling filing, LocalDate paymentDate, String paymentReference) {
    if (filing == null || filing.getPeriodFrom() == null || filing.getPeriodTo() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT filing period is required.");
    }

    int vatAmount = filing.getVatToPay();
    if (vatAmount == 0) {
      return List.of();
    }

    if (!hasVatSettlementForPeriod(filing.getPeriodFrom(), filing.getPeriodTo())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT settlement voucher must be booked before VAT payment is booked.");
    }

    LocalDate voucherDate = requireExplicitAccountingDate(paymentDate, "VAT payment date is required.");
    requireUnlockedAccountingDate(voucherDate);

    String baseDescription = vatAmount > 0
        ? "VAT payment " + filing.getPeriodFrom() + " - " + filing.getPeriodTo()
        : "VAT refund " + filing.getPeriodFrom() + " - " + filing.getPeriodTo();
    if (journalEntryRepository.findAll().stream()
        .anyMatch(entry -> entry.getDescription() != null && entry.getDescription().startsWith(baseDescription))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT filing already has a payment voucher.");
    }

    String cleanReference = paymentReference == null ? "" : paymentReference.trim();
    String description = cleanReference.isBlank() ? baseDescription : baseDescription + " " + cleanReference;
    String voucherNumber = voucherNumberService.nextVoucherNumber("MOMS");
    Account bank = account("1930");
    Account taxClearing = account(vatTaxClearingAccountNumber());
    Account vatPayable = account("2650");
    Account vatReceivable = account("1650");
    int amount = Math.abs(vatAmount);
    List<JournalEntry> entries = new ArrayList<>();

    if (vatAmount > 0) {
      entries.add(journalEntryRepository.save(new JournalEntry(null, vatPayable, voucherNumber, amount, 0, description, voucherDate)));
      entries.add(journalEntryRepository.save(new JournalEntry(null, taxClearing, voucherNumber, 0, amount, description, voucherDate)));
      entries.add(journalEntryRepository.save(new JournalEntry(null, taxClearing, voucherNumber, amount, 0, description, voucherDate)));
      entries.add(journalEntryRepository.save(new JournalEntry(null, bank, voucherNumber, 0, amount, description, voucherDate)));
    } else {
      entries.add(journalEntryRepository.save(new JournalEntry(null, taxClearing, voucherNumber, amount, 0, description, voucherDate)));
      entries.add(journalEntryRepository.save(new JournalEntry(null, vatReceivable, voucherNumber, 0, amount, description, voucherDate)));
      entries.add(journalEntryRepository.save(new JournalEntry(null, bank, voucherNumber, amount, 0, description, voucherDate)));
      entries.add(journalEntryRepository.save(new JournalEntry(null, taxClearing, voucherNumber, 0, amount, description, voucherDate)));
    }

    return entries;
  }

  private String vatTaxClearingAccountNumber() {
    String companyType = settingsService.getSettings().getCompanyType();
    return "LIMITED_COMPANY".equals(companyType) ? "1630" : "2012";
  }

  private List<VatFilingProofVoucher> proofVouchers(List<JournalEntry> entries) {
    return entries.stream()
        .filter(entry -> entry.getVoucherNumber() != null && !entry.getVoucherNumber().isBlank())
        .collect(Collectors.groupingBy(
            JournalEntry::getVoucherNumber,
            LinkedHashMap::new,
            Collectors.toList()
        ))
        .entrySet()
        .stream()
        .map(entry -> new VatFilingProofVoucher(
            entry.getKey(),
            entry.getValue().stream()
                .map(JournalEntry::getVoucherDate)
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .orElse(null),
            entry.getValue().stream()
                .map(JournalEntry::getDescription)
                .filter(description -> description != null && !description.isBlank())
                .findFirst()
                .orElse(""),
            reportAccountingWholeKrona(
                entry.getValue().stream().mapToLong(JournalEntry::getDebitMinorValue).reduce(0L, Math::addExact),
                "momsbevisets debetsumma"),
            reportAccountingWholeKrona(
                entry.getValue().stream().mapToLong(JournalEntry::getCreditMinorValue).reduce(0L, Math::addExact),
                "momsbevisets kreditsumma")
        ))
        .toList();
  }

  public ProfitAndLossReport createProfitAndLossReport() {
    return createProfitAndLossReport(null, null);
  }

  public ProfitAndLossReport createProfitAndLossReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> entries = journalEntryRepository.findAll().stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .toList();
    List<ReportLine> revenue = reportLines(entries, "3", true);
    List<ReportLine> expenses = entries.stream()
        .filter(entry -> isProfitAndLossExpenseAccount(entry.getAccountNumber()))
        .collect(Collectors.groupingBy(
            entry -> entry.getAccountNumber() + "|" + entry.getAccountName(),
            Collectors.summingLong(entry -> accountingReportMovementMinor(
                entry,
                false,
                "resultatrapportens kostnadspost"))
        ))
        .entrySet()
        .stream()
        .map(this::toReportLine)
        .toList();
    int totalRevenue = reportAmount(revenue.stream().mapToLong(ReportLine::amount).sum());
    int totalExpenses = reportAmount(expenses.stream().mapToLong(ReportLine::amount).sum());

    return new ProfitAndLossReport(periodFrom, periodTo, revenue, expenses, totalRevenue, totalExpenses, reportAmount((long) totalRevenue - totalExpenses));
  }

  public BalanceReport createBalanceReport() {
    return createBalanceReport(null);
  }

  public BalanceReport createBalanceReport(LocalDate asOfDate) {
    List<JournalEntry> entries = journalEntryRepository.findAll().stream()
        .filter(entry -> asOfDate == null || isWithinPeriod(entry.getVoucherDate(), null, asOfDate))
        .toList();
    List<ReportLine> assets = reportLines(entries, "1", false);
    List<ReportLine> liabilitiesAndEquity = reportLines(entries, "2", true);
    ProfitAndLossReport profitAndLossReport = createProfitAndLossReport(null, asOfDate);
    int result = profitAndLossReport.result();

    liabilitiesAndEquity = new java.util.ArrayList<>(liabilitiesAndEquity);
    ReportLine yearResultLine = yearResultLine(result);
    int year = asOfDate == null ? LocalDate.now().getYear() : asOfDate.getYear();
    if (!hasAnnualResultVoucher(year, yearResultLine.accountNumber())) {
      liabilitiesAndEquity.add(yearResultLine);
    }

    int totalAssets = reportAmount(assets.stream().mapToLong(ReportLine::amount).sum());
    int totalLiabilitiesAndEquity = reportAmount(liabilitiesAndEquity.stream().mapToLong(ReportLine::amount).sum());

    return new BalanceReport(
        asOfDate,
        assets,
        liabilitiesAndEquity,
        totalAssets,
        totalLiabilitiesAndEquity,
        reportAmount((long) totalAssets - totalLiabilitiesAndEquity)
    );
  }

  public AnnualResultVoucherPreview createAnnualResultVoucherPreview(Integer year, LocalDate voucherDate) {
    int normalizedYear = normalizeAnnualResultYear(year);
    LocalDate periodFrom = LocalDate.of(normalizedYear, 1, 1);
    LocalDate periodTo = LocalDate.of(normalizedYear, 12, 31);
    LocalDate normalizedVoucherDate = voucherDate == null ? periodTo : voucherDate;
    String companyType = settingsService.getSettings().getCompanyType();
    Account resultAccount = annualResultAccount(companyType);
    ProfitAndLossReport profitAndLossReport = createProfitAndLossReport(periodFrom, periodTo);
    int result = profitAndLossReport.result();
    int amount = Math.abs(result);
    boolean profit = result > 0;

    return new AnnualResultVoucherPreview(
        normalizedYear,
        periodFrom,
        periodTo,
        normalizedVoucherDate,
        companyType,
        resultAccount.getNumber(),
        resultAccount.getName(),
        result,
        profit ? "8999" : resultAccount.getNumber(),
        profit ? resultAccount.getNumber() : "8999",
        amount,
        hasAnnualResultVoucher(normalizedYear, resultAccount.getNumber())
    );
  }

  @Transactional
  public List<JournalEntry> createAnnualResultVoucher(CreateAnnualResultVoucherRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annual result voucher request is required.");
    }

    AnnualResultVoucherPreview preview = createAnnualResultVoucherPreview(request.year(), request.voucherDate());
    requireUnlockedAccountingDate(preview.voucherDate());

    if (preview.voucherDate().isBefore(preview.periodFrom()) || preview.voucherDate().isAfter(preview.periodTo())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher date must be inside the selected fiscal year.");
    }

    if (preview.alreadyBooked()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annual result voucher already exists for " + preview.year() + ".");
    }

    if (preview.amount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Year result is 0 SEK. No annual result voucher is needed.");
    }

    String voucherNumber = voucherNumberService.nextVoucherNumber("BR");
    String description = annualResultDescription(preview.year());
    JournalEntry debitEntry = journalEntryRepository.save(new JournalEntry(
        null,
        account(preview.debitAccountNumber()),
        voucherNumber,
        preview.amount(),
        0,
        description,
        preview.voucherDate()
    ));
    JournalEntry creditEntry = journalEntryRepository.save(new JournalEntry(
        null,
        account(preview.creditAccountNumber()),
        voucherNumber,
        0,
        preview.amount(),
        description,
        preview.voucherDate()
    ));

    return List.of(debitEntry, creditEntry);
  }

  public TrialBalanceReport createTrialBalanceReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<TrialBalanceLine> lines = journalEntryRepository.findAll()
        .stream()
        .filter(entry -> entry.getAccountNumber() != null && !entry.getAccountNumber().isBlank())
        .collect(Collectors.groupingBy(entry -> entry.getAccountNumber() + "|" + entry.getAccountName()))
        .entrySet()
        .stream()
        .map(entry -> toTrialBalanceLine(entry, periodFrom, periodTo))
        .filter(line -> line.openingDebit() != 0
            || line.openingCredit() != 0
            || line.periodDebit() != 0
            || line.periodCredit() != 0
            || line.closingDebit() != 0
            || line.closingCredit() != 0)
        .sorted(Comparator.comparing(TrialBalanceLine::accountNumber))
        .toList();

    int openingDebitTotal = reportAmount(lines.stream().mapToLong(TrialBalanceLine::openingDebit).sum());
    int openingCreditTotal = reportAmount(lines.stream().mapToLong(TrialBalanceLine::openingCredit).sum());
    int periodDebitTotal = reportAmount(lines.stream().mapToLong(TrialBalanceLine::periodDebit).sum());
    int periodCreditTotal = reportAmount(lines.stream().mapToLong(TrialBalanceLine::periodCredit).sum());
    int closingDebitTotal = reportAmount(lines.stream().mapToLong(TrialBalanceLine::closingDebit).sum());
    int closingCreditTotal = reportAmount(lines.stream().mapToLong(TrialBalanceLine::closingCredit).sum());

    return new TrialBalanceReport(
        periodFrom,
        periodTo,
        lines,
        openingDebitTotal,
        openingCreditTotal,
        periodDebitTotal,
        periodCreditTotal,
        closingDebitTotal,
        closingCreditTotal,
        closingDebitTotal - closingCreditTotal
    );
  }

  public AccountSignControlReport createAccountSignControlReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> entries = journalEntryRepository.findAll().stream()
        .filter(entry -> isOnOrBeforePeriodEnd(entry.getVoucherDate(), periodTo))
        .toList();
    List<AccountSignControlLine> lines = accountSignRules().stream()
        .map(rule -> toAccountSignControlLine(entries, rule))
        .toList();
    int criticalIssueCount = Math.toIntExact(lines.stream()
        .filter(line -> "critical".equals(line.status()))
        .count());
    int warningIssueCount = Math.toIntExact(lines.stream()
        .filter(line -> "warning".equals(line.status()))
        .count());

    return new AccountSignControlReport(
        periodFrom,
        periodTo,
        Instant.now(),
        lines.size(),
        criticalIssueCount + warningIssueCount,
        criticalIssueCount,
        warningIssueCount,
        lines
    );
  }

  private AccountSignControlLine toAccountSignControlLine(List<JournalEntry> entries, AccountSignRule rule) {
    long balanceMinor = entries.stream()
        .filter(entry -> rule.accountNumber().equals(entry.getAccountNumber()))
        .mapToLong(entry -> accountingReportMovementMinor(
            entry,
            rule.creditNature(),
            "kontoteckenkontrollens kontopost"))
        .reduce(0L, Math::addExact);
    int balance = reportAccountingWholeKrona(balanceMinor, "kontoteckenkontrollens saldo");
    boolean hasIssue = balance < -1;
    String status = hasIssue ? rule.severity() : "ok";
    String expectedNature = rule.creditNature() ? "credit" : "debit";
    String message = hasIssue
        ? rule.accountNumber() + " " + rule.accountName() + " har ovantat minussaldo " + balance + " SEK."
        : rule.accountNumber() + " " + rule.accountName() + " har rimligt saldo for kontotypen.";

    return new AccountSignControlLine(
        rule.accountNumber(),
        rule.accountName(),
        expectedNature,
        balance,
        rule.severity(),
        status,
        rule.blocking(),
        message
    );
  }

  private boolean isOnOrBeforePeriodEnd(LocalDate voucherDate, LocalDate periodTo) {
    if (voucherDate == null) {
      return periodTo == null;
    }

    return periodTo == null || !voucherDate.isAfter(periodTo);
  }

  private List<AccountSignRule> accountSignRules() {
    return List.of(
        new AccountSignRule("1930", "Foretagskonto", false, true, "critical"),
        new AccountSignRule("1510", "Kundfordringar", false, true, "critical"),
        new AccountSignRule("1220", "Inventarier och verktyg", false, false, "warning"),
        new AccountSignRule("1229", "Ackumulerade avskrivningar pa inventarier", true, false, "warning"),
        new AccountSignRule("2440", "Leverantorsskulder", true, true, "critical"),
        new AccountSignRule("2890", "Kortskuld och kortclearing", true, false, "warning"),
        new AccountSignRule("2510", "Skatteskulder", true, true, "critical"),
        new AccountSignRule("2710", "Personalskatt", true, true, "critical"),
        new AccountSignRule("2731", "Arbetsgivaravgift", true, true, "critical"),
        new AccountSignRule("1580", "Stripe-fordran", false, false, "warning"),
        new AccountSignRule("2611", "Utgaende moms", true, false, "warning"),
        new AccountSignRule("2621", "Utgaende moms 12 procent", true, false, "warning"),
        new AccountSignRule("2631", "Utgaende moms 6 procent", true, false, "warning"),
        new AccountSignRule("2641", "Ingaende moms", false, false, "warning")
    );
  }

  public GeneralLedgerReport createGeneralLedgerReport(LocalDate periodFrom, LocalDate periodTo, String accountNumber) {
    validatePeriod(periodFrom, periodTo);

    String selectedAccountNumber = accountNumber == null || accountNumber.isBlank() ? null : accountNumber.trim();
    List<JournalEntry> entries = journalEntryRepository.findAll()
        .stream()
        .filter(entry -> entry.getAccountNumber() != null && !entry.getAccountNumber().isBlank())
        .filter(entry -> selectedAccountNumber == null || selectedAccountNumber.equals(entry.getAccountNumber()))
        .sorted(Comparator
            .comparing(JournalEntry::getVoucherDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(entry -> entry.getVoucherNumber() == null ? "" : entry.getVoucherNumber())
            .thenComparing(entry -> entry.getId() == null ? 0L : entry.getId()))
        .toList();

    List<GeneralLedgerAccount> accounts = entries.stream()
        .collect(Collectors.groupingBy(JournalEntry::getAccountNumber))
        .entrySet()
        .stream()
        .map(entry -> toGeneralLedgerAccount(entry.getKey(), entry.getValue(), periodFrom, periodTo))
        .filter(account -> account.openingBalance() != 0 || account.periodDebit() != 0 || account.periodCredit() != 0)
        .sorted(Comparator.comparing(GeneralLedgerAccount::accountNumber))
        .toList();

    int entryCount = Math.toIntExact(accounts.stream().mapToLong(account -> account.entries().size()).sum());
    int periodDebitTotal = reportAmount(accounts.stream().mapToLong(GeneralLedgerAccount::periodDebit).sum());
    int periodCreditTotal = reportAmount(accounts.stream().mapToLong(GeneralLedgerAccount::periodCredit).sum());

    return new GeneralLedgerReport(
        periodFrom,
        periodTo,
        selectedAccountNumber,
        accounts,
        accounts.size(),
        entryCount,
        periodDebitTotal,
      periodCreditTotal
    );
  }

  public JournalIntegrityReport createJournalIntegrityReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> entries = journalEntryRepository.findAll()
        .stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .sorted(Comparator
            .comparing(JournalEntry::getVoucherDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(entry -> entry.getVoucherNumber() == null ? "" : entry.getVoucherNumber())
            .thenComparing(entry -> entry.getAccountNumber() == null ? "" : entry.getAccountNumber())
            .thenComparing(entry -> entry.getId() == null ? 0L : entry.getId()))
        .toList();

    List<JournalIntegrityLine> lines = new ArrayList<>();
    String previousChainHash = "START";
    for (int index = 0; index < entries.size(); index++) {
      JournalEntry entry = entries.get(index);
      String rowHash = entry.getIntegrityHash();
      int debit = reportAccountingWholeKrona(entry.getDebitMinorValue(), "revisionsspårets debetpost");
      int credit = reportAccountingWholeKrona(entry.getCreditMinorValue(), "revisionsspårets kreditpost");
      String chainHash = sha256(String.join("|",
          previousChainHash,
          rowHash,
          entry.getEvidenceHash(),
          value(entry.getVoucherDate()),
          value(entry.getVoucherNumber()),
          value(entry.getAccountNumber()),
          String.valueOf(entry.getDebitMinorValue()),
          String.valueOf(entry.getCreditMinorValue())
      ));

      lines.add(new JournalIntegrityLine(
          index + 1,
          entry.getId(),
          entry.getVoucherDate(),
          entry.getVoucherNumber(),
          entry.getAccountNumber(),
          entry.getAccountName(),
          entry.getDescription(),
          debit,
          credit,
          entry.getSourceType(),
          entry.getSourceReference(),
          entry.getEvidenceStatus(),
          entry.getEvidenceHash(),
          rowHash,
          previousChainHash,
          chainHash
      ));
      previousChainHash = chainHash;
    }

    long totalDebitMinor = entries.stream().mapToLong(JournalEntry::getDebitMinorValue).reduce(0L, Math::addExact);
    long totalCreditMinor = entries.stream().mapToLong(JournalEntry::getCreditMinorValue).reduce(0L, Math::addExact);
    int totalDebit = reportAccountingWholeKrona(totalDebitMinor, "revisionsspårets totaldebet");
    int totalCredit = reportAccountingWholeKrona(totalCreditMinor, "revisionsspårets totalkredit");
    int missingEvidenceCount = (int) entries.stream()
        .filter(entry -> !"traceable".equals(entry.getEvidenceStatus()))
        .count();
    int voucherCount = (int) entries.stream()
        .map(JournalEntry::getVoucherNumber)
        .filter(voucherNumber -> voucherNumber != null && !voucherNumber.isBlank())
        .distinct()
        .count();
    String firstChainHash = lines.isEmpty() ? "" : lines.get(0).chainHash();
    String finalChainHash = lines.isEmpty() ? "" : lines.get(lines.size() - 1).chainHash();
    String periodFingerprint = sha256(String.join("|",
        value(periodFrom),
        value(periodTo),
        String.valueOf(entries.size()),
        String.valueOf(voucherCount),
        String.valueOf(totalDebitMinor),
        String.valueOf(totalCreditMinor),
        String.valueOf(totalDebitMinor - totalCreditMinor),
        String.valueOf(missingEvidenceCount),
        firstChainHash,
        finalChainHash
    ));

    return new JournalIntegrityReport(
        periodFrom,
        periodTo,
        Instant.now(),
        entries.size(),
        voucherCount,
        totalDebit,
        totalCredit,
        totalDebit - totalCredit,
        missingEvidenceCount,
        firstChainHash,
        finalChainHash,
        periodFingerprint,
        lines
    );
  }

  public String createSieExport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> entries = journalEntryRepository.findAll()
        .stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .sorted(Comparator
            .comparing(JournalEntry::getVoucherDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(entry -> entry.getVoucherNumber() == null ? "" : entry.getVoucherNumber())
            .thenComparing(entry -> entry.getId() == null ? 0L : entry.getId()))
        .toList();

    if (entries.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There are no journal entries to export.");
    }

    entries.forEach(entry -> {
      reportAccountingWholeKrona(entry.getDebitMinorValue(), "SIE-exportens debetpost");
      reportAccountingWholeKrona(entry.getCreditMinorValue(), "SIE-exportens kreditpost");
    });

    Map<String, List<JournalEntry>> voucherGroups = entries.stream()
        .collect(Collectors.groupingBy(
            entry -> entry.getVoucherNumber() == null || entry.getVoucherNumber().isBlank()
                ? "Utan verifikat"
                : entry.getVoucherNumber(),
            LinkedHashMap::new,
            Collectors.toList()
        ));

    List<String> unbalancedVouchers = voucherGroups.entrySet()
        .stream()
        .filter(entry -> entry.getValue().stream().mapToLong(JournalEntry::getDebitMinorValue).sum()
            != entry.getValue().stream().mapToLong(JournalEntry::getCreditMinorValue).sum())
        .map(Map.Entry::getKey)
        .toList();

    if (!unbalancedVouchers.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "SIE export stopped because these vouchers do not balance: " + String.join(", ", unbalancedVouchers)
      );
    }

    VoucherControlReport voucherControlReport = createVoucherControlReport(periodFrom, periodTo);
    if (voucherControlReport.criticalIssueCount() > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "SIE export stopped because critical voucher control issues remain."
      );
    }

    LocalDate periodStart = periodFrom == null
        ? entries.stream().map(JournalEntry::getVoucherDate).filter(date -> date != null).min(LocalDate::compareTo).orElse(LocalDate.now().withDayOfYear(1))
        : periodFrom;
    LocalDate periodEnd = periodTo == null
        ? entries.stream().map(JournalEntry::getVoucherDate).filter(date -> date != null).max(LocalDate::compareTo).orElse(LocalDate.now())
        : periodTo;

    Map<String, String> accounts = new TreeMap<>();
    accountRepository.findAll().forEach(account -> accounts.put(account.getNumber(), account.getName()));
    entries.forEach(entry -> {
      if (entry.getAccountNumber() != null && !entry.getAccountNumber().isBlank()) {
        accounts.putIfAbsent(entry.getAccountNumber(), entry.getAccountName());
      }
    });

    List<String> lines = new ArrayList<>();
    lines.add("#FLAGGA 0");
    lines.add("#PROGRAM " + sieString("AliBooks") + " " + sieString("0.1"));
    lines.add("#SIETYP 4");
    lines.add("#FORMAT PC8");
    lines.add("#GEN " + sieDate(LocalDate.now()));
    lines.add("#FNAMN " + sieString(settingsService.getSettings().getCompanyName() == null ? "AliBooks" : settingsService.getSettings().getCompanyName()));
    lines.add("#RAR 0 " + sieDate(periodStart) + " " + sieDate(periodEnd));
    lines.add("");

    accounts.forEach((number, name) -> lines.add("#KONTO " + number + " " + sieString(name == null || name.isBlank() ? number : name)));
    lines.add("");

    voucherGroups.forEach((voucherNumber, voucherEntries) -> {
      JournalEntry firstEntry = voucherEntries.get(0);
      LocalDate voucherDate = firstEntry.getVoucherDate() == null ? periodEnd : firstEntry.getVoucherDate();
      String voucherText = firstEntry.getDescription() == null || firstEntry.getDescription().isBlank()
          ? voucherNumber
          : firstEntry.getDescription();

      lines.add("#VER " + sieString("A") + " " + sieString(voucherNumber) + " " + sieDate(voucherDate) + " " + sieString(voucherText));
      lines.add("{");
      voucherEntries.forEach(entry -> {
        int amount = reportAccountingWholeKrona(
            entry.getDebitMinorValue() - entry.getCreditMinorValue(),
            "SIE-exportens verifikatbelopp"
        );
        lines.add("  #TRANS "
            + (entry.getAccountNumber() == null || entry.getAccountNumber().isBlank() ? "0000" : entry.getAccountNumber())
            + " {} "
            + sieAmount(amount)
            + " "
            + sieString(entry.getDescription() == null ? voucherText : entry.getDescription()));
      });
      lines.add("}");
      lines.add("");
    });

    return String.join("\r\n", lines);
  }

  public SieExportReceipt createSieExportReceipt(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> entries = journalEntryRepository.findAll()
        .stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .toList();
    LocalDate periodStart = periodFrom == null
        ? entries.stream().map(JournalEntry::getVoucherDate).filter(date -> date != null).min(LocalDate::compareTo).orElse(LocalDate.now().withDayOfYear(1))
        : periodFrom;
    LocalDate periodEnd = periodTo == null
        ? entries.stream().map(JournalEntry::getVoucherDate).filter(date -> date != null).max(LocalDate::compareTo).orElse(LocalDate.now())
        : periodTo;
    VoucherControlReport voucherControlReport = createVoucherControlReport(periodFrom, periodTo);
    JournalIntegrityReport journalIntegrityReport = createJournalIntegrityReport(periodFrom, periodTo);
    int accountCount = (int) entries.stream()
        .map(JournalEntry::getAccountNumber)
        .filter(accountNumber -> accountNumber != null && !accountNumber.isBlank())
        .distinct()
        .count();
    long totalDebitMinor = entries.stream().mapToLong(JournalEntry::getDebitMinorValue).reduce(0L, Math::addExact);
    long totalCreditMinor = entries.stream().mapToLong(JournalEntry::getCreditMinorValue).reduce(0L, Math::addExact);
    int totalDebit = reportAccountingWholeKrona(totalDebitMinor, "SIE-kvittots totaldebet");
    int totalCredit = reportAccountingWholeKrona(totalCreditMinor, "SIE-kvittots totalkredit");
    boolean exportReady = !entries.isEmpty()
        && totalDebit == totalCredit
        && voucherControlReport.criticalIssueCount() == 0
        && journalIntegrityReport.difference() == 0;
    String message;
    if (entries.isEmpty()) {
      message = "No journal entries in the selected period.";
    } else if (totalDebit != totalCredit || journalIntegrityReport.difference() != 0) {
      message = "SIE export is not ready because debit and credit do not balance.";
    } else if (voucherControlReport.criticalIssueCount() > 0) {
      message = "SIE export has critical voucher issues that should be fixed before handoff.";
    } else if (voucherControlReport.warningIssueCount() > 0) {
      message = "SIE export is balanced, but voucher warnings should be reviewed.";
    } else {
      message = "SIE export is ready for accountant handoff.";
    }
    String exportControlHash = sha256(String.join("|",
        value(periodStart),
        value(periodEnd),
        value(settingsService.getSettings().getCompanyName()),
        String.valueOf(voucherControlReport.voucherCount()),
        String.valueOf(entries.size()),
        String.valueOf(accountCount),
        String.valueOf(totalDebit),
        String.valueOf(totalCredit),
        String.valueOf(totalDebit - totalCredit),
        String.valueOf(voucherControlReport.criticalIssueCount()),
        String.valueOf(voucherControlReport.warningIssueCount()),
        value(journalIntegrityReport.periodFingerprint()),
        value(journalIntegrityReport.finalChainHash())
    ));

    return new SieExportReceipt(
        periodStart,
        periodEnd,
        Instant.now(),
        settingsService.getSettings().getCompanyName(),
        exportReady,
        message,
        voucherControlReport.voucherCount(),
        entries.size(),
        accountCount,
        totalDebit,
        totalCredit,
        totalDebit - totalCredit,
        voucherControlReport.criticalIssueCount(),
        voucherControlReport.warningIssueCount(),
        voucherControlReport.unbalancedVoucherCount(),
        journalIntegrityReport.missingEvidenceCount(),
        journalIntegrityReport.periodFingerprint(),
        journalIntegrityReport.finalChainHash(),
        exportControlHash
    );
  }

  public AccountantPackageReport createAccountantPackageReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    ProfitAndLossReport profitAndLossReport = createProfitAndLossReport(periodFrom, periodTo);
    BalanceReport balanceReport = createBalanceReport(periodTo);
    VatReport vatReport = createVatReport(periodFrom, periodTo);
    TrialBalanceReport trialBalanceReport = createTrialBalanceReport(periodFrom, periodTo);
    VoucherControlReport voucherControlReport = createVoucherControlReport(periodFrom, periodTo);
    VatControlReport vatControlReport = createVatControlReport(periodFrom, periodTo);
    AccountSignControlReport accountSignControlReport = createAccountSignControlReport(periodFrom, periodTo);
    BankReconciliationReport bankReconciliationReport = bankReconciliationService.createReport(periodFrom, periodTo);
    ReceivablesAgingReport receivablesReport = receivablesReportService.createAgingReport(periodTo);
    PayablesAgingReport payablesReport = payablesReportService.createAgingReport(periodTo);
    JournalIntegrityReport journalIntegrityReport = createJournalIntegrityReport(periodFrom, periodTo);
    List<JournalEntry> periodEntries = journalEntryRepository.findAll()
        .stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .toList();
    VoucherApprovalStats voucherApprovalStats = calculateVoucherApprovalStats(periodEntries);
    boolean sieExportReady = !periodEntries.isEmpty() && voucherControlReport.criticalIssueCount() == 0;

    List<AccountantPackageItem> items = new ArrayList<>();
    items.add(new AccountantPackageItem(
        "Resultat och balans",
        balanceReport.difference() == 0 ? "ok" : "critical",
        "Resultat " + profitAndLossReport.result() + " SEK, balansdifferens " + balanceReport.difference() + " SEK.",
        "/profit-and-loss/export och /balance-report/export"
    ));
    items.add(new AccountantPackageItem(
        "Saldobalans",
        trialBalanceReport.difference() == 0 ? "ok" : "critical",
        "Saldobalans differens " + trialBalanceReport.difference() + " SEK.",
        "/trial-balance/export"
    ));
    items.add(new AccountantPackageItem(
        "Kontotecken",
        accountSignControlReport.criticalIssueCount() > 0 ? "critical" : accountSignControlReport.warningIssueCount() > 0 ? "warning" : "ok",
        accountSignControlReport.accountCount() + " konton kontrollerade, " + accountSignControlReport.criticalIssueCount() + " kritiska och " + accountSignControlReport.warningIssueCount() + " varningar.",
        "/account-sign-control/export"
    ));
    items.add(new AccountantPackageItem(
        "Verifikationer",
        voucherControlReport.criticalIssueCount() > 0 ? "critical" : voucherControlReport.warningIssueCount() > 0 ? "warning" : "ok",
        voucherControlReport.voucherCount() + " verifikat, " + voucherControlReport.criticalIssueCount() + " kritiska och " + voucherControlReport.warningIssueCount() + " varningar.",
        "/journal-entries/export, /journal-integrity/export och /voucher-control/export"
    ));
    items.add(new AccountantPackageItem(
        "Verifikationsattest",
        voucherApprovalStats.blocked() > 0 || voucherApprovalStats.missing() > 0 || voucherApprovalStats.pending() > 0 ? "critical" : "ok",
        voucherApprovalStats.approved() + " godkanda, " + voucherApprovalStats.missing() + " saknar attest, " + voucherApprovalStats.pending() + " vantar och " + voucherApprovalStats.blocked() + " blockerade.",
        "/voucher-approvals och verifikationsattest.csv"
    ));
    items.add(new AccountantPackageItem(
        "Moms",
        vatControlReport.criticalIssueCount() > 0 ? "critical" : vatControlReport.warningIssueCount() > 0 ? "warning" : "ok",
        "Moms att betala/fa tillbaka " + vatReport.vatToPay() + " SEK, " + vatControlReport.criticalIssueCount() + " kritiska momspunkter.",
        "/vat-control/export"
    ));
    items.add(new AccountantPackageItem(
        "Bankavstamning",
        bankReconciliationReport.criticalIssueCount() > 0 ? "critical" : bankReconciliationReport.warningIssueCount() > 0 ? "warning" : "ok",
        "Bankdifferens " + bankReconciliationReport.difference() + " SEK, " + bankReconciliationReport.criticalIssueCount() + " kritiska och " + bankReconciliationReport.warningIssueCount() + " varningar.",
        "/bank-reconciliations/report/export"
    ));
    items.add(new AccountantPackageItem(
        "Kundreskontra",
        receivablesReport.totalOutstanding() > 0 ? "warning" : "ok",
        receivablesReport.invoiceCount() + " oppna kundfakturor, " + receivablesReport.totalOutstanding() + " SEK utestaende och " + receivablesReport.overdueOutstanding() + " SEK forfallet.",
        "/receivables/aging/export"
    ));
    items.add(new AccountantPackageItem(
        "Leverantorsreskontra",
        payablesReport.totalOutstanding() > 0 ? "warning" : "ok",
        payablesReport.invoiceCount() + " oppna leverantorsfakturor, " + payablesReport.totalOutstanding() + " SEK att betala och " + payablesReport.overdueOutstanding() + " SEK forfallet.",
        "/payables/aging/export"
    ));
    items.add(new AccountantPackageItem(
        "Beviskedja",
        journalIntegrityReport.difference() != 0 || journalIntegrityReport.missingEvidenceCount() > 0 ? "critical" : "ok",
        "Bokforingskedja differens " + journalIntegrityReport.difference() + " SEK, " + journalIntegrityReport.missingEvidenceCount() + " rader saknar tydlig kallkoppling. Periodstampel " + journalIntegrityReport.periodFingerprint() + ".",
        "/journal-integrity/export"
    ));
    items.add(new AccountantPackageItem(
        "SIE",
        sieExportReady ? "ok" : "warning",
        sieExportReady
            ? "SIE kan exporteras for vald period. Spara aven SIE-kvittens med kontrollkod."
            : "SIE kraver bokforingsrader utan kritiska verifikationsfel.",
        "/sie/export och /sie/receipt/export"
    ));
    items.add(new AccountantPackageItem(
        "Systemdokumentation",
        "ok",
        "Beskriver kontoplan, verifikationsserier, automatiska bokforingsfloden, kontroller och exportvagar.",
        "/system-documentation/export"
    ));
    items.add(new AccountantPackageItem(
        "Backup och underlag",
        "warning",
        "Backend kan inte verifiera lokal JSON-backup eller kvittofiler har. Exportera backup och underlag separat innan overlamning.",
        "JSON-backup, faktura-PDF och kvitton"
    ));

    int readyItemCount = (int) items.stream().filter(item -> "ok".equals(item.status())).count();
    int warningItemCount = (int) items.stream().filter(item -> "warning".equals(item.status())).count();
    int criticalItemCount = (int) items.stream().filter(item -> "critical".equals(item.status())).count();

    return new AccountantPackageReport(
        periodFrom,
        periodTo,
        settingsService.getSettings().getCompanyName() == null ? "AliBooks" : settingsService.getSettings().getCompanyName(),
        profitAndLossReport.totalRevenue(),
        profitAndLossReport.totalExpenses(),
        profitAndLossReport.result(),
        balanceReport.totalAssets(),
        balanceReport.totalLiabilitiesAndEquity(),
        balanceReport.difference(),
        vatReport.outputVat(),
        vatReport.inputVat(),
        vatReport.vatToPay(),
        voucherControlReport.voucherCount(),
        periodEntries.size(),
        trialBalanceReport.difference(),
        voucherControlReport.criticalIssueCount(),
        vatControlReport.criticalIssueCount(),
        accountSignControlReport.criticalIssueCount(),
        accountSignControlReport.warningIssueCount(),
        bankReconciliationReport.difference(),
        bankReconciliationReport.criticalIssueCount(),
        bankReconciliationReport.warningIssueCount(),
        receivablesReport.invoiceCount(),
        receivablesReport.totalOutstanding(),
        receivablesReport.overdueOutstanding(),
        receivablesReport.dueSoonOutstanding(),
        payablesReport.invoiceCount(),
        payablesReport.totalOutstanding(),
        payablesReport.overdueOutstanding(),
        payablesReport.dueSoonOutstanding(),
        journalIntegrityReport.difference(),
        journalIntegrityReport.missingEvidenceCount(),
        journalIntegrityReport.periodFingerprint(),
        journalIntegrityReport.finalChainHash(),
        voucherApprovalStats.approved(),
        voucherApprovalStats.missing(),
        voucherApprovalStats.pending(),
        voucherApprovalStats.blocked(),
        sieExportReady,
        readyItemCount,
        warningItemCount,
        criticalItemCount,
        items
    );
  }

  private VoucherApprovalStats calculateVoucherApprovalStats(List<JournalEntry> periodEntries) {
    List<String> voucherNumbers = periodEntries.stream()
        .map(JournalEntry::getVoucherNumber)
        .filter(voucherNumber -> voucherNumber != null && !voucherNumber.isBlank())
        .distinct()
        .toList();
    Map<String, String> approvalStatuses = voucherApprovalRepository.findAll().stream()
        .filter(approval -> approval.getVoucherNumber() != null && !approval.getVoucherNumber().isBlank())
        .collect(Collectors.toMap(
            VoucherApproval::getVoucherNumber,
            approval -> approval.getStatus() == null ? "" : approval.getStatus(),
            (first, second) -> second
        ));

    int approved = Math.toIntExact(voucherNumbers.stream()
        .filter(voucherNumber -> "approved".equals(approvalStatuses.get(voucherNumber)))
        .count());
    int missing = Math.toIntExact(voucherNumbers.stream()
        .filter(voucherNumber -> !approvalStatuses.containsKey(voucherNumber))
        .count());
    int pending = Math.toIntExact(voucherNumbers.stream()
        .filter(voucherNumber -> "pending".equals(approvalStatuses.get(voucherNumber)))
        .count());
    int blocked = Math.toIntExact(voucherNumbers.stream()
        .filter(voucherNumber -> "blocked".equals(approvalStatuses.get(voucherNumber)))
        .count());

    return new VoucherApprovalStats(approved, missing, pending, blocked);
  }

  private record VoucherApprovalStats(int approved, int missing, int pending, int blocked) {
  }

  public SystemDocumentationReport createSystemDocumentationReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<Account> accounts = accountRepository.findAll().stream()
        .sorted(Comparator.comparing(Account::getNumber))
        .toList();
    List<JournalEntry> entries = journalEntryRepository.findAll().stream()
        .filter(entry -> isWithinControlPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .toList();
    List<String> voucherSeries = entries.stream()
        .map(JournalEntry::getVoucherSeries)
        .filter(series -> series != null && !series.isBlank())
        .distinct()
        .sorted()
        .toList();

    List<SystemDocumentationItem> items = new ArrayList<>();
    items.add(new SystemDocumentationItem(
        "Systemoversikt",
        "Installningar och period",
        "AliBooks anvander foretagsinstallningar for foretagsform, bokforingsmetod, momsperiod, rakenskapsar och standardmoms.",
        "AppSettings",
        "Kontrollera foretagsform, bokforingsmetod och momsperiod innan skarp bokforing eller bokslut.",
        "/settings och /system-documentation/export"
    ));
    items.add(new SystemDocumentationItem(
        "Kontoplan",
        "BAS-nara kontoplan",
        accounts.size() + " konton finns i kontoplanen. Konton anvands av automatisk bokforing, rapporter, momsrapport, saldobalans och huvudbok.",
        "AccountSeeder, AccountRepository",
        "Kontrollera att konton passar foretagsformen, sarskilt 201x for enskild firma och 20xx/2099 for aktiebolag.",
        "/accounts och /trial-balance/export"
    ));
    items.add(new SystemDocumentationItem(
        "Verifikationsserier",
        "Lopande verifikationsnummer",
        "Systemet anvander serier som F for faktura, B for betalning, K for kostnad, M for manuellt verifikat, R for rattelse, IB for ingaende balans, MOMS for moms och S/SU for Stripe.",
        "VoucherNumberService, JournalEntry.getVoucherSeries",
        "Verifikationskontrollen letar efter saknade nummer, fel format, obalans, datumordning och ateranvanda nummer.",
        "/voucher-control/export"
    ));
    items.add(new SystemDocumentationItem(
        "Automatisk bokforing",
        "Fakturering",
        "Vid faktureringsmetoden bokforas faktura normalt 1510 debet, 3041 kredit och 2611 kredit. Vid kontantmetoden bokforas intakt och moms nar betalningen registreras.",
        "AccountingService.createInvoiceEntries, createCashMethodPaymentEntries",
        "Kontrollera fakturadatum, fakturanummer, kund, moms och betalningsstatus.",
        "/journal-entries/export och faktura-PDF"
    ));
    items.add(new SystemDocumentationItem(
        "Automatisk bokforing",
        "Betalningar och delbetalningar",
        "Betalningar bokforas med B-serie. Vid faktureringsmetoden debiteras 1930 och krediteras 1510. Delbetalningar bokforas per registrerat belopp.",
        "AccountingService.createPaymentEntries",
        "Betalningsdatum far inte ligga fore fakturadatum och belopp far inte overstiga kvarvarande belopp.",
        "/journal-entries/export och /general-ledger/export?accountNumber=1510"
    ));
    items.add(new SystemDocumentationItem(
        "Automatisk bokforing",
        "Kostnader och kvitton",
        "Kostnader bokforas med K-serie mot valt kostnadskonto, 2641 for ingaende moms och valt betalkonto, vanligtvis 1930.",
        "AccountingService.createExpenseEntries",
        "Kontrollera kvitto/underlag, datum, totalbelopp, moms och kategori.",
        "/journal-entries/export och underlagsarkiv"
    ));
    items.add(new SystemDocumentationItem(
        "Automatisk bokforing",
        "Stripe, Apple Pay och externa hemsidekop",
        "Stripe-forsaljning bokforas mot 1580, 3041 och 2611. Stripe-utbetalning bokforas mot 1930, 1580 och 6570 for avgifter.",
        "AccountingService.createStripeExternalSaleEntries, createStripePayoutEntry",
        "Kontrollera Stripe-referens, bruttobelopp, avgift, utbetalningsdatum och att 1580 stams av.",
        "/stripe-payouts och /general-ledger/export?accountNumber=1580"
    ));
    items.add(new SystemDocumentationItem(
        "Rattelser",
        "Rattelse i stallet for radering",
        "Bokforda verifikat ska normalt rattas med ny rattelseverifikation sa att originalet finns kvar och sambandet framgar.",
        "AccountingService.createCorrectionEntry, JournalEntry.correctionOfVoucherNumber",
        "Anvand rattelseverifikat for skickade, betalda eller bokforda handelser. Radera bara utkast dar det ar tillatet.",
        "/journal-entries/export"
    ));
    items.add(new SystemDocumentationItem(
        "Periodlasning",
        "Skyddad bokforingsperiod",
        "Periodlasning hindrar ny bokforing pa eller fore last datum. Momsperioder som ar deklarerade eller betalda kan ocksa lasa perioden.",
        "AccountingPeriodLockService, AccountingService.requireUnlockedAccountingDate",
        "Las period forst nar verifikationer, moms, bank, kontotecken, arkiv och backup ar kontrollerade.",
        "/accounting-period/close-check"
    ));
    items.add(new SystemDocumentationItem(
        "Kontroller",
        "Beraknings- och bokforingskontroller",
        "AliBooks har kontroll for saldobalans, verifikationer, moms, kontotecken, huvudbok, rapportdifferenser och integritetskedja.",
        "AccountingService reports",
        "Kritiska avvikelser bor vara noll innan export, periodlasning, momsrapport eller bokslut.",
        "/trial-balance/export, /voucher-control/export, /vat-control/export, /account-sign-control/export"
    ));
    items.add(new SystemDocumentationItem(
        "Behandlingshistorik",
        "Sparbarhet och integritet",
        "Verifikationsrader har kalltyp, kallreferens, underlagsstatus, rattelsekoppling och integritetshash. Auditlogg sparar viktiga handelser.",
        "JournalEntry.getIntegrityHash, AuditService",
        "Exportera journal och integritetsrapport vid backup, bokslut eller overlamning.",
        "/journal-integrity/export och /audit-events/integrity/export"
    ));
    items.add(new SystemDocumentationItem(
        "Arsarkiv",
        "Arsarkiv och verifikationsattest",
        "Arsarkivkontrollen sammanfattar bokforingskedja, fakturor, kvitton, momsarkiv, rapporter, verifikationskontroll, atteststatus och SIE/CSV-overlamning.",
        "AccountingService.createArchiveYearReport",
        "Alla kritiska punkter och attestavvikelser bor vara klara innan SIE/CSV, backup och bokslutsunderlag sparas som slutligt arsarkiv.",
        "/archive-year/export och /accountant-package/export"
    ));
    items.add(new SystemDocumentationItem(
        "Export",
        "Grundbok, huvudbok, rapporter och SIE",
        "Systemet kan exportera journal, huvudbok, saldobalans, resultatrapport, balansrapport, momsunderlag, SIE och redovisningspaket.",
        "AccountingExportController",
        "Spara exporter tillsammans med backup och underlag for vald period.",
        "/accountant-package/export"
    ));
    items.add(new SystemDocumentationItem(
        "AI och personuppgifter",
        "Saker analys utan onodig spridning",
        "AI-assistenten ska anvanda minimerad eller anonymiserad kontext for analys. Fakturor, kunder och backup kan innehalla personnummer, adress, telefon och e-post.",
        "AI-sakert lage, anonymiserad analys-export",
        "Skicka inte direkta personuppgifter till externa AI-verktyg om det inte ar absolut nodvandigt.",
        "anonymiserad analys-JSON och sakerhetscenter"
    ));

    long automationCount = items.stream().filter(item -> "Automatisk bokforing".equals(item.category())).count();
    long controlCount = items.stream().filter(item -> "Kontroller".equals(item.category()) || "Periodlasning".equals(item.category())).count();
    long exportCount = items.stream().filter(item -> item.recommendedExport() != null && item.recommendedExport().contains("/")).count();

    return new SystemDocumentationReport(
        Instant.now(),
        periodFrom,
        periodTo,
        settingsService.getSettings().getCompanyName() == null ? "AliBooks" : settingsService.getSettings().getCompanyName(),
        settingsService.getSettings().getCompanyType(),
        settingsService.getSettings().getAccountingMethod(),
        settingsService.getSettings().getVatReportingPeriod(),
        accounts.size(),
        voucherSeries.size(),
        (int) automationCount,
        (int) controlCount,
        (int) exportCount,
        voucherSeries,
        items
    );
  }

  public ArchiveYearReport createArchiveYearReport(Integer year) {
    int archiveYear = normalizeAnnualResultYear(year);
    LocalDate periodFrom = LocalDate.of(archiveYear, 1, 1);
    LocalDate periodTo = LocalDate.of(archiveYear, 12, 31);
    JournalIntegrityReport journalIntegrityReport = createJournalIntegrityReport(periodFrom, periodTo);
    ProfitAndLossReport profitAndLossReport = createProfitAndLossReport(periodFrom, periodTo);
    BalanceReport balanceReport = createBalanceReport(periodTo);
    VoucherControlReport voucherControlReport = createVoucherControlReport(periodFrom, periodTo);
    VatControlReport vatControlReport = createVatControlReport(periodFrom, periodTo);
    TrialBalanceReport trialBalanceReport = createTrialBalanceReport(periodFrom, periodTo);

    List<JournalEntry> yearEntries = journalEntryRepository.findAll().stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .toList();
    Set<String> invoiceReferences = yearEntries.stream()
        .filter(entry -> "invoice".equals(entry.getSourceType()) || "invoice_payment".equals(entry.getSourceType()) || "credit_invoice".equals(entry.getSourceType()))
        .map(JournalEntry::getSourceReference)
        .filter(reference -> reference != null && !reference.isBlank())
        .collect(Collectors.toCollection(HashSet::new));
    Set<String> expenseReferences = yearEntries.stream()
        .filter(entry -> "expense".equals(entry.getSourceType()))
        .map(JournalEntry::getSourceReference)
        .filter(reference -> reference != null && !reference.isBlank())
        .collect(Collectors.toCollection(HashSet::new));
    int missingReceiptCount = (int) yearEntries.stream()
        .filter(entry -> "missing_receipt".equals(entry.getEvidenceStatus()))
        .map(JournalEntry::getSourceReference)
        .filter(reference -> reference != null && !reference.isBlank())
        .distinct()
        .count();
    int missingReceiptHashCount = (int) yearEntries.stream()
        .filter(entry -> "missing_receipt_hash".equals(entry.getEvidenceStatus()))
        .map(JournalEntry::getSourceReference)
        .filter(reference -> reference != null && !reference.isBlank())
        .distinct()
        .count();
    int receiptCount = Math.max(expenseReferences.size() - missingReceiptCount, 0);
    int vatFilingCount = (int) vatFilingRepository.findAll().stream()
        .filter(filing -> filing.getPeriodFrom() != null && filing.getPeriodTo() != null)
        .filter(filing -> !filing.getPeriodFrom().isBefore(periodFrom) && !filing.getPeriodTo().isAfter(periodTo))
        .count();
    VoucherApprovalStats voucherApprovalStats = calculateVoucherApprovalStats(yearEntries);

    boolean journalBalanced = journalIntegrityReport.difference() == 0;
    List<ArchiveYearItem> items = new ArrayList<>();
    items.add(archiveItem(
        "journal",
        "Verifikationer och bokforingskedja",
        yearEntries.isEmpty() ? "critical" : journalBalanced && journalIntegrityReport.missingEvidenceCount() == 0 ? "ok" : "warning",
        yearEntries.size() + " rader / " + journalIntegrityReport.voucherCount() + " verifikat",
        journalBalanced
            ? "Bokforingskedjan balanserar. Periodstampel: " + journalIntegrityReport.periodFingerprint()
            : "Bokforingskedjan har differens " + journalIntegrityReport.difference() + " SEK.",
        "/journal-integrity/export och /general-ledger/export"
    ));
    items.add(archiveItem(
        "invoices",
        "Fakturor och betalningsreferenser",
        invoiceReferences.isEmpty() ? "warning" : "ok",
        invoiceReferences.size() + " fakturareferenser",
        invoiceReferences.isEmpty()
            ? "Inga fakturareferenser hittades i bokforingen for aret."
            : "Fakturor finns kopplade till bokforingen via kallreferenser.",
        "/invoices/export och faktura-PDF"
    ));
    items.add(archiveItem(
        "receipts",
        "Kvitton och kostnadsunderlag",
        missingReceiptCount > 0 ? "critical" : missingReceiptHashCount > 0 ? "warning" : "ok",
        receiptCount + "/" + expenseReferences.size() + " med underlag",
        missingReceiptCount > 0
            ? missingReceiptCount + " kostnader saknar kvitto eller underlag."
            : missingReceiptHashCount > 0
                ? missingReceiptHashCount + " kvitton saknar SHA-256-underlagskod."
                : "Kostnadsunderlag ar kopplade och har underlagskoder.",
        "/expenses export och kvittofiler"
    ));
    items.add(archiveItem(
        "vat",
        "Momsrapporter och momsarkiv",
        vatControlReport.criticalIssueCount() > 0 ? "critical" : vatFilingCount == 0 ? "warning" : "ok",
        vatFilingCount + " momsperioder arkiverade",
        vatControlReport.criticalIssueCount() > 0
            ? vatControlReport.criticalIssueCount() + " kritiska momspunkter finns kvar."
            : vatFilingCount == 0
                ? "Ingen momsperiod ar arkiverad for aret annu."
                : "Momsarkivet har perioder och momskontrollen saknar kritiska fel.",
        "/vat-filings/export och /vat-control/export"
    ));
    items.add(archiveItem(
        "reports",
        "Resultat, balans och saldobalans",
        balanceReport.difference() != 0 || trialBalanceReport.difference() != 0 ? "critical" : "ok",
        "Resultat " + profitAndLossReport.result() + " SEK",
        balanceReport.difference() != 0
            ? "Balansrapporten har differens " + balanceReport.difference() + " SEK."
            : trialBalanceReport.difference() != 0
                ? "Saldobalansen har differens " + trialBalanceReport.difference() + " SEK."
                : "Resultatrapport, balansrapport och saldobalans kan arkiveras.",
        "/profit-and-loss/export, /balance-report/export och /trial-balance/export"
    ));
    items.add(archiveItem(
        "voucher-control",
        "Nummerordning och verifikationskontroll",
        voucherControlReport.criticalIssueCount() > 0 ? "critical" : voucherControlReport.warningIssueCount() > 0 ? "warning" : "ok",
        voucherControlReport.issues().size() + " kontrollpunkter",
        voucherControlReport.issues().isEmpty()
            ? "Verifikationsnummer och datum ser kontrollerade ut."
            : voucherControlReport.criticalIssueCount() + " kritiska och " + voucherControlReport.warningIssueCount() + " varningar finns.",
        "/voucher-control/export"
    ));
    items.add(archiveItem(
        "voucher-approval",
        "Verifikationsattest",
        voucherApprovalStats.blocked() > 0 || voucherApprovalStats.missing() > 0 || voucherApprovalStats.pending() > 0 ? "critical" : "ok",
        voucherApprovalStats.approved() + " godkanda / " + voucherApprovalStats.missing() + " saknar attest",
        voucherApprovalStats.blocked() > 0
            ? voucherApprovalStats.blocked() + " verifikat ar blockerade och maste granskas innan arsarkiv."
            : voucherApprovalStats.missing() > 0 || voucherApprovalStats.pending() > 0
                ? voucherApprovalStats.missing() + " verifikat saknar attest och " + voucherApprovalStats.pending() + " vantar pa attest."
                : "Alla verifikat i aret ar attesterade.",
        "/voucher-approvals och verifikationsattest.csv"
    ));
    items.add(archiveItem(
        "sie",
        "SIE/CSV och overlamning",
        yearEntries.isEmpty() || !journalBalanced || voucherApprovalStats.missing() > 0 || voucherApprovalStats.pending() > 0 || voucherApprovalStats.blocked() > 0 ? "warning" : "ok",
        journalIntegrityReport.finalChainHash().isBlank() ? "-" : journalIntegrityReport.finalChainHash(),
        yearEntries.isEmpty()
            ? "Inget SIE-underlag finns for aret annu."
            : voucherApprovalStats.missing() > 0 || voucherApprovalStats.pending() > 0 || voucherApprovalStats.blocked() > 0
                ? "Atestera verifikat innan SIE/CSV lamnas som slutligt arsunderlag."
                : "Exportera SIE/CSV och spara tillsammans med periodstampeln.",
        "/sie/export och /accountant-package/export"
    ));

    int criticalItemCount = (int) items.stream().filter(item -> "critical".equals(item.status())).count();
    int warningItemCount = (int) items.stream().filter(item -> "warning".equals(item.status())).count();
    int readyItemCount = (int) items.stream().filter(item -> "ok".equals(item.status())).count();
    int score = Math.max(0, 100 - criticalItemCount * 18 - warningItemCount * 8);

    return new ArchiveYearReport(
        archiveYear,
        periodFrom,
        periodTo,
        Instant.now(),
        archiveYear + 7 + "-12-31",
        score,
        readyItemCount,
        warningItemCount,
        criticalItemCount,
        yearEntries.size(),
        journalIntegrityReport.voucherCount(),
        invoiceReferences.size(),
        expenseReferences.size(),
        receiptCount,
        missingReceiptCount,
        missingReceiptHashCount,
        vatFilingCount,
        voucherApprovalStats.approved(),
        voucherApprovalStats.missing(),
        voucherApprovalStats.pending(),
        voucherApprovalStats.blocked(),
        journalBalanced,
        journalIntegrityReport.periodFingerprint(),
        journalIntegrityReport.finalChainHash(),
        items
    );
  }

  private ArchiveYearItem archiveItem(String key, String title, String status, String count, String detail, String recommendedExport) {
    return new ArchiveYearItem(key, title, status, count, detail, recommendedExport);
  }

  public VoucherControlReport createVoucherControlReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> entries = journalEntryRepository.findAll()
        .stream()
        .filter(entry -> isWithinControlPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .toList();
    List<VoucherControlIssue> issues = new ArrayList<>();
    long missingVoucherNumberCount = entries.stream()
        .filter(entry -> entry.getVoucherNumber() == null || entry.getVoucherNumber().isBlank())
        .count();

    if (missingVoucherNumberCount > 0) {
      issues.add(new VoucherControlIssue(
          "critical",
          "missing_voucher_number",
          "",
          "",
          null,
          null,
          null,
          0,
          0,
          missingVoucherNumberCount + " journal line(s) are missing voucher number."
      ));
    }

    addJournalLineStructureIssues(entries, issues);

    Map<String, List<JournalEntry>> voucherGroups = entries.stream()
        .filter(entry -> entry.getVoucherNumber() != null && !entry.getVoucherNumber().isBlank())
        .collect(Collectors.groupingBy(JournalEntry::getVoucherNumber));
    Map<String, Set<Integer>> seriesNumbers = new java.util.TreeMap<>();
    Map<String, List<SequencedVoucher>> seriesVouchers = new TreeMap<>();
    int balancedVoucherCount = 0;
    int unbalancedVoucherCount = 0;
    int reusedVoucherNumberCount = 0;
    int invalidVoucherNumberCount = 0;

    for (Map.Entry<String, List<JournalEntry>> voucherGroup : voucherGroups.entrySet()) {
      String voucherNumber = voucherGroup.getKey();
      List<JournalEntry> voucherEntries = voucherGroup.getValue();
      int debit = reportAccountingWholeKrona(
          voucherEntries.stream().mapToLong(JournalEntry::getDebitMinorValue).reduce(0L, Math::addExact),
          "verifikationskontrollens debetsumma");
      int credit = reportAccountingWholeKrona(
          voucherEntries.stream().mapToLong(JournalEntry::getCreditMinorValue).reduce(0L, Math::addExact),
          "verifikationskontrollens kreditsumma");
      LocalDate voucherDate = voucherEntries.stream()
          .map(JournalEntry::getVoucherDate)
          .filter(date -> date != null)
          .min(LocalDate::compareTo)
          .orElse(null);
      ParsedVoucherNumber parsedVoucherNumber = parseVoucherNumber(voucherNumber);

      if (debit == credit) {
        balancedVoucherCount++;
      } else {
        unbalancedVoucherCount++;
        issues.add(new VoucherControlIssue(
            "critical",
            "unbalanced_voucher",
            voucherNumber,
            parsedVoucherNumber == null ? "" : parsedVoucherNumber.series(),
            null,
            parsedVoucherNumber == null ? null : parsedVoucherNumber.sequenceNumber(),
            voucherDate,
            debit,
            credit,
            "Voucher does not balance. Debit " + debit + " and credit " + credit + "."
        ));
      }

      Set<LocalDate> voucherDates = voucherEntries.stream()
          .map(JournalEntry::getVoucherDate)
          .filter(date -> date != null)
          .collect(Collectors.toSet());
      if (voucherDates.size() > 1) {
        reusedVoucherNumberCount++;
        issues.add(new VoucherControlIssue(
            "critical",
            "voucher_number_reused",
            voucherNumber,
            parsedVoucherNumber == null ? "" : parsedVoucherNumber.series(),
            null,
            parsedVoucherNumber == null ? null : parsedVoucherNumber.sequenceNumber(),
            voucherDate,
            debit,
            credit,
            "Voucher number is used on more than one voucher date."
        ));
      }

      Set<String> sourceKeys = voucherEntries.stream()
          .map(entry -> entry.getSourceType() + "|" + entry.getSourceReference())
          .filter(sourceKey -> !sourceKey.isBlank() && !sourceKey.endsWith("|"))
          .collect(Collectors.toSet());
      if (sourceKeys.size() > 1) {
        reusedVoucherNumberCount++;
        issues.add(new VoucherControlIssue(
            "critical",
            "voucher_number_reused_for_multiple_sources",
            voucherNumber,
            parsedVoucherNumber == null ? "" : parsedVoucherNumber.series(),
            null,
            parsedVoucherNumber == null ? null : parsedVoucherNumber.sequenceNumber(),
            voucherDate,
            debit,
            credit,
            "Voucher number is linked to more than one source reference."
        ));
      }

      if (parsedVoucherNumber == null) {
        invalidVoucherNumberCount++;
        issues.add(new VoucherControlIssue(
            "warning",
            "invalid_voucher_number",
            voucherNumber,
            "",
            null,
            null,
            voucherDate,
            debit,
            credit,
            "Voucher number should use format SERIES-N, for example F-2026-0001 or M-12."
        ));
      } else {
        seriesNumbers.computeIfAbsent(parsedVoucherNumber.series(), key -> new HashSet<>())
            .add(parsedVoucherNumber.sequenceNumber());
        seriesVouchers.computeIfAbsent(parsedVoucherNumber.series(), key -> new ArrayList<>())
            .add(new SequencedVoucher(voucherNumber, parsedVoucherNumber.sequenceNumber(), voucherDate));
      }

      voucherEntries.stream()
          .map(JournalEntry::getEvidenceStatus)
          .filter(status -> status != null && !"traceable".equals(status))
          .distinct()
          .sorted()
          .forEach(status -> issues.add(new VoucherControlIssue(
              evidenceIssueSeverity(status),
              "evidence_" + status,
              voucherNumber,
              parsedVoucherNumber == null ? "" : parsedVoucherNumber.series(),
              null,
              parsedVoucherNumber == null ? null : parsedVoucherNumber.sequenceNumber(),
              voucherDate,
              debit,
              credit,
              evidenceIssueMessage(voucherNumber, status)
          )));
    }

    int gapCount = 0;
    for (Map.Entry<String, Set<Integer>> seriesEntry : seriesNumbers.entrySet()) {
      List<Integer> numbers = seriesEntry.getValue().stream().sorted().toList();
      for (int index = 1; index < numbers.size(); index++) {
        int previous = numbers.get(index - 1);
        int current = numbers.get(index);
        for (int expected = previous + 1; expected < current; expected++) {
          gapCount++;
          issues.add(new VoucherControlIssue(
              "warning",
              "voucher_gap",
              seriesEntry.getKey() + "-" + expected,
              seriesEntry.getKey(),
              expected,
              current,
              null,
              0,
              0,
              "Voucher series " + seriesEntry.getKey() + " jumps from " + previous + " to " + current + "."
          ));
        }
      }
    }

    int dateOrderIssueCount = 0;
    for (Map.Entry<String, List<SequencedVoucher>> seriesEntry : seriesVouchers.entrySet()) {
      List<SequencedVoucher> sortedVouchers = seriesEntry.getValue()
          .stream()
          .filter(voucher -> voucher.voucherDate() != null)
          .sorted(Comparator.comparingInt(SequencedVoucher::sequenceNumber))
          .toList();
      LocalDate latestDate = null;
      SequencedVoucher latestVoucher = null;

      for (SequencedVoucher voucher : sortedVouchers) {
        if (latestDate != null && voucher.voucherDate().isBefore(latestDate)) {
          dateOrderIssueCount++;
          issues.add(new VoucherControlIssue(
              "warning",
              "voucher_date_order",
              voucher.voucherNumber(),
              seriesEntry.getKey(),
              latestVoucher == null ? null : latestVoucher.sequenceNumber(),
              voucher.sequenceNumber(),
              voucher.voucherDate(),
              0,
              0,
              "Voucher " + voucher.voucherNumber() + " is dated " + voucher.voucherDate()
                  + " but an earlier number " + latestVoucher.voucherNumber() + " is dated " + latestDate + "."
          ));
        }

        if (latestDate == null || !voucher.voucherDate().isBefore(latestDate)) {
          latestDate = voucher.voucherDate();
          latestVoucher = voucher;
        }
      }
    }

    int criticalIssueCount = (int) issues.stream().filter(issue -> "critical".equals(issue.severity())).count();
    int warningIssueCount = (int) issues.stream().filter(issue -> "warning".equals(issue.severity())).count();

    return new VoucherControlReport(
        periodFrom,
        periodTo,
        voucherGroups.size() + (int) missingVoucherNumberCount,
        entries.size(),
        balancedVoucherCount,
        unbalancedVoucherCount,
        reusedVoucherNumberCount,
        (int) missingVoucherNumberCount,
        invalidVoucherNumberCount,
        gapCount,
        dateOrderIssueCount,
        criticalIssueCount,
        warningIssueCount,
        issues.stream()
            .sorted(Comparator
                .comparing(VoucherControlIssue::severity)
                .thenComparing(issue -> issue.voucherSeries() == null ? "" : issue.voucherSeries())
                .thenComparing(issue -> issue.actualNumber() == null ? Integer.MAX_VALUE : issue.actualNumber())
                .thenComparing(issue -> issue.voucherNumber() == null ? "" : issue.voucherNumber()))
            .toList()
    );
  }

  private List<ReportLine> reportLines(List<JournalEntry> entries, String accountPrefix, boolean creditPositive) {
    return entries.stream()
        .filter(entry -> entry.getAccountNumber().startsWith(accountPrefix))
        .collect(Collectors.groupingBy(
            entry -> entry.getAccountNumber() + "|" + entry.getAccountName(),
            Collectors.summingLong(entry -> accountingReportMovementMinor(
                entry,
                creditPositive,
                "resultat- och balansrapportens kontopost"))
        ))
        .entrySet()
        .stream()
        .map(this::toReportLine)
        .toList();
  }

  private void validatePeriod(LocalDate periodFrom, LocalDate periodTo) {
    if (periodFrom != null && periodTo != null && periodFrom.isAfter(periodTo)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Period from must be before or equal to period to.");
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).toUpperCase();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  private String value(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private ParsedVoucherNumber parseVoucherNumber(String voucherNumber) {
    if (voucherNumber == null || voucherNumber.isBlank()) {
      return null;
    }

    int separatorIndex = voucherNumber.lastIndexOf("-");
    if (separatorIndex <= 0 || separatorIndex == voucherNumber.length() - 1) {
      return null;
    }

    String series = voucherNumber.substring(0, separatorIndex).trim();
    String numberPart = voucherNumber.substring(separatorIndex + 1).trim();
    if (series.isBlank() || numberPart.isBlank() || !numberPart.chars().allMatch(Character::isDigit)) {
      return null;
    }

    try {
      return new ParsedVoucherNumber(series, Integer.parseInt(numberPart));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String evidenceIssueSeverity(String evidenceStatus) {
    if ("missing_receipt".equals(evidenceStatus)
        || "missing_invoice_number".equals(evidenceStatus)
        || "missing_voucher_number".equals(evidenceStatus)) {
      return "critical";
    }

    return "warning";
  }

  private String evidenceIssueMessage(String voucherNumber, String evidenceStatus) {
    String selectedVoucherNumber = voucherNumber == null || voucherNumber.isBlank() ? "Voucher" : "Voucher " + voucherNumber;
    if ("missing_receipt".equals(evidenceStatus)) {
      return selectedVoucherNumber + " is missing receipt evidence.";
    }
    if ("missing_receipt_hash".equals(evidenceStatus)) {
      return selectedVoucherNumber + " has receipt evidence without SHA-256 archive hash.";
    }
    if ("missing_invoice_number".equals(evidenceStatus)) {
      return selectedVoucherNumber + " is linked to an invoice without invoice number.";
    }
    if ("missing_voucher_number".equals(evidenceStatus)) {
      return "Journal line is missing voucher number.";
    }
    if ("needs_review".equals(evidenceStatus)) {
      return selectedVoucherNumber + " has unclear source evidence and should be reviewed.";
    }

    return selectedVoucherNumber + " has evidence status " + evidenceStatus + ".";
  }

  private GeneralLedgerAccount toGeneralLedgerAccount(String accountNumber, List<JournalEntry> accountEntries, LocalDate periodFrom, LocalDate periodTo) {
    String accountName = accountEntries.stream()
        .map(JournalEntry::getAccountName)
        .filter(name -> name != null && !name.isBlank())
        .findFirst()
        .orElse("");
    long openingBalanceMinor = accountEntries.stream()
        .filter(entry -> periodFrom != null
            && entry.getVoucherDate() != null
            && entry.getVoucherDate().isBefore(periodFrom))
        .mapToLong(entry -> Math.subtractExact(entry.getDebitMinorValue(), entry.getCreditMinorValue()))
        .reduce(0L, Math::addExact);
    int openingBalance = reportAccountingWholeKrona(openingBalanceMinor, "huvudbokens ingående saldo");
    List<JournalEntry> periodEntries = accountEntries.stream()
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .toList();
    long periodDebitMinor = periodEntries.stream()
        .mapToLong(JournalEntry::getDebitMinorValue)
        .reduce(0L, Math::addExact);
    long periodCreditMinor = periodEntries.stream()
        .mapToLong(JournalEntry::getCreditMinorValue)
        .reduce(0L, Math::addExact);
    int periodDebit = reportAccountingWholeKrona(periodDebitMinor, "huvudbokens perioddebet");
    int periodCredit = reportAccountingWholeKrona(periodCreditMinor, "huvudbokens periodkredit");

    List<GeneralLedgerEntry> ledgerEntries = new ArrayList<>();
    long runningBalanceMinor = openingBalanceMinor;
    for (JournalEntry entry : periodEntries) {
      runningBalanceMinor = Math.addExact(runningBalanceMinor,
          Math.subtractExact(entry.getDebitMinorValue(), entry.getCreditMinorValue()));
      int runningBalance = reportAccountingWholeKrona(runningBalanceMinor, "huvudbokens löpande saldo");
      int debit = reportAccountingWholeKrona(entry.getDebitMinorValue(), "huvudbokens debetpost");
      int credit = reportAccountingWholeKrona(entry.getCreditMinorValue(), "huvudbokens kreditpost");
      ledgerEntries.add(new GeneralLedgerEntry(
          entry.getId(),
          entry.getVoucherDate(),
          entry.getVoucherNumber(),
          entry.getAccountNumber(),
          entry.getAccountName(),
          entry.getDescription(),
          entry.getSourceType(),
          entry.getSourceReference(),
          entry.getEvidenceStatus(),
          entry.getEvidenceHash(),
          entry.getCorrectionOfVoucherNumber(),
          debit,
          credit,
          runningBalance,
          entry.getIntegrityHash()
      ));
    }
    int closingBalance = reportAccountingWholeKrona(runningBalanceMinor, "huvudbokens utgående saldo");

    return new GeneralLedgerAccount(
        accountNumber,
        accountName,
        openingBalance,
        periodDebit,
        periodCredit,
        closingBalance,
        ledgerEntries
    );
  }

  private TrialBalanceLine toTrialBalanceLine(Map.Entry<String, List<JournalEntry>> entry, LocalDate periodFrom, LocalDate periodTo) {
    String[] parts = entry.getKey().split("\\|", 2);
    List<JournalEntry> entries = entry.getValue();

    long openingBalanceMinor = entries.stream()
        .filter(journalEntry -> periodFrom != null
            && journalEntry.getVoucherDate() != null
            && journalEntry.getVoucherDate().isBefore(periodFrom))
        .mapToLong(journalEntry -> Math.subtractExact(journalEntry.getDebitMinorValue(), journalEntry.getCreditMinorValue()))
        .reduce(0L, Math::addExact);
    int openingBalance = reportAccountingWholeKrona(openingBalanceMinor, "saldobalansens ingående saldo");
    long periodDebitMinor = entries.stream()
        .filter(journalEntry -> isWithinPeriod(journalEntry.getVoucherDate(), periodFrom, periodTo))
        .mapToLong(JournalEntry::getDebitMinorValue)
        .reduce(0L, Math::addExact);
    long periodCreditMinor = entries.stream()
        .filter(journalEntry -> isWithinPeriod(journalEntry.getVoucherDate(), periodFrom, periodTo))
        .mapToLong(JournalEntry::getCreditMinorValue)
        .reduce(0L, Math::addExact);
    int periodDebit = reportAccountingWholeKrona(periodDebitMinor, "saldobalansens perioddebet");
    int periodCredit = reportAccountingWholeKrona(periodCreditMinor, "saldobalansens periodkredit");
    int closingBalance = reportAccountingWholeKrona(
        Math.subtractExact(Math.addExact(openingBalanceMinor, periodDebitMinor), periodCreditMinor),
        "saldobalansens utgående saldo");

    return new TrialBalanceLine(
        parts[0],
        parts.length > 1 ? parts[1] : "",
        debitSide(openingBalance),
        creditSide(openingBalance),
        periodDebit,
        periodCredit,
        debitSide(closingBalance),
        creditSide(closingBalance)
    );
  }

  private int debitSide(int balance) {
    return Math.max(balance, 0);
  }

  private int creditSide(int balance) {
    return Math.max(-balance, 0);
  }

  private ReportLine toReportLine(Map.Entry<String, Long> entry) {
    String[] parts = entry.getKey().split("\\|", 2);
    return new ReportLine(parts[0], parts[1], reportAccountingWholeKrona(entry.getValue(), "rapportens kontosaldo"));
  }

  private long accountingReportMovementMinor(JournalEntry entry, boolean creditPositive, String field) {
    long movementMinor = creditPositive
        ? journalMovementMinor(entry)
        : Math.negateExact(journalMovementMinor(entry));
    reportAccountingWholeKrona(movementMinor, field);
    return movementMinor;
  }

  private ReportLine yearResultLine(int result) {
    String companyType = settingsService.getSettings().getCompanyType();

    if ("LIMITED_COMPANY".equals(companyType)) {
      return new ReportLine("2099", "Arets resultat", result);
    }

    return new ReportLine("2019", "Arets resultat", result);
  }

  private boolean isProfitAndLossExpenseAccount(String accountNumber) {
    if (accountNumber == null || accountNumber.isBlank()) {
      return false;
    }

    return accountNumber.startsWith("4")
        || accountNumber.startsWith("5")
        || accountNumber.startsWith("6")
        || accountNumber.startsWith("7")
        || accountNumber.startsWith("8");
  }

  private int normalizeAnnualResultYear(Integer year) {
    int normalizedYear = year == null ? LocalDate.now().getYear() : year;
    if (normalizedYear < 2000 || normalizedYear > 2100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Year must be between 2000 and 2100.");
    }
    return normalizedYear;
  }

  private Account annualResultAccount(String companyType) {
    return account("LIMITED_COMPANY".equals(companyType) ? "2099" : "2019");
  }

  private boolean hasAnnualResultVoucher(int year, String resultAccountNumber) {
    String description = annualResultDescription(year);
    return journalEntryRepository.findAll().stream()
        .anyMatch(entry -> description.equals(entry.getDescription())
            && (resultAccountNumber.equals(entry.getAccountNumber()) || "8999".equals(entry.getAccountNumber())));
  }

  private String annualResultDescription(int year) {
    return "Annual result " + year;
  }

  private JournalEntry createCashMethodPaymentEntries(Order invoice, LocalDate voucherDate, int paidAmount, String receivedAccount) {
    int amount = Math.min(paidAmount, invoice.getRemainingAmount());
    if (amount <= 0) {
      return null;
    }

    int vatAmount = cashMethodVatForPayment(invoice, amount);
    int netAmount = amount - vatAmount;

    String voucherNumber = voucherNumberService.nextVoucherNumber("B");
    Account bank = account(receivedAccount);
    Account sales = account(VatRate.salesAccount(invoice.getVatPercent()));
    Account outputVat = account(VatRate.outputVatAccount(invoice.getVatPercent()));

    JournalEntry bankEntry = journalEntryRepository.save(new JournalEntry(
        invoice,
        bank,
        voucherNumber,
        amount,
        0,
        "Invoice paid - cash method",
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        sales,
        voucherNumber,
        0,
        netAmount,
        "Invoice paid - cash method",
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        outputVat,
        voucherNumber,
        0,
        vatAmount,
        "Invoice paid - cash method",
        voucherDate
    ));
    return bankEntry;
  }

  private void createCashMethodRefundEntries(Order invoice, LocalDate voucherDate, int refundAmount) {
    int amount = Math.min(refundAmount, invoice.getRefundableAmount());
    if (amount <= 0) {
      return;
    }

    int vatAmount = cashMethodVatForRefund(invoice, amount);
    int netAmount = amount - vatAmount;

    String voucherNumber = voucherNumberService.nextVoucherNumber("AR");
    Account sales = account(VatRate.salesAccount(invoice.getVatPercent()));
    Account outputVat = account(VatRate.outputVatAccount(invoice.getVatPercent()));
    Account bank = account("1930");

    journalEntryRepository.save(new JournalEntry(
        invoice,
        sales,
        voucherNumber,
        netAmount,
        0,
        "Customer refund - cash method",
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        outputVat,
        voucherNumber,
        vatAmount,
        0,
        "Customer refund - cash method",
        voucherDate
    ));
    journalEntryRepository.save(new JournalEntry(
        invoice,
        bank,
        voucherNumber,
        0,
        amount,
        "Customer refund - cash method",
        voucherDate
    ));
  }

  private void createCashMethodSupplierInvoicePaymentEntries(SupplierInvoice supplierInvoice, LocalDate voucherDate, int paidAmount, String reference) {
    int amount = Math.min(paidAmount, supplierInvoice.getRemainingAmount());
    if (amount <= 0) {
      return;
    }

    int vatAmount = cashMethodVatForSupplierInvoicePayment(supplierInvoice, amount);
    int netAmount = amount - vatAmount;
    String voucherNumber = voucherNumberService.nextVoucherNumber("LB");
    Account expenseAccount = account(supplierInvoice.getCategory() == null || supplierInvoice.getCategory().isBlank()
        ? "5420"
        : supplierInvoice.getCategory());
    Account inputVat = account("2641");
    Account bank = account("1930");
    String description = "Supplier invoice paid - cash method: " + supplierInvoice.getDescription();
    if (!reference.isBlank()) {
      description += " " + reference;
    }

    journalEntryRepository.save(new JournalEntry(
        null,
        null,
        supplierInvoice,
        expenseAccount,
        voucherNumber,
        netAmount,
        0,
        description,
        voucherDate
    ));

    if (vatAmount > 0) {
      journalEntryRepository.save(new JournalEntry(
          null,
          null,
          supplierInvoice,
          inputVat,
          voucherNumber,
          vatAmount,
          0,
          "Supplier invoice VAT - cash method: " + supplierInvoice.getDescription(),
          voucherDate
      ));
    }

    journalEntryRepository.save(new JournalEntry(
        null,
        null,
        supplierInvoice,
        bank,
        voucherNumber,
        0,
        amount,
        description,
        voucherDate
    ));
  }

  private int cashMethodVatForPayment(Order invoice, int paidAmount) {
    int invoiceTotal = wholeKronaFromMinor(invoice.getTotalAmountMinor(), invoice.getTotalAmount(), "fakturans totalbelopp");
    int invoiceVat = wholeKronaFromMinor(invoice.getVatAmountMinor(), invoice.getVatAmount(), "fakturans momsbelopp");
    if (invoiceTotal <= 0 || invoiceVat <= 0) {
      return 0;
    }

    int previouslyPaid = Math.max(wholeKronaFromMinor(invoice.getPaidAmountMinor(), invoice.getPaidAmount(), "fakturans tidigare betalt"), 0);
    int paidAfterThisPayment = (int) Math.min((long) previouslyPaid + paidAmount, invoiceTotal);
    int previousVat = WholeKronaMath.roundedRatio(invoiceVat, previouslyPaid, invoiceTotal);
    int vatAfterThisPayment = WholeKronaMath.roundedRatio(invoiceVat, paidAfterThisPayment, invoiceTotal);
    return Math.max(vatAfterThisPayment - previousVat, 0);
  }

  private int cashMethodVatForRefund(Order invoice, int refundAmount) {
    int invoiceTotal = wholeKronaFromMinor(invoice.getTotalAmountMinor(), invoice.getTotalAmount(), "fakturans totalbelopp");
    int invoiceVat = wholeKronaFromMinor(invoice.getVatAmountMinor(), invoice.getVatAmount(), "fakturans momsbelopp");
    if (invoiceTotal <= 0 || invoiceVat <= 0) {
      return 0;
    }

    int previouslyRefunded = Math.max(wholeKronaFromMinor(invoice.getRefundedAmountMinor(), invoice.getRefundedAmount(), "fakturans tidigare återbetalt"), 0);
    int paidAmount = Math.max(wholeKronaFromMinor(invoice.getPaidAmountMinor(), invoice.getPaidAmount(), "fakturans betalt"), 0);
    int refundedAfterThisRefund = (int) Math.min((long) previouslyRefunded + refundAmount, paidAmount);
    int previousVat = WholeKronaMath.roundedRatio(invoiceVat, previouslyRefunded, invoiceTotal);
    int vatAfterThisRefund = WholeKronaMath.roundedRatio(invoiceVat, refundedAfterThisRefund, invoiceTotal);
    return Math.max(vatAfterThisRefund - previousVat, 0);
  }

  private int cashMethodVatForSupplierInvoicePayment(SupplierInvoice supplierInvoice, int paidAmount) {
    int invoiceTotal = wholeKronaFromMinor(supplierInvoice.getTotalAmountMinor(), supplierInvoice.getTotalAmount(), "leverantörsfakturans totalbelopp");
    int invoiceVat = wholeKronaFromMinor(supplierInvoice.getVatAmountMinor(), supplierInvoice.getVatAmount(), "leverantörsfakturans momsbelopp");
    if (invoiceTotal <= 0 || invoiceVat <= 0) {
      return 0;
    }

    int previouslyPaid = Math.max(wholeKronaFromMinor(supplierInvoice.getPaidAmountMinor(), supplierInvoice.getPaidAmount(), "leverantörsfakturans tidigare betalt"), 0);
    int paidAfterThisPayment = (int) Math.min((long) previouslyPaid + paidAmount, invoiceTotal);
    int previousVat = WholeKronaMath.roundedRatio(invoiceVat, previouslyPaid, invoiceTotal);
    int vatAfterThisPayment = WholeKronaMath.roundedRatio(invoiceVat, paidAfterThisPayment, invoiceTotal);
    return Math.max(vatAfterThisPayment - previousVat, 0);
  }

  private boolean usesCashMethod() {
    return "CASH_METHOD".equals(settingsService.getSettings().getAccountingMethod());
  }

  private int vatAt25Percent(int netAmount) {
    return WholeKronaMath.roundedRatio(netAmount, 25, 100);
  }

  private int salesBase(List<JournalEntry> entries, String accountNumber) {
    return reportVatWholeKrona(entries.stream()
        .filter(entry -> accountNumber.equals(entry.getAccountNumber()))
        .mapToLong(this::journalMovementMinor)
        .sum());
  }

  private int outputVat(List<JournalEntry> entries, String accountNumber) {
    return reportVatWholeKrona(entries.stream()
        .filter(entry -> accountNumber.equals(entry.getAccountNumber()))
        .mapToLong(this::journalMovementMinor)
        .sum());
  }

  private long journalMovementMinor(JournalEntry entry) {
    return Math.subtractExact(entry.getCreditMinorValue(), entry.getDebitMinorValue());
  }

  private int reportVatWholeKrona(long amountMinor) {
    return reportAccountingWholeKrona(amountMinor, "momsrapportens belopp");
  }

  private void validateCorrectionAmounts(List<JournalEntry> entries, String field) {
    for (JournalEntry entry : entries) {
      reportAccountingWholeKrona(entry.getDebitMinorValue(), field + " debet");
      reportAccountingWholeKrona(entry.getCreditMinorValue(), field + " kredit");
    }
  }

  private int reportAccountingWholeKrona(long amountMinor, String field) {
    if (amountMinor % 100L != 0L) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Rapporten innehåller ören i " + field + " som den nuvarande kronrapporten inte kan representera. Rapporten har stoppats.");
    }
    return reportAmount(amountMinor / 100L);
  }

  private int wholeKronaFromMinor(Long amountMinor, int legacyAmount, String field) {
    long valueMinor = amountMinor == null
        ? Math.multiplyExact((long) legacyAmount, 100L)
        : amountMinor;
    return reportAccountingWholeKrona(valueMinor, field);
  }

  private String sieString(String value) {
    return "\"" + String.valueOf(value == null ? "" : value)
        .replace("\\", "/")
        .replace("\"", "'")
        .replace("\r", " ")
        .replace("\n", " ")
        .trim() + "\"";
  }

  private String sieDate(LocalDate value) {
    LocalDate date = value == null ? LocalDate.now() : value;
    return date.toString().replace("-", "");
  }

  private String sieAmount(int value) {
    return String.format(Locale.US, "%.2f", (double) value);
  }

  private boolean isSalesAccount(String accountNumber) {
    return accountNumber != null && accountNumber.startsWith("3");
  }

  private int accountMovement(List<JournalEntry> entries, String accountNumber) {
    return reportVatWholeKrona(entries.stream()
        .filter(entry -> accountNumber.equals(entry.getAccountNumber()))
        .mapToLong(this::journalMovementMinor)
        .sum());
  }

  private boolean isPurchaseOrExpenseAccount(String accountNumber) {
    return accountNumber != null
        && (accountNumber.startsWith("4") || accountNumber.startsWith("5") || accountNumber.startsWith("6"));
  }

  private boolean isVatSettlementVoucher(String voucherNumber, List<JournalEntry> voucherEntries) {
    if (voucherNumber != null && voucherNumber.startsWith("MOMS-")) {
      return true;
    }
    return voucherEntries.stream()
        .map(JournalEntry::getDescription)
        .filter(description -> description != null)
        .anyMatch(description -> description.startsWith("VAT settlement"));
  }

  private void addVatClearingEntry(List<JournalEntry> entries, Account account, String voucherNumber, int balance, boolean creditPositive, String description, LocalDate settlementDate) {
    if (balance == 0) {
      return;
    }

    int debit = 0;
    int credit = 0;
    if (creditPositive) {
      debit = balance > 0 ? balance : 0;
      credit = balance < 0 ? Math.abs(balance) : 0;
    } else {
      credit = balance > 0 ? balance : 0;
      debit = balance < 0 ? Math.abs(balance) : 0;
    }

    entries.add(journalEntryRepository.save(new JournalEntry(null, account, voucherNumber, debit, credit, description, settlementDate)));
  }

  private boolean isWithinPeriod(LocalDate voucherDate, LocalDate periodFrom, LocalDate periodTo) {
    if (voucherDate == null) {
      return false;
    }
    if (periodFrom != null && voucherDate.isBefore(periodFrom)) {
      return false;
    }
    return periodTo == null || !voucherDate.isAfter(periodTo);
  }

  private boolean isWithinControlPeriod(LocalDate voucherDate, LocalDate periodFrom, LocalDate periodTo) {
    if (voucherDate == null) {
      return periodFrom == null && periodTo == null;
    }
    return isWithinPeriod(voucherDate, periodFrom, periodTo);
  }

  private record ParsedVoucherNumber(String series, int sequenceNumber) {
  }

  private record SequencedVoucher(String voucherNumber, int sequenceNumber, LocalDate voucherDate) {
  }

  private record AccountSignRule(String accountNumber, String accountName, boolean creditNature, boolean blocking, String severity) {
  }

  public void requireUnlockedAccountingDate(LocalDate voucherDate) {
    if (voucherDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher date is required.");
    }

    LocalDate lockedThroughDate = settingsService.lockSettingsForAccounting().getAccountingLockedThroughDate();
    if (lockedThroughDate != null && !voucherDate.isAfter(lockedThroughDate)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Bokforingen ar last till och med " + lockedThroughDate + ". Skapa en ny korrigeringsverifikation i en olast period."
      );
    }

    vatFilingRepository.findFirstByStatusInOrderByPeriodToDesc(List.of("SUBMITTED", "PAID"))
        .filter(filing -> filing.getPeriodTo() != null && !voucherDate.isAfter(filing.getPeriodTo()))
        .ifPresent(filing -> {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Momsperioden ar deklarerad eller betald till och med " + filing.getPeriodTo()
                  + ". Skapa en ny korrigeringsverifikation i en olast period."
          );
        });
  }

  private Account account(String number) {
    return accountRepository.findByNumber(number)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Account " + number + " is missing."));
  }

  private void validateJournalLines(List<CreateManualJournalEntryLineRequest> lines, String label) {
    for (int index = 0; index < lines.size(); index++) {
      CreateManualJournalEntryLineRequest line = lines.get(index);
      int rowNumber = index + 1;

      if (line == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " line " + rowNumber + " is required.");
      }

      if (line.accountNumber() == null || line.accountNumber().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " line " + rowNumber + " needs an account.");
      }

      if (line.debit() < 0 || line.credit() < 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " line " + rowNumber + " cannot have negative debit or credit.");
      }

      if (line.debit() == 0 && line.credit() == 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " line " + rowNumber + " needs either debit or credit.");
      }

      if (line.debit() > 0 && line.credit() > 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " line " + rowNumber + " cannot have both debit and credit.");
      }
    }
  }

  private void addJournalLineStructureIssues(List<JournalEntry> entries, List<VoucherControlIssue> issues) {
    for (JournalEntry entry : entries) {
      String voucherNumber = entry.getVoucherNumber() == null ? "" : entry.getVoucherNumber();
      ParsedVoucherNumber parsedVoucherNumber = parseVoucherNumber(voucherNumber);
      String series = parsedVoucherNumber == null ? entry.getVoucherSeries() : parsedVoucherNumber.series();
      Integer sequenceNumber = parsedVoucherNumber == null ? null : parsedVoucherNumber.sequenceNumber();
      int debit = reportAccountingWholeKrona(entry.getDebitMinorValue(), "verifikationskontrollens debetpost");
      int credit = reportAccountingWholeKrona(entry.getCreditMinorValue(), "verifikationskontrollens kreditpost");

      if (debit < 0 || credit < 0) {
        issues.add(new VoucherControlIssue(
            "critical",
            "journal_line_negative_amount",
            voucherNumber,
            series,
            null,
            sequenceNumber,
            entry.getVoucherDate(),
            debit,
            credit,
            "Journal line has a negative debit or credit amount."
        ));
      }

      if (debit == 0 && credit == 0) {
        issues.add(new VoucherControlIssue(
            "critical",
            "journal_line_zero_amount",
            voucherNumber,
            series,
            null,
            sequenceNumber,
            entry.getVoucherDate(),
            debit,
            credit,
            "Journal line has neither debit nor credit."
        ));
      }

      if (debit > 0 && credit > 0) {
        issues.add(new VoucherControlIssue(
            "critical",
            "journal_line_has_debit_and_credit",
            voucherNumber,
            series,
            null,
            sequenceNumber,
            entry.getVoucherDate(),
            debit,
            credit,
            "Journal line has both debit and credit. Split it into separate rows."
        ));
      }
    }
  }

  private boolean stripeWebsiteSaleReferenceExists(String reference) {
    return journalEntryRepository.existsByDescription(reference);
  }

  private LocalDate requireExplicitAccountingDate(LocalDate date, String message) {
    if (date == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    return date;
  }
}
