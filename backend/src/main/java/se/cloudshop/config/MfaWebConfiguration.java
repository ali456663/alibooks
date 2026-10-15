package se.cloudshop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import se.cloudshop.auth.JwtService;
import se.cloudshop.auth.MfaAuthorizationInterceptor;

@Configuration
public class MfaWebConfiguration implements WebMvcConfigurer {
  private final boolean required;
  private final MfaAuthorizationInterceptor interceptor;

  public MfaWebConfiguration(@Value("${app.auth.require-mfa:false}") boolean required, JwtService jwtService) {
    this.required = required;
    this.interceptor = new MfaAuthorizationInterceptor(jwtService);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    if (required) {
      registry.addInterceptor(interceptor)
          .addPathPatterns("/**")
          .excludePathPatterns(
              "/health",
              "/auth/login",
              "/auth/login/mfa",
              "/auth/register",
              "/auth/mfa/**",
              "/stripe/webhook"
          );
    }
  }
}
