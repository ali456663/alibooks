package se.cloudshop.accounting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import se.cloudshop.order.Order;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

  List<JournalEntry> findByVoucherNumber(String voucherNumber);

  List<JournalEntry> findByInvoice(Order invoice);

  void deleteByInvoice(Order invoice);
}
