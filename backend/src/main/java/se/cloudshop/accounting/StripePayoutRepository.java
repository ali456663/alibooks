package se.cloudshop.accounting;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripePayoutRepository extends JpaRepository<StripePayout, Long> {
  Optional<StripePayout> findByReference(String reference);
}
