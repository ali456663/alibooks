package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void capsTrackedEmailsAndFailsClosedWithoutEvictingAnActiveLock() {
    LoginAttemptService bounded = new LoginAttemptService(
        2,
        Duration.ofMinutes(15),
        Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC),
        2,
        Duration.ofMinutes(15)
    );
    bounded.recordFailure("locked@example.com");
    bounded.recordFailure("locked@example.com");
    bounded.recordFailure("other@example.com");

    LoginAttemptResult overflow = bounded.recordFailure("new@example.com");

    assertThat(overflow.blocked()).isTrue();
    assertThat(bounded.trackedAttemptCount()).isEqualTo(2);
    assertThat(bounded.isBlocked("locked@example.com")).isTrue();
  }

  @Test
  void removesExpiredEntriesAndAllowsNewEmails() {
    MutableClock clock = new MutableClock(Instant.parse("2026-07-01T10:00:00Z"));
    LoginAttemptService bounded = new LoginAttemptService(
        5, Duration.ofMinutes(15), clock, 1, Duration.ofMinutes(15));
    bounded.recordFailure("old@example.com");
    clock.advance(Duration.ofMinutes(16));

    LoginAttemptResult next = bounded.recordFailure("new@example.com");

    assertThat(next.blocked()).isFalse();
    assertThat(next.failedAttempts()).isEqualTo(1);
    assertThat(bounded.trackedAttemptCount()).isEqualTo(1);
  }

  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> instant;

    private MutableClock(Instant initial) {
      instant = new AtomicReference<>(initial);
    }

    private void advance(Duration duration) {
      instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }
}
