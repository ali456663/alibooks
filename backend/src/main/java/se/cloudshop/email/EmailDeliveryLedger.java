package se.cloudshop.email;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryLedger {

  private final EmailDeliveryAttemptRepository repository;

  public EmailDeliveryLedger(EmailDeliveryAttemptRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long startInvoiceAttempt(Long invoiceId, String recipientEmail, String subject, String body,
                                  String attachmentName, byte[] attachmentContent) {
    return repository.saveAndFlush(new EmailDeliveryAttempt(
        "INVOICE_EMAIL", invoiceId, recipientEmail, subject, body, attachmentName, attachmentContent
    )).getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markSent(Long attemptId) {
    repository.findById(attemptId).ifPresent(attempt -> {
      attempt.markSent();
      repository.saveAndFlush(attempt);
    });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markUncertain(Long attemptId, Throwable exception) {
    repository.findById(attemptId).ifPresent(attempt -> {
      attempt.markUncertain(exception);
      repository.saveAndFlush(attempt);
    });
  }

  public long countUncertain() {
    return repository.countByStatus("UNCERTAIN");
  }
}
