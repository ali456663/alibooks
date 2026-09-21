package se.cloudshop.invoice;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class InvoiceVatPolicy {

  public static final int SUPPORTED_RATE_PERCENT = 25;

  private InvoiceVatPolicy() {
  }

  public static void requireSupportedRate(int vatPercent) {
    if (vatPercent != SUPPORTED_RATE_PERCENT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The global VAT default supports 25% VAT only. Select the confirmed rate on each service or manual Stripe sale."
      );
    }
  }
}
