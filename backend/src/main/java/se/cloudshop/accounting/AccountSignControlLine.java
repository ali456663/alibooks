package se.cloudshop.accounting;

public record AccountSignControlLine(
    String accountNumber,
    String accountName,
    String expectedNature,
    int balance,
    String severity,
    String status,
    boolean blocking,
    String message
) {
}
