package se.cloudshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InvoicePaymentIntegrityTest {
  private InvoicePayment payment() {
    return new InvoicePayment(null, LocalDate.of(2026, 9, 29), 125, "test");
  }

  @Test
  void validPaymentKeepsItsMinorAmount() {
    InvoicePayment row = payment();
    row.validateMinorUnitShadow();
    assertThat(row.getAmountMinor()).isEqualTo(12_500L);
  }

  @Test
  void missingShadowIsNotRebuiltOnReadOrUpdate() {
    InvoicePayment row = payment();
    ReflectionTestUtils.setField(row, "amountMinor", null);
    assertThatThrownBy(row::getAmountMinor).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(row::validateMinorUnitShadow).isInstanceOf(IllegalStateException.class);
    assertThat(ReflectionTestUtils.getField(row, "amountMinor")).isNull();
  }

  @Test
  void mismatchedShadowAndCurrencyAreRejectedWithoutRepair() {
    InvoicePayment row = payment();
    ReflectionTestUtils.setField(row, "amountMinor", 12_501L);
    assertThatThrownBy(row::validateMinorUnitShadow).isInstanceOf(IllegalStateException.class);
    assertThat(ReflectionTestUtils.getField(row, "amountMinor")).isEqualTo(12_501L);
    ReflectionTestUtils.setField(row, "amountMinor", 12_500L);
    ReflectionTestUtils.setField(row, "currencyCode", "EUR");
    assertThatThrownBy(row::validateMinorUnitShadow).isInstanceOf(IllegalStateException.class);
    assertThat(row.getCurrencyCode()).isEqualTo("EUR");
  }
}
