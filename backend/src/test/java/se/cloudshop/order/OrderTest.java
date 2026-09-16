package se.cloudshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import se.cloudshop.product.Product;

class OrderTest {

  @Test
  void largePriceVatDoesNotLoseAWholeKronaToFloatPrecision() {
    Order order = new Order("Test", new Product("Test", "Test", 100_000_003), Instant.now());
    assertThat(order.getVatAmount()).isEqualTo(25_000_001);
    assertThat(order.getTotalAmount()).isEqualTo(125_000_004);
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
