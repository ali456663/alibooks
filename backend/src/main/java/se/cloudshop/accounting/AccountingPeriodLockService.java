package se.cloudshop.accounting;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.bank.BankReconciliationReport;
import se.cloudshop.bank.BankReconciliationService;
import se.cloudshop.expense.Expense;
import se.cloudshop.expense.ExpenseRepository;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;
import se.cloudshop.order.ReceivablesAgingReport;
import se.cloudshop.order.ReceivablesReportService;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;
import se.cloudshop.supplier.PayablesAgingReport;
import se.cloudshop.supplier.PayablesReportService;

@Service
public class AccountingPeriodLockService {

  private final JournalEntryRepository journalEntryRepository;
  private final OrderRepository orderRepository;
  private final ExpenseRepository expenseRepository;
  private final SettingsService settingsService;
  private final AuditService auditService;
  private final AccountingService accountingService;
  private final BankReconciliationService bankReconciliationService;
  private final ReceivablesReportService receivablesReportService;
  private final PayablesReportService payablesReportService;
  private final VoucherApprovalRepository voucherApprovalRepository;
  private final SubledgerControlService subledgerControlService;

  public AccountingPeriodLockService(
      JournalEntryRepository journalEntryRepository,
      OrderRepository orderRepository,
      ExpenseRepository expenseRepository,
      SettingsService settingsService,
      AuditService auditService,
      AccountingService accountingService,
      BankReconciliationService bankReconciliationService,
      ReceivablesReportService receivablesReportService,
      PayablesReportService payablesReportService,
      VoucherApprovalRepository voucherApprovalRepository,
      SubledgerControlService subledgerControlService
  ) {
    this.journalEntryRepository = journalEntryRepository;
    this.orderRepository = orderRepository;
    this.expenseRepository = expenseRepository;
    this.settingsService = settingsService;
    this.auditService = auditService;
    this.accountingService = accountingService;
    this.bankReconciliationService = bankReconciliationService;
    this.receivablesReportService = receivablesReportService;
    this.payablesReportService = payablesReportService;
    this.voucherApprovalRepository = voucherApprovalRepository;
    this.subledgerControlService = subledgerControlService;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PeriodCloseCheckResult checkPeriod(LocalDate lockedThroughDate) {
    LocalDate date = requireValidLockDate(lockedThroughDate);

    List<JournalEntry> entriesInPeriod = journalEntryRepository.findAll().stream()
        .filter(entry -> entry.getVoucherDate() != null && !entry.getVoucherDate().isAfter(date))
        .toList();

    Map<String, List<JournalEntry>> voucherGroups = entriesInPeriod.stream()
        .collect(Collectors.groupingBy(entry -> entry.getVoucherNumber() == null ? "" : entry.getVoucherNumber()));
    Map<String, String> approvalStatuses = voucherApprovalRepository.findAll().stream()
        .filter(approval -> approval.getVoucherNumber() != null && !approval.getVoucherNumber().isBlank())
        .collect(Collectors.toMap(
            VoucherApproval::getVoucherNumber,
            approval -> approval.getStatus() == null ? "" : approval.getStatus(),
            (first, second) -> second
        ));
    List<String> periodVoucherNumbers = voucherGroups.keySet().stream()
        .filter(voucherNumber -> voucherNumber != null && !voucherNumber.isBlank())
        .toList();
    int voucherApprovedCount = Math.toIntExact(periodVoucherNumbers.stream()
        .filter(voucherNumber -> "approved".equals(approvalStatuses.get(voucherNumber)))
        .count());
    int voucherMissingApprovalCount = Math.toIntExact(periodVoucherNumbers.stream()
        .filter(voucherNumber -> !approvalStatuses.containsKey(voucherNumber))
        .count());
    int voucherPendingApprovalCount = Math.toIntExact(periodVoucherNumbers.stream()
        .filter(voucherNumber -> "pending".equals(approvalStatuses.get(voucherNumber)))
        .count());
    int voucherBlockedApprovalCount = Math.toIntExact(periodVoucherNumbers.stream()
        .filter(voucherNumber -> "blocked".equals(approvalStatuses.get(voucherNumber)))
        .count());

    long unbalancedVoucherCount = voucherGroups.values().stream()
        .filter(group -> {
          long debit = group.stream().mapToLong(JournalEntry::getDebitMinorValue).reduce(0L, Math::addExact);
          long credit = group.stream().mapToLong(JournalEntry::getCreditMinorValue).reduce(0L, Math::addExact);
          return debit != credit;
        })
        .count();

    long draftInvoiceCount = orderRepository.findAll().stream()
        .filter(invoice -> invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isAfter(date))
        .filter(invoice -> "DRAFT".equals(invoice.getStatus()))
        .count();

    long expensesMissingReceiptCount = expenseRepository.findAll().stream()
        .filter(expense -> expense.getExpenseDate() != null && !expense.getExpenseDate().isAfter(date))
        .filter(expense -> !expense.hasReceipt())
        .count();
    LateBookingStats lateBookingStats = calculateLateBookingStats(voucherGroups);

    BalanceReport balanceReport = accountingService.createBalanceReport(date);
    TrialBalanceReport trialBalanceReport = accountingService.createTrialBalanceReport(null, date);
    VoucherControlReport voucherControlReport = accountingService.createVoucherControlReport(null, date);
    VatControlReport vatControlReport = accountingService.createVatControlReport(null, date);
    JournalIntegrityReport journalIntegrityReport = accountingService.createJournalIntegrityReport(null, date);
    BankReconciliationReport bankReconciliationReport = bankReconciliationService.createReport(null, date);
    ReceivablesAgingReport receivablesReport = receivablesReportService.createAgingReport(date);
    PayablesAgingReport payablesReport = payablesReportService.createAgingReport(date);
    List<VatFilingProofReport> vatFilingProofReports = accountingService.createVatFilingProofReportsThroughDate(date);
    int vatProofIncompleteCount = Math.toIntExact(vatFilingProofReports.stream()
        .filter(report -> !report.completeForCurrentStatus())
        .count());
    int vatProofSettlementMissingCount = Math.toIntExact(vatFilingProofReports.stream()
        .filter(report -> !report.settlementVoucherFound())
        .count());
    int vatProofPaymentMissingCount = Math.toIntExact(vatFilingProofReports.stream()
        .filter(report -> report.paymentVoucherRequired() && !report.paymentVoucherFound())
        .count());
    AccountSignControlReport accountSignControlReport = accountingService.createAccountSignControlReport(null, date);
    List<String> accountSignBlockers = accountSignControlReport.lines().stream()
        .filter(line -> "critical".equals(line.status()))
        .map(AccountSignControlLine::message)
        .toList();
    List<String> accountSignWarnings = accountSignControlReport.lines().stream()
        .filter(line -> "warning".equals(line.status()))
        .map(AccountSignControlLine::message)
        .toList();

    List<String> blockers = new ArrayList<>();
    if (unbalancedVoucherCount > 0) {
      blockers.add(unbalancedVoucherCount + " obalanserade verifikat finns i perioden.");
    }
    if (balanceReport.difference() != 0) {
      blockers.add("Balansrapporten har differens " + balanceReport.difference() + " SEK.");
    }
    if (trialBalanceReport.difference() != 0) {
      blockers.add("Saldobalansen har differens " + trialBalanceReport.difference() + " SEK.");
    }
    if (voucherControlReport.criticalIssueCount() > 0) {
      blockers.add(voucherControlReport.criticalIssueCount() + " kritiska verifikationspunkter finns i perioden.");
    }
    if (vatControlReport.criticalIssueCount() > 0) {
      blockers.add(vatControlReport.criticalIssueCount() + " kritiska momspunkter finns i perioden.");
    }
    if (vatProofIncompleteCount > 0) {
      blockers.add(vatProofIncompleteCount + " momsdeklarerade perioder saknar komplett beviskedja.");
    }
    if (journalIntegrityReport.difference() != 0) {
      blockers.add("Bokforingskedjans integritetskontroll har differens " + journalIntegrityReport.difference() + " SEK.");
    }
    if (journalIntegrityReport.missingEvidenceCount() > 0) {
      blockers.add(journalIntegrityReport.missingEvidenceCount() + " bokforingsrader saknar tydlig kallkoppling i integritetskedjan.");
    }
    if (bankReconciliationReport.criticalIssueCount() > 0) {
      blockers.add(bankReconciliationReport.criticalIssueCount() + " kritiska bankavstamningspunkter finns i perioden. Differens " + bankReconciliationReport.difference() + " SEK.");
    }
    if (draftInvoiceCount > 0) {
      blockers.add(draftInvoiceCount + " fakturautkast finns i perioden. Skicka, kreditera eller ta bort utkast innan lasning.");
    }
    if (expensesMissingReceiptCount > 0) {
      blockers.add(expensesMissingReceiptCount + " kostnader saknar kvitto/underlag i perioden.");
    }
    if (voucherMissingApprovalCount > 0) {
      blockers.add(voucherMissingApprovalCount + " verifikat saknar attest i perioden.");
    }
    if (voucherPendingApprovalCount > 0) {
      blockers.add(voucherPendingApprovalCount + " verifikat vantar pa attest i perioden.");
    }
    if (voucherBlockedApprovalCount > 0) {
      blockers.add(voucherBlockedApprovalCount + " verifikat ar blockerade i attestkontrollen.");
    }
    blockers.addAll(accountSignBlockers);
    SubledgerControlReport subledgerControl = subledgerControlService.createReport(date);
    if ("REVIEW_REQUIRED".equals(subledgerControl.status())) {
      blockers.add("Reskontra och huvudbok kravs avstamda per faktura. Differenser eller saknade kallkopplingar finns pa konto 1510/2440.");
    } else if (!List.of("MATCHED", "NO_DATA").contains(subledgerControl.status())) {
      blockers.add("Automatisk reskontraavstamning stodjer inte vald bokforingsmetod. Kontrollerat bokslutsflode kravs innan lasning.");
    }

    List<String> warnings = new ArrayList<>();
    if (voucherControlReport.warningIssueCount() > 0) {
      warnings.add(voucherControlReport.warningIssueCount() + " verifikationsvarningar finns i perioden.");
    }
    if (vatControlReport.warningIssueCount() > 0) {
      warnings.add(vatControlReport.warningIssueCount() + " momsvarningar finns i perioden.");
    }
    boolean sieExportReady = !entriesInPeriod.isEmpty() && unbalancedVoucherCount == 0 && voucherControlReport.criticalIssueCount() == 0;
    if (!sieExportReady) {
      warnings.add("SIE-export ar inte redo for vald period.");
    }
    if (bankReconciliationReport.warningIssueCount() > 0) {
      warnings.add(bankReconciliationReport.warningIssueCount() + " bankavstamningsvarningar finns i perioden.");
    }
    if (receivablesReport.totalOutstanding() > 0) {
      warnings.add(receivablesReport.invoiceCount() + " oppna kundfakturor finns vid periodlasning, totalt " + receivablesReport.totalOutstanding() + " SEK.");
    }
    if (receivablesReport.overdueOutstanding() > 0) {
      warnings.add("Forfallna kundfordringar finns vid periodlasning, totalt " + receivablesReport.overdueOutstanding() + " SEK.");
    }
    if (payablesReport.totalOutstanding() > 0) {
      warnings.add(payablesReport.invoiceCount() + " oppna leverantorsfakturor finns vid periodlasning, totalt " + payablesReport.totalOutstanding() + " SEK.");
    }
    if (payablesReport.overdueOutstanding() > 0) {
      warnings.add("Forfallna leverantorsskulder finns vid periodlasning, totalt " + payablesReport.overdueOutstanding() + " SEK.");
    }
    if (lateBookingStats.lateVoucherCount() > 0) {
      warnings.add(lateBookingStats.lateVoucherCount() + " verifikat verkar vara bokforda mer an 35 dagar efter verifikationsdatum. Langsta efterslapning ar " + lateBookingStats.longestLagDays() + " dagar.");
    }
    warnings.addAll(accountSignWarnings);

    AppSettings settings = settingsService.getSettings();
    boolean locked = settings.getAccountingLockedThroughDate() != null
        && !settings.getAccountingLockedThroughDate().isBefore(date);

    return new PeriodCloseCheckResult(
        date,
        locked,
        blockers,
        warnings,
        voucherGroups.size(),
        Math.toIntExact(unbalancedVoucherCount),
        Math.toIntExact(draftInvoiceCount),
        Math.toIntExact(expensesMissingReceiptCount),
        lateBookingStats.lateVoucherCount(),
        lateBookingStats.longestLagDays(),
        lateBookingStats.vouchers(),
        balanceReport.difference(),
        trialBalanceReport.difference(),
        voucherControlReport.criticalIssueCount(),
        voucherControlReport.warningIssueCount(),
        vatControlReport.criticalIssueCount(),
        vatControlReport.warningIssueCount(),
        vatProofIncompleteCount,
        vatProofSettlementMissingCount,
        vatProofPaymentMissingCount,
        journalIntegrityReport.difference(),
        journalIntegrityReport.missingEvidenceCount(),
        bankReconciliationReport.difference(),
        bankReconciliationReport.criticalIssueCount(),
        bankReconciliationReport.warningIssueCount(),
        receivablesReport.invoiceCount(),
        receivablesReport.totalOutstanding(),
        receivablesReport.overdueOutstanding(),
        receivablesReport.dueSoonOutstanding(),
        payablesReport.invoiceCount(),
        payablesReport.totalOutstanding(),
        payablesReport.overdueOutstanding(),
        payablesReport.dueSoonOutstanding(),
        voucherApprovedCount,
        voucherMissingApprovalCount,
        voucherPendingApprovalCount,
        voucherBlockedApprovalCount,
        journalIntegrityReport.periodFingerprint(),
        journalIntegrityReport.finalChainHash(),
        sieExportReady,
        blockers.isEmpty()
    );
  }

  @Transactional
  public PeriodCloseCheckResult closePeriod(LocalDate lockedThroughDate, String authorizationHeader) {
    requireValidLockDate(lockedThroughDate);
    settingsService.lockSettingsForAccounting();
    PeriodCloseCheckResult result = checkPeriod(lockedThroughDate);

    if (result.locked()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perioden ar redan last till detta datum eller senare.");
    }

    if (!result.blockers().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", result.blockers()));
    }

    settingsService.lockAccountingThroughDate(result.lockedThroughDate());
    auditService.record(
        "period",
        "settings",
        result.lockedThroughDate(),
        "period_locked",
        String.valueOf(result.lockedThroughDate()),
        "Accounting period locked. Period fingerprint: " + result.periodFingerprint() + ". Final chain hash: " + result.finalChainHash() + ".",
        result.voucherCount(),
        authorizationHeader
    );

    return new PeriodCloseCheckResult(
        result.lockedThroughDate(),
        true,
        result.blockers(),
        result.warnings(),
        result.voucherCount(),
        result.unbalancedVoucherCount(),
        result.draftInvoiceCount(),
        result.expensesMissingReceiptCount(),
        result.lateBookedVoucherCount(),
        result.longestBookingLagDays(),
        result.lateBookedVouchers(),
        result.balanceDifference(),
        result.trialBalanceDifference(),
        result.voucherCriticalIssueCount(),
        result.voucherWarningIssueCount(),
        result.vatCriticalIssueCount(),
        result.vatWarningIssueCount(),
        result.vatProofIncompleteCount(),
        result.vatProofSettlementMissingCount(),
        result.vatProofPaymentMissingCount(),
        result.journalIntegrityDifference(),
        result.journalIntegrityMissingEvidenceCount(),
        result.bankReconciliationDifference(),
        result.bankReconciliationCriticalIssueCount(),
        result.bankReconciliationWarningIssueCount(),
        result.receivablesInvoiceCount(),
        result.receivablesTotalOutstanding(),
        result.receivablesOverdueOutstanding(),
        result.receivablesDueSoonOutstanding(),
        result.payablesInvoiceCount(),
        result.payablesTotalOutstanding(),
        result.payablesOverdueOutstanding(),
        result.payablesDueSoonOutstanding(),
        result.voucherApprovedCount(),
        result.voucherMissingApprovalCount(),
        result.voucherPendingApprovalCount(),
        result.voucherBlockedApprovalCount(),
        result.periodFingerprint(),
        result.finalChainHash(),
        result.sieExportReady(),
        true
    );
  }

  private LocalDate requireValidLockDate(LocalDate lockedThroughDate) {
    if (lockedThroughDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Locked-through date is required.");
    }

    if (lockedThroughDate.isAfter(LocalDate.now())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perioden kan inte lasas till ett framtida datum.");
    }

    return lockedThroughDate;
  }

  private LateBookingStats calculateLateBookingStats(Map<String, List<JournalEntry>> voucherGroups) {
    List<LateBookedVoucher> lateVouchers = new ArrayList<>();
    int longestLagDays = 0;

    for (Map.Entry<String, List<JournalEntry>> voucherGroup : voucherGroups.entrySet()) {
      List<JournalEntry> entries = voucherGroup.getValue();
      LocalDate voucherDate = entries.stream()
          .map(JournalEntry::getVoucherDate)
          .filter(entryDate -> entryDate != null)
          .min(LocalDate::compareTo)
          .orElse(null);
      LocalDate createdDate = entries.stream()
          .map(JournalEntry::getCreatedAt)
          .filter(createdAt -> createdAt != null)
          .map(createdAt -> createdAt.atZone(ZoneOffset.UTC).toLocalDate())
          .max(LocalDate::compareTo)
          .orElse(null);

      if (voucherDate == null || createdDate == null) {
        continue;
      }

      int lagDays = Math.toIntExact(ChronoUnit.DAYS.between(voucherDate, createdDate));
      if (lagDays > 35) {
        longestLagDays = Math.max(longestLagDays, lagDays);
        int debit = reportWholeKrona(entries.stream()
            .mapToLong(JournalEntry::getDebitMinorValue)
            .reduce(0L, Math::addExact), "senbokförda verifikatens debet");
        int credit = reportWholeKrona(entries.stream()
            .mapToLong(JournalEntry::getCreditMinorValue)
            .reduce(0L, Math::addExact), "senbokförda verifikatens kredit");
        String description = entries.stream()
            .map(JournalEntry::getDescription)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("");
        lateVouchers.add(new LateBookedVoucher(
            voucherGroup.getKey(),
            voucherDate,
            createdDate,
            lagDays,
            debit,
            credit,
            description
        ));
      }
    }

    List<LateBookedVoucher> sortedVouchers = lateVouchers.stream()
        .sorted((first, second) -> Integer.compare(second.lagDays(), first.lagDays()))
        .toList();

    return new LateBookingStats(sortedVouchers.size(), longestLagDays, sortedVouchers);
  }

  private record LateBookingStats(int lateVoucherCount, int longestLagDays, List<LateBookedVoucher> vouchers) {
  }

  private int reportWholeKrona(long amountMinor, String field) {
    if (amountMinor % 100L != 0L) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Periodstangningen innehaller oren i " + field + ". Kontrollera underlaget innan perioden stangs.");
    }
    return ReportAmounts.reportAmount(amountMinor / 100L);
  }

}
