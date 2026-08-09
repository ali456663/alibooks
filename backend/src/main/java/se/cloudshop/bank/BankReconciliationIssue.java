package se.cloudshop.bank;

import java.time.LocalDate;

public record BankReconciliationIssue(
    String severity,
    String issueType,
    LocalDate date,
    String reference,
    int amount,
    String message
) {
}
