package se.cloudshop.product;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class ProductController {

  private final ProductService productService;
  private final AuthHeader authHeader;

  public ProductController(ProductService productService, AuthHeader authHeader) {
    this.productService = productService;
    this.authHeader = authHeader;
  }

  @GetMapping("/products")
  public List<Product> getProducts() {
    return productService.findAll();
  }

  @GetMapping("/services")
  public List<Product> getServices() {
    return productService.findAll();
  }

  @GetMapping("/products/admin")
  public List<Product> getProductsForAdmin(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
    authHeader.requireValidToken(authorizationHeader);
    return productService.findAllForAdmin();
  }

  @PostMapping("/products")
  public Product createProduct(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody Product product
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return productService.create(product);
  }

  @PutMapping("/products/{id}")
  public Product updateProduct(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable long id,
      @RequestBody Product product
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return productService.update(id, product);
  }
}
