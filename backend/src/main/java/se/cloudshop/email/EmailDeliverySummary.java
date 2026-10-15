package se.cloudshop.email;

import java.time.Instant;

public record EmailDeliverySummary(
    Long id,
    String deliveryType,
    Long invoiceId,
    String recipientEmail,
    String subject,
    String status,
    Instant createdAt
) {
  static EmailDeliverySummary from(EmailDeliveryAttempt attempt) {
    return new EmailDeliverySummary(attempt.getId(), attempt.getDeliveryType(), attempt.getInvoiceId(),
        attempt.getRecipientEmail(), attempt.getSubject(), attempt.getStatus(), attempt.getCreatedAt());
  }
}
