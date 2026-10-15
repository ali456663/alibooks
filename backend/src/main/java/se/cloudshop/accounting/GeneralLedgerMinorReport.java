package se.cloudshop.accounting;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record GeneralLedgerMinorReport(
    LocalDate periodFrom, LocalDate periodTo, String currencyCode, int minorUnitExponent,
    String selectedAccountNumber, List<Account> accounts, int accountCount, int entryCount,
    long periodDebitTotalMinor, long periodCreditTotalMinor
) {
  public GeneralLedgerMinorReport {
    accounts = List.copyOf(accounts);
  }

  public record Account(
      String accountNumber, String accountName, long openingBalanceMinor,
      long periodDebitMinor, long periodCreditMinor, long closingBalanceMinor, List<Entry> entries
  ) {
    public Account {
      entries = List.copyOf(entries);
    }
  }

  public record Entry(
      String journalEntryId, LocalDate voucherDate, String voucherNumber,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
      String correctionOfVoucherNumber, String description,
      long debitMinor, long creditMinor, long runningBalanceMinor
  ) {
  }
}
