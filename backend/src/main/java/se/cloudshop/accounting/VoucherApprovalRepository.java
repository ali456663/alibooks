package se.cloudshop.accounting;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherApprovalRepository extends JpaRepository<VoucherApproval, Long> {

  List<VoucherApproval> findAllByOrderByReviewedAtDesc();

  Optional<VoucherApproval> findByVoucherNumber(String voucherNumber);

  void deleteByVoucherNumber(String voucherNumber);
}
