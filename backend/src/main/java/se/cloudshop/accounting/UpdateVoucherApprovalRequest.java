package se.cloudshop.accounting;

public record UpdateVoucherApprovalRequest(
    String status,
    String note,
    String reviewer
) {
}
