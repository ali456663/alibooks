package se.cloudshop.supplier;

import java.time.LocalDate;

public record CreateSupplierInvoiceRequest(
    Long supplierId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String description,
    String reference,
    int totalAmount,
    int vatAmount,
    String category,
    Boolean selfBilling,
    String buyerName,
    String buyerReference,
    String approvalReference
) {
  public CreateSupplierInvoiceRequest(
      Long supplierId,
      LocalDate invoiceDate,
      LocalDate dueDate,
      String description,
      String reference,
      int totalAmount,
      int vatAmount,
      String category
  ) {
    this(
        supplierId,
        invoiceDate,
        dueDate,
        description,
        reference,
        totalAmount,
        vatAmount,
        category,
        false,
        "",
        "",
        ""
    );
  }
}
