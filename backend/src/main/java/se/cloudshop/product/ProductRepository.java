package se.cloudshop.product;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

  boolean existsByName(String name);

  Optional<Product> findByName(String name);

  List<Product> findByActiveTrueOrderByIdAsc();

  List<Product> findAllByName(String name);

  List<Product> findAllByOrderByIdAsc();
}
