package se.cloudshop.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
  @Column(name = "gross_amount_minor")
  private Long grossAmountMinor;
  @Column(name = "fee_amount_minor")
  private Long feeAmountMinor;
  @Column(name = "net_amount_minor")
  private Long netAmountMinor;
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
    this.grossAmountMinor = toMinorUnits(grossAmount);
    this.feeAmountMinor = toMinorUnits(feeAmount);
    this.netAmountMinor = toMinorUnits(this.netAmount);
    this.reference = reference;
    this.voucherNumber = voucherNumber;
    this.createdAt = Instant.now();
  }

  @PostLoad
  private void validateMinorUnitShadow() {
    if (grossAmountMinor != null && grossAmountMinor.longValue() != toMinorUnits(grossAmount)) {
      throw new IllegalStateException("Stripe payout gross amount shadow does not match the ledger amount.");
    }
    if (feeAmountMinor != null && feeAmountMinor.longValue() != toMinorUnits(feeAmount)) {
      throw new IllegalStateException("Stripe payout fee shadow does not match the ledger amount.");
    }
    if (netAmountMinor != null && netAmountMinor.longValue() != toMinorUnits(netAmount)) {
      throw new IllegalStateException("Stripe payout net amount shadow does not match the ledger amount.");
    }
    synchronizeMinorUnits();
  }

  @PrePersist
  @PreUpdate
  private void synchronizeMinorUnits() {
    grossAmountMinor = toMinorUnits(grossAmount);
    feeAmountMinor = toMinorUnits(feeAmount);
    netAmountMinor = toMinorUnits(netAmount);
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

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getGrossAmountMinor() {
    return grossAmountMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getFeeAmountMinor() {
    return feeAmountMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getNetAmountMinor() {
    return netAmountMinor;
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

  private long toMinorUnits(int amount) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Stripe payout amount is outside the supported minor-unit range.", exception);
    }
  }
}
