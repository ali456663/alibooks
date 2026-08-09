package se.cloudshop.supplier;

import java.time.LocalDate;

public record UpdateSupplierInvoiceStatusRequest(
    String status,
    LocalDate paidAt,
    Integer paidAmount,
    String paymentReference
) {
}
