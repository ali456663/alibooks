package se.cloudshop.accounting;

public record ArchiveYearItem(
    String key,
    String title,
    String status,
    String count,
    String detail,
    String recommendedExport
) {
}
