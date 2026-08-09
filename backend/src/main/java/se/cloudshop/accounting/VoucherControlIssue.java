package se.cloudshop.accounting;

import java.time.LocalDate;

public record VoucherControlIssue(
    String severity,
    String issueType,
    String voucherNumber,
    String voucherSeries,
    Integer expectedNumber,
    Integer actualNumber,
    LocalDate voucherDate,
    int debit,
    int credit,
    String message
) {
}
