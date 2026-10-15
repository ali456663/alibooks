package se.cloudshop.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "voucher_evidence")
public class VoucherEvidence {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "voucher_number", nullable = false, unique = true, length = 255)
  private String voucherNumber;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(nullable = false, length = 64)
  private String sha256;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;

  @Column(name = "file_size", nullable = false)
  private int fileSize;

  protected VoucherEvidence() {
  }

  public VoucherEvidence(String voucherNumber, String fileName, String contentType, String sha256, byte[] content) {
    this.voucherNumber = voucherNumber;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sha256 = sha256;
    this.fileSize = content.length;
    this.uploadedAt = Instant.now();
  }

  public String getVoucherNumber() {
    return voucherNumber;
  }

  public Long getId() {
    return id;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public String getSha256() {
    return sha256;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public int getSize() {
    return fileSize;
  }
}
