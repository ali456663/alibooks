package se.cloudshop.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_snapshots")
public class PayrollSnapshot {

  @Id
  private Long id;

  @Column(columnDefinition = "text")
  private String content;

  private LocalDateTime updatedAt;

  public PayrollSnapshot() {
  }

  public PayrollSnapshot(Long id, String content, LocalDateTime updatedAt) {
    this.id = id;
    this.content = content;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public String getContent() {
    return content;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void updateContent(String content) {
    this.content = content;
    this.updatedAt = LocalDateTime.now();
  }
}
