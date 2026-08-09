package se.cloudshop.accounting;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VatFilingRepository extends JpaRepository<VatFiling, Long> {
  List<VatFiling> findAllByOrderByPeriodToDesc();

  Optional<VatFiling> findByPeriodFromAndPeriodTo(LocalDate periodFrom, LocalDate periodTo);

  Optional<VatFiling> findFirstByStatusInOrderByPeriodToDesc(List<String> statuses);
}
