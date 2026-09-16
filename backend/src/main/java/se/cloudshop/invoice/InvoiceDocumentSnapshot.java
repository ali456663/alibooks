package se.cloudshop.invoice;

import se.cloudshop.order.Order;
import se.cloudshop.customer.Customer;
import se.cloudshop.settings.AppSettings;

/** Invoice-time master data. Never refreshed from mutable customer/product/settings records. */
public record InvoiceDocumentSnapshot(int version, String customerName, String email, String personalNumber,
    String address, String phone, String postalCode, String city, String productName, String issuerName,
    String contactEmail, String plusGiro, String ocr, String paymentRecipient, boolean fTaxApproved) {
  public static InvoiceDocumentSnapshot capture(Order invoice, AppSettings settings) {
    Customer customer = invoice.getCustomer();
    return new InvoiceDocumentSnapshot(1, invoice.getCustomerName(), customer == null ? "" : customer.getEmail(),
        customer == null ? "" : customer.getPersonalNumber(), customer == null ? "" : customer.getAddress(),
        customer == null ? "" : customer.getPhone(), customer == null ? "" : customer.getPostalCode(),
        customer == null ? "" : customer.getCity(), invoice.getProduct() == null ? "" : invoice.getProduct().getName(),
        settings.getCompanyName(), settings.getContactEmail(), invoice.getPlusGiro(), invoice.getOcrNumber(),
        invoice.getPaymentRecipient(), settings.isFTaxApproved());
  }
}
