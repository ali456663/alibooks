package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VatFilingService {

  private final AccountingService accountingService;
  private final VatFilingRepository vatFilingRepository;

  public VatFilingService(AccountingService accountingService, VatFilingRepository vatFilingRepository) {
    this.accountingService = accountingService;
    this.vatFilingRepository = vatFilingRepository;
  }

  public List<VatFiling> findAll() {
    return vatFilingRepository.findAllByOrderByPeriodToDesc();
  }

  @Transactional
  public VatFiling create(CreateVatFilingRequest request) {
    if (request == null || request.periodFrom() == null || request.periodTo() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT period from and to dates are required.");
    }
    validatePeriod(request.periodFrom(), request.periodTo());

    vatFilingRepository.findByPeriodFromAndPeriodTo(request.periodFrom(), request.periodTo())
        .ifPresent(existing -> {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT filing already exists for this period.");
        });

    VatReport report = accountingService.createVatReport(request.periodFrom(), request.periodTo());
    String normalizedStatus = normalizeStatus(request.status());
    validateControlsBeforeFiling(report.periodFrom(), report.periodTo(), normalizedStatus);
    validatePaymentReference(report.vatToPay(), normalizedStatus, request.paymentReference());
    VatFiling filing = new VatFiling(report, request, normalizedStatus);
    if ("PAID".equals(normalizedStatus)) {
      accountingService.createVatFilingPaymentEntry(filing, request.paymentDate(), request.paymentReference());
    }
    return vatFilingRepository.save(filing);
  }

  @Transactional
  public VatFiling updateStatus(Long id, UpdateVatFilingStatusRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT filing status is required.");
    }

    VatFiling filing = vatFilingRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VAT filing was not found."));
    String normalizedStatus = normalizeStatus(request.status());
    validateStatusTransition(filing.getStatus(), normalizedStatus);
    validateControlsBeforeFiling(filing.getPeriodFrom(), filing.getPeriodTo(), normalizedStatus);
    validatePaymentReference(filing.getVatToPay(), normalizedStatus, request.paymentReference());
    if ("PAID".equals(normalizedStatus) && !"PAID".equals(filing.getStatus())) {
      accountingService.createVatFilingPaymentEntry(filing, request.paymentDate(), request.paymentReference());
    }
    filing.updateStatus(request, normalizedStatus);
    return vatFilingRepository.save(filing);
  }

  private String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      return "DRAFT";
    }

    String normalizedStatus = status.trim().toUpperCase();
    if (!List.of("DRAFT", "SUBMITTED", "PAID").contains(normalizedStatus)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT filing status must be DRAFT, SUBMITTED or PAID.");
    }

    return normalizedStatus;
  }

  private void validateStatusTransition(String currentStatus, String nextStatus) {
    int currentRank = statusRank(currentStatus);
    int nextRank = statusRank(nextStatus);

    if (nextRank < currentRank) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "VAT filing status cannot move backwards. Create a correction note instead of downgrading the filing."
      );
    }
  }

  private int statusRank(String status) {
    String normalizedStatus = status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase();
    if ("PAID".equals(normalizedStatus)) {
      return 2;
    }
    if ("SUBMITTED".equals(normalizedStatus)) {
      return 1;
    }
    return 0;
  }

  private void validatePaymentReference(int vatToPay, String status, String paymentReference) {
    if (!"PAID".equals(status) || vatToPay == 0) {
      return;
    }

    String reference = paymentReference == null ? "" : paymentReference.trim();
    if (reference.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Payment reference is required when a VAT filing is marked as paid."
      );
    }
    if (reference.length() > 255 || reference.contains("\n") || reference.contains("\r")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Payment reference must be a single line of max 255 characters."
      );
    }
  }

  private void validateControlsBeforeFiling(LocalDate periodFrom, LocalDate periodTo, String status) {
    if (!List.of("SUBMITTED", "PAID").contains(status)) {
      return;
    }

    VatControlReport vatControlReport = accountingService.createVatControlReport(periodFrom, periodTo);
    if (vatControlReport.criticalIssueCount() > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "VAT filing cannot be submitted or paid while critical VAT control issues remain."
      );
    }

    VoucherControlReport voucherControlReport = accountingService.createVoucherControlReport(periodFrom, periodTo);
    if (voucherControlReport.criticalIssueCount() > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "VAT filing cannot be submitted or paid while critical voucher control issues remain."
      );
    }

    VatReport vatReport = accountingService.createVatReport(periodFrom, periodTo);
    boolean vatSettlementRequired = vatReport.outputVat() != 0 || vatReport.inputVat() != 0;
    if (vatSettlementRequired && !accountingService.hasVatSettlementForPeriod(periodFrom, periodTo)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "VAT filing cannot be submitted or marked as paid before a VAT settlement voucher has been booked."
      );
    }
  }

  private void validatePeriod(LocalDate periodFrom, LocalDate periodTo) {
    if (periodFrom.isAfter(periodTo)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT period from date cannot be after to date.");
    }
  }
}
