package se.cloudshop.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String email;
  private String personalNumber;
  private String address;
  private String phone;
  private String postalCode;
  private String city;
  private boolean archived;

  public Customer() {
  }

  public Customer(
      String name,
      String email,
      String personalNumber,
      String address,
      String phone,
      String postalCode,
      String city
  ) {
    this.name = name;
    this.email = email;
    this.personalNumber = personalNumber;
    this.address = address;
    this.phone = phone;
    this.postalCode = postalCode;
    this.city = city;
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

  public String getPersonalNumber() {
    return personalNumber;
  }

  public String getAddress() {
    return address;
  }

  public String getPhone() {
    return phone;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public String getCity() {
    return city;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  public void updateFrom(CreateCustomerRequest request) {
    this.name = request.name();
    this.email = request.email();
    this.personalNumber = request.personalNumber();
    this.address = request.address();
    this.phone = request.phone();
    this.postalCode = request.postalCode();
    this.city = request.city();
  }
}
