package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Reconstructs balances from effective payment dates, never from today's status. */
public record SettlementSnapshot(int paidAmount, int remainingAmount) {
  public record Payment(LocalDate date, int amount) {}

  public static SettlementSnapshot at(int total, int savedPaid, LocalDate invoiceDate,
      LocalDate closedDate, List<Payment> payments, LocalDate asOf) {
    if (invoiceDate == null || total <= 0 || savedPaid < 0 || savedPaid > total
        || (closedDate != null && closedDate.isBefore(invoiceDate))) {
      throw incomplete();
    }
    long allPaid = 0;
    long paidAtDate = 0;
    for (Payment payment : payments) {
      if (payment.date() == null || payment.amount() <= 0 || payment.date().isBefore(invoiceDate)
          || (closedDate != null && payment.date().isAfter(closedDate))) {
        throw incomplete();
      }
      allPaid += payment.amount();
      if (!payment.date().isAfter(asOf)) paidAtDate += payment.amount();
    }
    if (allPaid != savedPaid) throw incomplete();
    if (invoiceDate.isAfter(asOf)) return new SettlementSnapshot(0, 0);
    int paid = ReportAmounts.reportAmount(paidAtDate);
    int remaining = closedDate != null && !closedDate.isAfter(asOf) ? 0 : total - paid;
    return new SettlementSnapshot(paid, remaining);
  }

  public static HistoryIncomplete incomplete() {
    return new HistoryIncomplete();
  }

  public static final class HistoryIncomplete extends ResponseStatusException {
    private HistoryIncomplete() {
      super(HttpStatus.UNPROCESSABLE_ENTITY,
          "Reskontran kan inte beraknas sakert. Fakturans datum, status eller betalningshistorik ar ofullstandig. Kontrollera underlagen; inga saldon har uppskattats.");
    }
  }
}
