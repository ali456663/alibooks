package se.cloudshop.accounting;

import java.util.List;

public record ProfitAndLossReport(
    List<ReportLine> revenue,
    List<ReportLine> expenses,
    int totalRevenue,
    int totalExpenses,
    int result
) {
}

