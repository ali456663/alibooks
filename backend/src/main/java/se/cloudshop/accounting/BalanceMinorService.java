package se.cloudshop.accounting;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.settings.SettingsService;

@Service
public class BalanceMinorService {
  private static final long MAX_JSON_INTEGER = 9_007_199_254_740_991L;
  private final TrialBalanceMinorService trialBalance;
  private final SettingsService settings;
  private final JdbcTemplate jdbc;

  public BalanceMinorService(TrialBalanceMinorService trialBalance, SettingsService settings, JdbcTemplate jdbc) {
    this.trialBalance = trialBalance;
    this.settings = settings;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public BalanceMinorReport create(LocalDate asOfDate) {
    TrialBalanceMinorReport balance = trialBalance.create(null, asOfDate);
    trialBalance.requireBalancedPeriodVouchers(null, asOfDate);
    List<BalanceMinorReport.Line> assets = new ArrayList<>();
    List<BalanceMinorReport.Line> liabilitiesAndEquity = new ArrayList<>();
    try {
      long totalAssets = 0;
      long totalLiabilitiesAndEquity = 0;
      long yearResult = 0;
      for (TrialBalanceMinorReport.Line line : balance.lines()) {
        long debitBalance = Math.subtractExact(line.closingDebitMinor(), line.closingCreditMinor());
        char accountClass = line.accountNumber().charAt(0);
        if (accountClass == '1') {
          long amount = exact(debitBalance);
          assets.add(new BalanceMinorReport.Line(line.accountNumber(), line.accountName(), amount));
          totalAssets = exact(Math.addExact(totalAssets, amount));
        } else if (accountClass == '2') {
          long amount = exact(Math.negateExact(debitBalance));
          liabilitiesAndEquity.add(new BalanceMinorReport.Line(line.accountNumber(), line.accountName(), amount));
          totalLiabilitiesAndEquity = exact(Math.addExact(totalLiabilitiesAndEquity, amount));
        } else if (accountClass >= '3' && accountClass <= '8') {
          yearResult = exact(Math.subtractExact(yearResult, debitBalance));
        }
      }
      String companyType = settings.getSettings().getCompanyType();
      String resultAccount = "LIMITED_COMPANY".equals(companyType) ? "2099" : "2019";
      int year = asOfDate == null ? LocalDate.now().getYear() : asOfDate.getYear();
      if (!hasAnnualResultVoucher(year, resultAccount, asOfDate)) {
        liabilitiesAndEquity.add(new BalanceMinorReport.Line(resultAccount, "Arets resultat", yearResult));
        totalLiabilitiesAndEquity = exact(Math.addExact(totalLiabilitiesAndEquity, yearResult));
      }
      return new BalanceMinorReport(asOfDate, balance.currencyCode(), balance.minorUnitExponent(),
          assets, liabilitiesAndEquity, totalAssets, totalLiabilitiesAndEquity,
          exact(Math.subtractExact(totalAssets, totalLiabilitiesAndEquity)));
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Balansrapportens belopp overskrider den exakta beloppsgransen. Rapporten har stoppats.");
    }
  }

  private boolean hasAnnualResultVoucher(int year, String resultAccount, LocalDate asOfDate) {
    Long count = jdbc.queryForObject("""
        SELECT COUNT(*) FROM journal_entries
        WHERE voucher_number LIKE 'BR-%' AND description = ?
          AND voucher_date >= ? AND voucher_date < ?
          AND (CAST(? AS date) IS NULL OR voucher_date <= ?)
          AND account_number IN (?, '8999')
        """, Long.class, "Annual result " + year, Date.valueOf(LocalDate.of(year, 1, 1)),
        Date.valueOf(LocalDate.of(year + 1, 1, 1)),
        asOfDate == null ? null : Date.valueOf(asOfDate), asOfDate == null ? null : Date.valueOf(asOfDate),
        resultAccount);
    return count != null && count > 0;
  }

  private static long exact(long amount) {
    if (amount < -MAX_JSON_INTEGER || amount > MAX_JSON_INTEGER) {
      throw new ArithmeticException("Amount cannot be represented as an exact JSON integer.");
    }
    return amount;
  }
}
