package se.cloudshop.accounting;

import java.time.LocalDate;

public record LateBookedVoucher(
    String voucherNumber,
    LocalDate voucherDate,
    LocalDate bookedDate,
    int lagDays,
    int debit,
    int credit,
    String description
) {
}
