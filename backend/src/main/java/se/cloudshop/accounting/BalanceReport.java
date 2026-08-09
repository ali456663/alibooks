package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record BalanceReport(
    LocalDate asOfDate,
    List<ReportLine> assets,
    List<ReportLine> liabilitiesAndEquity,
    int totalAssets,
    int totalLiabilitiesAndEquity,
    int difference
) {
  public BalanceReport(List<ReportLine> assets, List<ReportLine> liabilitiesAndEquity, int totalAssets, int totalLiabilitiesAndEquity, int difference) {
    this(null, assets, liabilitiesAndEquity, totalAssets, totalLiabilitiesAndEquity, difference);
  }
}
