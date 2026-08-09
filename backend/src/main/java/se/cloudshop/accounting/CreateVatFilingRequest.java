package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateVatFilingRequest(
    LocalDate periodFrom,
    LocalDate periodTo,
    String status,
    String submissionReference,
    String paymentReference,
    LocalDate paymentDate,
    String note
) {
}
