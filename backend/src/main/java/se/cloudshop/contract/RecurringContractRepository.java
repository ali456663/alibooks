package se.cloudshop.contract;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringContractRepository extends JpaRepository<RecurringContract, Long> {
  @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  @org.springframework.data.jpa.repository.Query(value = "SELECT id FROM recurring_contracts WHERE id = :id FOR UPDATE", nativeQuery = true)
  Long lockById(@org.springframework.data.repository.query.Param("id") Long id);
  List<RecurringContract> findByArchivedFalseOrderByNextInvoiceDateAscIdAsc();
}
