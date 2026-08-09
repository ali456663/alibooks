package se.cloudshop.accounting;

import java.time.LocalDate;

public record VatFilingProofVoucher(
    String voucherNumber,
    LocalDate voucherDate,
    String description,
    int debit,
    int credit
) {}
