package se.cloudshop.accounting;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.audit.AuditEvent;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;

@RestController
public class AccountingController {

  private final AccountingService accountingService;
  private final VatFilingService vatFilingService;
  private final VoucherApprovalService voucherApprovalService;
  private final AuthHeader authHeader;
  private final AuditService auditService;

  public AccountingController(
      AccountingService accountingService,
      VatFilingService vatFilingService,
      VoucherApprovalService voucherApprovalService,
      AuthHeader authHeader,
      AuditService auditService
  ) {
    this.accountingService = accountingService;
    this.vatFilingService = vatFilingService;
    this.voucherApprovalService = voucherApprovalService;
    this.authHeader = authHeader;
    this.auditService = auditService;
  }

  @GetMapping("/journal-entries")
  public List<JournalEntry> getJournalEntries(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.findAllEntries();
  }

  @GetMapping("/stripe-payouts")
  public List<StripePayout> getStripePayouts(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.findStripePayouts();
  }

  @PostMapping("/journal-entries/manual")
  public List<JournalEntry> createManualJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateManualJournalEntryRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<JournalEntry> entries = accountingService.createManualEntry(request);
    auditService.record("voucher", "journal_entry", entries.isEmpty() ? null : entries.get(0).getVoucherNumber(), "manual_created", entries.isEmpty() ? "" : entries.get(0).getVoucherNumber(), "Manual voucher created", request.amount(), authorizationHeader);
    return entries;
  }

  @PostMapping("/journal-entries/manual-multi")
  public List<JournalEntry> createManualMultiLineJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateManualMultiLineJournalEntryRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<JournalEntry> entries = accountingService.createManualMultiLineEntry(request);
    int amount = entries.stream().mapToInt(entry -> Math.max(entry.getDebit(), entry.getCredit())).max().orElse(0);
    auditService.record("voucher", "journal_entry", entries.isEmpty() ? null : entries.get(0).getVoucherNumber(), "manual_multi_created", entries.isEmpty() ? "" : entries.get(0).getVoucherNumber(), "Manual multi-line voucher created", amount, authorizationHeader);
    return entries;
  }

  @PostMapping("/journal-entries/opening-balance")
  public List<JournalEntry> createOpeningBalanceJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateOpeningBalanceRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<JournalEntry> entries = accountingService.createOpeningBalanceEntry(request);
    int amount = entries.stream().mapToInt(entry -> Math.max(entry.getDebit(), entry.getCredit())).max().orElse(0);
    auditService.record("voucher", "journal_entry", entries.isEmpty() ? null : entries.get(0).getVoucherNumber(), "opening_balance_created", entries.isEmpty() ? "" : entries.get(0).getVoucherNumber(), "Opening balance voucher created", amount, authorizationHeader);
    return entries;
  }

  @PostMapping("/journal-entries/stripe-website-sale")
  public List<JournalEntry> createStripeWebsiteSaleJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateStripeWebsiteSaleRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<JournalEntry> entries = accountingService.createStripeWebsiteSaleEntry(request);
    auditService.record("stripe", "journal_entry", entries.isEmpty() ? null : entries.get(0).getVoucherNumber(), "website_sale_booked", request.reference(), "Stripe website sale booked", request.totalAmount(), authorizationHeader);
    return entries;
  }

  @PostMapping("/journal-entries/stripe-payout")
  public List<JournalEntry> createStripePayoutJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateStripePayoutRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<JournalEntry> entries = accountingService.createStripePayoutEntry(request);
    auditService.record("stripe", "journal_entry", entries.isEmpty() ? null : entries.get(0).getVoucherNumber(), "payout_booked", request.reference(), "Stripe payout booked", request.grossAmount(), authorizationHeader);
    return entries;
  }

  @PostMapping("/journal-entries/{voucherNumber}/correction")
  public List<JournalEntry> createCorrectionJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String voucherNumber,
      @RequestBody(required = false) CreateCorrectionJournalEntryRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<JournalEntry> entries = accountingService.createCorrectionEntry(voucherNumber, request);
    int amount = entries.stream().mapToInt(entry -> Math.max(entry.getDebit(), entry.getCredit())).max().orElse(0);
    String correctionVoucherNumber = entries.isEmpty() ? "" : entries.get(0).getVoucherNumber();
    auditService.record(
        "voucher",
        "journal_entry",
        correctionVoucherNumber,
        "correction_created",
        voucherNumber,
        "Correction voucher " + correctionVoucherNumber + " created for " + voucherNumber,
        amount,
        authorizationHeader
    );
    return entries;
  }

  @GetMapping("/vat-report")
  public VatReport getVatReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createVatReport(from, to);
  }

  @GetMapping("/vat-control")
  public VatControlReport getVatControlReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createVatControlReport(from, to);
  }

  @GetMapping("/vat-filing-proof")
  public VatFilingProofReport getVatFilingProofReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createVatFilingProofReport(from, to);
  }

  @PostMapping("/vat-report/settlement")
  public List<JournalEntry> createVatSettlement(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateVatSettlementRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<JournalEntry> entries = accountingService.createVatSettlementEntry(request);
    auditService.record("vat", "journal_entry", entries.isEmpty() ? null : entries.get(0).getVoucherNumber(), "vat_settlement_created", entries.isEmpty() ? "" : entries.get(0).getVoucherNumber(), "VAT settlement created", entries.stream().mapToInt(entry -> Math.max(entry.getDebit(), entry.getCredit())).max().orElse(0), authorizationHeader);
    return entries;
  }

  @GetMapping("/vat-filings")
  public List<VatFiling> getVatFilings(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return vatFilingService.findAll();
  }

  @GetMapping("/vat-filings/export")
  public ResponseEntity<byte[]> exportVatFilings(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<VatFiling> filings = vatFilingService.findAll();
    List<AuditEvent> auditEvents = auditService.eventsForEntityType("vat_filing");
    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks momsdeklarationsarkiv\n");
    csv.append("Antal perioder,").append(filings.size()).append("\n");
    csv.append("Antal handelser,").append(auditEvents.size()).append("\n\n");

    csv.append("Period fran,Period till,Status,Utgaende moms,Ingaende moms,Moms att betala,Referens deklaration,Referens betalning,Notering,Deklarerad datum,Betald datum,Skapad,Uppdaterad\n");
    filings.forEach(filing -> csv
        .append(filing.getPeriodFrom()).append(",")
        .append(filing.getPeriodTo()).append(",")
        .append(csvEscape(filing.getStatus())).append(",")
        .append(filing.getOutputVat()).append(",")
        .append(filing.getInputVat()).append(",")
        .append(filing.getVatToPay()).append(",")
        .append(csvEscape(filing.getSubmissionReference())).append(",")
        .append(csvEscape(filing.getPaymentReference())).append(",")
        .append(csvEscape(filing.getNote())).append(",")
        .append(filing.getSubmittedAt() == null ? "" : filing.getSubmittedAt()).append(",")
        .append(filing.getPaidAt() == null ? "" : filing.getPaidAt()).append(",")
        .append(filing.getCreatedAt() == null ? "" : filing.getCreatedAt()).append(",")
        .append(filing.getUpdatedAt() == null ? "" : filing.getUpdatedAt()).append("\n"));

    csv.append("\nAndringslogg\n");
    csv.append("Tid,Handelse,Entity ID,Referens,Meddelande,Belopp,Aktor\n");
    auditEvents.forEach(event -> csv
        .append(event.getCreatedAt() == null ? "" : event.getCreatedAt()).append(",")
        .append(csvEscape(event.getAction())).append(",")
        .append(csvEscape(event.getEntityId())).append(",")
        .append(csvEscape(event.getReference())).append(",")
        .append(csvEscape(event.getMessage())).append(",")
        .append(event.getAmount()).append(",")
        .append(csvEscape(event.getActorEmail())).append("\n"));
    int totalVatToPay = ReportAmounts.reportAmount(filings.stream().mapToLong(VatFiling::getVatToPay).sum());

    auditService.record(
        "export",
        "vat_filings",
        "vat_filings",
        "vat_filings_exported",
        "vat_filings",
        "VAT filings archive exported. Periods: " + filings.size()
            + ". Events: " + auditEvents.size() + ".",
        totalVatToPay,
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=momsdeklarationsarkiv.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @PostMapping("/vat-filings")
  public VatFiling createVatFiling(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateVatFilingRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    VatFiling filing = vatFilingService.create(request);
    auditService.record("vat", "vat_filing", filing.getId(), "vat_filing_created", filing.getPeriodFrom() + " - " + filing.getPeriodTo(), "VAT filing archived", filing.getVatToPay(), authorizationHeader);
    return filing;
  }

  @PatchMapping("/vat-filings/{id}/status")
  public VatFiling updateVatFilingStatus(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody UpdateVatFilingStatusRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    VatFiling filing = vatFilingService.updateStatus(id, request);
    auditService.record("vat", "vat_filing", filing.getId(), "vat_filing_status_updated", filing.getStatus(), "VAT filing status updated", filing.getVatToPay(), authorizationHeader);
    return filing;
  }

  @GetMapping("/profit-and-loss")
  public ProfitAndLossReport getProfitAndLossReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createProfitAndLossReport(from, to);
  }

  @GetMapping("/balance-report")
  public BalanceReport getBalanceReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createBalanceReport(to);
  }

  @GetMapping("/annual-result-voucher")
  public AnnualResultVoucherPreview getAnnualResultVoucherPreview(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate voucherDate
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createAnnualResultVoucherPreview(year, voucherDate);
  }

  @PostMapping("/annual-result-voucher")
  public List<JournalEntry> createAnnualResultVoucher(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateAnnualResultVoucherRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    List<JournalEntry> entries = accountingService.createAnnualResultVoucher(request);
    int amount = entries.stream().mapToInt(entry -> Math.max(entry.getDebit(), entry.getCredit())).max().orElse(0);
    auditService.record("closing", "journal_entry", entries.isEmpty() ? null : entries.get(0).getVoucherNumber(), "annual_result_created", entries.isEmpty() ? "" : entries.get(0).getVoucherNumber(), "Annual result voucher created", amount, authorizationHeader);
    return entries;
  }

  @GetMapping("/accountant-package")
  public AccountantPackageReport getAccountantPackageReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createAccountantPackageReport(from, to);
  }

  @GetMapping("/system-documentation")
  public SystemDocumentationReport getSystemDocumentationReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createSystemDocumentationReport(from, to);
  }

  @GetMapping("/archive-year")
  public ArchiveYearReport getArchiveYearReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) Integer year
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createArchiveYearReport(year);
  }

  @GetMapping("/trial-balance")
  public TrialBalanceReport getTrialBalanceReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createTrialBalanceReport(from, to);
  }

  @GetMapping("/account-sign-control")
  public AccountSignControlReport getAccountSignControlReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createAccountSignControlReport(from, to);
  }

  @GetMapping("/general-ledger")
  public GeneralLedgerReport getGeneralLedgerReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String accountNumber
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createGeneralLedgerReport(from, to, accountNumber);
  }

  @GetMapping("/voucher-control")
  public VoucherControlReport getVoucherControlReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createVoucherControlReport(from, to);
  }

  @GetMapping("/journal-integrity")
  public JournalIntegrityReport getJournalIntegrityReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createJournalIntegrityReport(from, to);
  }

  @GetMapping("/voucher-approvals")
  public List<VoucherApproval> getVoucherApprovals(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return voucherApprovalService.findAll();
  }

  @PutMapping("/voucher-approvals/{voucherNumber}")
  public VoucherApproval updateVoucherApproval(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String voucherNumber,
      @RequestBody UpdateVoucherApprovalRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    VoucherApproval approval = voucherApprovalService.update(voucherNumber, request);
    auditService.record(
        "voucher",
        "voucher_approval",
        approval.getVoucherNumber(),
        "voucher_approval_" + approval.getStatus(),
        approval.getVoucherNumber(),
        "Voucher approval updated to " + approval.getStatus(),
        0,
        authorizationHeader
    );
    return approval;
  }

  @DeleteMapping("/voucher-approvals/{voucherNumber}")
  public void deleteVoucherApproval(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String voucherNumber
  ) {
    authHeader.requireValidToken(authorizationHeader);
    voucherApprovalService.delete(voucherNumber);
    auditService.record(
        "voucher",
        "voucher_approval",
        voucherNumber,
        "voucher_approval_reset",
        voucherNumber,
        "Voucher approval reset",
        0,
        authorizationHeader
    );
  }

  private String csvEscape(String value) {
    if (value == null) {
      return "";
    }

    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
