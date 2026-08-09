package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record TrialBalanceReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    List<TrialBalanceLine> lines,
    int openingDebitTotal,
    int openingCreditTotal,
    int periodDebitTotal,
    int periodCreditTotal,
    int closingDebitTotal,
    int closingCreditTotal,
    int difference
) {
}
