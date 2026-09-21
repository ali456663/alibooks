package se.cloudshop.invoice;

import se.cloudshop.order.Order;
import se.cloudshop.customer.Customer;
import se.cloudshop.settings.AppSettings;

/** Historical invoice data; only seller settings may refresh while the invoice remains a draft. */
public record InvoiceDocumentSnapshot(int version, String customerName, String email, String personalNumber,
    String address, String phone, String postalCode, String city, String productName, String issuerName,
    String issuerAddress, String issuerPostalCode, String issuerCity, String issuerOrganizationNumber,
    String vatRegistrationNumber, String contactEmail, String plusGiro, String ocr, String paymentRecipient,
    boolean fTaxApproved, String creditedInvoiceNumber) {
  public static InvoiceDocumentSnapshot capture(Order invoice, AppSettings settings) {
    Customer customer = invoice.getCustomer();
    return new InvoiceDocumentSnapshot(3, invoice.getCustomerName(), customer == null ? "" : customer.getEmail(),
        customer == null ? "" : customer.getPersonalNumber(), customer == null ? "" : customer.getAddress(),
        customer == null ? "" : customer.getPhone(), customer == null ? "" : customer.getPostalCode(),
        customer == null ? "" : customer.getCity(), invoice.getProduct() == null ? "" : invoice.getProduct().getName(),
        settings.getCompanyName(), settings.getCompanyAddress(), settings.getCompanyPostalCode(),
        settings.getCompanyCity(), settings.getCompanyOrganizationNumber(), settings.getVatRegistrationNumber(),
        settings.getContactEmail(), invoice.getPlusGiro(), invoice.getOcrNumber(), invoice.getPaymentRecipient(),
        settings.isFTaxApproved(), invoice.isCreditInvoice() ? invoice.getCreditedInvoiceNumber() : null);
  }

  public InvoiceDocumentSnapshot withIssuerDetails(InvoiceDocumentSnapshot current) {
    return new InvoiceDocumentSnapshot(3, customerName, email, personalNumber, address, phone, postalCode, city,
        productName, current.issuerName, current.issuerAddress, current.issuerPostalCode, current.issuerCity,
        current.issuerOrganizationNumber, current.vatRegistrationNumber, current.contactEmail, plusGiro, ocr,
        paymentRecipient, current.fTaxApproved, creditedInvoiceNumber);
  }

  public InvoiceDocumentSnapshot withCreditedInvoiceNumber(String invoiceNumber) {
    return new InvoiceDocumentSnapshot(3, customerName, email, personalNumber, address, phone, postalCode, city,
        productName, issuerName, issuerAddress, issuerPostalCode, issuerCity, issuerOrganizationNumber,
        vatRegistrationNumber, contactEmail, plusGiro, ocr, paymentRecipient, fTaxApproved, invoiceNumber);
  }
}
