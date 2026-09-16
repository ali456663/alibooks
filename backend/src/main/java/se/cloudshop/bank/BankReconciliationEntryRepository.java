package se.cloudshop.bank;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankReconciliationEntryRepository extends JpaRepository<BankReconciliationEntry, Long> {
  @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  @org.springframework.data.jpa.repository.Query(value = "SELECT 1 FROM pg_advisory_xact_lock(4279371, hashtext(:key))", nativeQuery = true)
  Integer lockBankRow(@org.springframework.data.repository.query.Param("key") String key);

  boolean existsByBankRowId(String bankRowId);
  List<BankReconciliationEntry> findAllByBankRowId(String bankRowId);
  List<BankReconciliationEntry> findAllByOrderByBookedAtDesc();

  long deleteByBankRowIdAndStatus(String bankRowId, String status);
}
