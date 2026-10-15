package se.cloudshop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import se.cloudshop.auth.JwtService;
import se.cloudshop.auth.UserRepository;

@Configuration
@ConditionalOnProperty(prefix = "app", name = "environment", havingValue = "production")
public class ProductionAccountWebConfiguration implements WebMvcConfigurer {
  private final ProductionAccountRequestGuard guard;

  public ProductionAccountWebConfiguration(UserRepository users, JwtService jwt) {
    this.guard = new ProductionAccountRequestGuard(users, jwt);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(guard).addPathPatterns("/**").excludePathPatterns("/health", "/error");
  }
}
