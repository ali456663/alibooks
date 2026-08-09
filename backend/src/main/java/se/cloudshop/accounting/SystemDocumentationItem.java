package se.cloudshop.accounting;

public record SystemDocumentationItem(
    String category,
    String title,
    String detail,
    String source,
    String controlPoint,
    String recommendedExport
) {
}
