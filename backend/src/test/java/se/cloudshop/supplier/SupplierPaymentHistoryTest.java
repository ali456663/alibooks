package se.cloudshop.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.cloudshop.accounting.SettlementSnapshot;

class SupplierPaymentHistoryTest {
  @Test
  void readsExistingFormatWithReferencesAndWindowsNewlines() {
    assertThat(SupplierPaymentHistory.read("2026-01-01 - 50 SEK - Bank - ref\r\n2026-01-02 - 75 SEK"))
        .containsExactly(new SettlementSnapshot.Payment(LocalDate.of(2026, 1, 1), 50),
            new SettlementSnapshot.Payment(LocalDate.of(2026, 1, 2), 75));
    assertThat(SupplierPaymentHistory.read(null)).isEmpty();
    assertThat(SupplierPaymentHistory.read("")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-02-30 - 50 SEK", "2026-01-01 - -50 SEK", "2026-01-01 - 0 SEK",
      "2026-01-01 - 50.5 SEK", "2026-01-01 - 2147483648 SEK", "unknown", "2026-01-01 - 50 SEK\n", "2026-01-01 - 50 SEK\nreference"})
  void refusesAmbiguousLegacyHistory(String history) {
    assertThatThrownBy(() -> SupplierPaymentHistory.read(history)).isInstanceOf(SettlementSnapshot.HistoryIncomplete.class);
  }
}
