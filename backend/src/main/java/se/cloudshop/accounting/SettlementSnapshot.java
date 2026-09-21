package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Reconstructs balances from effective payment dates, never from today's status. */
public record SettlementSnapshot(int paidAmount, int remainingAmount) {
  public record Payment(LocalDate date, int amount) {}

  public record MinorPayment(LocalDate date, long amountMinor) {}

  public record MinorSettlement(long paidAmountMinor, long remainingAmountMinor) {}

  public static SettlementSnapshot at(int total, int savedPaid, LocalDate invoiceDate,
      LocalDate closedDate, List<Payment> payments, LocalDate asOf) {
    try {
      long totalMinor = Math.multiplyExact((long) total, 100L);
      long savedPaidMinor = Math.multiplyExact((long) savedPaid, 100L);
      MinorSettlement settlement = atMinor(totalMinor, savedPaidMinor, invoiceDate, closedDate,
          payments.stream().map(payment -> new MinorPayment(payment.date(), Math.multiplyExact((long) payment.amount(), 100L))).toList(), asOf);
      return new SettlementSnapshot(ReportAmounts.reportAmount(settlement.paidAmountMinor() / 100L),
          ReportAmounts.reportAmount(settlement.remainingAmountMinor() / 100L));
    } catch (ArithmeticException exception) {
      throw incomplete();
    }
  }

  public static MinorSettlement atMinor(long totalMinor, long savedPaidMinor, LocalDate invoiceDate,
      LocalDate closedDate, List<MinorPayment> payments, LocalDate asOf) {
    if (invoiceDate == null || totalMinor <= 0 || savedPaidMinor < 0 || savedPaidMinor > totalMinor
        || (closedDate != null && closedDate.isBefore(invoiceDate))) {
      throw incomplete();
    }
    long allPaid = 0;
    long paidAtDate = 0;
    for (MinorPayment payment : payments) {
      if (payment.date() == null || payment.amountMinor() <= 0 || payment.date().isBefore(invoiceDate)
          || (closedDate != null && payment.date().isAfter(closedDate))) {
        throw incomplete();
      }
      try {
        allPaid = Math.addExact(allPaid, payment.amountMinor());
        if (!payment.date().isAfter(asOf)) paidAtDate = Math.addExact(paidAtDate, payment.amountMinor());
      } catch (ArithmeticException exception) {
        throw incomplete();
      }
    }
    if (allPaid != savedPaidMinor) throw incomplete();
    if (invoiceDate.isAfter(asOf)) return new MinorSettlement(0, 0);
    long remaining = closedDate != null && !closedDate.isAfter(asOf) ? 0 : Math.subtractExact(totalMinor, paidAtDate);
    return new MinorSettlement(paidAtDate, remaining);
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
