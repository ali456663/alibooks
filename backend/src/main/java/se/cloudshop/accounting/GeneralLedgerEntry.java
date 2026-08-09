package se.cloudshop.accounting;

import java.time.LocalDate;

public record GeneralLedgerEntry(
    Long journalEntryId,
    LocalDate voucherDate,
    String voucherNumber,
    String accountNumber,
    String accountName,
    String description,
    String sourceType,
    String sourceReference,
    String evidenceStatus,
    String evidenceHash,
    String correctionOfVoucherNumber,
    int debit,
    int credit,
    int balance,
    String integrityHash
) {
}
