package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateCorrectionJournalEntryRequest(LocalDate voucherDate) {
}
