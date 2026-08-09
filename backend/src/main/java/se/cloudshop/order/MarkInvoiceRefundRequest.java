package se.cloudshop.order;

import java.time.LocalDate;

public record MarkInvoiceRefundRequest(
    LocalDate refundDate,
    Integer refundAmount,
    String refundReference
) {
}
