package se.cloudshop.accounting;

import java.util.List;

public record BalanceReport(
    List<ReportLine> assets,
    List<ReportLine> liabilitiesAndEquity,
    int totalAssets,
    int totalLiabilitiesAndEquity,
    int difference
) {
}
