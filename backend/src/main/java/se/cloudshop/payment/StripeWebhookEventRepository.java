package se.cloudshop.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, String> {
  @Transactional(propagation = Propagation.MANDATORY)
  @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(4279370, hashtext(:key))", nativeQuery = true)
  Integer lockProcessingKey(@Param("key") String key);
}
