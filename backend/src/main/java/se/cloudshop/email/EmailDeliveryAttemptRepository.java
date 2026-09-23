package se.cloudshop.email;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, Long> {
  long countByStatus(String status);
}
