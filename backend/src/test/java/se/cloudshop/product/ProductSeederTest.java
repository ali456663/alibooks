package se.cloudshop.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class ProductSeederTest {

  @Test
  void demoSeedRequiresExplicitOptIn() {
    ConditionalOnProperty condition = ProductSeeder.class.getAnnotation(ConditionalOnProperty.class);
    assertThat(condition.prefix()).isEqualTo("app.demo-products");
    assertThat(condition.name()).containsExactly("seed-enabled");
    assertThat(condition.havingValue()).isEqualTo("true");
    assertThat(condition.matchIfMissing()).isFalse();
  }

  @Test
  void neverChangesAnExistingCatalogEvenWhenOptedIn() {
    ProductRepository repository = mock(ProductRepository.class);
    when(repository.count()).thenReturn(1L);

    new ProductSeeder(repository).run();

    verify(repository, never()).save(any(Product.class));
  }

  @Test
  void seedsAnEmptyCatalogWithConsistentMinorAmounts() {
    ProductRepository repository = mock(ProductRepository.class);
    when(repository.count()).thenReturn(0L);
    when(repository.save(any(Product.class))).thenAnswer(invocation -> {
      Product product = invocation.getArgument(0);
      product.validateMoneyShadow();
      assertThat(product.getPriceMinor()).isEqualTo(product.getPrice() * 100L);
      return product;
    });

    new ProductSeeder(repository).run();

    verify(repository).count();
    verify(repository, org.mockito.Mockito.atLeastOnce()).save(any(Product.class));
  }
}
