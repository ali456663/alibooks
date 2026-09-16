package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportAmountsTest {
  @ParameterizedTest
  @ValueSource(longs = {0, 1, -1, 2147483647L, -2147483647L})
  void preservesSupportedSignedAmounts(long amount) {
    assertThat(ReportAmounts.reportAmount(amount)).isEqualTo(amount);
  }

  @ParameterizedTest
  @ValueSource(longs = {2147483648L, -2147483648L, 4294967296L, Long.MAX_VALUE, Long.MIN_VALUE})
  void rejectsOutOfRangeInsteadOfWrapping(long amount) {
    assertThatThrownBy(() -> ReportAmounts.reportAmount(amount)).isInstanceOf(ReportAmounts.LimitExceeded.class);
  }
}
