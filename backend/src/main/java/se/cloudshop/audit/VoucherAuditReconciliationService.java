package se.cloudshop.audit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherAuditReconciliationService {

  private final JdbcTemplate jdbc;

  public VoucherAuditReconciliationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Report reconcile() {
    List<Finding> findings = new ArrayList<>();
    long examined = jdbc.query("""
        SELECT a.id, a.entity_id, a.event_action, a.amount, a.amount_minor,
               count(j.id) AS journal_rows,
               count(j.id) FILTER (WHERE j.debit_minor IS NULL OR j.credit_minor IS NULL) AS missing_shadow_rows,
               sum(j.debit_minor) AS journal_debit_minor,
               sum(j.credit_minor) AS journal_credit_minor
        FROM audit_events a
        LEFT JOIN journal_entries j ON j.voucher_number = a.entity_id
            AND a.entity_id IS NOT NULL AND a.entity_id <> ''
        WHERE a.event_action IN ('manual_created', 'manual_multi_created', 'opening_balance_created')
        GROUP BY a.id
        ORDER BY a.id
        """, resultSet -> {
      long count = 0;
      while (resultSet.next()) {
        count++;
        BigInteger debitMinor = integer(resultSet.getBigDecimal("journal_debit_minor"));
        BigInteger creditMinor = integer(resultSet.getBigDecimal("journal_credit_minor"));
        Integer auditAmount = resultSet.getObject("amount", Integer.class);
        Long auditAmountMinor = resultSet.getObject("amount_minor", Long.class);
        List<String> issues = new ArrayList<>();
        if (resultSet.getLong("journal_rows") == 0) issues.add("missing_journal");
        if (resultSet.getLong("missing_shadow_rows") > 0) issues.add("missing_journal_minor_amount");
        if (debitMinor != null && creditMinor != null && !debitMinor.equals(creditMinor)) {
          issues.add("unbalanced_journal");
        }
        if (auditAmount == null || auditAmountMinor == null) {
          issues.add("missing_audit_amount");
        } else if (!BigInteger.valueOf(auditAmount).multiply(BigInteger.valueOf(100))
            .equals(BigInteger.valueOf(auditAmountMinor))) {
          issues.add("inconsistent_audit_minor_amount");
        }
        if (debitMinor != null && auditAmountMinor != null
            && !debitMinor.equals(BigInteger.valueOf(auditAmountMinor))) {
          issues.add("audit_journal_amount_mismatch");
        }
        if (!issues.isEmpty()) {
          findings.add(new Finding(resultSet.getLong("id"), resultSet.getString("entity_id"),
              resultSet.getString("event_action"), auditAmountMinor, debitMinor, creditMinor,
              List.copyOf(issues)));
        }
      }
      return count;
    });
    long examinedVouchers = jdbc.queryForObject("""
        SELECT count(DISTINCT voucher_number) FROM journal_entries
        WHERE voucher_number LIKE 'M-%' OR voucher_number LIKE 'IB-%'
        """, Long.class);
    jdbc.query("""
        SELECT j.voucher_number, sum(j.debit_minor) AS journal_debit_minor,
               sum(j.credit_minor) AS journal_credit_minor
        FROM journal_entries j
        WHERE (j.voucher_number LIKE 'M-%' OR j.voucher_number LIKE 'IB-%')
          AND NOT EXISTS (
            SELECT 1 FROM audit_events a
            WHERE a.entity_type = 'journal_entry' AND a.entity_id = j.voucher_number
              AND ((j.voucher_number LIKE 'M-%'
                    AND a.event_action IN ('manual_created', 'manual_multi_created'))
                OR (j.voucher_number LIKE 'IB-%'
                    AND a.event_action = 'opening_balance_created')))
        GROUP BY j.voucher_number
        ORDER BY j.voucher_number
        """, resultSet -> {
      while (resultSet.next()) {
        findings.add(new Finding(null, resultSet.getString("voucher_number"),
            "missing_creation_audit", null,
            integer(resultSet.getBigDecimal("journal_debit_minor")),
            integer(resultSet.getBigDecimal("journal_credit_minor")),
            List.of("missing_audit_event")));
      }
      return null;
    });
    return new Report(Instant.now(), examined, examinedVouchers, findings.size(), List.copyOf(findings));
  }

  private static BigInteger integer(BigDecimal value) {
    return value == null ? null : value.toBigIntegerExact();
  }

  public record Report(Instant generatedAt, long examinedEvents, long examinedVouchers,
                       int discrepantEvents, List<Finding> findings) {}

  public record Finding(Long auditId, String voucherNumber, String action, Long auditAmountMinor,
                        BigInteger journalDebitMinor, BigInteger journalCreditMinor, List<String> issues) {}
}
