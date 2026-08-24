package se.cloudshop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

  @Test
  void systemStatusShowsLocalCorsAsDevelopmentReady() {
    when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

    HealthController controller = controller("", true);

    Map<String, Object> status = controller.systemStatus();
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

    Map<String, Object> status = controller.systemStatus();
    Map<String, Object> cors = nested(status, "cors");

    assertThat(cors.get("configured")).isEqualTo(true);
    assertThat(cors.get("localDevEnabled")).isEqualTo(false);
    assertThat(cors.get("localDevelopmentReady")).isEqualTo(false);
    assertThat(cors.get("productionReady")).isEqualTo(true);
    assertThat(effectiveOrigins(cors))
        .containsExactly("https://app.alibooks.example");
  }

  private HealthController controller(String corsAllowedOrigins, boolean corsLocalDevEnabled) {
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
        15
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
