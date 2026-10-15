package se.cloudshop.accounting;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherEvidenceRepository extends JpaRepository<VoucherEvidence, Long> {
  Optional<VoucherEvidence> findByVoucherNumber(String voucherNumber);
}
