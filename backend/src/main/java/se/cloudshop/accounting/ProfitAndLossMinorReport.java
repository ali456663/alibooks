package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record ProfitAndLossMinorReport(
    LocalDate periodFrom, LocalDate periodTo, String currencyCode, int minorUnitExponent,
    List<Line> revenue, List<Line> expenses,
    long totalRevenueMinor, long totalExpensesMinor, long resultMinor
) {
  public ProfitAndLossMinorReport {
    revenue = List.copyOf(revenue);
    expenses = List.copyOf(expenses);
  }

  public record Line(String accountNumber, String accountName, long amountMinor) {
  }
}
