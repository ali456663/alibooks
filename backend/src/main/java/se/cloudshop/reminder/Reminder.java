package se.cloudshop.reminder;

public record Reminder(
    String type,
    String title,
    String message,
    String severity
) {
}

