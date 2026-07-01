package se.cloudshop.bank;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankReconciliationEntryRepository extends JpaRepository<BankReconciliationEntry, Long> {
  List<BankReconciliationEntry> findAllByOrderByBookedAtDesc();

  void deleteByBankRowIdAndStatus(String bankRowId, String status);
}
