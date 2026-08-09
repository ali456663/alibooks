package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;

class AuthControllerTest {

  private final UserService userService = mock(UserService.class);
  private final JwtService jwtService = new JwtService("test_secret");
  private final LoginAttemptService loginAttemptService = new LoginAttemptService(
      2,
      Duration.ofMinutes(15),
      Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC)
  );
  private final AuditService auditService = mock(AuditService.class);
  private final AuthController authController = new AuthController(
      userService,
      jwtService,
      loginAttemptService,
      auditService
  );

  @Test
  void successfulRegistrationReturnsTokenAndAuditsEvent() {
    User user = new User("ali@example.com", "encoded");
    when(userService.register("ali@example.com", "secret123")).thenReturn(user);

    Map<String, String> response = authController.register(new AuthRequest("ali@example.com", "secret123"));

    assertThat(response.get("email")).isEqualTo("ali@example.com");
    assertThat(jwtService.isValid(response.get("token"))).isTrue();
    verify(auditService).record(eq("auth"), eq("user"), eq("ali@example.com"), eq("register_success"), eq("ali@example.com"), eq("User registered."), eq(0), isNull());
  }

  @Test
  void failedRegistrationAuditsEvent() {
    when(userService.register("bad-email", "short"))
        .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email and password with at least 8 characters are required."));

    assertThatThrownBy(() -> authController.register(new AuthRequest("bad-email", "short")))
        .isInstanceOf(ResponseStatusException.class);

    verify(auditService).record(
        eq("auth"),
        eq("user"),
        eq("bad-email"),
        eq("register_failed"),
        eq("bad-email"),
        eq("Valid email and password with at least 8 characters are required."),
        eq(0),
        isNull()
    );
  }

  @Test
  void loginLocksAfterRepeatedFailures() {
    when(userService.login("ali@example.com", "wrong")).thenReturn(Optional.empty());
    AuthRequest request = new AuthRequest("ali@example.com", "wrong");

    assertThatThrownBy(() -> authController.login(request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid email or password");

    assertThatThrownBy(() -> authController.login(request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

    verify(auditService).record(eq("auth"), eq("user"), eq("ali@example.com"), eq("login_failed"), eq("ali@example.com"), eq("Login failed."), eq(1), isNull());
    verify(auditService).record(eq("auth"), eq("user"), eq("ali@example.com"), eq("login_failed"), eq("ali@example.com"), eq("Login failed."), eq(2), isNull());
  }

  @Test
  void successfulLoginClearsFailuresAndReturnsToken() {
    User user = new User("ali@example.com", "encoded");
    when(userService.login("ali@example.com", "secret123")).thenReturn(Optional.of(user));

    Map<String, String> response = authController.login(new AuthRequest("ali@example.com", "secret123"));

    assertThat(response.get("email")).isEqualTo("ali@example.com");
    assertThat(jwtService.isValid(response.get("token"))).isTrue();
    verify(auditService).record(eq("auth"), eq("user"), eq("ali@example.com"), eq("login_success"), eq("ali@example.com"), eq("Login successful."), eq(0), isNull());
  }
}
