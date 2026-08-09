package se.cloudshop.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "vat_filings")
public class VatFiling {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDate periodFrom;
  private LocalDate periodTo;
  private int outputVat;
  private int inputVat;
  private int vatToPay;
  private String status;
  private String submissionReference;
  private String paymentReference;
  @Column(length = 1000)
  private String note;
  private Instant submittedAt;
  private Instant paidAt;
  private Instant createdAt;
  private Instant updatedAt;

  public VatFiling() {
  }

  public VatFiling(VatReport report, CreateVatFilingRequest request, String normalizedStatus) {
    this.periodFrom = report.periodFrom();
    this.periodTo = report.periodTo();
    this.outputVat = report.outputVat();
    this.inputVat = report.inputVat();
    this.vatToPay = report.vatToPay();
    this.status = normalizedStatus;
    this.submissionReference = clean(request.submissionReference());
    this.paymentReference = clean(request.paymentReference());
    this.note = clean(request.note());
    this.createdAt = Instant.now();
    applyStatusTimestamps(normalizedStatus);
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public LocalDate getPeriodFrom() {
    return periodFrom;
  }

  public LocalDate getPeriodTo() {
    return periodTo;
  }

  public int getOutputVat() {
    return outputVat;
  }

  public int getInputVat() {
    return inputVat;
  }

  public int getVatToPay() {
    return vatToPay;
  }

  public String getStatus() {
    return status;
  }

  public String getSubmissionReference() {
    return submissionReference;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public String getNote() {
    return note;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void updateStatus(UpdateVatFilingStatusRequest request, String normalizedStatus) {
    this.status = normalizedStatus;
    if (request.submissionReference() != null) {
      this.submissionReference = clean(request.submissionReference());
    }
    if (request.paymentReference() != null) {
      this.paymentReference = clean(request.paymentReference());
    }
    if (request.note() != null) {
      this.note = clean(request.note());
    }
    applyStatusTimestamps(normalizedStatus);
    this.updatedAt = Instant.now();
  }

  private void applyStatusTimestamps(String normalizedStatus) {
    Instant now = Instant.now();
    if ("SUBMITTED".equals(normalizedStatus) || "PAID".equals(normalizedStatus)) {
      if (submittedAt == null) {
        submittedAt = now;
      }
    }
    if ("PAID".equals(normalizedStatus) && paidAt == null) {
      paidAt = now;
    }
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
