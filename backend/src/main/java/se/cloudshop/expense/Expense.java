package se.cloudshop.expense;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
  private String category;
  private String paidFrom;
  private String receiptFileName;
  private String receiptContentType;
  private String receiptStoragePath;
  private Instant createdAt;

  public Expense() {
  }

  public Expense(LocalDate expenseDate, String description, int netAmount, int vatAmount, String category, String paidFrom) {
    this.expenseDate = expenseDate;
    this.description = description;
    this.netAmount = netAmount;
    this.vatAmount = vatAmount;
    this.totalAmount = netAmount + vatAmount;
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

  public boolean hasReceipt() {
    return receiptStoragePath != null && !receiptStoragePath.isBlank();
  }

  public boolean getHasReceipt() {
    return hasReceipt();
  }

  public void setReceipt(String receiptFileName, String receiptContentType, String receiptStoragePath) {
    this.receiptFileName = receiptFileName;
    this.receiptContentType = receiptContentType;
    this.receiptStoragePath = receiptStoragePath;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
