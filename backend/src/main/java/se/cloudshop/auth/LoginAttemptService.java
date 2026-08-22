package se.cloudshop.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

  private final int maxFailedAttempts;
  private final Duration lockDuration;
  private final Clock clock;
  private final Map<String, LoginAttemptState> attempts = new ConcurrentHashMap<>();

  @Autowired
  public LoginAttemptService(
      @Value("${app.auth.max-failed-login-attempts:5}") int maxFailedAttempts,
      @Value("${app.auth.login-lock-minutes:15}") int lockMinutes
  ) {
    this(maxFailedAttempts, Duration.ofMinutes(lockMinutes), Clock.systemUTC());
  }

  LoginAttemptService(int maxFailedAttempts, Duration lockDuration, Clock clock) {
    this.maxFailedAttempts = Math.max(maxFailedAttempts, 0);
    this.lockDuration = lockDuration == null || lockDuration.isNegative() ? Duration.ZERO : lockDuration;
    this.clock = clock;
  }

  public synchronized boolean isBlocked(String email) {
    if (!enabled()) {
      return false;
    }

    LoginAttemptState state = attempts.get(normalize(email));
    if (state == null || state.lockedUntil == null) {
      return false;
    }

    if (Instant.now(clock).isBefore(state.lockedUntil)) {
      return true;
    }

    attempts.remove(normalize(email));
    return false;
  }

  public synchronized LoginAttemptResult recordFailure(String email) {
    if (!enabled()) {
      return new LoginAttemptResult(0, false, 0);
    }

    String key = normalize(email);
    LoginAttemptState state = attempts.computeIfAbsent(key, ignored -> new LoginAttemptState());
    state.failedAttempts += 1;
    if (state.failedAttempts >= maxFailedAttempts) {
      state.lockedUntil = Instant.now(clock).plus(lockDuration);
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

  private boolean enabled() {
    return maxFailedAttempts > 0 && !lockDuration.isZero();
  }

  private String normalize(String email) {
    return email == null || email.isBlank() ? "unknown" : email.trim().toLowerCase();
  }

  private static class LoginAttemptState {
    private int failedAttempts;
    private Instant lockedUntil;
  }
}
