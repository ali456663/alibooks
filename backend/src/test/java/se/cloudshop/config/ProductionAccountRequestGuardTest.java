package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import se.cloudshop.auth.JwtService;
import se.cloudshop.auth.User;
import se.cloudshop.auth.UserRepository;

class ProductionAccountRequestGuardTest {
  private final UserRepository users = mock(UserRepository.class);
  private final JwtService jwt = new JwtService("test-secret");
  private final ProductionAccountRequestGuard guard = new ProductionAccountRequestGuard(users, jwt);

  @Test
  void bootstrapOnlyAllowsFirstRegistration() throws Exception {
    assertThat(guard.preHandle(request("POST", "/auth/register"), new MockHttpServletResponse(), null)).isTrue();
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThat(guard.preHandle(request("GET", "/journal-entries"), response, null)).isFalse();
    assertThat(response.getStatus()).isEqualTo(503);
  }

  @Test
  void blocksEveryDataPathWhenExtraAccountAppears() throws Exception {
    when(users.count()).thenReturn(2L);
    for (String path : new String[] {"/journal-entries", "/stripe/webhook", "/auth/login"}) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      assertThat(guard.preHandle(request("POST", path), response, null)).isFalse();
      assertThat(response.getStatus()).isEqualTo(503);
    }
  }

  @Test
  void blocksTokenOfAnOwnerRemovedAfterIssuance() throws Exception {
    when(users.count()).thenReturn(1L);
    MockHttpServletRequest request = request("GET", "/journal-entries");
    request.addHeader("Authorization", "Bearer " + jwt.createToken("former@example.test", true));
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThat(guard.preHandle(request, response, null)).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void allowsCurrentOwnerAndFailsClosedOnDatabaseError() throws Exception {
    when(users.count()).thenReturn(1L);
    User owner = new User("owner@example.test", "hash");
    owner.setMfaEnabled(true);
    when(users.findByEmail("owner@example.test")).thenReturn(Optional.of(owner));
    MockHttpServletRequest request = request("GET", "/journal-entries");
    request.addHeader("Authorization", "Bearer " + jwt.createToken("owner@example.test", true));
    assertThat(guard.preHandle(request, new MockHttpServletResponse(), null)).isTrue();
    when(users.count()).thenThrow(new IllegalStateException("database unavailable"));
    assertThatThrownBy(() -> guard.preHandle(request, new MockHttpServletResponse(), null))
        .hasMessageContaining("database unavailable");
  }

  @Test
  void disabledMfaInvalidatesExistingVerifiedTokenForAccounting() throws Exception {
    when(users.count()).thenReturn(1L);
    when(users.findByEmail("owner@example.test"))
        .thenReturn(Optional.of(new User("owner@example.test", "hash")));
    String token = "Bearer " + jwt.createToken("owner@example.test", true);
    MockHttpServletRequest accounting = request("GET", "/journal-entries");
    accounting.addHeader("Authorization", token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThat(guard.preHandle(accounting, response, null)).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);

    MockHttpServletRequest setup = request("POST", "/auth/mfa/setup");
    setup.addHeader("Authorization", token);
    assertThat(guard.preHandle(setup, new MockHttpServletResponse(), null)).isTrue();
  }

  @Test
  void reenabledMfaDoesNotReviveOldVerifiedToken() throws Exception {
    when(users.count()).thenReturn(1L);
    User owner = new User("owner@example.test", "hash");
    owner.setMfaEnabled(true);
    when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
    String oldToken = jwt.createToken(owner.getEmail(), true, owner.getAuthVersion());

    owner.rotateAuthVersion();
    owner.rotateAuthVersion();
    MockHttpServletRequest oldRequest = request("GET", "/journal-entries");
    oldRequest.addHeader("Authorization", "Bearer " + oldToken);
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThat(guard.preHandle(oldRequest, response, null)).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);

    MockHttpServletRequest newRequest = request("GET", "/journal-entries");
    newRequest.addHeader("Authorization", "Bearer " + jwt.createToken(owner.getEmail(), true, owner.getAuthVersion()));
    assertThat(guard.preHandle(newRequest, new MockHttpServletResponse(), null)).isTrue();
  }

  @Test
  void appliesToWebhookAndAccountingButNotHealth() {
    ProductionAccountWebConfiguration configuration = new ProductionAccountWebConfiguration(users, jwt);
    ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();
    configuration.addInterceptors(registry);
    MappedInterceptor mapped = (MappedInterceptor) registry.exposedInterceptors().getFirst();
    assertThat(mapped.matches(request("POST", "/stripe/webhook"))).isTrue();
    assertThat(mapped.matches(request("GET", "/v2/general-ledger"))).isTrue();
    assertThat(mapped.matches(request("GET", "/health"))).isFalse();
    assertThat(mapped.matches(request("GET", "/error"))).isFalse();
  }

  private static MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    ServletRequestPathUtils.parseAndCache(request);
    return request;
  }

  private static class ExposedInterceptorRegistry extends InterceptorRegistry {
    private java.util.List<Object> exposedInterceptors() {
      return super.getInterceptors();
    }
  }
}
