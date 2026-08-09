package se.cloudshop.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

  private String accountNumber;
  private String accountName;
  private String voucherNumber;
  private int debit;
  private int credit;
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
    if ("unknown".equals(getSourceType())) {
      return "needs_review";
    }
    return "traceable";
  }

  public String getEvidenceHash() {
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
        String.valueOf(debit),
        String.valueOf(credit),
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

  private String value(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
