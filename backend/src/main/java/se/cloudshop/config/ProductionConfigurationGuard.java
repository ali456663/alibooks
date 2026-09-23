package se.cloudshop.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app", name = "environment", havingValue = "production")
public class ProductionConfigurationGuard {

  public ProductionConfigurationGuard(
      @Value("${jwt.secret:}") String jwtSecret,
      @Value("${app.auth.registration-bootstrap-key:}") String registrationBootstrapKey,
      @Value("${jwt.expiration-minutes:60}") int jwtExpirationMinutes,
      @Value("${spring.jpa.hibernate.ddl-auto:update}") String ddlAuto,
      @Value("${app.schema-patch.enabled:true}") boolean schemaPatchEnabled,
      @Value("${app.cors.local-dev-enabled:true}") boolean corsLocalDevEnabled,
      @Value("${app.cors.allowed-origins:}") String corsAllowedOrigins,
      @Value("${app.test-data-reset.enabled:false}") boolean testDataResetEnabled,
      @Value("${app.bank-reconciliation-reset.enabled:false}") boolean bankReconciliationResetEnabled,
      @Value("${app.money-migration.verify-on-startup:true}") boolean moneyMigrationVerifyOnStartup,
      @Value("${spring.datasource.url:}") String datasourceUrl,
      @Value("${spring.datasource.username:}") String datasourceUsername,
      @Value("${spring.datasource.password:}") String datasourcePassword
  ) {
    List<String> violations = violations(
        jwtSecret, registrationBootstrapKey, jwtExpirationMinutes, ddlAuto, schemaPatchEnabled, corsLocalDevEnabled,
        corsAllowedOrigins, testDataResetEnabled, bankReconciliationResetEnabled, moneyMigrationVerifyOnStartup,
        datasourceUrl, datasourceUsername, datasourcePassword);
    if (!violations.isEmpty()) {
      throw new IllegalStateException("Unsafe production configuration: " + String.join(", ", violations));
    }
  }

  private static List<String> violations(
      String jwtSecret,
      String registrationBootstrapKey,
      int jwtExpirationMinutes,
      String ddlAuto,
      boolean schemaPatchEnabled,
      boolean corsLocalDevEnabled,
      String corsAllowedOrigins,
      boolean testDataResetEnabled,
      boolean bankReconciliationResetEnabled,
      boolean moneyMigrationVerifyOnStartup,
      String datasourceUrl,
      String datasourceUsername,
      String datasourcePassword
  ) {
    List<String> errors = new ArrayList<>();
    String secret = safe(jwtSecret);
    if (secret.length() < 32 || isPlaceholder(secret) || distinctCharacterCount(secret) < 12) {
      errors.add("JWT_SECRET must be a non-placeholder secret of at least 32 characters");
    }
    String bootstrapKey = safe(registrationBootstrapKey);
    if (bootstrapKey.length() < 32 || isPlaceholder(bootstrapKey) || distinctCharacterCount(bootstrapKey) < 12
        || bootstrapKey.equals(secret)) {
      errors.add("APP_AUTH_REGISTRATION_BOOTSTRAP_KEY must be a unique non-placeholder secret of at least 32 characters");
    }
    if (jwtExpirationMinutes < 1 || jwtExpirationMinutes > 1440) {
      errors.add("JWT_EXPIRATION_MINUTES must be between 1 and 1440");
    }
    if (!List.of("validate", "none").contains(safe(ddlAuto).toLowerCase(Locale.ROOT))) {
      errors.add("SPRING_JPA_HIBERNATE_DDL_AUTO must be validate or none");
    }
    if (schemaPatchEnabled) {
      errors.add("APP_SCHEMA_PATCH_ENABLED must be false");
    }
    if (corsLocalDevEnabled) {
      errors.add("APP_CORS_LOCAL_DEV_ENABLED must be false");
    }
    if (!hasExplicitPublicOrigins(corsAllowedOrigins)) {
      errors.add("APP_CORS_ALLOWED_ORIGINS must contain explicit non-local HTTP(S) origins");
    }
    if (testDataResetEnabled || bankReconciliationResetEnabled) {
      errors.add("test-data and bank-reconciliation reset features must be disabled");
    }
    if (!moneyMigrationVerifyOnStartup) {
      errors.add("APP_MONEY_MIGRATION_VERIFY_ON_STARTUP must be true");
    }
    errors.add("Accounting amounts are stored as whole SEK; production is blocked until exact ore-based accounting and historical reconciliation are implemented and verified");
    if (!hasRemotePostgresHost(datasourceUrl)) {
      errors.add("SPRING_DATASOURCE_URL must target a remote PostgreSQL host");
    }
    if (!hasText(datasourceUsername) || "cloudshop".equalsIgnoreCase(datasourceUsername.trim())) {
      errors.add("SPRING_DATASOURCE_USERNAME must not use the local default");
    }
    if (!hasText(datasourcePassword) || "cloudshop".equalsIgnoreCase(datasourcePassword.trim())
        || isPlaceholder(datasourcePassword)) {
      errors.add("SPRING_DATASOURCE_PASSWORD must be set to a production credential");
    }
    return errors;
  }

