package se.cloudshop.system;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final JdbcTemplate jdbcTemplate;
  private final String mailHost;
  private final String mailUsername;
  private final String stripeSecretKey;
  private final String stripeWebhookSecret;
  private final String frontendUrl;
  private final String jwtSecret;
  private final String reminderCron;
  private final String timeZone;

  public HealthController(
      JdbcTemplate jdbcTemplate,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername,
      @Value("${stripe.secret-key:}") String stripeSecretKey,
      @Value("${stripe.webhook-secret:}") String stripeWebhookSecret,
      @Value("${app.frontend-url:}") String frontendUrl,
      @Value("${jwt.secret:}") String jwtSecret,
      @Value("${app.invoice-reminders.cron:}") String reminderCron,
      @Value("${app.time-zone:}") String timeZone
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.mailHost = mailHost;
    this.mailUsername = mailUsername;
    this.stripeSecretKey = stripeSecretKey;
    this.stripeWebhookSecret = stripeWebhookSecret;
    this.frontendUrl = frontendUrl;
    this.jwtSecret = jwtSecret;
    this.reminderCron = reminderCron;
    this.timeZone = timeZone;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of(
        "status", "ok",
        "service", "cloudshop-backend"
    );
  }

  @GetMapping("/system/status")
  public Map<String, Object> systemStatus() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("service", "cloudshop-backend");
    status.put("backend", Map.of("ok", true));
    status.put("database", Map.of("ok", databaseIsAvailable()));
    status.put("email", Map.of(
        "configured", hasText(mailHost) && hasText(mailUsername),
        "hostConfigured", hasText(mailHost),
        "usernameConfigured", hasText(mailUsername)
    ));
    status.put("stripe", Map.of(
        "configured", hasText(stripeSecretKey),
        "webhookConfigured", hasText(stripeWebhookSecret)
    ));
    status.put("frontend", Map.of(
        "configured", hasText(frontendUrl),
        "url", hasText(frontendUrl) ? frontendUrl : ""
    ));
    status.put("security", Map.of(
        "jwtConfigured", jwtIsConfigured(),
        "jwtStrong", jwtIsConfigured() && jwtSecret.length() >= 32
    ));
    status.put("automation", Map.of(
        "invoiceRemindersConfigured", hasText(reminderCron),
        "invoiceRemindersCron", hasText(reminderCron) ? reminderCron : "",
        "timeZone", hasText(timeZone) ? timeZone : ""
    ));
    return status;
  }

  private boolean databaseIsAvailable() {
    try {
      Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
      return result != null && result == 1;
    } catch (Exception exception) {
      return false;
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private boolean jwtIsConfigured() {
    return hasText(jwtSecret) && !"change_me_in_production".equals(jwtSecret);
  }
}
