package se.cloudshop.accounting;

import java.time.LocalDate;

public record UpdateVatFilingStatusRequest(
    String status,
    String submissionReference,
    String paymentReference,
    LocalDate paymentDate,
    String note
) {
}
