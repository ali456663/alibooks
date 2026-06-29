package se.cloudshop.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import se.cloudshop.email.InvoiceReminderEmailService;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@Service
public class AutomaticInvoiceReminderService {

  private static final String AUTO_METHOD_PREFIX = "AUTO_EMAIL_";
  private static final String SENT_STATUS = "SENT";
  private static final String SKIPPED_STATUS = "SKIPPED";

  private final OrderRepository orderRepository;
  private final InvoiceReminderEmailService invoiceReminderEmailService;
  private final SettingsService settingsService;

  public AutomaticInvoiceReminderService(
      OrderRepository orderRepository,
      InvoiceReminderEmailService invoiceReminderEmailService,
      SettingsService settingsService
  ) {
    this.orderRepository = orderRepository;
    this.invoiceReminderEmailService = invoiceReminderEmailService;
    this.settingsService = settingsService;
  }

  @Scheduled(cron = "${app.invoice-reminders.cron:0 0 9 * * *}", zone = "${app.time-zone:Europe/Stockholm}")
  public void sendDailyAutomaticReminders() {
    sendAutomaticReminders(LocalDate.now());
  }

  public AutomaticReminderResult sendAutomaticReminders(LocalDate today) {
    AppSettings settings = settingsService.getSettings();
    int reminderDaysBeforeDue = settings.getInvoiceReminderDaysBeforeDue() <= 0
        ? 5
        : settings.getInvoiceReminderDaysBeforeDue();

    if (!settings.isAutomaticInvoiceRemindersEnabled()) {
      return new AutomaticReminderResult(Instant.now(), false, reminderDaysBeforeDue, 0, 0, 0);
    }

    String autoMethod = AUTO_METHOD_PREFIX + reminderDaysBeforeDue + "_DAYS";
    int overdueDaysAfterDue = settings.getOverdueInvoiceReminderDaysAfterDue() <= 0
        ? 3
        : settings.getOverdueInvoiceReminderDaysAfterDue();
    String overdueAutoMethod = "AUTO_OVERDUE_EMAIL_" + overdueDaysAfterDue + "_DAYS";
    int checked = 0;
    int sent = 0;
    int skipped = 0;

    for (Order invoice : orderRepository.findAll()) {
      if (shouldSendAutomaticReminder(invoice, today, reminderDaysBeforeDue, autoMethod)) {
        checked++;
        try {
          invoiceReminderEmailService.sendReminder(invoice);
          invoice.addReminder(autoMethod, SENT_STATUS, invoice.getCustomer().getEmail());
          sent++;
        } catch (RuntimeException exception) {
          invoice.addReminderHistory(autoMethod, SKIPPED_STATUS, reminderEmail(invoice));
          skipped++;
        }

        orderRepository.save(invoice);
      }

      if (settings.isOverdueInvoiceRemindersEnabled()
          && shouldSendOverdueReminder(invoice, today, overdueDaysAfterDue, overdueAutoMethod)) {
        checked++;
        try {
          invoiceReminderEmailService.sendOverdueReminder(invoice);
          invoice.addReminder(overdueAutoMethod, SENT_STATUS, invoice.getCustomer().getEmail());
          sent++;
        } catch (RuntimeException exception) {
          invoice.addReminderHistory(overdueAutoMethod, SKIPPED_STATUS, reminderEmail(invoice));
          skipped++;
        }

        orderRepository.save(invoice);
      }
    }

    return new AutomaticReminderResult(Instant.now(), true, reminderDaysBeforeDue, checked, sent, skipped);
  }

  private boolean shouldSendAutomaticReminder(Order invoice, LocalDate today, int reminderDaysBeforeDue, String autoMethod) {
    if (!invoice.hasRemainingAmount() || invoice.getDueDate() == null || invoice.isCreditInvoice()) {
      return false;
    }

    if (invoice.hasReminder(autoMethod, SENT_STATUS)) {
      return false;
    }

    long daysUntilDue = ChronoUnit.DAYS.between(today, invoice.getDueDate());
    return daysUntilDue == reminderDaysBeforeDue;
  }

  private boolean shouldSendOverdueReminder(Order invoice, LocalDate today, int overdueDaysAfterDue, String autoMethod) {
    if (!invoice.hasRemainingAmount() || invoice.getDueDate() == null || invoice.isCreditInvoice()) {
      return false;
    }

    if (invoice.hasReminder(autoMethod, SENT_STATUS)) {
      return false;
    }

    long daysOverdue = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
    return daysOverdue == overdueDaysAfterDue;
  }

  private String reminderEmail(Order invoice) {
    if (invoice.getCustomer() == null) {
      return null;
    }

    return invoice.getCustomer().getEmail();
  }
}
