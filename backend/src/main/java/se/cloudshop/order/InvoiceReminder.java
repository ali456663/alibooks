package se.cloudshop.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "invoice_reminders")
public class InvoiceReminder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JsonIgnore
  private Order invoice;

  private Instant createdAt;
  private String method;
  private String status;
  private String recipientEmail;

  public InvoiceReminder() {
  }

  public InvoiceReminder(Order invoice, String method, String status, String recipientEmail) {
    this.invoice = invoice;
    this.createdAt = Instant.now();
    this.method = method;
    this.status = status;
    this.recipientEmail = recipientEmail;
  }

  public Long getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getMethod() {
    return method;
  }

  public String getStatus() {
    return status;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }
}
