package se.cloudshop.email;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, Long> {
  long countByStatus(String status);
  long countByStatusIn(List<String> statuses);
  boolean existsByDeliveryTypeAndInvoiceId(String deliveryType, Long invoiceId);
  boolean existsByDeliveryTypeAndInvoiceIdAndStatusIn(String deliveryType, Long invoiceId, List<String> statuses);
  boolean existsByDeliveryTypeAndInvoiceIdAndStatus(String deliveryType, Long invoiceId, String status);
  boolean existsByDeliveryTypeAndInvoiceIdAndStatusNot(String deliveryType, Long invoiceId, String status);
  Optional<EmailDeliveryAttempt> findTopByDeliveryTypeAndInvoiceIdOrderByIdDesc(String deliveryType, Long invoiceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select attempt from EmailDeliveryAttempt attempt where attempt.id = :id")
  Optional<EmailDeliveryAttempt> findByIdForReconciliation(@Param("id") Long id);

  @Query("select new se.cloudshop.email.EmailDeliverySummary(a.id, a.deliveryType, a.invoiceId, "
      + "a.recipientEmail, a.subject, a.status, a.createdAt) "
      + "from EmailDeliveryAttempt a where a.status in :statuses order by a.createdAt, a.id")
  List<EmailDeliverySummary> findUnresolvedSummaries(@Param("statuses") List<String> statuses, Pageable limit);
}
