package se.cloudshop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import se.cloudshop.config.MoneyMigrationVerifier;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.email.EmailDeliveryLedger;

class HealthControllerTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

  @Test
  void systemStatusShowsLocalCorsAsDevelopmentReady() {
    when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

    HealthController controller = controller("", true);

    Map<String, Object> status = controller.systemStatus("Bearer test-token");
    Map<String, Object> cors = nested(status, "cors");

    assertThat(cors.get("configured")).isEqualTo(false);
    assertThat(cors.get("localDevEnabled")).isEqualTo(true);
    assertThat(cors.get("localDevelopmentReady")).isEqualTo(true);
    assertThat(cors.get("productionReady")).isEqualTo(false);
    assertThat(effectiveOrigins(cors))
        .contains("http://localhost:*", "http://127.0.0.1:*");
  }

  @Test
  void systemStatusShowsProductionCorsAsReadyOnlyWhenLocalFallbackIsOff() {
    when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

    HealthController controller = controller("https://app.alibooks.example", false);

    Map<String, Object> status = controller.systemStatus("Bearer test-token");
    Map<String, Object> cors = nested(status, "cors");

    assertThat(cors.get("configured")).isEqualTo(true);
    assertThat(cors.get("localDevEnabled")).isEqualTo(false);
    assertThat(cors.get("localDevelopmentReady")).isEqualTo(false);
    assertThat(cors.get("productionReady")).isEqualTo(true);
    assertThat(effectiveOrigins(cors))
        .containsExactly("https://app.alibooks.example");
  }

  @Test
  void systemStatusExposesMaintenanceResetFlags() {
    when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

    HealthController controller = controller("https://app.alibooks.example", false, true, false);

    Map<String, Object> status = controller.systemStatus("Bearer test-token");
    Map<String, Object> maintenance = nested(status, "maintenance");

    assertThat(maintenance.get("testDataResetEnabled")).isEqualTo(true);
    assertThat(maintenance.get("bankReconciliationResetEnabled")).isEqualTo(false);
    assertThat(maintenance.get("safeForProduction")).isEqualTo(false);
  }

  @Test
  void systemStatusDoesNotOverclaimMinorUnitSupport() {
    when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

    Map<String, Object> moneyModel = nested(controller("", true).systemStatus("Bearer test-token"), "moneyModel");

    assertThat(moneyModel.get("currency")).isEqualTo("SEK");
    assertThat(moneyModel.get("unit")).isEqualTo("whole-krona");
    assertThat(moneyModel.get("supportsMinorUnits")).isEqualTo(false);
    assertThat(moneyModel.get("productionBookkeepingReady")).isEqualTo(false);
    Map<String, Object> migration = nested(moneyModel, "migration");
    assertThat(migration.get("state")).isEqualTo("verified-shadow");
    assertThat(migration.get("readyForAuthoritativeCutover")).isEqualTo(false);
  }

  @Test
  void systemStatusShowsPendingAndUncertainDeliveriesAsUnresolved() {
    when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);
    EmailDeliveryLedger deliveryLedger = mock(EmailDeliveryLedger.class);
    when(deliveryLedger.countPending()).thenReturn(1L);
    when(deliveryLedger.countUncertain()).thenReturn(2L);
    when(deliveryLedger.countUnresolved()).thenReturn(3L);

    Map<String, Object> email = nested(
        controller("https://app.alibooks.example", false, false, false, deliveryLedger)
            .systemStatus("Bearer test-token"), "email");

    assertThat(email.get("deliveryLedgerEnabled")).isEqualTo(true);
    assertThat(email.get("pendingDeliveryCount")).isEqualTo(1L);
    assertThat(email.get("uncertainDeliveryCount")).isEqualTo(2L);
    assertThat(email.get("unresolvedDeliveryCount")).isEqualTo(3L);
  }

  @Test
  void systemStatusRequiresAnEnrolledOwnerAndStrongMfaKey() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
    Map<String, Object> auth = nested(controller("https://app.alibooks.example", false, false, false,
        null, true, "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
        .systemStatus("Bearer test-token"), "auth");
    assertThat(auth.get("mfaRequired")).isEqualTo(true);
    assertThat(auth.get("mfaKeyStrong")).isEqualTo(true);
    assertThat(auth.get("ownerMfaEnabled")).isEqualTo(true);

    when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
    auth = nested(controller("https://app.alibooks.example", false, false, false,
        null, true, "short")
        .systemStatus("Bearer test-token"), "auth");
    assertThat(auth.get("mfaKeyStrong")).isEqualTo(false);
    assertThat(auth.get("ownerMfaEnabled")).isEqualTo(false);
  }

  private HealthController controller(String corsAllowedOrigins, boolean corsLocalDevEnabled) {
    return controller(corsAllowedOrigins, corsLocalDevEnabled, false, false);
  }

  private HealthController controller(
      String corsAllowedOrigins,
      boolean corsLocalDevEnabled,
      boolean testDataResetEnabled,
      boolean bankReconciliationResetEnabled
  ) {
    return controller(corsAllowedOrigins, corsLocalDevEnabled,
        testDataResetEnabled, bankReconciliationResetEnabled, null);
  }

  private HealthController controller(
      String corsAllowedOrigins,
      boolean corsLocalDevEnabled,
      boolean testDataResetEnabled,
      boolean bankReconciliationResetEnabled,
      EmailDeliveryLedger deliveryLedger
  ) {
    return controller(corsAllowedOrigins, corsLocalDevEnabled, testDataResetEnabled,
        bankReconciliationResetEnabled, deliveryLedger, false, "");
  }

  private HealthController controller(
      String corsAllowedOrigins,
      boolean corsLocalDevEnabled,
      boolean testDataResetEnabled,
      boolean bankReconciliationResetEnabled,
      EmailDeliveryLedger deliveryLedger,
      boolean mfaRequired,
      String mfaEncryptionKey
  ) {
    MoneyMigrationVerifier moneyMigrationVerifier = mock(MoneyMigrationVerifier.class);
    AuthHeader authHeader = mock(AuthHeader.class);
    when(moneyMigrationVerifier.status()).thenReturn(Map.of(
        "state", "verified-shadow",
        "currency", "SEK",
        "verified", true,
        "readyForAuthoritativeCutover", false
    ));
    return new HealthController(
        jdbcTemplate,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "openai-compatible",
        "http://localhost:5157",
        "ci_test_secret_must_be_long_enough_for_demo",
        60,
        "0 0 9 * * *",
        "Europe/Stockholm",
        corsAllowedOrigins,
        corsLocalDevEnabled,
        5,
        15,
        mfaRequired,
        mfaEncryptionKey,
        testDataResetEnabled,
        bankReconciliationResetEnabled,
        moneyMigrationVerifier,
        authHeader,
        deliveryLedger
    );
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> nested(Map<String, Object> source, String key) {
    return (Map<String, Object>) source.get(key);
  }

  @SuppressWarnings("unchecked")
  private List<String> effectiveOrigins(Map<String, Object> cors) {
    return (List<String>) cors.get("effectiveAllowedOrigins");
  }
}
