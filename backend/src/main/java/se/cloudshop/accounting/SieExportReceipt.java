package se.cloudshop.accounting;

import java.time.Instant;
import java.time.LocalDate;

public record SieExportReceipt(
    LocalDate periodFrom,
    LocalDate periodTo,
    Instant generatedAt,
    String companyName,
    boolean exportReady,
    String message,
    int voucherCount,
    int entryCount,
    int accountCount,
    int totalDebit,
    int totalCredit,
    int difference,
    int criticalIssueCount,
    int warningIssueCount,
    int unbalancedVoucherCount,
    int missingEvidenceCount,
    String periodFingerprint,
    String finalChainHash,
    String exportControlHash
) {
}
