package se.cloudshop.card;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "card_purchases")
public class CardPurchase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDate purchaseDate;
  private String merchantName;
  private String cardHolder;
  private String cardLast4;
  private String reference;
  private int netAmount;
  private int vatAmount;
  private int totalAmount;
  @Column(name = "net_amount_minor")
  private Long netAmountMinor;
  @Column(name = "vat_amount_minor")
  private Long vatAmountMinor;
  @Column(name = "total_amount_minor")
  private Long totalAmountMinor;
  private String category;
  private String clearingAccount;
  private String status;
  private Long bookedExpenseId;
  private Instant createdAt;
  private Instant updatedAt;

  public CardPurchase() {
  }

  public CardPurchase(
      LocalDate purchaseDate,
      String merchantName,
      String cardHolder,
      String cardLast4,
      String reference,
      int totalAmount,
      int vatAmount,
      String category,
      String clearingAccount
  ) {
    this.purchaseDate = purchaseDate;
    this.merchantName = merchantName;
    this.cardHolder = cardHolder;
    this.cardLast4 = cardLast4;
    this.reference = reference;
    this.totalAmount = totalAmount;
    this.vatAmount = vatAmount;
    this.netAmount = Math.max(totalAmount - vatAmount, 0);
    this.totalAmountMinor = toMinorUnits(totalAmount);
    this.vatAmountMinor = toMinorUnits(vatAmount);
    this.netAmountMinor = toMinorUnits(this.netAmount);
    this.category = category;
    this.clearingAccount = clearingAccount;
    this.status = "review";
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public Long getId() {
    return id;
  }

  public LocalDate getPurchaseDate() {
    return purchaseDate;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public String getCardHolder() {
    return cardHolder;
  }

  public String getCardLast4() {
    return cardLast4;
  }

  public String getReference() {
    return reference;
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

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getNetAmountMinor() {
    return netAmountMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getVatAmountMinor() {
    return vatAmountMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getTotalAmountMinor() {
    return totalAmountMinor;
  }

  public String getCategory() {
    return category;
  }

  public String getClearingAccount() {
    return clearingAccount;
  }

  public String getStatus() {
    return status;
  }

  public Long getBookedExpenseId() {
    return bookedExpenseId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void markBooked(Long bookedExpenseId) {
    this.status = "booked";
    this.bookedExpenseId = bookedExpenseId;
    this.updatedAt = Instant.now();
  }

  private long toMinorUnits(int amount) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Card purchase amount is outside the supported minor-unit range.", exception);
    }
  }
}
