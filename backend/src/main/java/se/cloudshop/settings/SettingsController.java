package se.cloudshop.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.email.InvoiceReminderEmailService;

@RestController
public class SettingsController {

  private final AuthHeader authHeader;
  private final SettingsService settingsService;
  private final InvoiceReminderEmailService invoiceReminderEmailService;

  public SettingsController(
      AuthHeader authHeader,
      SettingsService settingsService,
      InvoiceReminderEmailService invoiceReminderEmailService
  ) {
    this.authHeader = authHeader;
    this.settingsService = settingsService;
    this.invoiceReminderEmailService = invoiceReminderEmailService;
  }

  @GetMapping("/settings")
  public AppSettings getSettings(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return settingsService.getSettings();
  }

  @PutMapping("/settings")
  public AppSettings updateSettings(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody AppSettings settings
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return settingsService.updateSettings(settings);
  }

  @PostMapping("/settings/test-email")
  public AppSettings sendTestEmail(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    invoiceReminderEmailService.sendSettingsTestEmail();
    return settingsService.getSettings();
  }
}
