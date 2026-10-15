package se.cloudshop.accounting;

import java.time.LocalDate;

public record VatMinorReport(
    LocalDate periodFrom, LocalDate periodTo, String currencyCode, int minorUnitExponent,
    long salesBase25Minor, long outputVat25Minor,
    long salesBase12Minor, long outputVat12Minor,
    long salesBase6Minor, long outputVat6Minor,
    long outputVatMinor, long inputVatMinor, long vatToPayMinor, boolean settled
) {
}
