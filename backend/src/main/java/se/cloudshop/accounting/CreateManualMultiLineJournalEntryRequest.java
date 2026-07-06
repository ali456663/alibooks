package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record CreateManualMultiLineJournalEntryRequest(
    LocalDate voucherDate,
    String description,
    List<CreateManualJournalEntryLineRequest> lines
) {
}
