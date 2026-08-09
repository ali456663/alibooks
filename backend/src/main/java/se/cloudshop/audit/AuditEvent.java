package se.cloudshop.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String eventType;
  private String entityType;
  private String entityId;
  @Column(name = "event_action")
  private String action;
  private String reference;
  private String message;
  private int amount;
  private String actorEmail;
  private Instant createdAt;

  public AuditEvent() {
  }

  public AuditEvent(String eventType, String entityType, String entityId, String action, String reference, String message, int amount, String actorEmail) {
    this.eventType = eventType;
    this.entityType = entityType;
    this.entityId = entityId;
    this.action = action;
    this.reference = reference;
    this.message = message;
    this.amount = amount;
    this.actorEmail = actorEmail;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getEventType() {
    return eventType;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public String getAction() {
    return action;
  }

  public String getReference() {
    return reference;
  }

  public String getMessage() {
    return message;
  }

  public int getAmount() {
    return amount;
  }

  public String getActorEmail() {
    return actorEmail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
