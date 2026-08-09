package se.cloudshop.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "voucher_approvals")
public class VoucherApproval {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

    @Column(name = "voucher_number")
    private String voucherNumber;

  private String status;

  @Column(columnDefinition = "text")
  private String note;

  private String reviewer;
  private Instant reviewedAt;
  private Instant createdAt;
  private Instant updatedAt;

  public VoucherApproval() {
  }

  public VoucherApproval(String voucherNumber, String status, String note, String reviewer) {
    this.voucherNumber = voucherNumber;
    this.createdAt = Instant.now();
    update(status, note, reviewer);
  }

  public void update(String status, String note, String reviewer) {
    this.status = status;
    this.note = note;
    this.reviewer = reviewer;
    this.reviewedAt = Instant.now();
    this.updatedAt = this.reviewedAt;
  }

  public Long getId() {
    return id;
  }

  public String getVoucherNumber() {
    return voucherNumber;
  }

  public String getStatus() {
    return status;
  }

  public String getNote() {
    return note;
  }

  public String getReviewer() {
    return reviewer;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
