package se.cloudshop.supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** A dated, immutable-in-practice payment row for a supplier invoice. */
@Entity
@Table(name = "supplier_invoice_payments")
public class SupplierPayment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "supplier_invoice_id", nullable = false)
  @JsonIgnore
  private SupplierInvoice invoice;

  @Column(name = "payment_date", nullable = false)
  private LocalDate paymentDate;

  private int amount;

  @Column(name = "amount_minor")
  private Long amountMinor;

  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode = "SEK";

  private String reference;
  private Instant createdAt;

  protected SupplierPayment() {
  }

  SupplierPayment(SupplierInvoice invoice, LocalDate paymentDate, int amount, String reference) {
    this.invoice = invoice;
    this.paymentDate = paymentDate;
    this.amount = amount;
    this.amountMinor = toMinorUnits(amount);
    this.currencyCode = invoice.getCurrencyCode();
    this.reference = reference == null ? "" : reference.trim();
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public int getAmount() {
    return amount;
  }

  @com.fasterxml.jackson.annotation.JsonProperty("amountMinor")
  public long getAmountMinor() {
    return amountMinor == null ? toMinorUnits(amount) : amountMinor;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public String getReference() {
    return reference;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  long getAmountMinorValue() {
    return getAmountMinor();
  }

  private static long toMinorUnits(int amount) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Supplier payment is outside the supported money range.", exception);
    }
  }

  @PostLoad
  private void validateMinorUnitShadow() {
    if (amountMinor != null && amountMinor.longValue() != toMinorUnits(amount)) {
      throw new IllegalStateException("Supplier payment shadow does not match the payment amount.");
    }
    amountMinor = toMinorUnits(amount);
  }

  @PrePersist
  @PreUpdate
  private void synchronizeMinorUnits() {
    amountMinor = toMinorUnits(amount);
    currencyCode = currencyCode == null || currencyCode.isBlank() ? "SEK" : currencyCode;
  }
}
