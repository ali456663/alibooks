package se.cloudshop.accounting;

public record TrialBalanceLine(
    String accountNumber,
    String accountName,
    int openingDebit,
    int openingCredit,
    int periodDebit,
    int periodCredit,
    int closingDebit,
    int closingCredit
) {
}
