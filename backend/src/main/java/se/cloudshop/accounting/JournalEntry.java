package se.cloudshop.accounting;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import se.cloudshop.order.Order;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  private Order invoice;

  private String accountNumber;
  private String accountName;
  private String voucherNumber;
  private int debit;
  private int credit;
  private String description;
  private LocalDate voucherDate;
  private String correctionOfVoucherNumber;
  private Instant createdAt;

  public JournalEntry() {
  }

  public JournalEntry(Order invoice, Account account, String voucherNumber, int debit, int credit, String description) {
    this(invoice, account, voucherNumber, debit, credit, description, LocalDate.now());
  }

  public JournalEntry(Order invoice, Account account, String voucherNumber, int debit, int credit, String description, LocalDate voucherDate) {
    this.invoice = invoice;
    this.accountNumber = account.getNumber();
    this.accountName = account.getName();
    this.voucherNumber = voucherNumber;
    this.debit = debit;
    this.credit = credit;
    this.description = description;
    this.voucherDate = voucherDate == null ? LocalDate.now() : voucherDate;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Order getInvoice() {
    return invoice;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getAccountName() {
    return accountName;
  }

  public String getVoucherNumber() {
    return voucherNumber;
  }

  public int getDebit() {
    return debit;
  }

  public int getCredit() {
    return credit;
  }

  public String getDescription() {
    return description;
  }

  public LocalDate getVoucherDate() {
    return voucherDate;
  }

  public String getCorrectionOfVoucherNumber() {
    return correctionOfVoucherNumber;
  }

  public void setCorrectionOfVoucherNumber(String correctionOfVoucherNumber) {
    this.correctionOfVoucherNumber = correctionOfVoucherNumber;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
