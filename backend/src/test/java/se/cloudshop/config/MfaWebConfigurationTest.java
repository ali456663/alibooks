package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import se.cloudshop.auth.JwtService;

class MfaWebConfigurationTest {

  @Test
  void stripeWebhookUsesStripeSignatureInsteadOfUserMfa() {
    MfaWebConfiguration configuration = new MfaWebConfiguration(true, mock(JwtService.class));
    ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();
    configuration.addInterceptors(registry);

    var registered = registry.exposedInterceptors();
    assertThat(registered).hasSize(1);
    MappedInterceptor mfa = (MappedInterceptor) registered.getFirst();

    assertThat(matches(mfa, "/stripe/webhook")).isFalse();
    assertThat(matches(mfa, "/invoices/42/checkout-session")).isTrue();
    assertThat(matches(mfa, "/system/status")).isTrue();
    assertThat(matches(mfa, "/v2/general-ledger/export")).isTrue();
    assertThat(matches(mfa, "/health")).isFalse();
  }

  private boolean matches(MappedInterceptor interceptor, String path) {
    HttpServletRequest request = new MockHttpServletRequest("POST", path);
    ServletRequestPathUtils.parseAndCache(request);
    return interceptor.matches(request);
  }

  private static class ExposedInterceptorRegistry extends InterceptorRegistry {
    private java.util.List<Object> exposedInterceptors() {
      return super.getInterceptors();
    }
  }
}
