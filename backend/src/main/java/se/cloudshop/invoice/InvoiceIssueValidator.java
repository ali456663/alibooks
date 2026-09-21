package se.cloudshop.invoice;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.order.Order;

public final class InvoiceIssueValidator {
  private InvoiceIssueValidator() {
  }

  public static void requireIssuable(Order invoice) {
    InvoiceDocumentSnapshot snapshot = invoice.getDocumentSnapshot();
    if (snapshot == null || blank(snapshot.issuerName()) || blank(snapshot.issuerAddress())
        || blank(snapshot.issuerPostalCode()) || blank(snapshot.issuerCity())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Complete the seller name and registered address in Settings before issuing an invoice.");
    }
    int vatAmount = wholeKrona(invoice.getVatAmountMinor(), invoice.getVatAmount(), "fakturans momsbelopp");
    if (vatAmount != 0 && blank(snapshot.vatRegistrationNumber())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Add the seller VAT registration number in Settings before issuing an invoice with VAT.");
    }
    if (blank(snapshot.productName())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Add a clear product or service description before issuing the invoice.");
    }
    int totalAmount = wholeKrona(invoice.getTotalAmountMinor(), invoice.getTotalAmount(), "fakturans totalbelopp");
    if (totalAmount > 4_000
        && (blank(snapshot.customerName()) || blank(snapshot.address())
            || blank(snapshot.postalCode()) || blank(snapshot.city()))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Invoices over 4,000 SEK require the buyer's full name and address before issuance.");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static int wholeKrona(Long amountMinor, int legacyAmount, String field) {
    long valueMinor = amountMinor == null ? Math.multiplyExact((long) legacyAmount, 100L) : amountMinor;
    if (valueMinor % 100L != 0L) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          field + " innehåller ören som fakturautskicket inte kan representera.");
    }
    long wholeKrona = valueMinor / 100L;
    if (wholeKrona < Integer.MIN_VALUE || wholeKrona > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          field + " ligger utanför det belopp som fakturautskicket kan representera.");
    }
    return (int) wholeKrona;
  }
}
