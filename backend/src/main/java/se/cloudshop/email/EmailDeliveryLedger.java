package se.cloudshop.email;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryLedger {

  private static final String REMINDER = "INVOICE_REMINDER";
  private static final String OVERDUE_REMINDER = "OVERDUE_REMINDER";
  private static final List<String> UNRESOLVED = List.of("PENDING", "UNCERTAIN");

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
  public Long startReminderAttempt(Long invoiceId, boolean overdue, String recipientEmail, String subject, String body) {
    return repository.saveAndFlush(new EmailDeliveryAttempt(
        reminderType(overdue), invoiceId, recipientEmail, subject, body, null, null
    )).getId();
  }

  public boolean hasReminderAttempt(Long invoiceId, boolean overdue) {
    return repository.existsByDeliveryTypeAndInvoiceId(reminderType(overdue), invoiceId);
  }

  public boolean hasUnresolvedReminderAttempt(Long invoiceId, boolean overdue) {
    return repository.existsByDeliveryTypeAndInvoiceIdAndStatusIn(reminderType(overdue), invoiceId, UNRESOLVED);
  }

  public boolean hasInvoiceAttempt(Long invoiceId) {
    return repository.existsByDeliveryTypeAndInvoiceId("INVOICE_EMAIL", invoiceId);
  }

  public boolean hasUnresolvedInvoiceAttempt(Long invoiceId) {
    return repository.existsByDeliveryTypeAndInvoiceIdAndStatusIn("INVOICE_EMAIL", invoiceId, UNRESOLVED);
  }

  public boolean hasOnlyConfirmedNotDeliveredInvoiceAttempts(Long invoiceId) {
    return repository.existsByDeliveryTypeAndInvoiceIdAndStatus("INVOICE_EMAIL", invoiceId, "NOT_DELIVERED")
        && !repository.existsByDeliveryTypeAndInvoiceIdAndStatusNot("INVOICE_EMAIL", invoiceId, "NOT_DELIVERED");
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

  public long countPending() {
    return repository.countByStatus("PENDING");
  }

  public long countUnresolved() {
    return repository.countByStatusIn(UNRESOLVED);
  }

  private String reminderType(boolean overdue) {
    return overdue ? OVERDUE_REMINDER : REMINDER;
  }
}
