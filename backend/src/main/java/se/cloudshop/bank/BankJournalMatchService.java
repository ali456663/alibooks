package se.cloudshop.bank;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.accounting.JournalEntryRepository;
import se.cloudshop.audit.AuditService;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankJournalMatchService {
  private final BankReconciliationEntryRepository rows;
  private final JournalEntryRepository journal;
  private final AccountingService accounting;
  private final AuditService audit;

  public BankJournalMatchService(BankReconciliationEntryRepository rows, JournalEntryRepository journal,
      AccountingService accounting, AuditService audit) {
    this.rows = rows;
    this.journal = journal;
    this.accounting = accounting;
    this.audit = audit;
  }

  public record Candidate(Long id, String voucherNumber, LocalDate date, int amount, String description) {}

  @Transactional(readOnly = true)
  public List<Candidate> candidates(long rowId) {
    BankReconciliationEntry row = requireUnlinkedRow(rowId);
    int rowAmount = wholeKrona(row, "bankrad");
    Set<Long> used = rows.findAll().stream().map(BankReconciliationEntry::getJournalEntryId)
        .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    return journal.findAll().stream()
        .filter(entry -> "1930".equals(entry.getAccountNumber()) && row.getBankDate().equals(entry.getVoucherDate())
            && signedMinorMovement(entry) == bankAmountMinor(row) && !used.contains(entry.getId()))
        .map(entry -> new Candidate(entry.getId(), entry.getVoucherNumber(), entry.getVoucherDate(), rowAmount, entry.getDescription()))
        .toList();
  }

  @Transactional
  public BankReconciliationEntry match(long rowId, Long journalId, String authorization) {
    if (journalId == null || journalId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal entry id is required.");
    // Separate keys serialize competing manual links without creating any accounting entries.
    rows.lockBankRow("match-row:" + rowId);
    rows.lockBankRow("match-journal:" + journalId);
    BankReconciliationEntry row = requireUnlinkedRow(rowId);
    int auditAmount = wholeKrona(row, "bankrad");
    accounting.requireUnlockedAccountingDate(row.getBankDate());
    if (rows.findAll().stream().anyMatch(existing -> journalId.equals(existing.getJournalEntryId()))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Journal entry is already linked to a bank row.");
    }
    var entry = journal.findById(journalId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal entry not found."));
    row.linkJournalEntry(entry);
    rows.saveAndFlush(row);
    audit.record("bank", "bank_reconciliation_entry", row.getId(), "bank_journal_linked", row.getBankRowId(),
        "Bank row explicitly linked to journal row " + journalId + ", voucher " + entry.getVoucherNumber() + ".", auditAmount, authorization);
    return row;
  }

  private BankReconciliationEntry requireUnlinkedRow(long id) {
    var row = rows.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank row not found."));
    if (!"booked".equals(row.getStatus()) || row.getJournalEntryId() != null || row.getBankDate() == null || bankAmountMinor(row) == 0
        || row.getBankRowId() == null || row.getBankRowId().isBlank() || rows.findAllByBankRowId(row.getBankRowId()).size() != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a unique, dated, booked and unlinked bank row can be matched.");
    }
    return row;
  }

  private long bankAmountMinor(BankReconciliationEntry row) {
    Long shadow = row.getAmountMinor();
    return shadow == null ? Math.multiplyExact((long) row.getAmount(), 100L) : shadow;
  }

  private int wholeKrona(BankReconciliationEntry row, String field) {
    long amountMinor = bankAmountMinor(row);
    if (amountMinor % 100L != 0L) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Bankavstamningen innehaller oren i " + field + ". Matchningen har stoppats tills beloppsmigreringen ar verifierad.");
    }
    try {
      return Math.toIntExact(amountMinor / 100L);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Bankavstamningen innehaller ett belopp utanfor rapportens stod i " + field + ".", exception);
    }
  }

  private long signedMinorMovement(se.cloudshop.accounting.JournalEntry entry) {
    return Math.subtractExact(entry.getDebitMinorValue(), entry.getCreditMinorValue());
  }
}
