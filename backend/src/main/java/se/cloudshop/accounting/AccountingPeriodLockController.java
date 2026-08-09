package se.cloudshop.accounting;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.export.CsvEscaper;

@RestController
public class AccountingPeriodLockController {

  private final AuthHeader authHeader;
  private final AccountingPeriodLockService accountingPeriodLockService;
  private final AuditService auditService;

  public AccountingPeriodLockController(AuthHeader authHeader, AccountingPeriodLockService accountingPeriodLockService, AuditService auditService) {
    this.authHeader = authHeader;
    this.accountingPeriodLockService = accountingPeriodLockService;
    this.auditService = auditService;
  }

  @GetMapping("/accounting-period/close-check")
  public PeriodCloseCheckResult checkPeriod(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lockedThroughDate
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingPeriodLockService.checkPeriod(lockedThroughDate);
  }

  @GetMapping("/accounting-period/close-check/export")
  public ResponseEntity<byte[]> exportPeriodCheck(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lockedThroughDate
  ) {
    authHeader.requireValidToken(authorizationHeader);
    PeriodCloseCheckResult result = accountingPeriodLockService.checkPeriod(lockedThroughDate);
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks periodlasningskontroll\n");
    csv.append("Last till,").append(result.lockedThroughDate()).append("\n");
    csv.append("Redan last,").append(result.locked() ? "Ja" : "Nej").append("\n");
    csv.append("Redo att lasa,").append(result.readyToLock() ? "Ja" : "Nej").append("\n");
    csv.append("Verifikat,").append(result.voucherCount()).append("\n");
    csv.append("Obalanserade verifikat,").append(result.unbalancedVoucherCount()).append("\n");
    csv.append("Fakturautkast,").append(result.draftInvoiceCount()).append("\n");
    csv.append("Kostnader utan underlag,").append(result.expensesMissingReceiptCount()).append("\n");
    csv.append("Sent bokforda verifikat,").append(result.lateBookedVoucherCount()).append("\n");
    csv.append("Langsta bokforingsefterslapning dagar,").append(result.longestBookingLagDays()).append("\n");
    csv.append("Balansdifferens,").append(result.balanceDifference()).append("\n");
    csv.append("Saldobalans differens,").append(result.trialBalanceDifference()).append("\n");
    csv.append("Kritiska verifikationspunkter,").append(result.voucherCriticalIssueCount()).append("\n");
    csv.append("Verifikationsvarningar,").append(result.voucherWarningIssueCount()).append("\n");
    csv.append("Kritiska momspunkter,").append(result.vatCriticalIssueCount()).append("\n");
    csv.append("Momsvarningar,").append(result.vatWarningIssueCount()).append("\n");
    csv.append("Ofullstandiga momsbeviskedjor,").append(result.vatProofIncompleteCount()).append("\n");
    csv.append("Saknade momsavstamningar,").append(result.vatProofSettlementMissingCount()).append("\n");
    csv.append("Saknade momsbetalningsverifikat,").append(result.vatProofPaymentMissingCount()).append("\n");
    csv.append("Bokforingskedja differens,").append(result.journalIntegrityDifference()).append("\n");
    csv.append("Bokforingskedja saknade kallkopplingar,").append(result.journalIntegrityMissingEvidenceCount()).append("\n");
    csv.append("Bankdifferens,").append(result.bankReconciliationDifference()).append("\n");
    csv.append("Kritiska bankavstamningspunkter,").append(result.bankReconciliationCriticalIssueCount()).append("\n");
    csv.append("Bankavstamningsvarningar,").append(result.bankReconciliationWarningIssueCount()).append("\n");
    csv.append("Oppna kundfakturor,").append(result.receivablesInvoiceCount()).append("\n");
    csv.append("Kundfordringar totalt,").append(result.receivablesTotalOutstanding()).append("\n");
    csv.append("Forfallna kundfordringar,").append(result.receivablesOverdueOutstanding()).append("\n");
    csv.append("Kundfordringar som forfaller snart,").append(result.receivablesDueSoonOutstanding()).append("\n");
    csv.append("Oppna leverantorsfakturor,").append(result.payablesInvoiceCount()).append("\n");
    csv.append("Leverantorsskulder totalt,").append(result.payablesTotalOutstanding()).append("\n");
    csv.append("Forfallna leverantorsskulder,").append(result.payablesOverdueOutstanding()).append("\n");
    csv.append("Leverantorsskulder som forfaller snart,").append(result.payablesDueSoonOutstanding()).append("\n");
    csv.append("Godkanda verifikat,").append(result.voucherApprovedCount()).append("\n");
    csv.append("Verifikat utan attest,").append(result.voucherMissingApprovalCount()).append("\n");
    csv.append("Verifikat som vantar pa attest,").append(result.voucherPendingApprovalCount()).append("\n");
    csv.append("Blockerade verifikat,").append(result.voucherBlockedApprovalCount()).append("\n");
    csv.append("Periodstampel,").append(escape(result.periodFingerprint())).append("\n");
    csv.append("Slutlig kedjekod,").append(escape(result.finalChainHash())).append("\n");
    csv.append("SIE redo,").append(result.sieExportReady() ? "Ja" : "Nej").append("\n");
    csv.append("\n");
    csv.append("Typ,Meddelande\n");
    result.blockers().forEach(blocker -> csv.append("Stoppar,").append(escape(blocker)).append("\n"));
    result.warnings().forEach(warning -> csv.append("Varning,").append(escape(warning)).append("\n"));
    csv.append("\n");
    csv.append("Sena verifikat\n");
    csv.append("Verifikat,Verifikationsdatum,Bokfort datum,Efterslapning dagar,Debet,Kredit,Beskrivning\n");
    result.lateBookedVouchers().forEach(voucher -> csv
        .append(escape(voucher.voucherNumber())).append(",")
        .append(voucher.voucherDate() == null ? "" : voucher.voucherDate()).append(",")
        .append(voucher.bookedDate() == null ? "" : voucher.bookedDate()).append(",")
        .append(voucher.lagDays()).append(",")
        .append(voucher.debit()).append(",")
        .append(voucher.credit()).append(",")
        .append(escape(voucher.description())).append("\n"));

    auditService.record(
        "export",
        "period_close_check",
        result.lockedThroughDate(),
        "period_close_check_exported",
        String.valueOf(result.lockedThroughDate()),
        "Period close check exported. Ready to lock: " + result.readyToLock()
            + ". Blockers: " + result.blockers().size()
            + ". Warnings: " + result.warnings().size()
            + ". Late vouchers: " + result.lateBookedVoucherCount() + ".",
        result.voucherCount(),
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=periodlasningskontroll.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @PostMapping("/accounting-period/close")
  public PeriodCloseCheckResult closePeriod(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CloseAccountingPeriodRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Locked-through date is required.");
    }
    return accountingPeriodLockService.closePeriod(request.lockedThroughDate(), authorizationHeader);
  }

  private String escape(String value) {
    return CsvEscaper.escape(value);
  }
}
