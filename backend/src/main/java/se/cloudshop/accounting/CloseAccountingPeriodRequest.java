package se.cloudshop.accounting;

import java.time.LocalDate;

public record CloseAccountingPeriodRequest(
    LocalDate lockedThroughDate
) {
}
