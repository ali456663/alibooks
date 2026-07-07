package se.cloudshop.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_snapshot_revisions")
public class PayrollSnapshotRevision {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(columnDefinition = "text")
  private String content;

  private LocalDateTime createdAt;

  public PayrollSnapshotRevision() {
  }

  public PayrollSnapshotRevision(String content, LocalDateTime createdAt) {
    this.content = content;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getContent() {
    return content;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