  private static boolean hasExplicitPublicOrigins(String configuredOrigins) {
    if (!hasText(configuredOrigins)) {
      return false;
    }
    String[] origins = configuredOrigins.split(",");
    return origins.length > 0 && Arrays.stream(origins).allMatch(ProductionConfigurationGuard::isExplicitPublicOrigin);
  }

  private static boolean isExplicitPublicOrigin(String value) {
    String origin = value.trim();
    if (origin.isEmpty() || origin.contains("*")) {
      return false;
    }
    try {
      URI uri = new URI(origin);
      String host = uri.getHost();
      if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          || !hasText(host) || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
          || (uri.getPath() != null && !uri.getPath().isEmpty())) {
        return false;
      }
      String normalizedHost = normalizeHost(host);
      return !normalizedHost.equals("localhost")
          && !normalizedHost.equals("127.0.0.1")
          && !normalizedHost.equals("0.0.0.0")
          && !normalizedHost.equals("::1")
          && !normalizedHost.endsWith(".localhost")
          && !normalizedHost.endsWith(".local")
          && !isTemplateHost(normalizedHost);
    } catch (URISyntaxException exception) {
      return false;
    }
  }

  private static boolean hasRemotePostgresHost(String value) {
    String url = safe(value);
    if (!url.startsWith("jdbc:postgresql://")) {
      return false;
    }
    try {
      String host = new URI(url.substring("jdbc:".length())).getHost();
      if (!hasText(host)) {
        return false;
      }
      String normalizedHost = normalizeHost(host);
      return !normalizedHost.equals("localhost")
          && !normalizedHost.equals("127.0.0.1")
          && !normalizedHost.equals("0.0.0.0")
          && !normalizedHost.equals("::1")
          && !isTemplateHost(normalizedHost);
    } catch (URISyntaxException exception) {
      return false;
    }
  }

  private static boolean isPlaceholder(String value) {
    String normalized = safe(value).toLowerCase(Locale.ROOT);
    return normalized.equals("change_me_in_production")
        || normalized.contains("replace_with")
        || normalized.contains("your_")
        || normalized.contains("your-");
  }

  private static int distinctCharacterCount(String value) {
    return (int) value.chars().distinct().count();
  }

  private static String normalizeHost(String host) {
    return host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
  }

  private static boolean isTemplateHost(String host) {
    return host.contains("your-")
        || host.contains("your_")
        || host.equals("db")
        || host.equals("postgres")
        || host.equals("database")
        || host.equals("host.docker.internal")
        || host.equals("example.com")
        || host.endsWith(".example.com")
        || host.endsWith(".example");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
