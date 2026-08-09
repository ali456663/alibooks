package se.cloudshop.supplier;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {
  List<SupplierInvoice> findAllByOrderByDueDateAscIdAsc();

  boolean existsBySupplier_IdAndReferenceIgnoreCase(Long supplierId, String reference);
}
