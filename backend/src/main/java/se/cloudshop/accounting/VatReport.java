package se.cloudshop.accounting;

import java.time.LocalDate;

public record VatReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    int outputVat,
    int inputVat,
    int vatToPay,
    boolean settled
) {
}
