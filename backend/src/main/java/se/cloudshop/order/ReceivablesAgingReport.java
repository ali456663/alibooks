package se.cloudshop.order;

import java.time.LocalDate;
import java.util.List;

public record ReceivablesAgingReport(
    LocalDate asOf,
    int invoiceCount,
    int totalOutstanding,
    int notDueOutstanding,
    int overdueOutstanding,
    int dueSoonOutstanding,
    int noDueDateOutstanding,
    List<ReceivablesAgingBucket> buckets,
    List<ReceivablesAgingInvoice> invoices
) {
}
