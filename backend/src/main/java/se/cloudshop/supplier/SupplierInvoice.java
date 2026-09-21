package se.cloudshop.supplier;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "supplier_invoices")
public class SupplierInvoice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "supplier_id")
  private Supplier supplier;

  private String supplierName;
  private String supplierEmail;
  private String supplierOrgNumber;
  private LocalDate invoiceDate;
  private LocalDate dueDate;
  private String description;
  private String reference;
  private int totalAmount;
  private int vatAmount;
  private int netAmount;
  @Column(name = "total_amount_minor")
  private Long totalAmountMinor;
  @Column(name = "vat_amount_minor")
  private Long vatAmountMinor;
  @Column(name = "net_amount_minor")
  private Long netAmountMinor;
  private String category;
  private String status;
  private LocalDate paidAt;
  private int paidAmount;
  @Column(name = "paid_amount_minor")
  private Long paidAmountMinor;
  private String paymentReference;
  private String paymentHistory;
  private LocalDate cancelledAt;
  private String cancellationVoucherNumber;
  @Column(columnDefinition = "boolean default false")
  private boolean selfBilling = false;
  private String buyerName;
  private String buyerReference;
  private String approvalReference;
  private Instant createdAt;

  public SupplierInvoice() {
  }

  public SupplierInvoice(
      Supplier supplier,
      LocalDate invoiceDate,
      LocalDate dueDate,
      String description,
      String reference,
      int totalAmount,
      int vatAmount,
      String category
  ) {
    this(
        supplier,
        invoiceDate,
        dueDate,
        description,
        reference,
        totalAmount,
        vatAmount,
        category,
        false,
        "",
        "",
        ""
    );
  }

  public SupplierInvoice(
      Supplier supplier,
      LocalDate invoiceDate,
      LocalDate dueDate,
      String description,
      String reference,
      int totalAmount,
      int vatAmount,
      String category,
      boolean selfBilling,
      String buyerName,
      String buyerReference,
      String approvalReference
  ) {
    this.supplier = supplier;
    this.supplierName = supplier.getName();
    this.supplierEmail = supplier.getEmail();
    this.supplierOrgNumber = supplier.getOrgNumber();
    this.invoiceDate = invoiceDate;
    this.dueDate = dueDate;
    this.description = description;
    this.reference = reference;
    this.totalAmount = totalAmount;
    this.vatAmount = vatAmount;
    this.netAmount = Math.max(0, totalAmount - vatAmount);
    this.totalAmountMinor = toMinorUnits(this.totalAmount, "totalAmount");
    this.vatAmountMinor = toMinorUnits(this.vatAmount, "vatAmount");
    this.netAmountMinor = toMinorUnits(this.netAmount, "netAmount");
    this.category = category;
    this.status = "unpaid";
    this.paidAmount = 0;
    this.paidAmountMinor = 0L;
    this.paymentReference = "";
    this.paymentHistory = "";
    this.selfBilling = selfBilling;
    this.buyerName = clean(buyerName);
    this.buyerReference = clean(buyerReference);
    this.approvalReference = clean(approvalReference);
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getSupplierId() {
    return supplier == null ? null : supplier.getId();
  }

  public String getSupplierName() {
    return supplierName;
  }

  public String getSupplierEmail() {
    return supplierEmail;
  }

  public String getSupplierOrgNumber() {
    return supplierOrgNumber;
  }

  public LocalDate getInvoiceDate() {
    return invoiceDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public String getDescription() {
    return description;
  }

  public String getReference() {
    return reference;
  }

  public int getTotalAmount() {
    return totalAmount;
  }

  public int getVatAmount() {
    return vatAmount;
  }

  public int getNetAmount() {
    return netAmount;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getTotalAmountMinor() {
    return totalAmountMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getVatAmountMinor() {
    return vatAmountMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getNetAmountMinor() {
    return netAmountMinor;
  }

  public String getCategory() {
    return category;
  }

  public String getStatus() {
    return status;
  }

  public LocalDate getPaidAt() {
    return paidAt;
  }

  public int getPaidAmount() {
    return paidAmount;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getPaidAmountMinor() {
    return paidAmountMinor;
  }

  public int getRemainingAmount() {
    return wholeKrona(getRemainingAmountMinor(), "remainingAmount");
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public long getRemainingAmountMinor() {
    return Math.max(totalAmountMinorValue() - paidAmountMinorValue(), 0L);
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public String getPaymentHistory() {
    return paymentHistory;
  }

  public LocalDate getCancelledAt() {
    return cancelledAt;
  }

  public String getCancellationVoucherNumber() {
    return cancellationVoucherNumber;
  }

  public boolean isSelfBilling() {
    return selfBilling;
  }

  public String getBuyerName() {
    return buyerName;
  }

  public String getBuyerReference() {
    return buyerReference;
  }

  public String getApprovalReference() {
    return approvalReference;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void updateStatus(String status, LocalDate paidAt) {
    this.status = status;
    if ("paid".equals(status) && this.paidAt == null) {
      this.paidAt = paidAt == null ? LocalDate.now() : paidAt;
    }
  }

  public void markCancelled(LocalDate cancelledAt, String cancellationVoucherNumber) {
    this.status = "cancelled";
    this.cancelledAt = cancelledAt == null ? LocalDate.now() : cancelledAt;
    this.cancellationVoucherNumber = cancellationVoucherNumber == null ? "" : cancellationVoucherNumber.trim();
  }

  public void registerPayment(LocalDate paidAt, int amount, String reference) {
    LocalDate paymentDate = paidAt == null ? LocalDate.now() : paidAt;
    if ("cancelled".equals(status) || amount <= 0 || paidAmountMinorValue() < 0) {
      throw new IllegalArgumentException("Supplier payment conflicts with invoice status or remaining amount.");
    }
    long newPaidAmountMinor = Math.addExact(paidAmountMinorValue(), toMinorUnits(amount, "paymentAmount"));
    if (newPaidAmountMinor > totalAmountMinorValue()) {
      throw new IllegalArgumentException("Supplier payment conflicts with invoice status or remaining amount.");
    }
    this.paidAmountMinor = newPaidAmountMinor;
    this.paidAmount = wholeKrona(newPaidAmountMinor, "paidAmount");
    this.paymentReference = reference == null ? "" : reference.trim();
    this.paidAt = paymentDate;
    this.status = getRemainingAmountMinor() == 0 ? "paid" : "partial";

    String historyLine = paymentHistoryLine(paymentDate, amount, this.paymentReference);
    this.paymentHistory = (paymentHistory == null || paymentHistory.isBlank())
        ? historyLine
        : paymentHistory + "\n" + historyLine;
  }

  public boolean hasPayment(LocalDate paidAt, int amount, String reference) {
    String normalizedReference = normalizeReference(reference);
    if (normalizedReference.isBlank()) {
      return false;
    }

    LocalDate paymentDate = paidAt == null ? LocalDate.now() : paidAt;
    String expectedPrefix = paymentDate + " - " + amount + " SEK";
    String[] historyLines = paymentHistory == null ? new String[0] : paymentHistory.split("\\R");

    for (String historyLine : historyLines) {
      if (!historyLine.startsWith(expectedPrefix)) {
        continue;
      }

      String lineReference = historyLine.length() > expectedPrefix.length()
          ? historyLine.substring(expectedPrefix.length()).replaceFirst("^\\s+-\\s+", "")
          : "";
      if (normalizeReference(lineReference).equals(normalizedReference)) {
        return true;
      }
    }

    return false;
  }

  private String paymentHistoryLine(LocalDate paymentDate, int amount, String reference) {
    String historyLine = paymentDate + " - " + amount + " SEK";
    if (reference != null && !reference.isBlank()) {
      historyLine += " - " + reference.trim();
    }
    return historyLine;
  }

  private String normalizeReference(String reference) {
    return reference == null ? "" : reference.trim().toLowerCase();
  }

  private static long toMinorUnits(int amount, String field) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Supplier invoice " + field + " is outside the supported money range.", exception);
    }
  }

  private static int wholeKrona(long amountMinor, String field) {
    if (amountMinor % 100L != 0) {
      throw new IllegalStateException("Supplier invoice " + field + " contains ore that the legacy API cannot represent.");
    }
    try {
      return Math.toIntExact(amountMinor / 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("Supplier invoice " + field + " is outside the supported whole-krona API range.", exception);
    }
  }

  private long totalAmountMinorValue() {
    return minorValue(totalAmountMinor, totalAmount, "totalAmount");
  }

  private long paidAmountMinorValue() {
    return minorValue(paidAmountMinor, paidAmount, "paidAmount");
  }

  private static long minorValue(Long shadow, int legacy, String field) {
    return shadow == null ? toMinorUnits(legacy, field) : shadow;
  }

  @PostLoad
  private void synchronizeMinorUnitShadowsFromLegacy() {
    // Legacy whole-krona columns remain authoritative during the staged migration.
    synchronizeMinorUnits();
  }

  @PrePersist
  @PreUpdate
  private void synchronizeMinorUnits() {
    totalAmountMinor = toMinorUnits(totalAmount, "totalAmount");
    vatAmountMinor = toMinorUnits(vatAmount, "vatAmount");
    netAmountMinor = toMinorUnits(netAmount, "netAmount");
    paidAmountMinor = toMinorUnits(paidAmount, "paidAmount");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
