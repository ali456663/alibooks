package se.cloudshop.contract;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringContractRepository extends JpaRepository<RecurringContract, Long> {
  List<RecurringContract> findByArchivedFalseOrderByNextInvoiceDateAscIdAsc();
}
