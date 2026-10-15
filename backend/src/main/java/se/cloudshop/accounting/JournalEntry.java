package se.cloudshop.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import se.cloudshop.expense.Expense;
import se.cloudshop.order.Order;
import se.cloudshop.supplier.SupplierInvoice;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  private Order invoice;

  @ManyToOne
  private Expense expense;

  @ManyToOne
  @JoinColumn(name = "supplier_invoice_id")
  private SupplierInvoice supplierInvoice;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "voucher_evidence_id")
  private VoucherEvidence voucherEvidence;

  private String accountNumber;
  private String accountName;
  private String voucherNumber;
  private int debit;
  private int credit;
  @jakarta.persistence.Column(name = "debit_minor")
  private Long debitMinor;
  @jakarta.persistence.Column(name = "credit_minor")
  private Long creditMinor;
  @jakarta.persistence.Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode = "SEK";
  private String description;
  private LocalDate voucherDate;
  private String correctionOfVoucherNumber;
  private Instant createdAt;

  public JournalEntry() {
  }

  public JournalEntry(Order invoice, Account account, String voucherNumber, int debit, int credit, String description) {
    this(invoice, account, voucherNumber, debit, credit, description, LocalDate.now());
  }

  public JournalEntry(Order invoice, Account account, String voucherNumber, int debit, int credit, String description, LocalDate voucherDate) {
    this(invoice, null, account, voucherNumber, debit, credit, description, voucherDate);
  }

  public JournalEntry(Order invoice, Expense expense, Account account, String voucherNumber, int debit, int credit, String description, LocalDate voucherDate) {
    this(invoice, expense, null, account, voucherNumber, debit, credit, description, voucherDate);
  }

  public JournalEntry(Order invoice, Expense expense, SupplierInvoice supplierInvoice, Account account, String voucherNumber, int debit, int credit, String description, LocalDate voucherDate) {
    this.invoice = invoice;
    this.expense = expense;
    this.supplierInvoice = supplierInvoice;
    this.accountNumber = account.getNumber();
    this.accountName = account.getName();
    this.voucherNumber = voucherNumber;
    this.debit = debit;
    this.credit = credit;
    this.debitMinor = toMinorUnits(debit, "debit");
    this.creditMinor = toMinorUnits(credit, "credit");
    this.description = description;
    this.voucherDate = voucherDate == null ? LocalDate.now() : voucherDate;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Order getInvoice() {
    return invoice;
  }

  public Expense getExpense() {
    return expense;
  }

  public SupplierInvoice getSupplierInvoice() {
    return supplierInvoice;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getAccountName() {
    return accountName;
  }

  public String getVoucherNumber() {
    return voucherNumber;
  }

  public String getVoucherSeries() {
    if (voucherNumber == null || voucherNumber.isBlank()) {
      return "";
    }

    int separatorIndex = voucherNumber.lastIndexOf("-");
    if (separatorIndex <= 0) {
      return voucherNumber;
    }

    return voucherNumber.substring(0, separatorIndex);
  }

  public Integer getVoucherSequenceNumber() {
    if (voucherNumber == null || voucherNumber.isBlank()) {
      return null;
    }

    int separatorIndex = voucherNumber.lastIndexOf("-");
    if (separatorIndex <= 0 || separatorIndex == voucherNumber.length() - 1) {
      return null;
    }

    try {
      return Integer.parseInt(voucherNumber.substring(separatorIndex + 1));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  public int getDebit() {
    return debit;
  }

  public int getCredit() {
    return credit;
  }

  public Long getDebitMinor() {
    return debitMinor;
  }

  public Long getCreditMinor() {
    return creditMinor;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public long getDebitMinorValue() {
    return minorValue(debitMinor);
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public long getCreditMinorValue() {
    return minorValue(creditMinor);
  }

  public String getDescription() {
    return description;
  }

  public LocalDate getVoucherDate() {
    return voucherDate;
  }

  public String getCorrectionOfVoucherNumber() {
    return correctionOfVoucherNumber;
  }

  public void setCorrectionOfVoucherNumber(String correctionOfVoucherNumber) {
    this.correctionOfVoucherNumber = correctionOfVoucherNumber;
  }

  void attachVoucherEvidence(VoucherEvidence evidence) {
    if (voucherEvidence != null || evidence == null || !evidence.getVoucherNumber().equals(voucherNumber)) {
      throw new IllegalStateException("Voucher evidence cannot be replaced or linked to another voucher.");
    }
    voucherEvidence = evidence;
  }

  public String getSourceType() {
    String series = getVoucherSeries();
    if ("F".equals(series)) {
      return "invoice";
    }
    if ("B".equals(series)) {
      return "invoice_payment";
    }
    if ("KR".equals(series)) {
      return "credit_invoice";
    }
    if ("AR".equals(series)) {
      return "customer_refund";
    }
    if ("K".equals(series)) {
      return "expense";
    }
    if ("L".equals(series)) {
      return "supplier_invoice";
    }
    if ("LB".equals(series)) {
      return "supplier_invoice_payment";
    }
    if ("S".equals(series)) {
      return "stripe_sale";
    }
    if ("SU".equals(series)) {
      return "stripe_payout";
    }
    if ("MOMS".equals(series) || series.startsWith("MOMS-")) {
      if (description != null && (description.startsWith("VAT payment") || description.startsWith("VAT refund"))) {
        return "vat_payment";
      }
      return "vat_settlement";
    }
    if ("BR".equals(series)) {
      return "annual_result";
    }
    if ("IB".equals(series)) {
      return "opening_balance";
    }
    if ("E".equals(series)) {
      return "owner_transaction";
    }
    if ("R".equals(series) || correctionOfVoucherNumber != null && !correctionOfVoucherNumber.isBlank()) {
      return "correction";
    }
    if ("M".equals(series)) {
      return "manual_voucher";
    }
    if (invoice != null) {
      return invoice.isCreditInvoice() ? "credit_invoice" : "invoice";
    }
    if (expense != null) {
      return "expense";
    }
    if (supplierInvoice != null) {
      return "supplier_invoice";
    }

    return "unknown";
  }

  public String getSourceReference() {
    if (invoice != null) {
      if (invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isBlank()) {
        return invoice.getInvoiceNumber();
      }
      return invoice.getId() == null ? "" : "invoice:" + invoice.getId();
    }

    if (expense != null) {
      if (expense.getReceiptFileName() != null && !expense.getReceiptFileName().isBlank()) {
        return expense.getReceiptFileName();
      }
      if (expense.getDescription() != null && !expense.getDescription().isBlank()) {
        return expense.getDescription();
      }
      return expense.getId() == null ? "" : "expense:" + expense.getId();
    }

    if (supplierInvoice != null) {
      if (supplierInvoice.getReference() != null && !supplierInvoice.getReference().isBlank()) {
        return supplierInvoice.getReference();
      }
      if (supplierInvoice.getSupplierName() != null && !supplierInvoice.getSupplierName().isBlank()) {
        return supplierInvoice.getSupplierName();
      }
      return supplierInvoice.getId() == null ? "" : "supplier_invoice:" + supplierInvoice.getId();
    }

    if (correctionOfVoucherNumber != null && !correctionOfVoucherNumber.isBlank()) {
      return correctionOfVoucherNumber;
    }

    return voucherNumber == null ? "" : voucherNumber;
  }

  public String getEvidenceStatus() {
    if (voucherNumber == null || voucherNumber.isBlank()) {
      return "missing_voucher_number";
    }
    if (invoice != null && (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank())) {
      return "missing_invoice_number";
    }
    if (expense != null && !expense.hasReceipt()) {
      return "missing_receipt";
    }
    if (expense != null && (expense.getReceiptSha256() == null || expense.getReceiptSha256().isBlank())) {
      return "missing_receipt_hash";
    }
    if (("manual_voucher".equals(getSourceType()) || "opening_balance".equals(getSourceType()))
        && voucherEvidence == null) {
      return "missing_voucher_evidence";
    }
    if ("unknown".equals(getSourceType())) {
      return "needs_review";
    }
    return "traceable";
  }

  public String getEvidenceHash() {
    if (voucherEvidence != null) {
      return voucherEvidence.getSha256();
    }
    if (expense != null && expense.getReceiptSha256() != null && !expense.getReceiptSha256().isBlank()) {
      return expense.getReceiptSha256();
    }
    return "";
  }

  public String getIntegrityHash() {
    String payload = String.join("|",
        value(voucherNumber),
        value(voucherDate),
        value(accountNumber),
        value(accountName),
        String.valueOf(getDebitMinorValue()),
        String.valueOf(getCreditMinorValue()),
        value(description),
        value(correctionOfVoucherNumber),
        getSourceType(),
        getSourceReference(),
        getEvidenceStatus(),
        getEvidenceHash()
    );

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8))).toUpperCase();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Legacy whole-krona columns remain authoritative during the staged migration.
   * Validate persisted values without repairing them during reads or unrelated updates.
   */
  @PostLoad
  @PreUpdate
  void validatePersistedMoney() {
    validateMoneySides();
    if (debitMinor == null || creditMinor == null
        || debitMinor != toMinorUnits(debit, "debit")
        || creditMinor != toMinorUnits(credit, "credit")) {
      throw new JournalMoneyIntegrityException();
    }
  }

  @PrePersist
  void prepareMoneyForInsert() {
    validateMoneySides();
    long expectedDebit = toMinorUnits(debit, "debit");
    long expectedCredit = toMinorUnits(credit, "credit");
    if ((debitMinor != null && debitMinor != expectedDebit)
        || (creditMinor != null && creditMinor != expectedCredit)) {
      throw new JournalMoneyIntegrityException();
    }
    // Only a new row may initialize absent shadows. Never overwrite contradictory values.
    debitMinor = expectedDebit;
    creditMinor = expectedCredit;
  }

  private void validateMoneySides() {
    if (!"SEK".equals(currencyCode) || debit < 0 || credit < 0 || (debit > 0 && credit > 0)) {
      throw new JournalMoneyIntegrityException();
    }
  }

  private static long toMinorUnits(int amount, String field) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Journal entry " + field + " is outside the supported money range.", exception);
    }
  }

  private static long minorValue(Long shadow) {
    if (shadow == null) throw new JournalMoneyIntegrityException();
    return shadow;
  }

  private String value(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
