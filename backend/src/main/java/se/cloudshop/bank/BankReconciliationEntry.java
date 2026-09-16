package se.cloudshop.bank;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ForeignKey;
import com.fasterxml.jackson.annotation.JsonIgnore;
import se.cloudshop.accounting.JournalEntry;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bank_reconciliation_entries")
public class BankReconciliationEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String bankRowId;
  private LocalDate bankDate;
  private String description;
  private String reference;
  private int amount;
  private String entryType;
  private String status;
  private String matchLabel;
  private Instant bookedAt;

  @JsonIgnore
  @OneToOne
  @JoinColumn(name = "journal_entry_id", unique = true,
      foreignKey = @ForeignKey(name = "fk_bank_reconciliation_journal"))
  private JournalEntry journalEntry;

  public Long getJournalEntryId() {
    return journalEntry == null ? null : journalEntry.getId();
  }

  public void linkJournalEntry(JournalEntry entry) {
    if (journalEntry != null || !"booked".equals(status) || entry == null || entry.getId() == null
        || !"1930".equals(entry.getAccountNumber()) || bankDate == null
        || !bankDate.equals(entry.getVoucherDate()) || amount == 0
        || (long) entry.getDebit() - entry.getCredit() != amount) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Bank row must match one unlinked journal entry on 1930 with the same date and signed amount.");
    }
    journalEntry = entry;
  }

  public BankReconciliationEntry() {
  }

  public BankReconciliationEntry(CreateBankReconciliationEntryRequest request) {
    this.bankRowId = clean(request.bankRowId());
    this.bankDate = request.date() == null ? LocalDate.now() : request.date();
    this.description = clean(request.description());
    this.reference = clean(request.reference());
    this.amount = request.amount();
    this.entryType = clean(request.type());
    this.status = clean(request.status()).isBlank() ? "booked" : clean(request.status());
    this.matchLabel = clean(request.matchLabel());
    this.bookedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getBankRowId() {
    return bankRowId;
  }

  public LocalDate getDate() {
    return bankDate;
  }

  public LocalDate getBankDate() {
    return bankDate;
  }

  public String getDescription() {
    return description;
  }

  public String getReference() {
    return reference;
  }

  public int getAmount() {
    return amount;
  }

  public String getType() {
    return entryType;
  }

  public String getEntryType() {
    return entryType;
  }

  public String getStatus() {
    return status;
  }

  public String getMatchLabel() {
    return matchLabel;
  }

  public Instant getBookedAt() {
    return bookedAt;
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
