package se.cloudshop.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import se.cloudshop.auth.JwtService;

@Service
public class AuditService {

  private final AuditEventRepository auditEventRepository;
  private final JwtService jwtService;

  public AuditService(AuditEventRepository auditEventRepository, JwtService jwtService) {
    this.auditEventRepository = auditEventRepository;
    this.jwtService = jwtService;
  }

  public List<AuditEvent> latestEvents() {
    return auditEventRepository.findTop300ByOrderByCreatedAtDesc();
  }

  public List<AuditEvent> allEventsNewestFirst() {
    return auditEventRepository.findAllByOrderByCreatedAtDescIdDesc();
  }

  public List<AuditEvent> eventsForEntityType(String entityType) {
    return auditEventRepository.findByEntityTypeOrderByCreatedAtDesc(clean(entityType));
  }

  public AuditIntegrityReport createIntegrityReport() {
    List<AuditEvent> events = auditEventRepository.findAllByOrderByCreatedAtAscIdAsc();
    List<AuditIntegrityLine> lines = new ArrayList<>();

    String previousChainHash = "START";
    for (int index = 0; index < events.size(); index++) {
      AuditEvent event = events.get(index);
      String rowHash = sha256(String.join("|",
          value(event.getId()),
          value(event.getCreatedAt()),
          value(event.getEventType()),
          value(event.getEntityType()),
          value(event.getEntityId()),
          value(event.getAction()),
          value(event.getReference()),
          value(event.getMessage()),
          String.valueOf(event.getAmount()),
          value(event.getActorEmail())
      ));
      String chainHash = sha256(String.join("|",
          previousChainHash,
          rowHash,
          String.valueOf(index + 1)
      ));

      lines.add(new AuditIntegrityLine(
          index + 1,
          event.getId(),
          event.getCreatedAt(),
          event.getEventType(),
          event.getEntityType(),
          event.getEntityId(),
          event.getAction(),
          event.getReference(),
          event.getAmount(),
          event.getActorEmail(),
          rowHash,
          previousChainHash,
          chainHash
      ));
      previousChainHash = chainHash;
    }

    String firstChainHash = lines.isEmpty() ? "" : lines.get(0).chainHash();
    String finalChainHash = lines.isEmpty() ? "" : lines.get(lines.size() - 1).chainHash();
    String auditFingerprint = sha256(String.join("|",
        String.valueOf(events.size()),
        firstChainHash,
        finalChainHash
    ));

    return new AuditIntegrityReport(
        Instant.now(),
        events.size(),
        firstChainHash,
        finalChainHash,
        auditFingerprint,
        lines
    );
  }

  public void record(String eventType, String entityType, Object entityId, String action, String reference, String message, int amount, String authorizationHeader) {
    auditEventRepository.save(new AuditEvent(
        clean(eventType),
        clean(entityType),
        entityId == null ? "" : String.valueOf(entityId),
        clean(action),
        clean(reference),
        clean(message),
        amount,
        actorFromHeader(authorizationHeader)
    ));
  }

  private String clean(String value) {
    if (value == null) {
      return "";
    }

    return value.length() > 1000 ? value.substring(0, 1000) : value;
  }

  private String actorFromHeader(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      return "system";
    }

    if (!authorizationHeader.startsWith("Bearer ")) {
      return "authenticated-user";
    }

    String token = authorizationHeader.substring("Bearer ".length());
    return jwtService.subject(token).orElse("authenticated-user");
  }

  private String value(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).toUpperCase();
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }
}
