package se.cloudshop.accounting;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeneralLedgerMinorService {
  private static final long MAX_JSON_INTEGER = 9_007_199_254_740_991L;
  private static final String SQL = """
      WITH parameters AS (SELECT CAST(? AS date) AS period_to, CAST(? AS varchar) AS account_filter)
      SELECT id, account_number, account_name, voucher_date, voucher_number, created_at,
        correction_of_voucher_number, description,
        debit_minor, credit_minor,
        (voucher_date IS NULL OR voucher_number IS NULL OR btrim(voucher_number) = ''
          OR account_number IS NULL OR btrim(account_number) = ''
          OR currency_code IS DISTINCT FROM 'SEK'
          OR debit IS NULL OR credit IS NULL OR debit_minor IS NULL OR credit_minor IS NULL
          OR debit_minor < 0 OR credit_minor < 0 OR (debit_minor > 0 AND credit_minor > 0)
          OR debit_minor IS DISTINCT FROM CAST(debit AS numeric) * 100
          OR credit_minor IS DISTINCT FROM CAST(credit AS numeric) * 100) AS invalid_row
      FROM journal_entries CROSS JOIN parameters
      WHERE (period_to IS NULL OR voucher_date <= period_to OR voucher_date IS NULL)
        AND (account_filter IS NULL OR account_number = account_filter)
      ORDER BY account_number NULLS FIRST, voucher_date NULLS FIRST,
        voucher_number NULLS FIRST, id
      """;

  private final JdbcTemplate jdbc;

  public GeneralLedgerMinorService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public GeneralLedgerMinorReport create(LocalDate from, LocalDate to, String accountNumber) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date.");
    }
    String selectedAccount = accountNumber == null || accountNumber.isBlank() ? null : accountNumber.trim();
    Long currencyCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM currencies WHERE currency_code = 'SEK' AND minor_unit_exponent = 2", Long.class);
    if (currencyCount == null || currencyCount != 1L) {
      throw invalidData();
    }
    try {
      List<LedgerRow> rows = jdbc.query(SQL, (row, index) -> {
        if (row.getBoolean("invalid_row")) {
          throw invalidData();
        }
        return new LedgerRow(row.getString("id"), row.getString("account_number"),
            row.getString("account_name"), row.getDate("voucher_date").toLocalDate(),
            row.getString("voucher_number"),
            row.getTimestamp("created_at") == null ? null : row.getTimestamp("created_at").toInstant(),
            row.getString("correction_of_voucher_number"), row.getString("description"),
            amount(row.getLong("debit_minor")), amount(row.getLong("credit_minor")));
      }, to == null ? null : Date.valueOf(to), selectedAccount);

      Map<String, AccountBuilder> grouped = new LinkedHashMap<>();
      for (LedgerRow row : rows) {
        AccountBuilder account = grouped.computeIfAbsent(row.accountNumber(), AccountBuilder::new);
        account.accountName = row.accountName() == null ? "" : row.accountName();
        if (from != null && row.voucherDate().isBefore(from)) {
          account.openingBalanceMinor = add(account.openingBalanceMinor,
              Math.subtractExact(row.debitMinor(), row.creditMinor()));
          continue;
        }
        account.periodDebitMinor = add(account.periodDebitMinor, row.debitMinor());
        account.periodCreditMinor = add(account.periodCreditMinor, row.creditMinor());
        account.rows.add(row);
      }

      List<GeneralLedgerMinorReport.Account> accounts = new ArrayList<>();
      long debitTotal = 0;
      long creditTotal = 0;
      int entryCount = 0;
      for (AccountBuilder account : grouped.values()) {
        long balance = account.openingBalanceMinor;
        List<GeneralLedgerMinorReport.Entry> entries = new ArrayList<>();
        for (LedgerRow row : account.rows) {
          balance = add(balance, Math.subtractExact(row.debitMinor(), row.creditMinor()));
          entries.add(new GeneralLedgerMinorReport.Entry(row.id(), row.voucherDate(),
              row.voucherNumber(), row.createdAt(), row.correctionOfVoucherNumber(), row.description(),
              row.debitMinor(), row.creditMinor(), balance));
        }
        if (balance == 0 && account.openingBalanceMinor == 0
            && account.periodDebitMinor == 0 && account.periodCreditMinor == 0
            && account.rows.isEmpty()) {
          continue;
        }
        debitTotal = add(debitTotal, account.periodDebitMinor);
        creditTotal = add(creditTotal, account.periodCreditMinor);
        entryCount = Math.addExact(entryCount, entries.size());
        accounts.add(new GeneralLedgerMinorReport.Account(account.accountNumber, account.accountName,
            account.openingBalanceMinor, account.periodDebitMinor, account.periodCreditMinor, balance, entries));
      }
      return new GeneralLedgerMinorReport(from, to, "SEK", 2, selectedAccount, accounts,
          accounts.size(), entryCount, debitTotal, creditTotal);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Huvudbokens oresbelopp overskrider den exakta beloppsgransen.", exception);
    }
  }

  private static long add(long first, long second) {
    return amount(Math.addExact(first, second));
  }

  private static long amount(long value) {
    if (value < -MAX_JSON_INTEGER || value > MAX_JSON_INTEGER) {
      throw new ArithmeticException("Amount cannot be represented as an exact JSON integer.");
    }
    return value;
  }

  private static ResponseStatusException invalidData() {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "Huvudboken har stoppats: kontrollera verifikat, konton, valuta och lagrade oresbelopp.");
  }

  private record LedgerRow(String id, String accountNumber, String accountName, LocalDate voucherDate,
      String voucherNumber, Instant createdAt, String correctionOfVoucherNumber,
      String description, long debitMinor, long creditMinor) {
  }

  private static class AccountBuilder {
    private final String accountNumber;
    private String accountName = "";
    private long openingBalanceMinor;
    private long periodDebitMinor;
    private long periodCreditMinor;
    private final List<LedgerRow> rows = new ArrayList<>();

    private AccountBuilder(String accountNumber) {
      this.accountNumber = accountNumber;
    }
  }
}
