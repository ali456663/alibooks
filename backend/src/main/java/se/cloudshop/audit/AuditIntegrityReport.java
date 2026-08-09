package se.cloudshop.audit;

import java.time.Instant;
import java.util.List;

public record AuditIntegrityReport(
    Instant generatedAt,
    int eventCount,
    String firstChainHash,
    String finalChainHash,
    String auditFingerprint,
    List<AuditIntegrityLine> lines
) {
}
