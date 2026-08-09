package se.cloudshop.accounting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ArchiveYearReport(
    int year,
    LocalDate periodFrom,
    LocalDate periodTo,
    Instant generatedAt,
    String retentionUntil,
    int score,
    int readyItemCount,
    int warningItemCount,
    int criticalItemCount,
    int journalEntryCount,
    int voucherCount,
    int invoiceCount,
    int expenseCount,
    int receiptCount,
    int missingReceiptCount,
    int missingReceiptHashCount,
    int vatFilingCount,
    int voucherApprovedCount,
    int voucherMissingApprovalCount,
    int voucherPendingApprovalCount,
    int voucherBlockedApprovalCount,
    boolean journalBalanced,
    String periodFingerprint,
    String finalChainHash,
    List<ArchiveYearItem> items
) {
}
