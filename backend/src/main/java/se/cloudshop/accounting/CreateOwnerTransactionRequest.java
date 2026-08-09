package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateOwnerTransactionRequest(
    String type,
    LocalDate date,
    int amount,
    String description,
    String reference
) {
}
