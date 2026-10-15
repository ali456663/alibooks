package se.cloudshop.email;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;

@Service
public class EmailDeliveryReconciliationService {

  private static final Set<String> TYPES = Set.of("INVOICE_EMAIL", "INVOICE_REMINDER", "OVERDUE_REMINDER");
  private static final List<String> UNRESOLVED = List.of("PENDING", "UNCERTAIN");

  private final EmailDeliveryAttemptRepository attempts;
  private final OrderRepository orders;
  private final AuditService audit;

  public EmailDeliveryReconciliationService(EmailDeliveryAttemptRepository attempts,
                                            OrderRepository orders, AuditService audit) {
    this.attempts = attempts;
    this.orders = orders;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<EmailDeliverySummary> unresolved() {
    return attempts.findUnresolvedSummaries(UNRESOLVED, PageRequest.of(0, 100));
  }

  @Transactional
  public EmailDeliverySummary resolve(Long attemptId, ResolveEmailDeliveryRequest request, String authorization) {
    if (request == null || request.outcome() == null
        || !Set.of("DELIVERED", "NOT_DELIVERED").contains(request.outcome())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose DELIVERED or NOT_DELIVERED.");
    }
    String evidence = request.evidence() == null ? "" : request.evidence().trim().replaceAll("\\s+", " ");
    if (evidence.length() < 20 || evidence.length() > 500) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Record 20 to 500 characters of external delivery evidence before reconciliation.");
    }

    EmailDeliveryAttempt initial = attempts.findById(attemptId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery attempt not found."));
    if (initial.getInvoiceId() == null || !TYPES.contains(initial.getDeliveryType())) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Delivery attempt has no supported invoice link.");
    }

    orders.lockById(initial.getInvoiceId());
    Order invoice = orders.findById(initial.getInvoiceId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
            "Invoice is missing; delivery must be investigated outside this workflow."));
    EmailDeliveryAttempt attempt = attempts.findByIdForReconciliation(attemptId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery attempt not found."));
    if (!initial.getInvoiceId().equals(attempt.getInvoiceId())
        || !initial.getDeliveryType().equals(attempt.getDeliveryType())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery attempt changed during reconciliation.");
    }
    if (!UNRESOLVED.contains(attempt.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery attempt is already resolved.");
    }
    Long latestId = attempts.findTopByDeliveryTypeAndInvoiceIdOrderByIdDesc(
        attempt.getDeliveryType(), attempt.getInvoiceId()).map(EmailDeliveryAttempt::getId).orElse(null);
    if (!attemptId.equals(latestId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only the latest delivery attempt can be reconciled.");
    }

    boolean delivered = "DELIVERED".equals(request.outcome());
    if (delivered) {
      if ("INVOICE_EMAIL".equals(attempt.getDeliveryType()) && "DRAFT".equals(invoice.getStatus())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Invoice is still a draft; investigate issuance before confirming delivery.");
      }
      attempt.confirmDelivered();
      String method = switch (attempt.getDeliveryType()) {
        case "INVOICE_EMAIL" -> "INVOICE_EMAIL";
        case "INVOICE_REMINDER" -> "EMAIL";
        default -> "OVERDUE_EMAIL";
      };
      if (!invoice.hasReminder(method, "SENT")) {
        invoice.addReminderHistory(method, "SENT", attempt.getRecipientEmail());
        orders.saveAndFlush(invoice);
      }
    } else {
      attempt.confirmNotDelivered();
    }
    attempts.saveAndFlush(attempt);
    audit.record("email_delivery", "invoice", invoice.getId(),
        delivered ? "delivery_reconciled_sent" : "delivery_reconciled_not_delivered",
        String.valueOf(attemptId), evidence, 0, authorization);
    return EmailDeliverySummary.from(attempt);
  }
}
