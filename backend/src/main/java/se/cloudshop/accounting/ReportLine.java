package se.cloudshop.accounting;

public record ReportLine(
    String accountNumber,
    String accountName,
    int amount
) {
}

