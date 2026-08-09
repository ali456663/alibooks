package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

  private final LoginAttemptService loginAttemptService = new LoginAttemptService(
      2,
      Duration.ofMinutes(15),
      Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC)
  );

  @Test
  void blocksEmailAfterMaximumFailures() {
    assertThat(loginAttemptService.recordFailure("ALI@EXAMPLE.COM").blocked()).isFalse();

    LoginAttemptResult result = loginAttemptService.recordFailure("ali@example.com");

    assertThat(result.blocked()).isTrue();
    assertThat(loginAttemptService.isBlocked("ali@example.com")).isTrue();
    assertThat(loginAttemptService.remainingLockSeconds("ali@example.com")).isEqualTo(900);
  }

  @Test
  void successClearsPreviousFailures() {
    loginAttemptService.recordFailure("ali@example.com");

    loginAttemptService.recordSuccess("ali@example.com");

    assertThat(loginAttemptService.isBlocked("ali@example.com")).isFalse();
    assertThat(loginAttemptService.recordFailure("ali@example.com").blocked()).isFalse();
  }

  @Test
  void canBeDisabledWithZeroConfiguration() {
    LoginAttemptService disabledService = new LoginAttemptService(
        0,
        Duration.ZERO,
        Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC)
    );

    LoginAttemptResult result = disabledService.recordFailure("ali@example.com");

    assertThat(result.blocked()).isFalse();
    assertThat(result.failedAttempts()).isZero();
    assertThat(disabledService.isBlocked("ali@example.com")).isFalse();
  }
}
