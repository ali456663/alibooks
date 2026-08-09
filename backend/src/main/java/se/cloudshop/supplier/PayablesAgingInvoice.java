package se.cloudshop.supplier;

import java.time.LocalDate;

public record PayablesAgingInvoice(
    Long invoiceId,
    String supplierName,
    String supplierEmail,
    String supplierOrgNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String status,
    String reference,
    String description,
    String category,
    int netAmount,
    int vatAmount,
    int totalAmount,
    int remainingAmount,
    int daysOverdue,
    String bucketKey,
    String bucketTitle,
    boolean paymentRecommended
) {
}
