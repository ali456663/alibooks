package se.cloudshop.bank;

import java.time.LocalDate;

public record CreateBankReconciliationEntryRequest(
    String bankRowId,
    LocalDate date,
    String description,
    String reference,
    int amount,
    String type,
    String status,
    String matchLabel
) {
}
