package se.cloudshop.order;

import java.time.LocalDate;

public record ReceivablesAgingBucket(
    String key,
    String title,
    int invoiceCount,
    int totalRemaining,
    LocalDate oldestDueDate
) {
}
