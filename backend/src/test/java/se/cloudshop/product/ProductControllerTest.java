package se.cloudshop.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class ProductControllerTest {

  private final ProductService productService = mock(ProductService.class);
  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final AuditService auditService = mock(AuditService.class);
  private final ProductController productController = new ProductController(
      productService,
      authHeader,
      auditService
  );

  @Test
  void createProductRecordsAuditEvent() {
    Product product = product("PT Start", 900);
    setProductId(product, 31L);
    when(productService.create(product)).thenReturn(product);

    Product created = productController.createProduct("Bearer " + authHeaderToken(), product);

    assertThat(created.getId()).isEqualTo(31L);
    verify(auditService).record(
        eq("service"),
        eq("product"),
        eq(31L),
        eq("service_created"),
        eq("PT Start"),
        eq("Service created."),
        eq(900),
        any(String.class)
    );
  }

  @Test
  void updateProductRecordsAuditEventWithEffectivePrice() {
    Product product = product("PT Premium", 1200);
    product.setDiscountPrice(1000);
    setProductId(product, 32L);
    when(productService.update(32L, product)).thenReturn(product);

    Product updated = productController.updateProduct("Bearer " + authHeaderToken(), 32L, product);

    assertThat(updated.getEffectivePrice()).isEqualTo(1000);
    verify(auditService).record(
        eq("service"),
        eq("product"),
        eq(32L),
        eq("service_updated"),
        eq("PT Premium"),
        eq("Service updated."),
        eq(1000),
        any(String.class)
    );
  }

  private Product product(String name, int price) {
    return new Product(name, "Training", price);
  }

  private String authHeaderToken() {
    return new JwtService("test_secret").createToken("ali@example.com");
  }

  private void setProductId(Product product, Long id) {
    try {
      Field field = Product.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(product, id);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
