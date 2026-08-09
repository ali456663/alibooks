package se.cloudshop.accounting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AccountSignControlReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    Instant generatedAt,
    int accountCount,
    int issueCount,
    int criticalIssueCount,
    int warningIssueCount,
    List<AccountSignControlLine> lines
) {
}
