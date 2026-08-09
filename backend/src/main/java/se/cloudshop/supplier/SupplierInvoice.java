package se.cloudshop.supplier;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
  private String category;
  private String status;
  private LocalDate paidAt;
  private int paidAmount;
  private String paymentReference;
  private String paymentHistory;
  private LocalDate cancelledAt;
  private String cancellationVoucherNumber;
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
    this.category = category;
    this.status = "unpaid";
    this.paidAmount = 0;
    this.paymentReference = "";
    this.paymentHistory = "";
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

  public int getRemainingAmount() {
    return Math.max(totalAmount - paidAmount, 0);
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
    int newPaidAmount = Math.min(totalAmount, Math.max(0, this.paidAmount) + amount);
    this.paidAmount = newPaidAmount;
    this.paymentReference = reference == null ? "" : reference.trim();
    this.paidAt = paymentDate;
    this.status = getRemainingAmount() == 0 ? "paid" : "partial";

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
}
