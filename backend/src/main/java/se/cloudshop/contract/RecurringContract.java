package se.cloudshop.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_contracts")
public class RecurringContract {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long customerId;
  private String customerName;
  private Long serviceId;
  private String serviceName;
  private int quantity;
  @Column(name = "contract_interval")
  private String interval;
  private LocalDate nextInvoiceDate;
  private boolean active;
  private String lastInvoiceNumber;
  private Instant createdAt;

  public RecurringContract() {
  }

  public RecurringContract(
      Long customerId,
      String customerName,
      Long serviceId,
      String serviceName,
      int quantity,
      String interval,
      LocalDate nextInvoiceDate
  ) {
    this.customerId = customerId;
    this.customerName = customerName;
    this.serviceId = serviceId;
    this.serviceName = serviceName;
    this.quantity = quantity;
    this.interval = interval;
    this.nextInvoiceDate = nextInvoiceDate;
    this.active = true;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public String getCustomerName() {
    return customerName;
  }

  public Long getServiceId() {
    return serviceId;
  }

  public String getServiceName() {
    return serviceName;
  }

  public int getQuantity() {
    return quantity;
  }

  public String getInterval() {
    return interval;
  }

  public LocalDate getNextInvoiceDate() {
    return nextInvoiceDate;
  }

  public boolean isActive() {
    return active;
  }

  public String getLastInvoiceNumber() {
    return lastInvoiceNumber;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public void setNextInvoiceDate(LocalDate nextInvoiceDate) {
    this.nextInvoiceDate = nextInvoiceDate;
  }

  public void setLastInvoiceNumber(String lastInvoiceNumber) {
    this.lastInvoiceNumber = lastInvoiceNumber;
  }
}
