package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MfaAuthorizationInterceptorTest {
  private final JwtService jwt = new JwtService("test_secret");
  private final MfaAuthorizationInterceptor interceptor = new MfaAuthorizationInterceptor(jwt);

  @Test
  void protectedApiRejectsMissingAndPasswordOnlyTokens() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/invoices");
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);

    request.addHeader("Authorization", "Bearer " + jwt.createToken("owner@example.com"));
    response = new MockHttpServletResponse();
    assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
  }

  @Test
  void protectedApiAcceptsOnlyMfaVerifiedTokensAndAllowsCorsPreflight() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/invoices");
    request.addHeader("Authorization", "Bearer " + jwt.createToken("owner@example.com", true));
    assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

    MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/invoices");
    assertThat(interceptor.preHandle(preflight, new MockHttpServletResponse(), new Object())).isTrue();
  }
}
