package se.cloudshop.reminder;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@Service
public class AutomaticInvoiceReminderService {

  private static final String AUTO_METHOD_PREFIX = "AUTO_EMAIL_";

  private final OrderRepository orderRepository;
  private final AutomaticReminderDeliveryService deliveryService;
  private final SettingsService settingsService;

  public AutomaticInvoiceReminderService(
      OrderRepository orderRepository,
      AutomaticReminderDeliveryService deliveryService,
      SettingsService settingsService
  ) {
    this.orderRepository = orderRepository;
    this.deliveryService = deliveryService;
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

    for (Order candidate : orderRepository.findAll()) {
      if (candidate.getId() == null) {
        continue;
      }

      AutomaticReminderDeliveryService.DeliveryResult dueResult = deliveryService.deliver(
          candidate.getId(), today, reminderDaysBeforeDue, autoMethod, false, overdueDaysAfterDue, overdueAutoMethod);
      if (dueResult != AutomaticReminderDeliveryService.DeliveryResult.NOT_ELIGIBLE) {
        checked++;
        if (dueResult == AutomaticReminderDeliveryService.DeliveryResult.SENT) sent++;
        if (dueResult == AutomaticReminderDeliveryService.DeliveryResult.SKIPPED) skipped++;
      }

      if (settings.isOverdueInvoiceRemindersEnabled()) {
        AutomaticReminderDeliveryService.DeliveryResult overdueResult = deliveryService.deliver(
            candidate.getId(), today, reminderDaysBeforeDue, autoMethod, true, overdueDaysAfterDue, overdueAutoMethod);
        if (overdueResult != AutomaticReminderDeliveryService.DeliveryResult.NOT_ELIGIBLE) {
          checked++;
          if (overdueResult == AutomaticReminderDeliveryService.DeliveryResult.SENT) sent++;
          if (overdueResult == AutomaticReminderDeliveryService.DeliveryResult.SKIPPED) skipped++;
        }
      }
    }

    return new AutomaticReminderResult(Instant.now(), true, reminderDaysBeforeDue, checked, sent, skipped);
  }

}
