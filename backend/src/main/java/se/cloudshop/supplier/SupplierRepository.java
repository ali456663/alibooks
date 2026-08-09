package se.cloudshop.supplier;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
  List<Supplier> findAllByOrderByNameAsc();
}
