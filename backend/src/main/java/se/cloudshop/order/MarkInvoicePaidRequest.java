package se.cloudshop.order;

import java.time.LocalDate;

public record MarkInvoicePaidRequest(
    LocalDate paymentDate,
    Integer paidAmount,
    String paymentReference
) {
}
