package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record VoucherControlReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    int voucherCount,
    int entryCount,
    int balancedVoucherCount,
    int unbalancedVoucherCount,
    int reusedVoucherNumberCount,
    int missingVoucherNumberCount,
    int invalidVoucherNumberCount,
    int gapCount,
    int dateOrderIssueCount,
    int criticalIssueCount,
    int warningIssueCount,
    List<VoucherControlIssue> issues
) {
}
