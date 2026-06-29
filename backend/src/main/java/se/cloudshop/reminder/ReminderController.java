package se.cloudshop.reminder;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class ReminderController {

  private final AuthHeader authHeader;
  private final ReminderService reminderService;
  private final AutomaticInvoiceReminderService automaticInvoiceReminderService;

  public ReminderController(
      AuthHeader authHeader,
      ReminderService reminderService,
      AutomaticInvoiceReminderService automaticInvoiceReminderService
  ) {
    this.authHeader = authHeader;
    this.reminderService = reminderService;
    this.automaticInvoiceReminderService = automaticInvoiceReminderService;
  }

  @GetMapping("/reminders")
  public List<Reminder> getReminders(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return reminderService.createReminders();
  }

  @GetMapping("/advisor-summary")
  public AdvisorSummary getAdvisorSummary(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return reminderService.createAdvisorSummary();
  }

  @PostMapping("/reminders/automatic/run")
  public AutomaticReminderResult runAutomaticReminders(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return automaticInvoiceReminderService.sendAutomaticReminders(java.time.LocalDate.now());
  }
}
