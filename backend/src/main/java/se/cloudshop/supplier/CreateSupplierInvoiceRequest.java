package se.cloudshop.supplier;

import java.time.LocalDate;

public record CreateSupplierInvoiceRequest(
    Long supplierId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String description,
    String reference,
    int totalAmount,
    int vatAmount,
    String category
) {
}
