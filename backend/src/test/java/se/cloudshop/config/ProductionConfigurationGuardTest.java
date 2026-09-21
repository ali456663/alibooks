package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ProductionConfigurationGuardTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(GuardTestConfiguration.class);

  @Test
  void productionPropertyActivatesWholeKronaBlocker() {
    contextRunner.withPropertyValues(
        "app.environment=production",
        "jwt.secret=Q3n9wY7rL2pV8xK4mD6sF1aH5jB0cE9uT7zN3qW6",
        "app.auth.registration-bootstrap-key=test-owner-bootstrap-key-unique-2026",
        "jwt.expiration-minutes=60",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.schema-patch.enabled=false",
        "app.cors.local-dev-enabled=false",
        "app.cors.allowed-origins=https://books.alibooks.se",
        "app.test-data-reset.enabled=false",
        "app.bank-reconciliation-reset.enabled=false",
        "spring.datasource.url=jdbc:postgresql://alibooks-prod.abc.eu-north-1.rds.amazonaws.com:5432/alibooks",
        "spring.datasource.username=alibooks_prod",
        "spring.datasource.password=a-production-database-password"
    ).run(context -> {
      Throwable failure = context.getStartupFailure();
      assertThat(failure).isNotNull();
      Throwable rootCause = rootCause(failure);
      assertThat(rootCause)
          .hasMessageContaining("whole SEK")
          .hasMessageContaining("historical reconciliation");
    });
  }

  @Test
  void localPropertyDoesNotActivateProductionGuard() {
    contextRunner.withPropertyValues("app.environment=local")
        .run(context -> assertThat(context.getStartupFailure()).isNull());
  }

  @Test
  void blocksProductionUntilExactOreAccountingAndReconciliationAreVerified() {
    assertThatThrownBy(() -> new ProductionConfigurationGuard(
        "Q3n9wY7rL2pV8xK4mD6sF1aH5jB0cE9uT7zN3qW6",
        "test-owner-bootstrap-key-unique-2026",
        60,
        "validate",
        false,
        false,
        "https://books.alibooks.se",
        false,
        false,
        "jdbc:postgresql://alibooks-prod.abc.eu-north-1.rds.amazonaws.com:5432/alibooks",
        "alibooks_prod",
        "a-production-database-password"
    )).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("whole SEK")
        .hasMessageContaining("ore-based accounting")
        .hasMessageContaining("historical reconciliation");
  }

  @Test
  void rejectsUnsafeSettingsWithoutPrintingSecretValues() {
    assertThatThrownBy(() -> new ProductionConfigurationGuard(
        "short-secret-value",
        "short-setup-key",
        0,
        "update",
        true,
        true,
        "http://localhost:5157,*",
        true,
        true,
        "jdbc:postgresql://localhost:5432/cloudshop",
        "cloudshop",
        "cloudshop"
    ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining("APP_AUTH_REGISTRATION_BOOTSTRAP_KEY")
        .hasMessageContaining("APP_SCHEMA_PATCH_ENABLED")
        .hasMessageContaining("SPRING_DATASOURCE_URL")
        .satisfies(error -> assertThat(error.getMessage()).doesNotContain("short-secret-value"));
  }

  @Test
  void rejectsPlaceholderCredentialsEvenWhenLongEnough() {
    assertThatThrownBy(() -> new ProductionConfigurationGuard(
        "replace_with_long_random_secret_at_least_32_chars",
        "replace_with_one_time_owner_setup_key_32_chars",
        60,
        "validate",
        false,
        false,
        "https://books.alibooks.se",
        false,
        false,
        "jdbc:postgresql://alibooks-prod.abc.eu-north-1.rds.amazonaws.com:5432/alibooks",
        "alibooks_prod",
        "replace_with_rds_password"
    ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining("SPRING_DATASOURCE_PASSWORD");
  }

  @Test
  void rejectsTemplateProductionHosts() {
    assertThatThrownBy(() -> new ProductionConfigurationGuard(
        "Q3n9wY7rL2pV8xK4mD6sF1aH5jB0cE9uT7zN3qW6",
        "test-owner-bootstrap-key-unique-2026",
        60,
        "validate",
        false,
        false,
        "http://your-ec2-public-ip",
        false,
        false,
        "jdbc:postgresql://your-rds-endpoint.eu-north-1.rds.amazonaws.com:5432/cloudshop",
        "alibooks_prod",
        "a-production-database-password"
    ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS")
        .hasMessageContaining("SPRING_DATASOURCE_URL");
  }

  @Test
  void rejectsDockerDatabaseAliasesInProduction() {
    assertThatThrownBy(() -> new ProductionConfigurationGuard(
        "Q3n9wY7rL2pV8xK4mD6sF1aH5jB0cE9uT7zN3qW6",
        "test-owner-bootstrap-key-unique-2026",
        60,
        "validate",
        false,
        false,
        "https://books.alibooks.se",
        false,
        false,
        "jdbc:postgresql://db:5432/cloudshop",
        "alibooks_prod",
        "a-production-database-password"
    ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SPRING_DATASOURCE_URL");
  }

  @Configuration(proxyBeanMethods = false)
  @Import(ProductionConfigurationGuard.class)
  static class GuardTestConfiguration {
  }

  private Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
