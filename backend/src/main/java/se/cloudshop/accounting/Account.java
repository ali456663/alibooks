package se.cloudshop.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String number;
  private String name;
  private String companyType;

  public Account() {
  }

  public Account(String number, String name) {
    this.number = number;
    this.name = name;
    this.companyType = "BOTH";
  }

  public Account(String number, String name, String companyType) {
    this.number = number;
    this.name = name;
    this.companyType = companyType;
  }

  public Long getId() {
    return id;
  }

  public String getNumber() {
    return number;
  }

  public String getName() {
    return name;
  }

  public String getCompanyType() {
    return companyType;
  }

  public void update(String name, String companyType) {
    this.name = name;
    this.companyType = companyType;
  }
}
