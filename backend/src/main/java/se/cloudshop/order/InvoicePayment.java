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
    return amountMinor == null ? toMinorUnits(amount) : amountMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getAmountMinor() {
    return amountMinor;
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
  private void validateMinorUnitShadow() {
    if (amountMinor != null && amountMinor.longValue() != toMinorUnits(amount)) {
      throw new IllegalStateException("Invoice payment shadow does not match the payment amount.");
    }
    amountMinor = toMinorUnits(amount);
  }

  @PrePersist
  @PreUpdate
  private void synchronizeMinorUnits() {
    amountMinor = toMinorUnits(amount);
  }
}
