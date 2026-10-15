package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfitAndLossMinorService {
  private static final long MAX_JSON_INTEGER = 9_007_199_254_740_991L;
  private final TrialBalanceMinorService trialBalance;

  public ProfitAndLossMinorService(TrialBalanceMinorService trialBalance) {
    this.trialBalance = trialBalance;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ProfitAndLossMinorReport create(LocalDate from, LocalDate to) {
    TrialBalanceMinorReport balance = trialBalance.create(from, to);
    trialBalance.requireBalancedPeriodVouchers(from, to);
    List<ProfitAndLossMinorReport.Line> revenue = new ArrayList<>();
    List<ProfitAndLossMinorReport.Line> expenses = new ArrayList<>();
    try {
      long totalRevenue = 0;
      long totalExpenses = 0;
      for (TrialBalanceMinorReport.Line line : balance.lines()) {
        String account = line.accountNumber();
        if (account.startsWith("3")) {
          long amount = exact(Math.subtractExact(line.periodCreditMinor(), line.periodDebitMinor()));
          revenue.add(new ProfitAndLossMinorReport.Line(account, line.accountName(), amount));
          totalRevenue = exact(Math.addExact(totalRevenue, amount));
        } else if (account.matches("[4-8].*")) {
          long amount = exact(Math.subtractExact(line.periodDebitMinor(), line.periodCreditMinor()));
          expenses.add(new ProfitAndLossMinorReport.Line(account, line.accountName(), amount));
          totalExpenses = exact(Math.addExact(totalExpenses, amount));
        }
      }
      return new ProfitAndLossMinorReport(from, to, balance.currencyCode(), balance.minorUnitExponent(),
          revenue, expenses, totalRevenue, totalExpenses, exact(Math.subtractExact(totalRevenue, totalExpenses)));
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Resultatrapportens belopp overskrider den exakta beloppsgransen. Rapporten har stoppats.");
    }
  }

  private static long exact(long amount) {
    if (amount < -MAX_JSON_INTEGER || amount > MAX_JSON_INTEGER) {
      throw new ArithmeticException("Amount cannot be represented as an exact JSON integer.");
    }
    return amount;
  }
}
