package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record GeneralLedgerReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    String accountNumber,
    List<GeneralLedgerAccount> accounts,
    int accountCount,
    int entryCount,
    int periodDebitTotal,
    int periodCreditTotal
) {
}
