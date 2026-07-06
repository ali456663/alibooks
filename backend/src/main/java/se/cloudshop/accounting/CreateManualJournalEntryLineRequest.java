package se.cloudshop.accounting;

public record CreateManualJournalEntryLineRequest(
    String accountNumber,
    int debit,
    int credit
) {
}
