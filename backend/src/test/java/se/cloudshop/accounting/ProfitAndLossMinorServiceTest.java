package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ProfitAndLossMinorServiceTest {
  private final TrialBalanceMinorService trialBalance = mock(TrialBalanceMinorService.class);
  private final ProfitAndLossMinorService service = new ProfitAndLossMinorService(trialBalance);
  private final LocalDate from = LocalDate.of(2026, 7, 1);
  private final LocalDate to = LocalDate.of(2026, 7, 31);

  @Test
  void usesOnlyPeriodMovementsAndPreservesOreAndContraAccounts() {
    when(trialBalance.create(from, to)).thenReturn(balance(List.of(
        line("1930", 500_000, 50_000, 0),
        line("3041", 0, 1, 125_050),
        line("3051", 0, 5_000, 0),
        line("5010", 0, 30_012, 0),
        line("8310", 0, 0, 13))));

    ProfitAndLossMinorReport report = service.create(from, to);

    assertThat(report.currencyCode()).isEqualTo("SEK");
    assertThat(report.minorUnitExponent()).isEqualTo(2);
    assertThat(report.revenue()).extracting(ProfitAndLossMinorReport.Line::amountMinor)
        .containsExactly(125_049L, -5_000L);
    assertThat(report.expenses()).extracting(ProfitAndLossMinorReport.Line::amountMinor)
        .containsExactly(30_012L, -13L);
    assertThat(report.totalRevenueMinor()).isEqualTo(120_049L);
    assertThat(report.totalExpensesMinor()).isEqualTo(29_999L);
    assertThat(report.resultMinor()).isEqualTo(90_050L);
  }

  @Test
  void rejectsTotalsThatCannotBeRepresentedExactlyInBrowserJson() {
    long maximum = 9_007_199_254_740_991L;
    when(trialBalance.create(from, to)).thenReturn(balance(List.of(
        line("3041", 0, 0, maximum), line("3042", 0, 0, 1))));

    assertThatThrownBy(() -> service.create(from, to)).isInstanceOfSatisfying(
        ResponseStatusException.class,
        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  private TrialBalanceMinorReport balance(List<TrialBalanceMinorReport.Line> lines) {
    return new TrialBalanceMinorReport(from, to, "SEK", 2, lines, 0, 0, 0, 0, 0, 0, 0);
  }

  private TrialBalanceMinorReport.Line line(String account, long opening, long debit, long credit) {
    long closing = opening + debit - credit;
    return new TrialBalanceMinorReport.Line(account, "Account " + account,
        Math.max(opening, 0), Math.max(-opening, 0), debit, credit,
        Math.max(closing, 0), Math.max(-closing, 0));
  }
}
