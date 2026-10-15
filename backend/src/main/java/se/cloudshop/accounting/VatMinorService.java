package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VatMinorService {
  private static final long MAX_JSON_INTEGER = 9_007_199_254_740_991L;
  private static final Set<String> MAPPED_VAT_ACCOUNTS = Set.of("2611", "2621", "2631", "2641", "2650");
  private final JournalMinorService journal;
  private final JdbcTemplate jdbc;

  public VatMinorService(JournalMinorService journal, JdbcTemplate jdbc) {
    this.journal = journal;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public VatMinorReport create(LocalDate from, LocalDate to) {
    JournalMinorReport source = journal.create(from, to);
    if (!source.voucherDifferences().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Momsunderlaget har stoppats: ett verifikat i perioden balanserar inte i oren.");
    }

    Set<String> settlementVouchers = source.entries().stream()
        .filter(entry -> entry.voucherNumber().startsWith("MOMS-")
            || entry.description() != null && entry.description().startsWith("VAT settlement"))
        .map(JournalMinorReport.Entry::voucherNumber)
        .collect(Collectors.toSet());
    for (JournalMinorReport.Entry entry : source.entries()) {
      String account = entry.accountNumber();
      if (account.startsWith("26") && !MAPPED_VAT_ACCOUNTS.contains(account)
          && (entry.debitMinor() != 0 || entry.creditMinor() != 0)) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "Momsunderlaget har stoppats: ett momskonto saknar verifierad rapportmappning.");
      }
    }
    long sales25 = 0;
    long sales12 = 0;
    long sales6 = 0;
    long output25 = 0;
    long output12 = 0;
    long output6 = 0;
    long input = 0;
    try {
      for (JournalMinorReport.Entry entry : source.entries()) {
        if (settlementVouchers.contains(entry.voucherNumber())) continue;
        long creditMovement = Math.subtractExact(entry.creditMinor(), entry.debitMinor());
        switch (entry.accountNumber()) {
          case "3041" -> sales25 = exact(Math.addExact(sales25, creditMovement));
          case "3042" -> sales12 = exact(Math.addExact(sales12, creditMovement));
          case "3043" -> sales6 = exact(Math.addExact(sales6, creditMovement));
          case "2611" -> output25 = exact(Math.addExact(output25, creditMovement));
          case "2621" -> output12 = exact(Math.addExact(output12, creditMovement));
          case "2631" -> output6 = exact(Math.addExact(output6, creditMovement));
          case "2641" -> input = exact(Math.subtractExact(input, creditMovement));
          default -> { }
        }
      }
      long output = exact(Math.addExact(Math.addExact(output25, output12), output6));
      long payable = exact(Math.subtractExact(output, input));
      boolean settled = !settlementVouchers.isEmpty();
      if (!settled && from != null && to != null) {
        Boolean found = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM journal_entries
              WHERE voucher_number IN (
                SELECT voucher_number FROM journal_entries WHERE description = ?
              )
              GROUP BY voucher_number
              HAVING COUNT(*) >= 2
                AND COUNT(*) FILTER (WHERE description IS DISTINCT FROM ?) = 0
                AND COUNT(*) FILTER (WHERE currency_code IS DISTINCT FROM 'SEK'
                  OR debit IS NULL OR credit IS NULL OR debit_minor IS NULL OR credit_minor IS NULL
                  OR debit_minor < 0 OR credit_minor < 0
                  OR (debit_minor > 0 AND credit_minor > 0)
                  OR debit_minor IS DISTINCT FROM CAST(debit AS numeric) * 100
                  OR credit_minor IS DISTINCT FROM CAST(credit AS numeric) * 100) = 0
                AND SUM(CAST(debit_minor AS numeric)) = SUM(CAST(credit_minor AS numeric))
            )
            """, Boolean.class, "VAT settlement " + from + " - " + to,
            "VAT settlement " + from + " - " + to);
        settled = Boolean.TRUE.equals(found);
      }
      return new VatMinorReport(from, to, source.currencyCode(), source.minorUnitExponent(),
          sales25, output25, sales12, output12, sales6, output6, output, input, payable, settled);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Momsunderlagets oresbelopp overskrider den exakta beloppsgransen.", exception);
    }
  }

  private static long exact(long amount) {
    if (amount < -MAX_JSON_INTEGER || amount > MAX_JSON_INTEGER) {
      throw new ArithmeticException("Amount cannot be represented as an exact JSON integer.");
    }
    return amount;
  }
}
