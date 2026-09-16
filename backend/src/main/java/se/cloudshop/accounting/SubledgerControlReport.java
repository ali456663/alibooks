package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record SubledgerControlReport(LocalDate asOf, String accountingMethod, String status,
    List<AccountResult> accounts) {
  public record AccountResult(String accountNumber, int subledgerBalance, int ledgerBalance,
      Integer difference, String status, int unlinkedEntryCount, int undatedEntryCount,
      List<InvoiceDifference> differences) {}

  public record InvoiceDifference(Long invoiceId, int subledgerBalance, int ledgerBalance, int difference) {}
}
