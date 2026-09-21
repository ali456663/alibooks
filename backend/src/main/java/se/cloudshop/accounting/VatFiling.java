package se.cloudshop.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
  @Column(name = "output_vat_minor")
  private Long outputVatMinor;
  @Column(name = "input_vat_minor")
  private Long inputVatMinor;
  @Column(name = "vat_to_pay_minor")
  private Long vatToPayMinor;
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
    this.outputVatMinor = toMinorUnits(this.outputVat);
    this.inputVatMinor = toMinorUnits(this.inputVat);
    this.vatToPayMinor = toMinorUnits(this.vatToPay);
    this.status = normalizedStatus;
    this.submissionReference = clean(request.submissionReference());
    this.paymentReference = clean(request.paymentReference());
    this.note = clean(request.note());
    this.createdAt = Instant.now();
    applyStatusTimestamps(normalizedStatus);
    this.updatedAt = Instant.now();
  }

  @PostLoad
  private void validateMinorUnitShadow() {
    if (outputVatMinor != null && outputVatMinor.longValue() != toMinorUnits(outputVat)) {
      throw new IllegalStateException("VAT output shadow does not match the filing amount.");
    }
    if (inputVatMinor != null && inputVatMinor.longValue() != toMinorUnits(inputVat)) {
      throw new IllegalStateException("VAT input shadow does not match the filing amount.");
    }
    if (vatToPayMinor != null && vatToPayMinor.longValue() != toMinorUnits(vatToPay)) {
      throw new IllegalStateException("VAT payable shadow does not match the filing amount.");
    }
    synchronizeMinorUnits();
  }

  @PrePersist
  @PreUpdate
  private void synchronizeMinorUnits() {
    outputVatMinor = toMinorUnits(outputVat);
    inputVatMinor = toMinorUnits(inputVat);
    vatToPayMinor = toMinorUnits(vatToPay);
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

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getOutputVatMinor() {
    return outputVatMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getInputVatMinor() {
    return inputVatMinor;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public Long getVatToPayMinor() {
    return vatToPayMinor;
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

  private long toMinorUnits(int amount) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("VAT filing amount is outside the supported minor-unit range.", exception);
    }
  }
}
