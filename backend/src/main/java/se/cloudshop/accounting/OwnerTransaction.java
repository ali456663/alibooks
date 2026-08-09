package se.cloudshop.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "owner_transactions")
public class OwnerTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "transaction_type")
  private String type;

  @Column(name = "transaction_date")
  private LocalDate date;

  private int amount;
  private String description;
  private String reference;
  private String debitAccount;
  private String creditAccount;
  private String status;
  @Column(name = "voucher_number")
  private String voucherNumber;
  private Instant createdAt;
  private Instant updatedAt;

  public OwnerTransaction() {
  }

  public OwnerTransaction(
      String type,
      LocalDate date,
      int amount,
      String description,
      String reference,
      String debitAccount,
      String creditAccount
  ) {
    this.type = type;
    this.date = date;
    this.amount = amount;
    this.description = description;
    this.reference = reference;
    this.debitAccount = debitAccount;
    this.creditAccount = creditAccount;
    this.status = "draft";
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public LocalDate getDate() {
    return date;
  }

  public int getAmount() {
    return amount;
  }

  public String getDescription() {
    return description;
  }

  public String getReference() {
    return reference;
  }

  public String getDebitAccount() {
    return debitAccount;
  }

  public String getCreditAccount() {
    return creditAccount;
  }

  public String getStatus() {
    return status;
  }

  public String getVoucherNumber() {
    return voucherNumber;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void updateStatus(String status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public void markBooked(String voucherNumber) {
    this.status = "booked";
    this.voucherNumber = voucherNumber;
    this.updatedAt = Instant.now();
  }
}
