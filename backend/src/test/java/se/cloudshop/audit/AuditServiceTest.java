package se.cloudshop.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.cloudshop.auth.JwtService;

class AuditServiceTest {

  private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
  private final JwtService jwtService = new JwtService("test_secret");
  private final AuditService auditService = new AuditService(auditEventRepository, jwtService);

  @Test
  void createsAuditIntegrityChainForEvents() {
    AuditEvent firstEvent = new AuditEvent(
        "invoice",
        "invoice",
        "F-2026-0001",
        "created",
        "F-2026-0001",
        "Invoice created",
        1000,
        "authenticated-user"
    );
    AuditEvent secondEvent = new AuditEvent(
        "payment",
        "invoice",
        "F-2026-0001",
        "paid",
        "BANK-1",
        "Payment registered",
        1000,
        "authenticated-user"
    );
    when(auditEventRepository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(firstEvent, secondEvent));

    AuditIntegrityReport report = auditService.createIntegrityReport();

    assertThat(report.eventCount()).isEqualTo(2);
    assertThat(report.firstChainHash()).hasSize(64).matches("[0-9A-F]+");
    assertThat(report.finalChainHash()).hasSize(64).matches("[0-9A-F]+");
    assertThat(report.auditFingerprint()).hasSize(64).matches("[0-9A-F]+");
    assertThat(report.lines()).hasSize(2);
    assertThat(report.lines().get(0).previousChainHash()).isEqualTo("START");
    assertThat(report.lines().get(1).previousChainHash()).isEqualTo(report.lines().get(0).chainHash());
  }

  @Test
  void recordsActorEmailFromJwtSubject() {
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    auditService.record("invoice", "invoice", 1L, "created", "F-1", "Invoice created", 100, authorizationHeader);

    org.mockito.Mockito.verify(auditEventRepository).save(org.mockito.ArgumentMatchers.argThat(event ->
        "ali@example.com".equals(event.getActorEmail())
    ));
  }

  @Test
  void changingAuditEventContentChangesFingerprint() {
    Instant createdAt = Instant.parse("2026-07-01T10:00:00Z");
    AuditEvent originalEvent = eventWithStableIdentity(1L, createdAt, "Invoice created");
    AuditEvent changedEvent = eventWithStableIdentity(1L, createdAt, "Invoice changed after export");

    when(auditEventRepository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(originalEvent));
    String originalFingerprint = auditService.createIntegrityReport().auditFingerprint();

    when(auditEventRepository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(changedEvent));
    String changedFingerprint = auditService.createIntegrityReport().auditFingerprint();

    assertThat(changedFingerprint).isNotEqualTo(originalFingerprint);
  }

  private AuditEvent eventWithStableIdentity(Long id, Instant createdAt, String message) {
    AuditEvent event = new AuditEvent(
        "invoice",
        "invoice",
        "F-2026-0001",
        "created",
        "F-2026-0001",
        message,
        1000,
        "authenticated-user"
    );
    setField(event, "id", id);
    setField(event, "createdAt", createdAt);
    return event;
  }

  private void setField(AuditEvent event, String fieldName, Object value) {
    try {
      Field field = AuditEvent.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(event, value);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not set audit event test field.", exception);
    }
  }
}
