package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;

public record VatFilingProofReport(
    LocalDate periodFrom,
    LocalDate periodTo,
    String filingStatus,
    int expectedVatAmount,
    boolean archived,
    boolean settlementVoucherFound,
    boolean paymentVoucherRequired,
    boolean paymentVoucherFound,
    boolean completeForCurrentStatus,
    List<VatFilingProofVoucher> settlementVouchers,
    List<VatFilingProofVoucher> paymentVouchers,
    String message
) {}
