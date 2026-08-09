package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import se.cloudshop.expense.Expense;
import se.cloudshop.order.Order;
import se.cloudshop.product.Product;

class JournalEntryTraceabilityTest {

  @Test
  void derivesInvoiceSourceFromVoucherAndInvoiceNumber() {
    Order invoice = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now());
    invoice.setInvoiceNumber("F-2026-0042");

    JournalEntry entry = new JournalEntry(
        invoice,
        new Account("1510", "Kundfordringar"),
        "F-42",
        1250,
        0,
        "Invoice created",
        LocalDate.of(2026, 7, 1)
    );

    assertThat(entry.getVoucherSeries()).isEqualTo("F");
    assertThat(entry.getVoucherSequenceNumber()).isEqualTo(42);
    assertThat(entry.getSourceType()).isEqualTo("invoice");
    assertThat(entry.getSourceReference()).isEqualTo("F-2026-0042");
    assertThat(entry.getEvidenceStatus()).isEqualTo("traceable");
    assertThat(entry.getIntegrityHash()).hasSize(64).matches("[0-9A-F]+");
    assertThat(entry.getIntegrityHash()).isEqualTo(entry.getIntegrityHash());
  }

  @Test
  void flagsExpenseWithoutReceiptAsMissingEvidence() {
    Expense expense = new Expense(LocalDate.of(2026, 7, 2), "Programvara", 400, 100, "5420", "1930");

    JournalEntry entry = new JournalEntry(
        null,
        expense,
        new Account("5420", "Programvaror"),
        "K-7",
        400,
        0,
        "Programvara",
        expense.getExpenseDate()
    );

    assertThat(entry.getSourceType()).isEqualTo("expense");
    assertThat(entry.getSourceReference()).isEqualTo("Programvara");
    assertThat(entry.getEvidenceStatus()).isEqualTo("missing_receipt");
    assertThat(entry.getEvidenceHash()).isEmpty();
    assertThat(entry.getIntegrityHash()).hasSize(64).matches("[0-9A-F]+");
  }

  @Test
  void bindsExpenseReceiptHashIntoIntegrityHash() {
    Expense expense = new Expense(LocalDate.of(2026, 7, 2), "Programvara", 400, 100, "5420", "1930");

    JournalEntry entry = new JournalEntry(
        null,
        expense,
        new Account("5420", "Programvaror"),
        "K-7",
        400,
        0,
        "Programvara",
        expense.getExpenseDate()
    );

    expense.setReceipt("kvitto.pdf", "application/pdf", "receipts/kvitto.pdf");
    String hashWithoutReceiptFingerprint = entry.getIntegrityHash();

    expense.setReceipt("kvitto.pdf", "application/pdf", "receipts/kvitto.pdf", "ABC123", Instant.parse("2026-07-02T10:00:00Z"));

    assertThat(entry.getEvidenceStatus()).isEqualTo("traceable");
    assertThat(entry.getEvidenceHash()).isEqualTo("ABC123");
    assertThat(entry.getIntegrityHash()).isNotEqualTo(hashWithoutReceiptFingerprint);
  }

  @Test
  void derivesCorrectionSourceFromCorrectionLink() {
    JournalEntry entry = new JournalEntry(
        null,
        new Account("3041", "Forsaljning"),
        "R-3",
        1000,
        0,
        "Correction of F-2",
        LocalDate.of(2026, 7, 3)
    );
    entry.setCorrectionOfVoucherNumber("F-2");

    assertThat(entry.getSourceType()).isEqualTo("correction");
    assertThat(entry.getSourceReference()).isEqualTo("F-2");
    assertThat(entry.getEvidenceStatus()).isEqualTo("traceable");
    assertThat(entry.getIntegrityHash()).hasSize(64).matches("[0-9A-F]+");
  }
}
