package se.cloudshop.reminder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.cloudshop.email.InvoiceReminderEmailService;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;

@Service
public class AutomaticReminderDeliveryService {

  public enum DeliveryResult {
    NOT_ELIGIBLE,
    SENT,
    SKIPPED
  }

  private static final String SENT_STATUS = "SENT";
  private static final String SKIPPED_STATUS = "SKIPPED";

  private final OrderRepository orderRepository;
  private final InvoiceReminderEmailService invoiceReminderEmailService;

  public AutomaticReminderDeliveryService(
      OrderRepository orderRepository,
      InvoiceReminderEmailService invoiceReminderEmailService
  ) {
    this.orderRepository = orderRepository;
    this.invoiceReminderEmailService = invoiceReminderEmailService;
  }

  @Transactional
  public DeliveryResult deliver(
      Long invoiceId,
      LocalDate today,
      int reminderDaysBeforeDue,
      String automaticMethod,
      boolean overdue,
      int overdueDaysAfterDue,
      String overdueMethod
  ) {
    if (invoiceId == null) {
      return DeliveryResult.NOT_ELIGIBLE;
    }

    // A second scheduler instance waits here, then reloads the updated history.
    orderRepository.lockById(invoiceId);
    Order invoice = orderRepository.findById(invoiceId).orElse(null);
    if (invoice == null || !isEligible(invoice, today, reminderDaysBeforeDue, automaticMethod,
        overdue, overdueDaysAfterDue, overdueMethod)) {
      return DeliveryResult.NOT_ELIGIBLE;
    }

    String method = overdue ? overdueMethod : automaticMethod;
    try {
      if (overdue) {
        invoiceReminderEmailService.sendOverdueReminder(invoice);
      } else {
        invoiceReminderEmailService.sendReminder(invoice);
      }
      invoice.addReminder(method, SENT_STATUS, reminderEmail(invoice));
      orderRepository.saveAndFlush(invoice);
      return DeliveryResult.SENT;
    } catch (RuntimeException exception) {
      // Persist the failed attempt so the next daily run can retry it safely.
      invoice.addReminderHistory(method, SKIPPED_STATUS, reminderEmail(invoice));
      orderRepository.saveAndFlush(invoice);
      return DeliveryResult.SKIPPED;
    }
  }

  private boolean isEligible(
      Order invoice,
      LocalDate today,
      int reminderDaysBeforeDue,
      String automaticMethod,
      boolean overdue,
      int overdueDaysAfterDue,
      String overdueMethod
  ) {
    if (!invoice.hasRemainingAmount() || invoice.getDueDate() == null || invoice.isCreditInvoice()) {
      return false;
    }

    String method = overdue ? overdueMethod : automaticMethod;
    if (invoice.hasReminder(method, SENT_STATUS)) {
      return false;
    }

    if (overdue) {
      long daysOverdue = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
      return daysOverdue >= overdueDaysAfterDue;
    }

    long daysUntilDue = ChronoUnit.DAYS.between(today, invoice.getDueDate());
    // Normal run is exactly N days before due; the range also recovers a missed run.
    return daysUntilDue >= 0 && daysUntilDue <= reminderDaysBeforeDue;
  }

  private String reminderEmail(Order invoice) {
    return invoice.getCustomer() == null ? null : invoice.getCustomer().getEmail();
  }
}
