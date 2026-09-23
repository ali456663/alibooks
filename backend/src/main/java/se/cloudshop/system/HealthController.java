package se.cloudshop.system;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.config.MoneyMigrationVerifier;
import se.cloudshop.email.EmailDeliveryLedger;

@RestController
public class HealthController {

  private final JdbcTemplate jdbcTemplate;
  private final String mailHost;
  private final String mailUsername;
  private final String stripeSecretKey;
  private final String stripeWebhookSecret;
  private final String geminiApiKey;
  private final String hfToken;
  private final String openAiCompatibleApiKey;
  private final String openAiCompatibleModel;
  private final String openAiCompatibleBaseUrl;
  private final String openAiCompatibleProviderName;
  private final String frontendUrl;
  private final String jwtSecret;
  private final int jwtExpirationMinutes;
  private final String reminderCron;
  private final String timeZone;
  private final String corsAllowedOrigins;
  private final boolean corsLocalDevEnabled;
  private final int authMaxFailedLoginAttempts;
  private final int authLoginLockMinutes;
  private final boolean testDataResetEnabled;
  private final boolean bankReconciliationResetEnabled;
  private final MoneyMigrationVerifier moneyMigrationVerifier;
  private final AuthHeader authHeader;
  private final EmailDeliveryLedger emailDeliveryLedger;

  @Autowired
  public HealthController(
      JdbcTemplate jdbcTemplate,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername,
      @Value("${stripe.secret-key:}") String stripeSecretKey,
      @Value("${stripe.webhook-secret:}") String stripeWebhookSecret,
      @Value("${ai.gemini.api-key:}") String geminiApiKey,
      @Value("${ai.huggingface.token:}") String hfToken,
      @Value("${ai.openai-compatible.api-key:}") String openAiCompatibleApiKey,
      @Value("${ai.openai-compatible.model:}") String openAiCompatibleModel,
      @Value("${ai.openai-compatible.base-url:}") String openAiCompatibleBaseUrl,
      @Value("${ai.openai-compatible.provider-name:openai-compatible}") String openAiCompatibleProviderName,
      @Value("${app.frontend-url:}") String frontendUrl,
      @Value("${jwt.secret:}") String jwtSecret,
      @Value("${jwt.expiration-minutes:60}") int jwtExpirationMinutes,
      @Value("${app.invoice-reminders.cron:}") String reminderCron,
      @Value("${app.time-zone:}") String timeZone,
      @Value("${app.cors.allowed-origins:}") String corsAllowedOrigins,
      @Value("${app.cors.local-dev-enabled:true}") boolean corsLocalDevEnabled,
      @Value("${app.auth.max-failed-login-attempts:5}") int authMaxFailedLoginAttempts,
      @Value("${app.auth.login-lock-minutes:15}") int authLoginLockMinutes,
      @Value("${app.test-data-reset.enabled:false}") boolean testDataResetEnabled,
      @Value("${app.bank-reconciliation-reset.enabled:false}") boolean bankReconciliationResetEnabled,
      MoneyMigrationVerifier moneyMigrationVerifier,
      AuthHeader authHeader,
      EmailDeliveryLedger emailDeliveryLedger
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.mailHost = mailHost;
    this.mailUsername = mailUsername;
    this.stripeSecretKey = stripeSecretKey;
    this.stripeWebhookSecret = stripeWebhookSecret;
    this.geminiApiKey = geminiApiKey;
    this.hfToken = hfToken;
    this.openAiCompatibleApiKey = openAiCompatibleApiKey;
    this.openAiCompatibleModel = openAiCompatibleModel;
    this.openAiCompatibleBaseUrl = openAiCompatibleBaseUrl;
    this.openAiCompatibleProviderName = openAiCompatibleProviderName;
    this.frontendUrl = frontendUrl;
    this.jwtSecret = jwtSecret;
    this.jwtExpirationMinutes = jwtExpirationMinutes;
    this.reminderCron = reminderCron;
    this.timeZone = timeZone;
    this.corsAllowedOrigins = corsAllowedOrigins;
    this.corsLocalDevEnabled = corsLocalDevEnabled;
    this.authMaxFailedLoginAttempts = authMaxFailedLoginAttempts;
    this.authLoginLockMinutes = authLoginLockMinutes;
    this.testDataResetEnabled = testDataResetEnabled;
    this.bankReconciliationResetEnabled = bankReconciliationResetEnabled;
    this.moneyMigrationVerifier = moneyMigrationVerifier;
    this.authHeader = authHeader;
    this.emailDeliveryLedger = emailDeliveryLedger;
  }

  // Keeps focused controller tests independent from the persistence-backed delivery ledger.
  public HealthController(
      JdbcTemplate jdbcTemplate,
      String mailHost,
      String mailUsername,
      String stripeSecretKey,
      String stripeWebhookSecret,
      String geminiApiKey,
      String hfToken,
      String openAiCompatibleApiKey,
      String openAiCompatibleModel,
      String openAiCompatibleBaseUrl,
      String openAiCompatibleProviderName,
      String frontendUrl,
      String jwtSecret,
      int jwtExpirationMinutes,
      String reminderCron,
      String timeZone,
      String corsAllowedOrigins,
      boolean corsLocalDevEnabled,
      int authMaxFailedLoginAttempts,
      int authLoginLockMinutes,
      boolean testDataResetEnabled,
      boolean bankReconciliationResetEnabled,
      MoneyMigrationVerifier moneyMigrationVerifier,
      AuthHeader authHeader
  ) {
    this(
        jdbcTemplate, mailHost, mailUsername, stripeSecretKey, stripeWebhookSecret, geminiApiKey, hfToken,
        openAiCompatibleApiKey, openAiCompatibleModel, openAiCompatibleBaseUrl, openAiCompatibleProviderName,
        frontendUrl, jwtSecret, jwtExpirationMinutes, reminderCron, timeZone, corsAllowedOrigins,
        corsLocalDevEnabled, authMaxFailedLoginAttempts, authLoginLockMinutes, testDataResetEnabled,
        bankReconciliationResetEnabled, moneyMigrationVerifier, authHeader, null
    );
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of(
        "status", "ok",
        "service", "cloudshop-backend"
    );
  }

