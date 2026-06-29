package se.cloudshop.product;

import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

  private final ProductRepository productRepository;

  public ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  public List<Product> findAll() {
    return productRepository.findByActiveTrueOrderByIdAsc();
  }

  public List<Product> findAllForAdmin() {
    return productRepository.findAllByOrderByIdAsc();
  }

  public Optional<Product> findById(long id) {
    return productRepository.findById(id);
  }

  public Product create(Product request) {
    Product product = new Product();
    applyValues(product, request);
    return productRepository.save(product);
  }

  public Product update(long id, Product request) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
    applyValues(product, request);
    return productRepository.save(product);
  }

  private void applyValues(Product product, Product request) {
    String name = request.getName() == null ? "" : request.getName().trim();

    if (name.length() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name must contain at least 2 characters.");
    }

    if (request.getPrice() < 0 || request.getDiscountPrice() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price cannot be negative.");
    }

    if (request.getDiscountPrice() > 0 && request.getDiscountPrice() >= request.getPrice()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount price must be lower than ordinary price.");
    }

    product.setName(name);
    product.setDescription(request.getDescription());
    product.setPrice(request.getPrice());
    product.setDiscountPrice(request.getDiscountPrice());
    product.setDiscountLabel(request.getDiscountLabel());
    product.setActive(request.isActive());
  }
}
