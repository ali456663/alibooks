package se.cloudshop.bank;

import static se.cloudshop.accounting.ReportAmounts.reportAmount;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.accounting.JournalEntryRepository;

@Service
public class BankReconciliationService {

  private static final String BANK_ACCOUNT_NUMBER = "1930";

  private final BankReconciliationEntryRepository bankReconciliationEntryRepository;
  private final JournalEntryRepository journalEntryRepository;

  public BankReconciliationService(
      BankReconciliationEntryRepository bankReconciliationEntryRepository,
      JournalEntryRepository journalEntryRepository
  ) {
    this.bankReconciliationEntryRepository = bankReconciliationEntryRepository;
    this.journalEntryRepository = journalEntryRepository;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public BankReconciliationReport createReport(LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);

    List<JournalEntry> allJournalEntries = journalEntryRepository.findAll();
    List<JournalEntry> bankJournalEntries = allJournalEntries
        .stream()
        .filter(entry -> BANK_ACCOUNT_NUMBER.equals(entry.getAccountNumber()))
        .filter(entry -> isWithinPeriod(entry.getVoucherDate(), periodFrom, periodTo))
        .sorted(Comparator
            .comparing(JournalEntry::getVoucherDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(entry -> entry.getVoucherNumber() == null ? "" : entry.getVoucherNumber()))
        .toList();

    List<BankReconciliationEntry> allBankRows = bankReconciliationEntryRepository.findAll();
    List<BankReconciliationEntry> bankRows = allBankRows
        .stream()
        .filter(entry -> isWithinPeriod(entry.getBankDate(), periodFrom, periodTo))
        .toList();
    List<BankReconciliationEntry> bookedRows = bankRows.stream()
        .filter(entry -> "booked".equalsIgnoreCase(entry.getStatus()))
        .toList();
    List<BankReconciliationEntry> skippedRows = bankRows.stream()
        .filter(entry -> "skipped".equalsIgnoreCase(entry.getStatus()))
        .toList();

    int ledgerMovement = reportAmount(bankJournalEntries.stream()
        .mapToLong(entry -> (long) entry.getDebit() - entry.getCredit())
        .sum());
    int reconciledMovement = reportAmount(bookedRows.stream()
        .mapToLong(BankReconciliationEntry::getAmount)
        .sum());
    int difference = reportAmount((long) ledgerMovement - reconciledMovement);

    List<BankReconciliationIssue> issues = new ArrayList<>();
    checkJournalLinks(allJournalEntries, allBankRows, bankJournalEntries, bankRows, issues);
    if (bankJournalEntries.isEmpty() && !bookedRows.isEmpty()) {
      issues.add(new BankReconciliationIssue(
          "critical",
          "bank_rows_without_bookkeeping",
          null,
          "",
          reconciledMovement,
          "Bank rows exist for the period, but no journal entries were found on account 1930."
      ));
    }

    if (!bankJournalEntries.isEmpty() && bookedRows.isEmpty()) {
      issues.add(new BankReconciliationIssue(
          "warning",
          "bookkeeping_without_bank_rows",
          null,
          "",
          ledgerMovement,
          "Journal entries exist on account 1930, but no booked bank reconciliation rows were found for the period."
      ));
    }

    if (difference != 0) {
      issues.add(new BankReconciliationIssue(
          "critical",
          "bank_reconciliation_difference",
          null,
          "",
          difference,
          "Booked movement on account 1930 differs from booked bank rows by " + difference + " SEK."
      ));
    }

    skippedRows.forEach(entry -> issues.add(new BankReconciliationIssue(
        "warning",
        "skipped_bank_row",
        entry.getBankDate(),
        entry.getReference(),
        entry.getAmount(),
        "A bank row was skipped and should be reviewed before period close."
    )));

    bookedRows.stream()
        .filter(entry -> entry.getReference() == null || entry.getReference().isBlank())
        .forEach(entry -> issues.add(new BankReconciliationIssue(
            "warning",
            "missing_bank_reference",
            entry.getBankDate(),
            "",
            entry.getAmount(),
            "A booked bank row is missing reference or transaction id."
        )));

    int criticalIssueCount = (int) issues.stream()
        .filter(issue -> "critical".equals(issue.severity()))
        .count();
    int warningIssueCount = (int) issues.stream()
        .filter(issue -> "warning".equals(issue.severity()))
        .count();

    return new BankReconciliationReport(
        periodFrom,
        periodTo,
        bankJournalEntries.size(),
        bankRows.size(),
        bookedRows.size(),
        skippedRows.size(),
        ledgerMovement,
        reconciledMovement,
        difference,
        criticalIssueCount,
        warningIssueCount,
        issues
    );
  }

  private void validatePeriod(LocalDate periodFrom, LocalDate periodTo) {
    if (periodFrom != null && periodTo != null && periodFrom.isAfter(periodTo)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Period from must be before or equal to period to.");
    }
  }

  private void checkJournalLinks(List<JournalEntry> allJournal, List<BankReconciliationEntry> allRows,
      List<JournalEntry> periodJournal, List<BankReconciliationEntry> periodRows,
      List<BankReconciliationIssue> issues) {
    Map<Long, JournalEntry> journalById = allJournal.stream().filter(entry -> entry.getId() != null)
        .collect(Collectors.toMap(JournalEntry::getId, Function.identity()));
    Map<Long, Long> linkCounts = allRows.stream().filter(row -> row.getJournalEntryId() != null)
        .collect(Collectors.groupingBy(BankReconciliationEntry::getJournalEntryId, Collectors.counting()));
    Map<String, Long> bankIdCounts = allRows.stream().filter(row -> row.getBankRowId() != null)
        .collect(Collectors.groupingBy(BankReconciliationEntry::getBankRowId, Collectors.counting()));
    Set<Long> matched = new HashSet<>();
    for (BankReconciliationEntry row : periodRows) {
      if (row.getBankDate() == null || row.getBankRowId() == null || row.getBankRowId().isBlank()
          || bankIdCounts.getOrDefault(row.getBankRowId(), 0L) > 1) {
        issues.add(new BankReconciliationIssue("critical", "invalid_bank_identity_or_date", row.getBankDate(), row.getBankRowId(),
            row.getAmount(), "Bank row has an absent date or missing/duplicated identity."));
        continue;
      }
      if ("skipped".equalsIgnoreCase(row.getStatus()) && row.getJournalEntryId() == null) continue;
      JournalEntry journal = journalById.get(row.getJournalEntryId());
      String problem = null;
      if (!"booked".equalsIgnoreCase(row.getStatus())) problem = "invalid_bank_row_status";
      else if (row.getJournalEntryId() == null) problem = "unlinked_bank_row";
      else if (journal == null) problem = "missing_linked_journal_entry";
      else if (linkCounts.get(row.getJournalEntryId()) > 1) problem = "duplicate_journal_link";
      else if (!BANK_ACCOUNT_NUMBER.equals(journal.getAccountNumber()) || row.getBankDate() == null
          || !row.getBankDate().equals(journal.getVoucherDate()) || row.getAmount() == 0
          || (long) journal.getDebit() - journal.getCredit() != row.getAmount()) problem = "bank_journal_link_mismatch";
      if (problem == null) matched.add(journal.getId());
      else issues.add(new BankReconciliationIssue("critical", problem, row.getBankDate(), row.getBankRowId(),
          row.getAmount(), "Bank row " + row.getBankRowId() + " requires review: " + problem + "."));
    }
    for (JournalEntry journal : periodJournal) {
      if (journal.getId() == null || !matched.contains(journal.getId())) {
        issues.add(new BankReconciliationIssue("critical", "unmatched_journal_entry", journal.getVoucherDate(),
            journal.getVoucherNumber(), reportAmount((long) journal.getDebit() - journal.getCredit()),
            "Journal row " + journal.getId() + " on account 1930 has no verified bank row link."));
      }
    }
  }

  private boolean isWithinPeriod(LocalDate date, LocalDate periodFrom, LocalDate periodTo) {
    if (date == null) {
      // Undated records cannot safely be assigned to, or excluded from, a closing period.
      return true;
    }

    if (periodFrom != null && date.isBefore(periodFrom)) {
      return false;
    }

    return periodTo == null || !date.isAfter(periodTo);
  }
}
