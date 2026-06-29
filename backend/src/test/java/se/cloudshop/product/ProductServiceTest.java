package se.cloudshop.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductServiceTest {

  @Test
  void createsProductForDatabaseStorage() {
    Product product = new Product("Cloud Keyboard", "A compact keyboard for focused cloud work.", 799);

    assertThat(product.getName()).isEqualTo("Cloud Keyboard");
    assertThat(product.getPrice()).isEqualTo(799);
  }
}
