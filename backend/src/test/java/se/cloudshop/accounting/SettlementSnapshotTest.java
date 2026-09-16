package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SettlementSnapshotTest {
  private static final LocalDate ISSUED = LocalDate.of(2026, 1, 1);

  @Test
  void reconstructsInclusiveDailyBalancesWithoutUsingCurrentPaidStatus() {
    var payments = List.of(new SettlementSnapshot.Payment(ISSUED.plusDays(2), 40),
        new SettlementSnapshot.Payment(ISSUED.plusDays(4), 60));
    assertThat(SettlementSnapshot.at(100, 100, ISSUED, null, payments, ISSUED.minusDays(1)))
        .isEqualTo(new SettlementSnapshot(0, 0));
    assertThat(SettlementSnapshot.at(100, 100, ISSUED, null, payments, ISSUED))
        .isEqualTo(new SettlementSnapshot(0, 100));
    assertThat(SettlementSnapshot.at(100, 100, ISSUED, null, payments, ISSUED.plusDays(2)))
        .isEqualTo(new SettlementSnapshot(40, 60));
    assertThat(SettlementSnapshot.at(100, 100, ISSUED, null, payments, ISSUED.plusDays(4)))
        .isEqualTo(new SettlementSnapshot(100, 0));
  }

  @Test
  void closureOnlyRemovesTheBalanceOnItsEffectiveDate() {
    assertThat(SettlementSnapshot.at(100, 0, ISSUED, ISSUED.plusDays(3), List.of(), ISSUED.plusDays(2)).remainingAmount()).isEqualTo(100);
    assertThat(SettlementSnapshot.at(100, 0, ISSUED, ISSUED.plusDays(3), List.of(), ISSUED.plusDays(3)).remainingAmount()).isZero();
  }

  @ParameterizedTest
  @MethodSource("invalidHistories")
  void refusesIncompleteOrConflictingHistories(int total, int savedPaid, LocalDate invoiceDate,
      LocalDate closedDate, List<SettlementSnapshot.Payment> payments) {
    assertThatThrownBy(() -> SettlementSnapshot.at(total, savedPaid, invoiceDate, closedDate, payments, ISSUED.plusDays(10)))
        .isInstanceOf(SettlementSnapshot.HistoryIncomplete.class);
  }

  static Stream<Arguments> invalidHistories() {
    return Stream.of(
        Arguments.of(0, 0, ISSUED, null, List.of()),
        Arguments.of(100, -1, ISSUED, null, List.of()),
        Arguments.of(100, 101, ISSUED, null, List.of()),
        Arguments.of(100, 0, null, null, List.of()),
        Arguments.of(100, 0, ISSUED, ISSUED.minusDays(1), List.of()),
        Arguments.of(100, 50, ISSUED, null, List.of()),
        Arguments.of(100, 0, ISSUED, null, List.of(new SettlementSnapshot.Payment(ISSUED, 50))),
        Arguments.of(100, 50, ISSUED, null, List.of(new SettlementSnapshot.Payment(null, 50))),
        Arguments.of(100, 50, ISSUED, null, List.of(new SettlementSnapshot.Payment(ISSUED.minusDays(1), 50))),
        Arguments.of(100, 0, ISSUED, null, List.of(new SettlementSnapshot.Payment(ISSUED, 0))),
        Arguments.of(100, 0, ISSUED, null, List.of(new SettlementSnapshot.Payment(ISSUED, -50))),
        Arguments.of(100, 50, ISSUED, ISSUED, List.of(new SettlementSnapshot.Payment(ISSUED.plusDays(1), 50))),
        Arguments.of(100, 0, ISSUED, null, List.of(new SettlementSnapshot.Payment(ISSUED, Integer.MAX_VALUE),
            new SettlementSnapshot.Payment(ISSUED, Integer.MAX_VALUE), new SettlementSnapshot.Payment(ISSUED, 2))));
  }
}
