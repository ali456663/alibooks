package se.cloudshop.expense;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDate expenseDate;
  private String description;
  private int netAmount;
  private int vatAmount;
  private int totalAmount;
  @jakarta.persistence.Column(name = "net_amount_minor")
  private Long netAmountMinor;
  @jakarta.persistence.Column(name = "vat_amount_minor")
  private Long vatAmountMinor;
  @jakarta.persistence.Column(name = "total_amount_minor")
  private Long totalAmountMinor;
  @jakarta.persistence.Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode = "SEK";
  private String category;
  private String paidFrom;
  private String receiptFileName;
  private String receiptContentType;
  private String receiptStoragePath;
  private String receiptSha256;
  private Instant receiptUploadedAt;
  private Instant createdAt;

  public Expense() {
  }

  public Expense(LocalDate expenseDate, String description, int netAmount, int vatAmount, String category, String paidFrom) {
    this.expenseDate = expenseDate;
    this.description = description;
    this.netAmount = netAmount;
    this.vatAmount = vatAmount;
    this.totalAmount = Math.addExact(netAmount, vatAmount);
    this.netAmountMinor = toMinorUnits(this.netAmount, "netAmount");
    this.vatAmountMinor = toMinorUnits(this.vatAmount, "vatAmount");
    this.totalAmountMinor = toMinorUnits(this.totalAmount, "totalAmount");
    this.category = category;
    this.paidFrom = paidFrom;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public LocalDate getExpenseDate() {
    return expenseDate;
  }

  public String getDescription() {
    return description;
  }

  public int getNetAmount() {
    return netAmount;
  }

  public int getVatAmount() {
    return vatAmount;
  }

  public int getTotalAmount() {
    return totalAmount;
  }

  public Long getNetAmountMinor() {
    return netAmountMinor;
  }

  public Long getVatAmountMinor() {
    return vatAmountMinor;
  }

  public Long getTotalAmountMinor() {
    return totalAmountMinor;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public long getNetAmountMinorValue() {
    return minorValue(netAmountMinor, netAmount, "netAmount");
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public long getVatAmountMinorValue() {
    return minorValue(vatAmountMinor, vatAmount, "vatAmount");
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public long getTotalAmountMinorValue() {
    return minorValue(totalAmountMinor, totalAmount, "totalAmount");
  }

  public String getCategory() {
    return category;
  }

  public String getPaidFrom() {
    return paidFrom;
  }

  public String getReceiptFileName() {
    return receiptFileName;
  }

  public String getReceiptContentType() {
    return receiptContentType;
  }

  public String getReceiptStoragePath() {
    return receiptStoragePath;
  }

  public String getReceiptSha256() {
    return receiptSha256;
  }

  public Instant getReceiptUploadedAt() {
    return receiptUploadedAt;
  }

  public boolean hasReceipt() {
    return receiptStoragePath != null && !receiptStoragePath.isBlank();
  }

  public boolean getHasReceipt() {
    return hasReceipt();
  }

  public void setReceipt(String receiptFileName, String receiptContentType, String receiptStoragePath) {
    setReceipt(receiptFileName, receiptContentType, receiptStoragePath, "", Instant.now());
  }

  public void setReceipt(String receiptFileName, String receiptContentType, String receiptStoragePath, String receiptSha256, Instant receiptUploadedAt) {
    this.receiptFileName = receiptFileName;
    this.receiptContentType = receiptContentType;
    this.receiptStoragePath = receiptStoragePath;
    this.receiptSha256 = receiptSha256;
    this.receiptUploadedAt = receiptUploadedAt == null ? Instant.now() : receiptUploadedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * The whole-krona columns remain authoritative while the minor-unit migration is staged.
   * This keeps older rows and direct legacy imports compatible without losing the new shadow data.
   */
  @PostLoad
  void synchronizeMinorUnitShadowsFromLegacy() {
    synchronizeMinorUnits();
  }

  @PrePersist
  @PreUpdate
  void synchronizeMinorUnits() {
    netAmountMinor = toMinorUnits(netAmount, "netAmount");
    vatAmountMinor = toMinorUnits(vatAmount, "vatAmount");
    totalAmountMinor = toMinorUnits(totalAmount, "totalAmount");
  }

  private static long toMinorUnits(int amount, String field) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Expense " + field + " is outside the supported money range.", exception);
    }
  }

  private static long minorValue(Long shadow, int legacyAmount, String field) {
    return shadow == null ? toMinorUnits(legacyAmount, field) : shadow;
  }
}
