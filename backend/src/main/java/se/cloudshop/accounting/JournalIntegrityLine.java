package se.cloudshop.accounting;

import java.time.LocalDate;

public record JournalIntegrityLine(
    int sequenceNumber,
    Long journalEntryId,
    LocalDate voucherDate,
    String voucherNumber,
    String accountNumber,
    String accountName,
    String description,
    int debit,
    int credit,
    String sourceType,
    String sourceReference,
    String evidenceStatus,
    String evidenceHash,
    String rowHash,
    String previousChainHash,
    String chainHash
) {
}
