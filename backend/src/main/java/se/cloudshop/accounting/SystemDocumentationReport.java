package se.cloudshop.accounting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SystemDocumentationReport(
    Instant generatedAt,
    LocalDate periodFrom,
    LocalDate periodTo,
    String companyName,
    String companyType,
    String accountingMethod,
    String vatReportingPeriod,
    int accountCount,
    int voucherSeriesCount,
    int automationCount,
    int controlCount,
    int exportCount,
    List<String> voucherSeries,
    List<SystemDocumentationItem> items
) {
}
