package se.cloudshop.accounting;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import se.cloudshop.order.Order;
import se.cloudshop.supplier.SupplierInvoice;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

  List<JournalEntry> findByVoucherNumber(String voucherNumber);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select entry from JournalEntry entry where entry.voucherNumber = :voucherNumber order by entry.id")
  List<JournalEntry> findByVoucherNumberForCorrection(@Param("voucherNumber") String voucherNumber);

  List<JournalEntry> findByInvoice(Order invoice);

  List<JournalEntry> findBySupplierInvoice(SupplierInvoice supplierInvoice);

  List<JournalEntry> findByCorrectionOfVoucherNumber(String correctionOfVoucherNumber);

  boolean existsByDescription(String description);

  @Transactional(propagation = Propagation.MANDATORY)
  @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(4279371, hashtext(:key))", nativeQuery = true)
  Integer lockStripeSaleReference(@Param("key") String key);

  void deleteByInvoice(Order invoice);

  @Query(value = """
      SELECT COALESCE(MAX(
          CASE
            WHEN suffix ~ '^[0-9]{1,10}$' AND suffix::numeric <= 2147483647 THEN suffix::bigint
            ELSE 0::bigint
          END
      ), 0::bigint)
      FROM (
          SELECT substring(voucher_number FROM :sequenceStart) AS suffix
          FROM journal_entries
          WHERE voucher_number LIKE CONCAT(:prefix, '-%')
      ) numbered_vouchers
      """, nativeQuery = true)
  long findLatestVoucherSequence(
      @Param("prefix") String prefix,
      @Param("sequenceStart") int sequenceStart
  );
}
