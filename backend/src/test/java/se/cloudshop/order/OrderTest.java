package se.cloudshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import se.cloudshop.product.Product;

class OrderTest {

  @Test
  void largePriceVatDoesNotLoseAWholeKronaToFloatPrecision() {
    Order order = new Order("Test", new Product("Test", "Test", 100_000_004), Instant.now());
    assertThat(order.getVatAmount()).isEqualTo(25_000_001);
    assertThat(order.getTotalAmount()).isEqualTo(125_000_005);
  }

  @Test
  void refusesInvoiceWhenVatContainsOreUntilMinorUnitMigrationIsComplete() {
    assertThatThrownBy(() -> new Order("Test", new Product("Test", "Test", 1), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("VAT contains ore")
        .hasMessageContaining("minor-unit migration");
  }

  @Test
  void rejectsOverflowInQuantityAndTotal() {
    assertThatThrownBy(() -> new Order("Test", new Product("Test", "Test", 1_000_000_000), Instant.now(), 3))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> new Order("Test", new Product("Test", "Test", 2_000_000_000), Instant.now()))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void calculatesInvoiceAmountsFromProductPriceAndQuantity() {
    Order order = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now(), 2);

    assertThat(order.getNetAmount()).isEqualTo(2000);
    assertThat(order.getVatAmount()).isEqualTo(500);
    assertThat(order.getTotalAmount()).isEqualTo(2500);
    assertThat(order.getOrdinaryPriceMinor()).isEqualTo(200_000L);
    assertThat(order.getNetAmountMinor()).isEqualTo(200_000L);
    assertThat(order.getVatAmountMinor()).isEqualTo(50_000L);
    assertThat(order.getTotalAmountMinor()).isEqualTo(250_000L);
  }

  @Test
  void keepsPaymentAndRefundShadowAmountsInMinorUnits() {
    Order order = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now());

    order.registerPayment(java.time.LocalDate.of(2026, 9, 20), 500, "bank-1");
    order.registerRefund(java.time.LocalDate.of(2026, 9, 21), 100, "refund-1");

    assertThat(order.getPaidAmountMinor()).isEqualTo(50_000L);
    assertThat(order.getRefundedAmountMinor()).isEqualTo(10_000L);
    assertThat(order.getPayments()).singleElement().extracting(se.cloudshop.order.InvoicePayment::getAmountMinor)
        .isEqualTo(50_000L);
  }

  @Test
  void usesMinorUnitsForRemainingAndRefundableAmounts() {
    Order order = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now());

    order.registerPayment(java.time.LocalDate.of(2026, 9, 20), 500, "bank-1");
    assertThat(order.getRemainingAmountMinor()).isEqualTo(75_000L);
    assertThat(order.getRemainingAmount()).isEqualTo(750);

    order.setStatus("CREDITED");
    order.registerRefund(java.time.LocalDate.of(2026, 9, 21), 100, "refund-1");
    assertThat(order.getRefundableAmount()).isEqualTo(400);
  }

  @Test
  void rejectsPaymentAndRefundBeyondTheInvoice() {
    Order order = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now());

    assertThatThrownBy(() -> order.registerPayment(java.time.LocalDate.of(2026, 9, 20), 1251, "bank-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("greater than the remaining");
    order.registerPayment(java.time.LocalDate.of(2026, 9, 20), 1250, "bank-1");
    order.setStatus("CREDITED");
    assertThatThrownBy(() -> order.registerRefund(java.time.LocalDate.of(2026, 9, 21), 1251, "refund-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("greater than the refundable");
  }

  @Test
  void acceptsConsistentCreditInvoiceAmounts() {
    Order order = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now());

    order.setAmounts(-1000, -250, -1250);

    assertThat(order.getNetAmount()).isEqualTo(-1000);
    assertThat(order.getVatAmount()).isEqualTo(-250);
    assertThat(order.getTotalAmount()).isEqualTo(-1250);
  }

  @Test
  void rejectsInvoiceAmountsWhenTotalDoesNotMatchNetPlusVat() {
    Order order = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now());

    assertThatThrownBy(() -> order.setAmounts(1000, 250, 1200))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("total must equal net amount plus VAT");
  }

  @Test
  void rejectsInvoiceAmountsThatMixPositiveAndNegativeValues() {
    Order order = new Order("Ali Wafa", new Product("PT", "Training", 1000), Instant.now());

    assertThatThrownBy(() -> order.setAmounts(1000, -250, 750))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not mix positive and negative");
  }
}
