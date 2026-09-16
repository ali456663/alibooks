package se.cloudshop.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WholeKronaMathTest {
  @ParameterizedTest
  @CsvSource({"25000,125000,125000,25000", "25000,62500,125000,12500",
      "100000003,1,4,25000001", "2147483647,2147483647,2147483647,2147483647",
      "1,1,2,1", "-1,1,2,0", "-3,1,2,-1", "0,1,4,0", "200,999,999,200"})
  void roundsExactlyWithoutIntOverflowOrFloatLoss(int amount, int numerator, int denominator, int expected) {
    assertThat(WholeKronaMath.roundedRatio(amount, numerator, denominator)).isEqualTo(expected);
  }

  @Test
  void agreesWithDecimalReferenceAcrossDeterministicLargeInputs() {
    Random random = new Random(20260909);
    for (int i = 0; i < 2000; i++) {
      int total = random.nextInt(Integer.MAX_VALUE - 1) + 1;
      int paid = random.nextInt(total);
      int vat = random.nextInt(total);
      int expected = BigDecimal.valueOf(vat).multiply(BigDecimal.valueOf(paid))
          .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).intValueExact();
      int before = WholeKronaMath.roundedRatio(vat, paid, total);
      assertThat(before).isEqualTo(expected);
      assertThat(before + (WholeKronaMath.roundedRatio(vat, total, total) - before)).isEqualTo(vat);
    }
  }

  @Test
  void rejectsInvalidRatiosAndResultsOutsideIntRange() {
    assertThatThrownBy(() -> WholeKronaMath.roundedRatio(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WholeKronaMath.roundedRatio(1, -1, 4)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WholeKronaMath.roundedRatio(Integer.MAX_VALUE, 2, 1)).isInstanceOf(ArithmeticException.class);
  }
}
