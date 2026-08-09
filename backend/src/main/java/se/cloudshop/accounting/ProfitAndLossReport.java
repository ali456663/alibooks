package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record ProfitAndLossReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    List<ReportLine> revenue,
    List<ReportLine> expenses,
    int totalRevenue,
    int totalExpenses,
    int result
) {
  public ProfitAndLossReport(List<ReportLine> revenue, List<ReportLine> expenses, int totalRevenue, int totalExpenses, int result) {
    this(null, null, revenue, expenses, totalRevenue, totalExpenses, result);
  }
}
