package se.cloudshop.card;

import java.time.LocalDate;

public record CreateCardPurchaseRequest(
    LocalDate purchaseDate,
    String merchantName,
    String cardHolder,
    String cardLast4,
    String reference,
    int totalAmount,
    int vatAmount,
    String category,
    String clearingAccount
) {
}
