package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateStripeWebsiteSaleRequest(
    LocalDate saleDate,
    int totalAmount,
    String reference
) {
}
