package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateVatSettlementRequest(
    LocalDate periodFrom,
    LocalDate periodTo,
    LocalDate settlementDate
) {
}
