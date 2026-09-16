package se.cloudshop.accounting;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bounds existing whole-SEK report fields without silently wrapping or truncating. */
public final class ReportAmounts {
  private ReportAmounts() {
  }

  public static int reportAmount(long amount) {
    // Symmetric bounds also keep absolute values and debit/credit conversions safe.
    if (amount < -Integer.MAX_VALUE || amount > Integer.MAX_VALUE) {
      throw new LimitExceeded();
    }
    return (int) amount;
  }

  static final class LimitExceeded extends ResponseStatusException {
    LimitExceeded() {
      super(HttpStatus.UNPROCESSABLE_ENTITY,
          "Rapportbeloppet overskrider nuvarande beloppsgrans. Rapporten har stoppats for att undvika felaktiga belopp. Kontakta support.");
    }
  }
}
