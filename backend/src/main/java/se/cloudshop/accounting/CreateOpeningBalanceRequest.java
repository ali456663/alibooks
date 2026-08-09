package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record CreateOpeningBalanceRequest(
    LocalDate voucherDate,
    String description,
    List<CreateManualJournalEntryLineRequest> lines
) {
}
