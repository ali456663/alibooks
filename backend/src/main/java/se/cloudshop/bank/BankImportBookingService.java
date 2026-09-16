package se.cloudshop.bank;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.audit.AuditService;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class BankImportBookingService {
  private final BankReconciliationEntryRepository rows;
  private final AccountingService accounting;
  private final AuditService audit;

  public BankImportBookingService(BankReconciliationEntryRepository rows, AccountingService accounting, AuditService audit) {
    this.rows = rows;
    this.accounting = accounting;
    this.audit = audit;
  }

  public void reserve(BankImportRow row, LocalDate date, long amount) {
    validate(row);
    if (!row.date().equals(date) || row.amount() != amount) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank date and full amount must match the booking.");
    }
    // Hold through accounting, history and audit commit. Colliding hashes only serialize extra work.
    rows.lockBankRow(row.bankRowId());
    if (rows.existsByBankRowId(row.bankRowId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Bank row already exists. Review persisted history before retrying.");
    }
  }

  public BankReconciliationEntry record(BankImportRow row, String type, String label, JournalEntry journalEntry, String authorization) {
    return save(row, type, "booked", label, journalEntry, authorization);
  }

  public BankReconciliationEntry skip(BankImportRow row, String authorization) {
    reserve(row, row == null ? null : row.date(), row == null ? 0 : row.amount());
    accounting.requireUnlockedAccountingDate(row.date());
    return save(row, row.amount() < 0 ? "outgoing" : "incoming", "skipped", "Skipped", null, authorization);
  }

  public long removeSkipped(String bankRowId) {
    rows.lockBankRow(bankRowId);
    for (BankReconciliationEntry row : rows.findAllByBankRowId(bankRowId)) {
      if ("skipped".equals(row.getStatus())) {
        if (row.getBankDate() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Undated legacy row requires review.");
        accounting.requireUnlockedAccountingDate(row.getBankDate());
      }
    }
    return rows.deleteByBankRowIdAndStatus(bankRowId, "skipped");
  }

  private BankReconciliationEntry save(BankImportRow row, String type, String status, String label, JournalEntry journalEntry, String authorization) {
    BankReconciliationEntry entry = new BankReconciliationEntry(new CreateBankReconciliationEntryRequest(
        row.bankRowId(), row.date(), row.description(), row.reference(), row.amount(), type, status, label));
    if ("booked".equals(status)) entry.linkJournalEntry(journalEntry);
    BankReconciliationEntry saved = rows.save(entry);
    audit.record("bank", "bank_reconciliation_entry", saved.getId(), "bank_reconciliation_entry_created",
        saved.getBankRowId(), "Bank reconciliation entry created.", saved.getAmount(), authorization);
    return saved;
  }

  private void validate(BankImportRow row) {
    if (row == null || row.date() == null || row.amount() == 0 || row.amount() == Integer.MIN_VALUE
        || row.bankRowId() == null || !row.bankRowId().matches("[A-Za-z0-9_-]{1,120}")
        || (row.description() != null && row.description().length() > 255)
        || (row.reference() != null && row.reference().length() > 255)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid bank row id, date, whole-SEK amount and bounded text are required.");
    }
  }
}
