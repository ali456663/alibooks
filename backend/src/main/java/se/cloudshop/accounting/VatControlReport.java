package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record VatControlReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    int voucherCount,
    int totalSalesNet,
    int totalOutputVat,
    int expectedOutputVat,
    int outputVatDifference,
    int totalPurchaseNet,
    int totalInputVat,
    int expectedMaxInputVat,
    int vatToPay,
    int criticalIssueCount,
    int warningIssueCount,
    List<VatControlIssue> issues
) {
}
