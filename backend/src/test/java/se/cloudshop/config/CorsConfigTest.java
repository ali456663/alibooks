package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorsConfigTest {

  @Test
  void productionCanRestrictCorsToConfiguredFrontend() {
    CorsConfig corsConfig = new CorsConfig("https://app.alibooks.example", false);

    assertThat(corsConfig.allowedOriginPatterns())
        .containsExactly("https://app.alibooks.example");
  }

  @Test
  void localDevelopmentKeepsLocalhostOrigins() {
    CorsConfig corsConfig = new CorsConfig("http://localhost:5157", true);

    assertThat(corsConfig.allowedOriginPatterns())
        .contains("http://localhost:5157", "http://localhost:*", "http://127.0.0.1:*");
  }

  @Test
  void trimsAndDeduplicatesConfiguredOrigins() {
    CorsConfig corsConfig = new CorsConfig(" https://app.example ,https://app.example, https://admin.example ", false);

    assertThat(corsConfig.allowedOriginPatterns())
        .containsExactly("https://app.example", "https://admin.example");
  }
}
