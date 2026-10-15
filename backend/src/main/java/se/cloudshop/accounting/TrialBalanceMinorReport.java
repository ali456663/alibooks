package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record TrialBalanceMinorReport(
    LocalDate periodFrom, LocalDate periodTo, String currencyCode, int minorUnitExponent,
    List<Line> lines,
    long openingDebitTotalMinor, long openingCreditTotalMinor,
    long periodDebitTotalMinor, long periodCreditTotalMinor,
    long closingDebitTotalMinor, long closingCreditTotalMinor, long differenceMinor
) {
  public TrialBalanceMinorReport {
    lines = List.copyOf(lines);
  }

  public record Line(
      String accountNumber, String accountName,
      long openingDebitMinor, long openingCreditMinor,
      long periodDebitMinor, long periodCreditMinor,
      long closingDebitMinor, long closingCreditMinor
  ) {
  }
}
