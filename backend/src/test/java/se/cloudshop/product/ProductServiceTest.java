package se.cloudshop.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProductServiceTest {

  @Test
  void createsProductForDatabaseStorage() {
    Product product = new Product("Cloud Keyboard", "A compact keyboard for focused cloud work.", 799);

    assertThat(product.getName()).isEqualTo("Cloud Keyboard");
    assertThat(product.getPrice()).isEqualTo(799);
  }

  @Test
  void createsServiceWithCleanedValues() {
    ProductRepository productRepository = mock(ProductRepository.class);
    ProductService productService = new ProductService(productRepository);
    Product request = new Product("  PT Start  ", "  Coaching package  ", 1200);
    request.setDiscountPrice(900);
    request.setDiscountLabel("  Intro  ");

    when(productRepository.save(any(Product.class)))
        .thenAnswer(invocation -> (Product) invocation.getArgument(0));

    Product saved = productService.create(request);

    assertThat(saved.getName()).isEqualTo("PT Start");
    assertThat(saved.getDescription()).isEqualTo("Coaching package");
    assertThat(saved.getPrice()).isEqualTo(1200);
    assertThat(saved.getDiscountPrice()).isEqualTo(900);
    assertThat(saved.getDiscountLabel()).isEqualTo("Intro");
  }

  @Test
  void rejectsEmptyServiceNameBeforeSaving() {
    ProductRepository productRepository = mock(ProductRepository.class);
    ProductService productService = new ProductService(productRepository);

    assertThatThrownBy(() -> productService.create(new Product(" ", "Training", 1000)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Service name");

    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void rejectsZeroPriceBeforeSaving() {
    ProductRepository productRepository = mock(ProductRepository.class);
    ProductService productService = new ProductService(productRepository);

    assertThatThrownBy(() -> productService.create(new Product("PT", "Training", 0)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("greater than 0");

    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void rejectsNegativeDiscountBeforeSaving() {
    ProductRepository productRepository = mock(ProductRepository.class);
    ProductService productService = new ProductService(productRepository);
    Product request = new Product("PT", "Training", 1000);
    request.setDiscountPrice(-1);

    assertThatThrownBy(() -> productService.create(request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Discount price cannot be negative");

    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void rejectsDiscountThatIsNotLowerThanOrdinaryPriceBeforeSaving() {
    ProductRepository productRepository = mock(ProductRepository.class);
    ProductService productService = new ProductService(productRepository);
    Product request = new Product("PT", "Training", 1000);
    request.setDiscountPrice(1000);

    assertThatThrownBy(() -> productService.create(request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Discount price must be lower");

    verify(productRepository, never()).save(any(Product.class));
  }
}
