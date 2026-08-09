package se.cloudshop.audit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.export.CsvEscaper;

@RestController
public class AuditController {

  private final AuthHeader authHeader;
  private final AuditService auditService;

  public AuditController(AuthHeader authHeader, AuditService auditService) {
    this.authHeader = authHeader;
    this.auditService = auditService;
  }

  @GetMapping("/audit-events")
  public List<AuditEvent> getAuditEvents(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return auditService.latestEvents();
  }

  @GetMapping("/audit-events/integrity")
  public AuditIntegrityReport getAuditIntegrityReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return auditService.createIntegrityReport();
  }

  @GetMapping("/audit-events/integrity/export")
  public ResponseEntity<byte[]> exportAuditIntegrityReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    AuditIntegrityReport report = auditService.createIntegrityReport();

    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks revisionsspar-integritet\n");
    csv.append("Genererad,").append(escape(report.generatedAt().toString())).append("\n");
    csv.append("Handelser,").append(report.eventCount()).append("\n");
    csv.append("Forsta kedjekod,").append(escape(report.firstChainHash())).append("\n");
    csv.append("Slutlig kedjekod,").append(escape(report.finalChainHash())).append("\n");
    csv.append("Auditstampel,").append(escape(report.auditFingerprint())).append("\n");
    csv.append("\n");
    csv.append("Nr,Audit-ID,Skapad,Typ,Objekt,Objekt-ID,Handelse,Referens,Belopp,Aktor,Radkod,Foregaende kedjekod,Kedjekod\n");
    for (AuditIntegrityLine line : report.lines()) {
      csv.append(line.sequenceNumber()).append(",");
      csv.append(line.eventId() == null ? "" : line.eventId()).append(",");
      csv.append(escape(line.createdAt() == null ? "" : line.createdAt().toString())).append(",");
      csv.append(escape(line.eventType())).append(",");
      csv.append(escape(line.entityType())).append(",");
      csv.append(escape(line.entityId())).append(",");
      csv.append(escape(line.action())).append(",");
      csv.append(escape(line.reference())).append(",");
      csv.append(line.amount()).append(",");
      csv.append(escape(line.actorEmail())).append(",");
      csv.append(escape(line.rowHash())).append(",");
      csv.append(escape(line.previousChainHash())).append(",");
      csv.append(escape(line.chainHash())).append("\n");
    }

    auditService.record(
        "export",
        "audit_trail",
        report.auditFingerprint(),
        "audit_integrity_exported",
        report.finalChainHash(),
        "Audit integrity report exported with fingerprint " + report.auditFingerprint(),
        report.eventCount(),
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=revisionsspar-integritet.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/audit-events/export")
  public ResponseEntity<byte[]> exportAuditEvents(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<AuditEvent> events = auditService.allEventsNewestFirst();
    AuditIntegrityReport integrityReport = auditService.createIntegrityReport();

    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks revisionsspar\n");
    csv.append("Exporterad,").append(escape(java.time.Instant.now().toString())).append("\n");
    csv.append("Handelser i export,").append(events.size()).append("\n");
    csv.append("Backend auditstampel,").append(escape(integrityReport.auditFingerprint())).append("\n");
    csv.append("Backend slutlig kedjekod,").append(escape(integrityReport.finalChainHash())).append("\n");
    csv.append("\n");
    csv.append("Audit-ID,Skapad,Typ,Objekt,Objekt-ID,Handelse,Referens,Meddelande,Belopp,Aktor\n");

    for (AuditEvent event : events) {
      csv.append(event.getId() == null ? "" : event.getId()).append(",");
      csv.append(escape(event.getCreatedAt() == null ? "" : event.getCreatedAt().toString())).append(",");
      csv.append(escape(event.getEventType())).append(",");
      csv.append(escape(event.getEntityType())).append(",");
      csv.append(escape(event.getEntityId())).append(",");
      csv.append(escape(event.getAction())).append(",");
      csv.append(escape(event.getReference())).append(",");
      csv.append(escape(event.getMessage())).append(",");
      csv.append(event.getAmount()).append(",");
      csv.append(escape(event.getActorEmail())).append("\n");
    }

    auditService.record(
        "export",
        "audit_trail",
        integrityReport.auditFingerprint(),
        "audit_events_exported",
        integrityReport.finalChainHash(),
        "Audit events exported with fingerprint " + integrityReport.auditFingerprint(),
        events.size(),
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=revisionsspar.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String escape(String value) {
    return CsvEscaper.escape(value);
  }
}
