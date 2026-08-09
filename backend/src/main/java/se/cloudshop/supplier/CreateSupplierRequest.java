package se.cloudshop.supplier;

public record CreateSupplierRequest(
    String name,
    String email,
    String orgNumber,
    String phone,
    String paymentInfo
) {
}
