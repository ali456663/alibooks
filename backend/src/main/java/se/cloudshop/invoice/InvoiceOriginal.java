package se.cloudshop.invoice;

import jakarta.persistence.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import se.cloudshop.order.Order;

@Entity
@Table(name = "invoice_originals")
public class InvoiceOriginal {
  @Id
  @Column(name = "invoice_id")
  private Long invoiceId;
  @Column(nullable = false, updatable = false, columnDefinition = "bytea")
  private byte[] pdf;
  @Column(nullable = false, updatable = false, length = 64)
  private String sha256;
  @Column(nullable = false, updatable = false)
  private Instant archivedAt;

  protected InvoiceOriginal() {}

  InvoiceOriginal(Order invoice, byte[] pdf) {
    if (invoice.getId() == null || !"SENT".equals(invoice.getStatus())) {
      throw new IllegalStateException("Only a persisted, newly issued invoice can be archived.");
    }
    if (pdf == null || pdf.length < 5 || pdf.length > 10 * 1024 * 1024
        || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F' || pdf[4] != '-') {
      throw new IllegalArgumentException("Invalid invoice PDF.");
    }
    this.invoiceId = invoice.getId();
    this.pdf = pdf.clone();
    this.sha256 = digest(pdf);
    this.archivedAt = Instant.now();
  }

  public Long getInvoiceId() { return invoiceId; }
  public String getSha256() { return sha256; }
  public Instant getArchivedAt() { return archivedAt; }

  public byte[] verifiedPdf() {
    if (pdf == null || !digest(pdf).equals(sha256)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Invoice original failed its integrity check. Restore the verified original before continuing.");
    }
    return pdf.clone();
  }

  static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }
}
