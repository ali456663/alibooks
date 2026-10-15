package se.cloudshop.accounting;

import java.sql.Date;
import java.time.LocalDate;
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
public class JournalMinorService {
  private static final long MAX_JSON_INTEGER = 9_007_199_254_740_991L;
  private static final String SQL = """
      WITH parameters AS (SELECT CAST(? AS date) AS period_from, CAST(? AS date) AS period_to)
      SELECT id, voucher_date, voucher_number, created_at, account_number, account_name,
        description, correction_of_voucher_number, voucher_evidence_id, debit_minor, credit_minor,
        (voucher_date IS NULL OR voucher_number IS NULL OR btrim(voucher_number) = ''
          OR account_number IS NULL OR btrim(account_number) = ''
          OR currency_code IS DISTINCT FROM 'SEK'
          OR debit IS NULL OR credit IS NULL OR debit_minor IS NULL OR credit_minor IS NULL
          OR debit_minor < 0 OR credit_minor < 0 OR (debit_minor > 0 AND credit_minor > 0)
          OR debit_minor IS DISTINCT FROM CAST(debit AS numeric) * 100
          OR credit_minor IS DISTINCT FROM CAST(credit AS numeric) * 100) AS invalid_row
      FROM journal_entries CROSS JOIN parameters
      WHERE (period_from IS NULL OR voucher_date >= period_from OR voucher_date IS NULL)
        AND (period_to IS NULL OR voucher_date <= period_to OR voucher_date IS NULL)
      ORDER BY id
      """;

  private final JdbcTemplate jdbc;

  public JournalMinorService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public JournalMinorReport create(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date.");
    }
    Long currencyCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM currencies WHERE currency_code = 'SEK' AND minor_unit_exponent = 2", Long.class);
    if (currencyCount == null || currencyCount != 1L) throw invalidData();

    try {
      List<JournalMinorReport.Entry> entries = jdbc.query(SQL, (row, index) -> {
        if (row.getBoolean("invalid_row")) throw invalidData();
        long debit = amount(row.getLong("debit_minor"));
        long credit = amount(row.getLong("credit_minor"));
        Object evidenceId = row.getObject("voucher_evidence_id");
        return new JournalMinorReport.Entry(row.getString("id"), row.getDate("voucher_date").toLocalDate(),
            row.getString("voucher_number"),
            row.getTimestamp("created_at") == null ? null : row.getTimestamp("created_at").toInstant(),
            row.getString("account_number"), row.getString("account_name"), row.getString("description"),
            row.getString("correction_of_voucher_number"),
            evidenceId == null ? null : ((Number) evidenceId).longValue(), debit, credit);
      }, from == null ? null : Date.valueOf(from), to == null ? null : Date.valueOf(to));
      long debitTotal = 0;
      long creditTotal = 0;
      Map<String, Long> voucherDifferences = new LinkedHashMap<>();
      for (JournalMinorReport.Entry entry : entries) {
        debitTotal = amount(Math.addExact(debitTotal, entry.debitMinor()));
        creditTotal = amount(Math.addExact(creditTotal, entry.creditMinor()));
        long rowDifference = Math.subtractExact(entry.debitMinor(), entry.creditMinor());
        voucherDifferences.merge(entry.voucherNumber(), rowDifference,
            (current, next) -> amount(Math.addExact(current, next)));
      }
      List<JournalMinorReport.VoucherDifference> unbalanced = voucherDifferences.entrySet().stream()
          .filter(voucher -> voucher.getValue() != 0)
          .map(voucher -> new JournalMinorReport.VoucherDifference(voucher.getKey(), voucher.getValue()))
          .toList();
      return new JournalMinorReport(from, to, "SEK", 2, entries, debitTotal, creditTotal,
          amount(Math.subtractExact(debitTotal, creditTotal)), unbalanced);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Grundbokens oresbelopp overskrider den exakta beloppsgransen.", exception);
    }
  }

  private static long amount(long value) {
    if (value < -MAX_JSON_INTEGER || value > MAX_JSON_INTEGER) {
      throw new ArithmeticException("Amount cannot be represented as an exact JSON integer.");
    }
    return value;
  }

  private static ResponseStatusException invalidData() {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "Grundboken har stoppats: kontrollera datum, verifikat, konton, valuta och lagrade oresbelopp.");
  }
}
