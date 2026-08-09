package se.cloudshop.order;

import java.time.LocalDate;

public record ReceivablesAgingInvoice(
    Long invoiceId,
    String invoiceNumber,
    String customerName,
    String customerEmail,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String status,
    int totalAmount,
    int paidAmount,
    int remainingAmount,
    long daysOverdue,
    String bucketKey,
    String bucketTitle,
    boolean reminderRecommended
) {
}
