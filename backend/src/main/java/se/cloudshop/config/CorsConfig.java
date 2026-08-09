package se.cloudshop.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

  private final String allowedOrigins;
  private final boolean localDevEnabled;

  public CorsConfig(
      @Value("${app.cors.allowed-origins:}") String allowedOrigins,
      @Value("${app.cors.local-dev-enabled:true}") boolean localDevEnabled
  ) {
    this.allowedOrigins = allowedOrigins;
    this.localDevEnabled = localDevEnabled;
  }

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(allowedOriginPatterns().toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
      }
    };
  }

  List<String> allowedOriginPatterns() {
    List<String> patterns = new ArrayList<>();
    Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isBlank())
        .forEach(patterns::add);

    if (localDevEnabled) {
      patterns.add("http://localhost:*");
      patterns.add("http://127.0.0.1:*");
    }

    if (patterns.isEmpty()) {
      patterns.add("http://localhost:*");
      patterns.add("http://127.0.0.1:*");
    }

    return patterns.stream().distinct().toList();
  }
}
