package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record BalanceMinorReport(
    LocalDate asOfDate, String currencyCode, int minorUnitExponent,
    List<Line> assets, List<Line> liabilitiesAndEquity,
    long totalAssetsMinor, long totalLiabilitiesAndEquityMinor, long differenceMinor
) {
  public BalanceMinorReport {
    assets = List.copyOf(assets);
    liabilitiesAndEquity = List.copyOf(liabilitiesAndEquity);
  }

  public record Line(String accountNumber, String accountName, long amountMinor) {
  }
}
