package se.cloudshop.payroll;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollSnapshotRevisionRepository extends JpaRepository<PayrollSnapshotRevision, Long> {

  List<PayrollSnapshotRevision> findTop10ByOrderByCreatedAtDesc();
}
