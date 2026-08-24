package se.cloudshop.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class AuditControllerTest {

  private final JwtService jwtService = new JwtService("test_secret");
  private final AuthHeader authHeader = new AuthHeader(jwtService);
  private final AuditService auditService = mock(AuditService.class);
  private final AuditController auditController = new AuditController(authHeader, auditService);

  @Test
  void auditEventsRequireJwt() {
    assertThatThrownBy(() -> auditController.getAuditEvents(null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("JWT token is required");
  }

  @Test
  void auditIntegrityExportIncludesFingerprintAndRecordsAuditEvent() {
    AuditIntegrityReport report = new AuditIntegrityReport(
        Instant.parse("2026-07-31T10:00:00Z"),
        1,
        "A".repeat(64),
        "B".repeat(64),
        "C".repeat(64),
        List.of(new AuditIntegrityLine(
            1,
            10L,
            Instant.parse("2026-07-01T10:00:00Z"),
            "invoice",
            "invoice",
            "F-2026-0001",
            "created",
            "F-2026-0001",
            1250,
            "ali@example.com",
            "D".repeat(64),
            "START",
            "B".repeat(64)
        ))
    );
    when(auditService.createIntegrityReport()).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = auditController.exportAuditIntegrityReport(authorizationHeader);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("AliBooks revisionsspar-integritet")
        .contains("Auditstampel")
        .contains("C".repeat(64))
        .contains("Slutlig kedjekod")
        .contains("B".repeat(64));
    verify(auditService).record(
        "export",
        "audit_trail",
        "C".repeat(64),
        "audit_integrity_exported",
        "B".repeat(64),
        "Audit integrity report exported with fingerprint " + "C".repeat(64),
        1,
        authorizationHeader
    );
  }

  @Test
  void auditEventsExportIncludesIntegrityControlValuesAndRecordsAuditEvent() {
    AuditEvent event = new AuditEvent(
        "invoice",
        "invoice",
        "F-2026-0001",
        "created",
        "F-2026-0001",
        "Invoice created",
        1250,
        "ali@example.com"
    );
    AuditIntegrityReport report = new AuditIntegrityReport(
        Instant.parse("2026-07-31T10:00:00Z"),
        1,
        "A".repeat(64),
        "B".repeat(64),
        "C".repeat(64),
        List.of()
    );
    when(auditService.allEventsNewestFirst()).thenReturn(List.of(event));
    when(auditService.createIntegrityReport()).thenReturn(report);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = auditController.exportAuditEvents(authorizationHeader);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("AliBooks revisionsspar")
        .contains("Backend auditstampel")
        .contains("C".repeat(64))
        .contains("Backend slutlig kedjekod")
        .contains("B".repeat(64))
        .contains("Invoice created");
    verify(auditService).record(
        "export",
        "audit_trail",
        "C".repeat(64),
        "audit_events_exported",
        "B".repeat(64),
        "Audit events exported with fingerprint " + "C".repeat(64),
        1,
        authorizationHeader
    );
  }
}
