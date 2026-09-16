package se.cloudshop.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
  boolean existsByEntityTypeAndAction(String entityType, String action);

  List<AuditEvent> findAllByOrderByCreatedAtAscIdAsc();

  List<AuditEvent> findAllByOrderByCreatedAtDescIdDesc();

  List<AuditEvent> findTop300ByOrderByCreatedAtDesc();

  List<AuditEvent> findByEntityTypeOrderByCreatedAtDesc(String entityType);
}
