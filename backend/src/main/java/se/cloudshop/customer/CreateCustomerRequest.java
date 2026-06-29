package se.cloudshop.customer;

public record CreateCustomerRequest(
    String name,
    String email,
    String personalNumber,
    String address,
    String phone,
    String postalCode,
    String city
) {
}
