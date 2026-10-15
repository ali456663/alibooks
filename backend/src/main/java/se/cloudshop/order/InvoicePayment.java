package se.cloudshop.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "invoice_payments")
public class InvoicePayment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JsonIgnore
  private Order invoice;

  private LocalDate paymentDate;
  private int amount;
  @Column(name = "amount_minor")
  private Long amountMinor;
  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode = "SEK";
  private String reference;
  private Instant createdAt;

  public InvoicePayment() {
  }

  public InvoicePayment(Order invoice, LocalDate paymentDate, int amount, String reference) {
    this.invoice = invoice;
    this.paymentDate = paymentDate;
    this.amount = amount;
    this.amountMinor = toMinorUnits(amount);
    this.reference = reference;
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

  long getAmountMinorValue() {
    validateMinorUnitShadow();
    return amountMinor;
  }

  public Long getAmountMinor() {
    validateMinorUnitShadow();
    return amountMinor;
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

  private static long toMinorUnits(int amount) {
    return Math.multiplyExact((long) amount, 100L);
  }

  @PostLoad
  @PrePersist
  @PreUpdate
  void validateMinorUnitShadow() {
    if (amountMinor == null || amountMinor.longValue() != toMinorUnits(amount) || !"SEK".equals(currencyCode)) {
      throw new IllegalStateException("Invoice payment minor units or currency do not match the payment amount.");
    }
  }
}
