package se.cloudshop.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Entity
@Table(name = "email_delivery_attempts")
public class EmailDeliveryAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "delivery_type", nullable = false, length = 64)
  private String deliveryType;

  @Column(name = "invoice_id")
  private Long invoiceId;

  @Column(name = "recipient_email", nullable = false, length = 255)
  private String recipientEmail;

  @Column(nullable = false, length = 512)
  private String subject;

  @Lob
  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "attachment_name", length = 255)
  private String attachmentName;

  @Lob
  @Column(name = "attachment_content", columnDefinition = "bytea")
  private byte[] attachmentContent;

  @Column(name = "attachment_sha256", length = 64)
  private String attachmentSha256;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 2000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  protected EmailDeliveryAttempt() {}

  public EmailDeliveryAttempt(
      String deliveryType,
      Long invoiceId,
      String recipientEmail,
      String subject,
      String body,
      String attachmentName,
      byte[] attachmentContent
  ) {
    this.deliveryType = required(deliveryType, "deliveryType");
    this.invoiceId = invoiceId;
    this.recipientEmail = required(recipientEmail, "recipientEmail");
    this.subject = required(subject, "subject");
    this.body = required(body, "body");
    this.attachmentName = attachmentName;
    this.attachmentContent = attachmentContent == null ? null : attachmentContent.clone();
    this.attachmentSha256 = attachmentContent == null ? null : sha256(attachmentContent);
    this.status = "PENDING";
    this.attempts = 1;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void markSent() {
    this.status = "SENT";
    this.sentAt = Instant.now();
    this.updatedAt = this.sentAt;
    this.lastError = null;
  }

  public void markUncertain(Throwable exception) {
    this.status = "UNCERTAIN";
    this.updatedAt = Instant.now();
    this.lastError = shorten(exception == null ? "Unknown email delivery failure." : exception.getMessage());
  }

  public Long getId() { return id; }
  public String getDeliveryType() { return deliveryType; }
  public Long getInvoiceId() { return invoiceId; }
  public String getRecipientEmail() { return recipientEmail; }
  public String getSubject() { return subject; }
  public String getBody() { return body; }
  public String getAttachmentName() { return attachmentName; }
  public byte[] getAttachmentContent() { return attachmentContent == null ? null : attachmentContent.clone(); }
  public String getAttachmentSha256() { return attachmentSha256; }
  public String getStatus() { return status; }
  public int getAttempts() { return attempts; }
  public String getLastError() { return lastError; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public Instant getSentAt() { return sentAt; }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required.");
    }
    return value;
  }

  private static String shorten(String value) {
    String clean = value == null || value.isBlank() ? "Unknown email delivery failure." : value;
    return clean.length() <= 2000 ? clean : clean.substring(0, 2000);
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }
}
