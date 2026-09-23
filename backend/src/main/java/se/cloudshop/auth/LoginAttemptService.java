package se.cloudshop.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

  private final int maxFailedAttempts;
  private final Duration lockDuration;
  private final Duration attemptRetention;
  private final int maxTrackedEmails;
  private final Clock clock;
  private final Map<String, LoginAttemptState> attempts = new LinkedHashMap<>();
  private Instant lastCleanup = Instant.MIN;

  @Autowired
  public LoginAttemptService(
      @Value("${app.auth.max-failed-login-attempts:5}") int maxFailedAttempts,
      @Value("${app.auth.login-lock-minutes:15}") int lockMinutes,
      @Value("${app.auth.login-attempt-cache-max-entries:10000}") int maxTrackedEmails,
      @Value("${app.auth.login-attempt-retention-minutes:15}") int retentionMinutes
  ) {
    this(maxFailedAttempts, Duration.ofMinutes(lockMinutes), Clock.systemUTC(), maxTrackedEmails,
        Duration.ofMinutes(retentionMinutes));
  }

  LoginAttemptService(int maxFailedAttempts, Duration lockDuration, Clock clock) {
    this(maxFailedAttempts, lockDuration, clock, 10_000, Duration.ofMinutes(15));
  }

  LoginAttemptService(
      int maxFailedAttempts,
      Duration lockDuration,
      Clock clock,
      int maxTrackedEmails,
      Duration attemptRetention
  ) {
    this.maxFailedAttempts = Math.max(maxFailedAttempts, 0);
    this.lockDuration = lockDuration == null || lockDuration.isNegative() ? Duration.ZERO : lockDuration;
    this.maxTrackedEmails = Math.max(maxTrackedEmails, 1);
    this.attemptRetention = attemptRetention == null || attemptRetention.isNegative()
        ? Duration.ZERO
        : attemptRetention;
    this.clock = clock;
  }

  public synchronized boolean isBlocked(String email) {
    if (!enabled()) {
      return false;
    }

    Instant now = Instant.now(clock);
    cleanupExpiredAttempts(now);
    String key = normalize(email);
    LoginAttemptState state = attempts.get(key);
    if (state == null || state.lockedUntil == null) {
      return false;
    }

    if (now.isBefore(state.lockedUntil)) {
      return true;
    }

    attempts.remove(key);
    return false;
  }

  public synchronized LoginAttemptResult recordFailure(String email) {
    if (!enabled()) {
      return new LoginAttemptResult(0, false, 0);
    }

    Instant now = Instant.now(clock);
    cleanupExpiredAttempts(now);
    String key = normalize(email);
    LoginAttemptState state = attempts.get(key);
    if (state == null) {
      if (attempts.size() >= maxTrackedEmails) {
        return new LoginAttemptResult(maxFailedAttempts, true, 0);
      }
      state = new LoginAttemptState();
      attempts.put(key, state);
    }
    state.failedAttempts += 1;
    state.lastFailureAt = now;
    if (state.failedAttempts >= maxFailedAttempts) {
      state.lockedUntil = now.plus(lockDuration);
    }
    return new LoginAttemptResult(state.failedAttempts, isBlocked(email), remainingLockSeconds(email));
  }

  public synchronized void recordSuccess(String email) {
    attempts.remove(normalize(email));
  }

  public synchronized long remainingLockSeconds(String email) {
    LoginAttemptState state = attempts.get(normalize(email));
    if (state == null || state.lockedUntil == null) {
      return 0;
    }
    long remaining = Duration.between(Instant.now(clock), state.lockedUntil).toSeconds();
    return Math.max(remaining, 0);
  }

  public int maxFailedAttempts() {
    return maxFailedAttempts;
  }

  public long lockMinutes() {
    return lockDuration.toMinutes();
  }

  synchronized int trackedAttemptCount() {
    return attempts.size();
  }

  private void cleanupExpiredAttempts(Instant now) {
    if (!lastCleanup.equals(Instant.MIN)
        && now.isBefore(lastCleanup.plus(Duration.ofMinutes(1)))) {
      return;
    }
    lastCleanup = now;
    Iterator<Map.Entry<String, LoginAttemptState>> iterator = attempts.entrySet().iterator();
    while (iterator.hasNext()) {
      LoginAttemptState state = iterator.next().getValue();
      boolean lockExpired = state.lockedUntil == null || !now.isBefore(state.lockedUntil);
      boolean attemptExpired = !now.isBefore(state.lastFailureAt.plus(attemptRetention));
      if (lockExpired && attemptExpired) {
        iterator.remove();
      }
    }
  }

  private boolean enabled() {
    return maxFailedAttempts > 0 && !lockDuration.isZero();
  }

  private String normalize(String email) {
    return email == null || email.isBlank() ? "unknown" : email.trim().toLowerCase(Locale.ROOT);
  }

  private static class LoginAttemptState {
    private int failedAttempts;
    private Instant lockedUntil;
    private Instant lastFailureAt;
  }
}
