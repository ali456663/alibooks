package se.cloudshop.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;

@Entity
@Table(name = "voucher_evidence_contents")
public class VoucherEvidenceContent {

  @Id
  @Column(name = "voucher_evidence_id")
  private Long voucherEvidenceId;

  @Column(nullable = false, columnDefinition = "bytea")
  private byte[] content;

  protected VoucherEvidenceContent() {
  }

  public VoucherEvidenceContent(Long voucherEvidenceId, byte[] content) {
    this.voucherEvidenceId = voucherEvidenceId;
    this.content = Arrays.copyOf(content, content.length);
  }

  public byte[] getContent() {
    return Arrays.copyOf(content, content.length);
  }
}
