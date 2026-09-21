package se.cloudshop.accounting;

import java.time.LocalDate;

public record VatReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    int outputVat,
    int inputVat,
    int vatToPay,
    boolean settled,
    int salesBase25,
    int outputVat25,
    int salesBase12,
    int outputVat12,
    int salesBase6,
    int outputVat6
) {
  public VatReport(LocalDate periodFrom, LocalDate periodTo, int outputVat, int inputVat, int vatToPay, boolean settled) {
    this(periodFrom, periodTo, outputVat, inputVat, vatToPay, settled, 0, outputVat, 0, 0, 0, 0);
  }
}
