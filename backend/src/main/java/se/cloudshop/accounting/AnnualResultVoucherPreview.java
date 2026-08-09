package se.cloudshop.accounting;

import java.time.LocalDate;

public record AnnualResultVoucherPreview(
    int year,
    LocalDate periodFrom,
    LocalDate periodTo,
    LocalDate voucherDate,
    String companyType,
    String resultAccountNumber,
    String resultAccountName,
    int result,
    String debitAccountNumber,
    String creditAccountNumber,
    int amount,
    boolean alreadyBooked
) {
}
