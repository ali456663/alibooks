package se.cloudshop.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
  private String reference;
  private Instant createdAt;

  public InvoicePayment() {
  }

  public InvoicePayment(Order invoice, LocalDate paymentDate, int amount, String reference) {
    this.invoice = invoice;
    this.paymentDate = paymentDate;
    this.amount = amount;
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

  public String getReference() {
    return reference;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
