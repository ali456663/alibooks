package se.cloudshop.supplier;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {
  @Transactional(propagation = Propagation.MANDATORY)
  @Query(value = "SELECT id FROM supplier_invoices WHERE id = :id FOR UPDATE", nativeQuery = true)
  Long lockById(@Param("id") Long id);

  List<SupplierInvoice> findAllByOrderByDueDateAscIdAsc();

  boolean existsBySupplier_IdAndReferenceIgnoreCase(Long supplierId, String reference);
}
