package se.cloudshop.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "stripe_payouts")
public class StripePayout {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDate payoutDate;
  private int grossAmount;
  private int feeAmount;
  private int netAmount;
  private String reference;
  private String voucherNumber;
  private Instant createdAt;

  public StripePayout() {
  }

  public StripePayout(LocalDate payoutDate, int grossAmount, int feeAmount, String reference, String voucherNumber) {
    this.payoutDate = payoutDate;
    this.grossAmount = grossAmount;
    this.feeAmount = feeAmount;
    this.netAmount = grossAmount - feeAmount;
    this.reference = reference;
    this.voucherNumber = voucherNumber;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public LocalDate getPayoutDate() {
    return payoutDate;
  }

  public int getGrossAmount() {
    return grossAmount;
  }

  public int getFeeAmount() {
    return feeAmount;
  }

  public int getNetAmount() {
    return netAmount;
  }

  public String getReference() {
    return reference;
  }

  public String getVoucherNumber() {
    return voucherNumber;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
