package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateAnnualResultVoucherRequest(
    Integer year,
    LocalDate voucherDate
) {
}
