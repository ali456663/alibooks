package se.cloudshop.contract;

import java.time.LocalDate;

public record CreateRecurringContractRequest(
    Long customerId,
    Long serviceId,
    Integer quantity,
    String interval,
    LocalDate nextInvoiceDate
) {
}
