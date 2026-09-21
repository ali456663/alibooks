package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateStripeWebsiteSaleRequest(
    LocalDate saleDate,
    int totalAmount,
    String reference,
    Integer vatPercent
) {
  public CreateStripeWebsiteSaleRequest(LocalDate saleDate, int totalAmount, String reference) {
    this(saleDate, totalAmount, reference, 25);
  }

  public int effectiveVatPercent() {
    return vatPercent == null ? 25 : vatPercent;
  }
}
