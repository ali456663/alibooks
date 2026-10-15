package se.cloudshop.accounting;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JournalMinorReport(
    LocalDate periodFrom, LocalDate periodTo, String currencyCode, int minorUnitExponent,
    List<Entry> entries, long debitTotalMinor, long creditTotalMinor, long differenceMinor,
    List<VoucherDifference> voucherDifferences
) {
  public JournalMinorReport {
    entries = List.copyOf(entries);
    voucherDifferences = List.copyOf(voucherDifferences);
  }

  public record VoucherDifference(String voucherNumber, long differenceMinor) {
  }

  public record Entry(
      String journalEntryId, LocalDate voucherDate, String voucherNumber,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
      String accountNumber, String accountName, String description,
      String correctionOfVoucherNumber, Long voucherEvidenceId,
      long debitMinor, long creditMinor
  ) {
  }
}
