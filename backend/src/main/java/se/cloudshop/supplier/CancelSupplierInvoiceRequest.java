package se.cloudshop.supplier;

import java.time.LocalDate;

public record CancelSupplierInvoiceRequest(
    LocalDate cancellationDate
) {
}
