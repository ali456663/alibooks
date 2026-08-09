package se.cloudshop.accounting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import se.cloudshop.order.Order;
import se.cloudshop.supplier.SupplierInvoice;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

  List<JournalEntry> findByVoucherNumber(String voucherNumber);

  List<JournalEntry> findByInvoice(Order invoice);

  List<JournalEntry> findBySupplierInvoice(SupplierInvoice supplierInvoice);

  List<JournalEntry> findByCorrectionOfVoucherNumber(String correctionOfVoucherNumber);

  void deleteByInvoice(Order invoice);
}
