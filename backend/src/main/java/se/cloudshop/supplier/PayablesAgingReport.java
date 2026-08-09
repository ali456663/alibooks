package se.cloudshop.supplier;

import java.time.LocalDate;
import java.util.List;

public record PayablesAgingReport(
    LocalDate asOf,
    int invoiceCount,
    int totalOutstanding,
    int notDueOutstanding,
    int overdueOutstanding,
    int dueSoonOutstanding,
    int noDueDateOutstanding,
    int inputVatOutstanding,
    List<PayablesAgingBucket> buckets,
    List<PayablesAgingInvoice> invoices
) {
}
