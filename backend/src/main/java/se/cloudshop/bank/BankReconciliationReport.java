package se.cloudshop.bank;

import java.time.LocalDate;
import java.util.List;

public record BankReconciliationReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    int journalEntryCount,
    int bankRowCount,
    int bookedBankRowCount,
    int skippedBankRowCount,
    int ledgerMovement,
    int reconciledMovement,
    int difference,
    int criticalIssueCount,
    int warningIssueCount,
    List<BankReconciliationIssue> issues
) {
}
