package se.cloudshop.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SupplierPaymentSafetyTest {
  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 76, Integer.MAX_VALUE})
  void invalidPaymentCannotClampOrOverflowExistingPaidAmount(int amount) {
    Supplier supplier = new Supplier("Test", "test@example.invalid", "", "", "");
    SupplierInvoice invoice = new SupplierInvoice(supplier, LocalDate.now(), LocalDate.now(), "Test", "ref", 125, 25, "5420");
    assertThat(invoice.getTotalAmountMinor()).isEqualTo(12500L);
    assertThat(invoice.getVatAmountMinor()).isEqualTo(2500L);
    assertThat(invoice.getNetAmountMinor()).isEqualTo(10000L);
    assertThat(invoice.getPaidAmountMinor()).isEqualTo(0L);
    invoice.registerPayment(LocalDate.now(), 50, "first");
    assertThat(invoice.getPaidAmountMinor()).isEqualTo(5000L);
    assertThat(invoice.getPaymentRows()).hasSize(1);
    assertThat(invoice.getPayments()).hasSize(1);
    assertThat(invoice.getPayments().get(0).getAmountMinor()).isEqualTo(5000L);
    assertThat(invoice.getPayments().get(0).getCurrencyCode()).isEqualTo("SEK");
    assertThat(invoice.getPaymentRows().get(0).getAmountMinorValue()).isEqualTo(5000L);
    String history = invoice.getPaymentHistory();
    assertThatThrownBy(() -> invoice.registerPayment(LocalDate.now(), amount, "bad")).isInstanceOf(IllegalArgumentException.class);
    assertThat(invoice.getPaidAmount()).isEqualTo(50);
    assertThat(invoice.getPaymentHistory()).isEqualTo(history);
    assertThat(invoice.getStatus()).isEqualTo("partial");
  }

  @org.junit.jupiter.api.Test
  void usesMinorUnitsForRemainingAndRejectsOverpayment() {
    Supplier supplier = new Supplier("Test", "test@example.invalid", "", "", "");
    SupplierInvoice invoice = new SupplierInvoice(supplier, LocalDate.now(), LocalDate.now(), "Test", "ref", 125, 25, "5420");

    invoice.registerPayment(LocalDate.now(), 100, "first");

    assertThat(invoice.getPaidAmountMinor()).isEqualTo(10_000L);
    assertThat(invoice.getPaymentRows()).hasSize(1);
    assertThat(invoice.getRemainingAmountMinor()).isEqualTo(2_500L);
    assertThat(invoice.getRemainingAmount()).isEqualTo(25);
    assertThatThrownBy(() -> invoice.registerPayment(LocalDate.now(), 26, "too-much"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(invoice.getPaidAmount()).isEqualTo(100);
    assertThat(invoice.getPaymentHistory()).doesNotContain("too-much");
  }
}
