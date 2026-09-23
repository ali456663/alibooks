package se.cloudshop.product;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String description;
  private int price;
  private int discountPrice;
  @Column(name = "price_minor")
  private Long priceMinor;
  @Column(name = "discount_price_minor")
  private Long discountPriceMinor;
  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode = "SEK";
  @Column(nullable = false, columnDefinition = "integer default 25")
  private int vatPercent = 25;
  private String discountLabel;
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean active = true;

  public Product() {
  }

  public Product(String name, String description, int price) {
    this.name = name;
    this.description = description;
    setPrice(price);
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getPrice() {
    return price;
  }

  public void setPrice(int price) {
    this.price = price;
    this.priceMinor = toMinorUnits(price, "price");
  }

  public int getDiscountPrice() {
    return discountPrice;
  }

  public void setDiscountPrice(int discountPrice) {
    this.discountPrice = discountPrice;
    this.discountPriceMinor = toMinorUnits(discountPrice, "discountPrice");
  }

  public Long getPriceMinor() {
    return priceMinor;
  }

  public Long getDiscountPriceMinor() {
    return discountPriceMinor;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public int getVatPercent() {
    return vatPercent;
  }

  public void setVatPercent(int vatPercent) {
    this.vatPercent = vatPercent;
  }

  public String getDiscountLabel() {
    return discountLabel;
  }

  public void setDiscountLabel(String discountLabel) {
    this.discountLabel = discountLabel;
  }

  public int getEffectivePrice() {
    return discountPrice > 0 ? discountPrice : price;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  private static long toMinorUnits(int amount, String field) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Product " + field + " is outside the supported money range.", exception);
    }
  }

}
