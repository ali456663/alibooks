package se.cloudshop.bank;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.export.CsvEscaper;

@RestController
public class BankReconciliationController {

  private final AuthHeader authHeader;
  private final BankReconciliationEntryRepository bankReconciliationEntryRepository;
  private final BankReconciliationService bankReconciliationService;
  private final AuditService auditService;
  private final boolean bankReconciliationResetEnabled;

  public BankReconciliationController(
      AuthHeader authHeader,
      BankReconciliationEntryRepository bankReconciliationEntryRepository,
      BankReconciliationService bankReconciliationService,
      AuditService auditService,
      @Value("${app.bank-reconciliation-reset.enabled:false}") boolean bankReconciliationResetEnabled
  ) {
    this.authHeader = authHeader;
    this.bankReconciliationEntryRepository = bankReconciliationEntryRepository;
    this.bankReconciliationService = bankReconciliationService;
    this.auditService = auditService;
    this.bankReconciliationResetEnabled = bankReconciliationResetEnabled;
  }

  @GetMapping("/bank-reconciliations")
  public List<BankReconciliationEntry> getBankReconciliations(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return bankReconciliationEntryRepository.findAllByOrderByBookedAtDesc();
  }

  @GetMapping("/bank-reconciliations/report")
  public BankReconciliationReport getBankReconciliationReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return bankReconciliationService.createReport(from, to);
  }

  @GetMapping("/bank-reconciliations/report/export")
  public ResponseEntity<byte[]> exportBankReconciliationReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    authHeader.requireValidToken(authorizationHeader);
    BankReconciliationReport report = bankReconciliationService.createReport(from, to);

    StringBuilder csv = new StringBuilder();
    csv.append("AliBooks bankavstamning 1930\n");
    csv.append("Period,").append(escape((from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString()))).append("\n");
    csv.append("Bokforingsrader 1930,").append(report.journalEntryCount()).append("\n");
    csv.append("Bankrader,").append(report.bankRowCount()).append("\n");
    csv.append("Bokade bankrader,").append(report.bookedBankRowCount()).append("\n");
    csv.append("Hoppade bankrader,").append(report.skippedBankRowCount()).append("\n");
    csv.append("Bokfort saldo/movement 1930,").append(report.ledgerMovement()).append("\n");
    csv.append("Avstamd bankmovement,").append(report.reconciledMovement()).append("\n");
    csv.append("Differens,").append(report.difference()).append("\n");
    csv.append("Kritiska,").append(report.criticalIssueCount()).append("\n");
    csv.append("Varningar,").append(report.warningIssueCount()).append("\n");
    csv.append("\n");
    csv.append("Status,Typ,Datum,Referens,Belopp,Meddelande\n");
    for (BankReconciliationIssue issue : report.issues()) {
      csv.append(escape(issue.severity())).append(",");
      csv.append(escape(issue.issueType())).append(",");
      csv.append(escape(issue.date() == null ? "" : issue.date().toString())).append(",");
      csv.append(escape(issue.reference())).append(",");
      csv.append(issue.amount()).append(",");
      csv.append(escape(issue.message())).append("\n");
    }

    auditService.record(
        "export",
        "bank_reconciliation",
        periodLabel(from, to),
        "bank_reconciliation_exported",
        periodLabel(from, to),
        "Bank reconciliation exported. Difference: " + report.difference()
            + ". Critical: " + report.criticalIssueCount()
            + ". Warnings: " + report.warningIssueCount() + ".",
        report.issues().size(),
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bankavstamning-1930.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @PostMapping("/bank-reconciliations")
  @ResponseStatus(HttpStatus.CREATED)
  public BankReconciliationEntry createBankReconciliation(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateBankReconciliationEntryRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank reconciliation entry is required.");
    }

    if (request.amount() == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required.");
    }

    BankReconciliationEntry entry = bankReconciliationEntryRepository.save(new BankReconciliationEntry(request));
    auditService.record(
        "bank",
        "bank_reconciliation_entry",
        entry.getId(),
        "bank_reconciliation_entry_created",
        entry.getBankRowId().isBlank() ? entry.getReference() : entry.getBankRowId(),
        "Bank reconciliation entry created.",
        entry.getAmount(),
        authorizationHeader
    );
    return entry;
  }

  @DeleteMapping("/bank-reconciliations")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearBankReconciliations(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestHeader(value = "X-AliBooks-Confirm-Bank-Reconciliation-Reset", required = false) String confirmation
  ) {
    authHeader.requireValidToken(authorizationHeader);
    if (!bankReconciliationResetEnabled) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Bank reconciliation reset is disabled. Set APP_BANK_RECONCILIATION_RESET_ENABLED=true only in a local test environment."
      );
    }
    if (!"DELETE_ALIBOOKS_BANK_RECONCILIATION_HISTORY".equals(confirmation)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Missing bank reconciliation reset confirmation header."
      );
    }
    long count = bankReconciliationEntryRepository.count();
    bankReconciliationEntryRepository.deleteAll();
    auditService.record(
        "bank",
        "bank_reconciliation",
        "all",
        "bank_reconciliations_cleared",
        "all",
        "Bank reconciliation entries cleared.",
        safeAmount(count),
        authorizationHeader
    );
  }

  @DeleteMapping("/bank-reconciliations/skipped/{bankRowId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void removeSkippedBankReconciliation(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String bankRowId
  ) {
    authHeader.requireValidToken(authorizationHeader);
    long deletedRows = bankReconciliationEntryRepository.deleteByBankRowIdAndStatus(bankRowId, "skipped");
    auditService.record(
        "bank",
        "bank_reconciliation_entry",
        bankRowId,
        "skipped_bank_reconciliation_removed",
        bankRowId,
        "Skipped bank reconciliation entry removed.",
        safeAmount(deletedRows),
        authorizationHeader
    );
  }

  private String escape(String value) {
    return CsvEscaper.escape(value);
  }

  private String periodLabel(LocalDate from, LocalDate to) {
    return (from == null ? "" : from.toString()) + " - " + (to == null ? "" : to.toString());
  }

  private int safeAmount(long amount) {
    if (amount > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }

    if (amount < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }

    return (int) amount;
  }
}
