package se.cloudshop.auth;

public record LoginAttemptResult(
    int failedAttempts,
    boolean blocked,
    long remainingLockSeconds
) {
}
