package se.cloudshop.order;

public record CreateOrderRequest(
    String customerName,
    Long customerId,
    long productId,
    Integer quantity
) {
}
