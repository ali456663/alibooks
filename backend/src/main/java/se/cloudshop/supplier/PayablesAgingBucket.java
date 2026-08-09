package se.cloudshop.supplier;

import java.time.LocalDate;

public record PayablesAgingBucket(
    String key,
    String title,
    int invoiceCount,
    int totalRemaining,
    LocalDate oldestDueDate
) {
}
