package se.cloudshop.supplier;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String email;
  private String orgNumber;
  private String phone;
  private String paymentInfo;
  @Column(nullable = false, columnDefinition = "boolean default false")
  private boolean archived = false;

  public Supplier() {
  }

  public Supplier(String name, String email, String orgNumber, String phone, String paymentInfo) {
    this.name = name;
    this.email = email;
    this.orgNumber = orgNumber;
    this.phone = phone;
    this.paymentInfo = paymentInfo;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getOrgNumber() {
    return orgNumber;
  }

  public String getPhone() {
    return phone;
  }

  public String getPaymentInfo() {
    return paymentInfo;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }
}
