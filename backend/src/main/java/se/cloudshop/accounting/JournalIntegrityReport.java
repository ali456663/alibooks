package se.cloudshop.accounting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JournalIntegrityReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    Instant generatedAt,
    int entryCount,
    int voucherCount,
    int totalDebit,
    int totalCredit,
    int difference,
    int missingEvidenceCount,
    String firstChainHash,
    String finalChainHash,
    String periodFingerprint,
    List<JournalIntegrityLine> lines
) {
}
