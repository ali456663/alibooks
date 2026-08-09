package se.cloudshop.accounting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerTransactionRepository extends JpaRepository<OwnerTransaction, Long> {
  List<OwnerTransaction> findAllByOrderByDateDescIdDesc();
}
