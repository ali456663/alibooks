package se.cloudshop.accounting;

import java.util.List;

public record GeneralLedgerAccount(
    String accountNumber,
    String accountName,
    int openingBalance,
    int periodDebit,
    int periodCredit,
    int closingBalance,
    List<GeneralLedgerEntry> entries
) {
}