  @GetMapping("/system/status")
  public Map<String, Object> systemStatus(
      @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false)
      String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("service", "cloudshop-backend");
    status.put("backend", Map.of("ok", true));
    status.put("database", Map.of("ok", databaseIsAvailable()));
    status.put("email", Map.of(
        "configured", hasText(mailHost) && hasText(mailUsername),
        "hostConfigured", hasText(mailHost),
        "usernameConfigured", hasText(mailUsername),
        "deliveryLedgerEnabled", emailDeliveryLedger != null,
        "uncertainDeliveryCount", emailDeliveryLedger == null ? 0L : emailDeliveryLedger.countUncertain()
    ));
    status.put("stripe", Map.of(
        "configured", hasText(stripeSecretKey),
        "webhookConfigured", hasText(stripeWebhookSecret)
    ));
    status.put("ai", Map.of(
        "configured", openAiCompatibleIsConfigured() || hasText(geminiApiKey) || hasText(hfToken),
        "provider", aiProvider(),
        "safeMode", true,
        "contextPolicy", "anonymized-minimized",
        "openAiCompatibleConfigured", openAiCompatibleIsConfigured(),
        "openAiCompatibleBaseUrlConfigured", hasText(openAiCompatibleBaseUrl),
        "openAiCompatibleModelConfigured", hasText(openAiCompatibleModel),
        "geminiConfigured", hasText(geminiApiKey),
        "huggingFaceConfigured", hasText(hfToken)
    ));
    status.put("frontend", Map.of(
        "configured", hasText(frontendUrl),
        "url", hasText(frontendUrl) ? frontendUrl : ""
    ));
    List<String> effectiveAllowedOrigins = allowedOriginPatterns();
    status.put("cors", Map.of(
        "configured", hasText(corsAllowedOrigins),
        "allowedOrigins", hasText(corsAllowedOrigins) ? corsAllowedOrigins : "",
        "effectiveAllowedOrigins", effectiveAllowedOrigins,
        "localDevEnabled", corsLocalDevEnabled,
        "localDevelopmentReady", corsLocalDevEnabled && effectiveAllowedOrigins.stream().anyMatch(origin -> origin.contains("localhost")),
        "productionReady", hasText(corsAllowedOrigins) && !corsLocalDevEnabled
    ));
    status.put("security", Map.of(
        "jwtConfigured", jwtIsConfigured(),
        "jwtStrong", jwtIsConfigured() && jwtSecret.length() >= 32,
        "jwtExpirationMinutes", jwtExpirationMinutes
    ));
    status.put("moneyModel", Map.of(
        "currency", "SEK",
        "unit", "whole-krona",
        "supportsMinorUnits", false,
        "productionBookkeepingReady", false,
        "migration", moneyMigrationVerifier.status()
    ));
    status.put("auth", Map.of(
        "loginAttemptLockEnabled", authMaxFailedLoginAttempts > 0 && authLoginLockMinutes > 0,
        "maxFailedLoginAttempts", authMaxFailedLoginAttempts,
        "loginLockMinutes", authLoginLockMinutes
    ));
    status.put("automation", Map.of(
        "invoiceRemindersConfigured", hasText(reminderCron),
        "invoiceRemindersCron", hasText(reminderCron) ? reminderCron : "",
        "timeZone", hasText(timeZone) ? timeZone : ""
    ));
    status.put("maintenance", Map.of(
        "testDataResetEnabled", testDataResetEnabled,
        "bankReconciliationResetEnabled", bankReconciliationResetEnabled,
        "safeForProduction", !testDataResetEnabled && !bankReconciliationResetEnabled
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

  private List<String> allowedOriginPatterns() {
    List<String> patterns = new ArrayList<>();
    Arrays.stream(corsAllowedOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isBlank())
        .forEach(patterns::add);

    if (corsLocalDevEnabled) {
      patterns.add("http://localhost:*");
      patterns.add("http://127.0.0.1:*");
    }

    if (patterns.isEmpty()) {
      patterns.add("http://localhost:*");
      patterns.add("http://127.0.0.1:*");
    }

    return patterns.stream().distinct().toList();
  }

  private boolean jwtIsConfigured() {
    return hasText(jwtSecret) && !"change_me_in_production".equals(jwtSecret);
  }

  private String aiProvider() {
    if (openAiCompatibleIsConfigured()) {
      return hasText(openAiCompatibleProviderName) ? openAiCompatibleProviderName : "openai-compatible";
    }

    if (hasText(geminiApiKey)) {
      return "gemini";
    }

    if (hasText(hfToken)) {
      return "huggingface";
    }

    return "local";
  }

  private boolean openAiCompatibleIsConfigured() {
    return hasText(openAiCompatibleApiKey) && hasText(openAiCompatibleBaseUrl) && hasText(openAiCompatibleModel);
  }
}
