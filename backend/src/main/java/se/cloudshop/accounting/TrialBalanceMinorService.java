package se.cloudshop.accounting;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrialBalanceMinorService {
  // JSON integer amounts must survive the browser's integer range without loss.
  private static final long MAX_JSON_INTEGER = 9_007_199_254_740_991L;
  private static final String SQL = """
      WITH parameters AS (SELECT CAST(? AS date) AS period_from, CAST(? AS date) AS period_to)
      SELECT account_number, COALESCE(MAX(account_name), '') AS account_name,
        COUNT(*) FILTER (WHERE voucher_date IS NULL OR account_number IS NULL
          OR btrim(account_number) = '' OR currency_code IS DISTINCT FROM 'SEK'
          OR debit_minor IS NULL OR credit_minor IS NULL OR debit IS NULL OR credit IS NULL
          OR debit_minor < 0 OR credit_minor < 0 OR (debit_minor > 0 AND credit_minor > 0)
          OR debit_minor <> CAST(debit AS numeric) * 100
          OR credit_minor <> CAST(credit AS numeric) * 100) AS invalid_rows,
        COALESCE(SUM(CASE WHEN period_from IS NOT NULL AND voucher_date < period_from
          THEN CAST(debit_minor AS numeric) - credit_minor ELSE 0 END), 0) AS opening_minor,
        COALESCE(SUM(CASE WHEN period_from IS NULL OR voucher_date >= period_from
          THEN debit_minor ELSE 0 END), 0) AS period_debit_minor,
        COALESCE(SUM(CASE WHEN period_from IS NULL OR voucher_date >= period_from
          THEN credit_minor ELSE 0 END), 0) AS period_credit_minor
      FROM journal_entries CROSS JOIN parameters
      WHERE period_to IS NULL OR voucher_date <= period_to OR voucher_date IS NULL
      GROUP BY account_number ORDER BY account_number
      """;

  private final JdbcTemplate jdbc;

  public TrialBalanceMinorService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public TrialBalanceMinorReport create(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date.");
    }
    Long currencyCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM currencies WHERE currency_code = 'SEK' AND minor_unit_exponent = 2", Long.class);
    if (currencyCount == null || currencyCount != 1L) {
      throw invalidData();
    }
    try {
      // Aggregate persisted amounts in SQL without loading the full ledger into JPA.
      List<TrialBalanceMinorReport.Line> lines = jdbc.query(SQL, (row, index) -> {
        if (row.getLong("invalid_rows") != 0) {
          throw invalidData();
        }
        long opening = amount(row.getBigDecimal("opening_minor").longValueExact());
        long debit = amount(row.getBigDecimal("period_debit_minor").longValueExact());
        long credit = amount(row.getBigDecimal("period_credit_minor").longValueExact());
        long closing = amount(Math.subtractExact(Math.addExact(opening, debit), credit));
        return new TrialBalanceMinorReport.Line(row.getString("account_number"), row.getString("account_name"),
            Math.max(opening, 0), Math.max(-opening, 0), debit, credit,
            Math.max(closing, 0), Math.max(-closing, 0));
      }, from == null ? null : Date.valueOf(from), to == null ? null : Date.valueOf(to)).stream()
          .filter(line -> line.openingDebitMinor() != 0 || line.openingCreditMinor() != 0
              || line.periodDebitMinor() != 0 || line.periodCreditMinor() != 0)
          .toList();

      long openingDebit = total(lines, TrialBalanceMinorReport.Line::openingDebitMinor);
      long openingCredit = total(lines, TrialBalanceMinorReport.Line::openingCreditMinor);
      long periodDebit = total(lines, TrialBalanceMinorReport.Line::periodDebitMinor);
      long periodCredit = total(lines, TrialBalanceMinorReport.Line::periodCreditMinor);
      long closingDebit = total(lines, TrialBalanceMinorReport.Line::closingDebitMinor);
      long closingCredit = total(lines, TrialBalanceMinorReport.Line::closingCreditMinor);
      return new TrialBalanceMinorReport(from, to, "SEK", 2, lines, openingDebit, openingCredit,
          periodDebit, periodCredit, closingDebit, closingCredit, amount(closingDebit - closingCredit));
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Saldobalansens belopp overskrider den exakta beloppsgransen. Rapporten har stoppats.");
    }
  }

  void requireBalancedPeriodVouchers(LocalDate from, LocalDate to) {
    Long invalidVouchers = jdbc.queryForObject("""
        SELECT COUNT(*) FROM (
          SELECT voucher_number FROM journal_entries
          WHERE (CAST(? AS date) IS NULL OR voucher_date >= CAST(? AS date))
            AND (CAST(? AS date) IS NULL OR voucher_date <= CAST(? AS date))
          GROUP BY voucher_number
          HAVING voucher_number IS NULL OR btrim(voucher_number) = ''
            OR COALESCE(SUM(CAST(debit_minor AS numeric)), 0)
              <> COALESCE(SUM(CAST(credit_minor AS numeric)), 0)
        ) invalid
        """, Long.class,
        from == null ? null : Date.valueOf(from), from == null ? null : Date.valueOf(from),
        to == null ? null : Date.valueOf(to), to == null ? null : Date.valueOf(to));
    if (invalidVouchers == null || invalidVouchers != 0) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Resultatrapporten har stoppats: ett verifikat i perioden saknar nummer eller balanserar inte i oren.");
    }
  }

  private static long total(List<TrialBalanceMinorReport.Line> lines,
      java.util.function.ToLongFunction<TrialBalanceMinorReport.Line> value) {
    return amount(lines.stream().mapToLong(value).reduce(0L, Math::addExact));
  }

  private static long amount(long value) {
    if (value < -MAX_JSON_INTEGER || value > MAX_JSON_INTEGER) {
      throw new ArithmeticException("Amount cannot be represented as an exact JSON integer.");
    }
    return value;
  }

  private static ResponseStatusException invalidData() {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "Saldobalansen har stoppats: kontrollera datum, konton, valuta och lagrade oresbelopp.");
  }
}
