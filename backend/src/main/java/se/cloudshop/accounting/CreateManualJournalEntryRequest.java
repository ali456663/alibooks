package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateManualJournalEntryRequest(
    LocalDate voucherDate,
    String description,
    String debitAccountNumber,
    String creditAccountNumber,
    int amount
) {
}
