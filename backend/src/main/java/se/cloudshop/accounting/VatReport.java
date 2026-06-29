package se.cloudshop.accounting;

public record VatReport(
    int outputVat,
    int inputVat,
    int vatToPay
) {
}

