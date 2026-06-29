package se.cloudshop.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stripe_webhook_events")
public class StripeWebhookEvent {

  @Id
  private String eventId;

  private String eventType;
  private Instant processedAt;

  public StripeWebhookEvent() {
  }

  public StripeWebhookEvent(String eventId, String eventType) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.processedAt = Instant.now();
  }

  public String getEventId() {
    return eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
