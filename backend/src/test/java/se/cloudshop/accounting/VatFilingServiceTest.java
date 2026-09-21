package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class VatFilingServiceTest {

  private final AccountingService accountingService = mock(AccountingService.class);
  private final VatFilingRepository vatFilingRepository = mock(VatFilingRepository.class);
  private final VatFilingService vatFilingService = new VatFilingService(accountingService, vatFilingRepository);

  @Test
  void createsVatFilingSnapshotForSubmittedPeriod() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    when(vatFilingRepository.findByPeriodFromAndPeriodTo(periodFrom, periodTo)).thenReturn(Optional.empty());
    when(accountingService.createVatReport(periodFrom, periodTo)).thenReturn(new VatReport(
        periodFrom,
        periodTo,
        2500,
        600,
        1900,
        false
    ));
    when(accountingService.createVatControlReport(periodFrom, periodTo)).thenReturn(cleanVatControlReport(periodFrom, periodTo));
    when(accountingService.createVoucherControlReport(periodFrom, periodTo)).thenReturn(cleanVoucherControlReport(periodFrom, periodTo));
    when(accountingService.hasVatSettlementForPeriod(periodFrom, periodTo)).thenReturn(true);
    when(vatFilingRepository.save(org.mockito.ArgumentMatchers.any(VatFiling.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VatFiling filing = vatFilingService.create(new CreateVatFilingRequest(
        periodFrom,
        periodTo,
        "submitted",
        "SKV-2026-07",
        "",
        null,
        "Uploaded in Mina sidor"
    ));

    assertThat(filing.getPeriodFrom()).isEqualTo(periodFrom);
    assertThat(filing.getPeriodTo()).isEqualTo(periodTo);
    assertThat(filing.getOutputVat()).isEqualTo(2500);
    assertThat(filing.getInputVat()).isEqualTo(600);
    assertThat(filing.getVatToPay()).isEqualTo(1900);
    assertThat(filing.getOutputVatMinor()).isEqualTo(250000L);
    assertThat(filing.getInputVatMinor()).isEqualTo(60000L);
    assertThat(filing.getVatToPayMinor()).isEqualTo(190000L);
    assertThat(filing.getStatus()).isEqualTo("SUBMITTED");
    assertThat(filing.getSubmissionReference()).isEqualTo("SKV-2026-07");
    assertThat(filing.getSubmittedAt()).isNotNull();
    assertThat(filing.getPaidAt()).isNull();
  }

  @Test
  void rejectsDuplicateVatFilingPeriod() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    when(vatFilingRepository.findByPeriodFromAndPeriodTo(periodFrom, periodTo))
        .thenReturn(Optional.of(new VatFiling()));

    assertThatThrownBy(() -> vatFilingService.create(new CreateVatFilingRequest(
        periodFrom,
        periodTo,
        "draft",
        "",
        "",
        null,
        ""
    )))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("VAT filing already exists");
  }

  @Test
  void listsVatFilingsInRepositoryOrder() {
    VatFiling filing = new VatFiling();
    when(vatFilingRepository.findAllByOrderByPeriodToDesc()).thenReturn(List.of(filing));

    assertThat(vatFilingService.findAll()).containsExactly(filing);
  }

  @Test
  void rejectsMovingSubmittedVatFilingBackToDraft() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    VatFiling submittedFiling = new VatFiling(
        new VatReport(periodFrom, periodTo, 2500, 600, 1900, false),
        new CreateVatFilingRequest(periodFrom, periodTo, "SUBMITTED", "SKV-2026-07", "", null, ""),
        "SUBMITTED"
    );
    when(vatFilingRepository.findById(7L)).thenReturn(Optional.of(submittedFiling));

    assertThatThrownBy(() -> vatFilingService.updateStatus(7L, new UpdateVatFilingStatusRequest(
        "DRAFT",
        "",
        "",
        null,
        "wrong downgrade"
    )))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("cannot move backwards");
  }

  @Test
  void rejectsSubmittedVatFilingWhenCriticalVatIssuesRemain() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    when(vatFilingRepository.findByPeriodFromAndPeriodTo(periodFrom, periodTo)).thenReturn(Optional.empty());
    when(accountingService.createVatReport(periodFrom, periodTo)).thenReturn(new VatReport(
        periodFrom,
        periodTo,
        2500,
        600,
        1900,
        false
    ));
    when(accountingService.createVatControlReport(periodFrom, periodTo)).thenReturn(new VatControlReport(
        periodFrom,
        periodTo,
        2,
        10000,
        1000,
        2500,
        -1500,
        0,
        0,
        0,
        1000,
        1,
        0,
        List.of(new VatControlIssue(
            "critical",
            "output_vat_mismatch",
            "F-1",
            LocalDate.of(2026, 7, 10),
            10000,
            1000,
            2500,
            0,
            0,
            0,
            -1500,
            "Output VAT does not match expected VAT."
        ))
    ));

    assertThatThrownBy(() -> vatFilingService.create(new CreateVatFilingRequest(
        periodFrom,
        periodTo,
        "SUBMITTED",
        "SKV-2026-07",
        "",
        null,
        ""
    )))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("critical VAT control issues");
  }

  @Test
  void rejectsSubmittedVatFilingWhenCriticalVoucherIssuesRemain() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    when(vatFilingRepository.findByPeriodFromAndPeriodTo(periodFrom, periodTo)).thenReturn(Optional.empty());
    when(accountingService.createVatReport(periodFrom, periodTo)).thenReturn(new VatReport(
        periodFrom,
        periodTo,
        2500,
        600,
        1900,
        false
    ));
    when(accountingService.createVatControlReport(periodFrom, periodTo)).thenReturn(cleanVatControlReport(periodFrom, periodTo));
    when(accountingService.createVoucherControlReport(periodFrom, periodTo)).thenReturn(new VoucherControlReport(
        periodFrom,
        periodTo,
        2,
        4,
        1,
        1,
        0,
        0,
        0,
        0,
        0,
        1,
        0,
        List.of(new VoucherControlIssue(
            "critical",
            "unbalanced_voucher",
            "A-2026-0007",
            "A",
            null,
            7,
            LocalDate.of(2026, 7, 10),
            1000,
            900,
            "Voucher debit and credit do not balance."
        ))
    ));

    assertThatThrownBy(() -> vatFilingService.create(new CreateVatFilingRequest(
        periodFrom,
        periodTo,
        "SUBMITTED",
        "SKV-2026-07",
        "",
        null,
        ""
    )))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("critical voucher control issues");
  }

  @Test
  void rejectsSubmittedVatFilingBeforeVatSettlementVoucherExists() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    when(vatFilingRepository.findByPeriodFromAndPeriodTo(periodFrom, periodTo)).thenReturn(Optional.empty());
    when(accountingService.createVatReport(periodFrom, periodTo)).thenReturn(new VatReport(
        periodFrom,
        periodTo,
        2500,
        600,
        1900,
        false
    ));
    when(accountingService.createVatControlReport(periodFrom, periodTo)).thenReturn(cleanVatControlReport(periodFrom, periodTo));
    when(accountingService.createVoucherControlReport(periodFrom, periodTo)).thenReturn(cleanVoucherControlReport(periodFrom, periodTo));
    when(accountingService.hasVatSettlementForPeriod(periodFrom, periodTo)).thenReturn(false);

    assertThatThrownBy(() -> vatFilingService.create(new CreateVatFilingRequest(
        periodFrom,
        periodTo,
        "SUBMITTED",
        "SKV-2026-07",
        "",
        null,
        ""
    )))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("before a VAT settlement voucher");

    verify(vatFilingRepository, never()).save(org.mockito.ArgumentMatchers.any(VatFiling.class));
  }

  @Test
  void marksVatFilingAsPaidAfterVatSettlementVoucherExists() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    VatFiling submittedFiling = new VatFiling(
        new VatReport(periodFrom, periodTo, 2500, 600, 1900, false),
        new CreateVatFilingRequest(periodFrom, periodTo, "SUBMITTED", "SKV-2026-07", "", null, ""),
        "SUBMITTED"
    );
    when(vatFilingRepository.findById(9L)).thenReturn(Optional.of(submittedFiling));
    when(accountingService.createVatReport(periodFrom, periodTo)).thenReturn(new VatReport(
        periodFrom,
        periodTo,
        2500,
        600,
        1900,
        true
    ));
    when(accountingService.createVatControlReport(periodFrom, periodTo)).thenReturn(cleanVatControlReport(periodFrom, periodTo));
    when(accountingService.createVoucherControlReport(periodFrom, periodTo)).thenReturn(cleanVoucherControlReport(periodFrom, periodTo));
    when(accountingService.hasVatSettlementForPeriod(periodFrom, periodTo)).thenReturn(true);
    when(vatFilingRepository.save(org.mockito.ArgumentMatchers.any(VatFiling.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VatFiling filing = vatFilingService.updateStatus(9L, new UpdateVatFilingStatusRequest(
        "PAID",
        "",
        "BANK-1",
        LocalDate.of(2026, 8, 12),
        ""
    ));

    assertThat(filing.getStatus()).isEqualTo("PAID");
    assertThat(filing.getPaymentReference()).isEqualTo("BANK-1");
    assertThat(filing.getPaidAt()).isNotNull();
    verify(accountingService).createVatFilingPaymentEntry(submittedFiling, LocalDate.of(2026, 8, 12), "BANK-1");
  }

  @Test
  void rejectsPaidVatFilingWithoutPaymentReference() {
    LocalDate periodFrom = LocalDate.of(2026, 7, 1);
    LocalDate periodTo = LocalDate.of(2026, 7, 31);
    VatFiling submittedFiling = new VatFiling(
        new VatReport(periodFrom, periodTo, 2500, 600, 1900, false),
        new CreateVatFilingRequest(periodFrom, periodTo, "SUBMITTED", "SKV-2026-07", "", null, ""),
        "SUBMITTED"
    );
    when(vatFilingRepository.findById(10L)).thenReturn(Optional.of(submittedFiling));
    when(accountingService.createVatReport(periodFrom, periodTo)).thenReturn(new VatReport(
        periodFrom,
        periodTo,
        2500,
        600,
        1900,
        true
    ));
    when(accountingService.createVatControlReport(periodFrom, periodTo)).thenReturn(cleanVatControlReport(periodFrom, periodTo));
    when(accountingService.createVoucherControlReport(periodFrom, periodTo)).thenReturn(cleanVoucherControlReport(periodFrom, periodTo));
    when(accountingService.hasVatSettlementForPeriod(periodFrom, periodTo)).thenReturn(true);

    assertThatThrownBy(() -> vatFilingService.updateStatus(10L, new UpdateVatFilingStatusRequest(
        "PAID",
        "",
        " ",
        LocalDate.of(2026, 8, 12),
        ""
    )))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Payment reference is required");

    verify(accountingService, never()).createVatFilingPaymentEntry(
        submittedFiling,
        LocalDate.of(2026, 8, 12),
        " "
    );
    verify(vatFilingRepository, never()).save(org.mockito.ArgumentMatchers.any(VatFiling.class));
  }

  private VatControlReport cleanVatControlReport(LocalDate periodFrom, LocalDate periodTo) {
    return new VatControlReport(
        periodFrom,
        periodTo,
        2,
        10000,
        2500,
        2500,
        0,
        2400,
        600,
        600,
        1900,
        0,
        0,
        List.of()
    );
  }

  private VoucherControlReport cleanVoucherControlReport(LocalDate periodFrom, LocalDate periodTo) {
    return new VoucherControlReport(
        periodFrom,
        periodTo,
        2,
        4,
        2,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of()
    );
  }
}
