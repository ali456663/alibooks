package se.cloudshop.accounting;

import java.time.LocalDate;

public record VatControlIssue(
    String severity,
    String issueType,
    String voucherNumber,
    LocalDate voucherDate,
    int salesNet,
    int outputVat,
    int expectedOutputVat,
    int purchaseNet,
    int inputVat,
    int expectedMaxInputVat,
    int difference,
    String message
) {
}
