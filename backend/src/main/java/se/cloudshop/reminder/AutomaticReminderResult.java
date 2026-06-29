package se.cloudshop.reminder;

import java.time.Instant;

public record AutomaticReminderResult(
    Instant ranAt,
    boolean enabled,
    int daysBeforeDue,
    int checked,
    int sent,
    int skipped
) {
}
