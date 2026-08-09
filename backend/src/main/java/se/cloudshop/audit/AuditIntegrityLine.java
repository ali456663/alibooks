package se.cloudshop.audit;

import java.time.Instant;

public record AuditIntegrityLine(
    int sequenceNumber,
    Long eventId,
    Instant createdAt,
    String eventType,
    String entityType,
    String entityId,
    String action,
    String reference,
    int amount,
    String actorEmail,
    String rowHash,
    String previousChainHash,
    String chainHash
) {
}
