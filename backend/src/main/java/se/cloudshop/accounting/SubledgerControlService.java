package se.cloudshop.accounting;

import static se.cloudshop.accounting.ReportAmounts.reportAmount;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.order.ReceivablesReportService;
import se.cloudshop.settings.AppSettingsRepository;
import se.cloudshop.supplier.PayablesReportService;

@Service
public class SubledgerControlService {
  private final JournalEntryRepository journal;
  private final AppSettingsRepository settings;
  private final ReceivablesReportService receivables;
  private final PayablesReportService payables;

  public SubledgerControlService(JournalEntryRepository journal, AppSettingsRepository settings,
      ReceivablesReportService receivables, PayablesReportService payables) {
    this.journal = journal;
    this.settings = settings;
    this.receivables = receivables;
    this.payables = payables;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SubledgerControlReport createReport(LocalDate asOfDate) {
    LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
    String method = settings.findById(1L).orElseThrow(() -> new ResponseStatusException(
        HttpStatus.CONFLICT, "Accounting settings must exist before reconciliation.")).getAccountingMethod();
    Map<Long, Integer> customerBalances = new LinkedHashMap<>();
    receivables.createAgingReport(asOf).invoices().forEach(row -> addBalance(customerBalances, row.invoiceId(), row.remainingAmount()));
    Map<Long, Integer> supplierBalances = new LinkedHashMap<>();
    payables.createAgingReport(asOf).invoices().forEach(row -> addBalance(supplierBalances, row.invoiceId(), row.remainingAmount()));
    List<JournalEntry> entries = journal.findAll();
    boolean comparable = "INVOICE_METHOD".equals(method);
    var accounts = List.of(compare("1510", customerBalances, entries, asOf, comparable),
        compare("2440", supplierBalances, entries, asOf, comparable));
    String status = !comparable ? "UNSUPPORTED_METHOD"
        : accounts.stream().anyMatch(account -> "REVIEW_REQUIRED".equals(account.status())) ? "REVIEW_REQUIRED"
        : accounts.stream().allMatch(account -> "NO_DATA".equals(account.status())) ? "NO_DATA" : "MATCHED";
    return new SubledgerControlReport(asOf, method, status, accounts);
  }

  private void addBalance(Map<Long, Integer> balances, Long id, int amount) {
    if (id == null || amount <= 0 || balances.putIfAbsent(id, amount) != null) throw SettlementSnapshot.incomplete();
  }

  private SubledgerControlReport.AccountResult compare(String account, Map<Long, Integer> expected,
      List<JournalEntry> entries, LocalDate asOf, boolean comparable) {
    Map<Long, Long> linked = new LinkedHashMap<>();
    long total = 0;
    int unlinked = 0;
    int undated = 0;
    int rowCount = 0;
    for (JournalEntry entry : entries) {
      if (!account.equals(entry.getAccountNumber())) continue;
      if (entry.getVoucherDate() == null) {
        undated++;
        continue;
      }
      if (entry.getVoucherDate().isAfter(asOf)) continue;
      rowCount++;
      long amount = "1510".equals(account) ? (long) entry.getDebit() - entry.getCredit()
          : (long) entry.getCredit() - entry.getDebit();
      total += amount;
      Long id = sourceId(account, entry);
      if (id == null) unlinked++;
      else linked.merge(id, amount, Long::sum);
    }
    int subledger = reportAmount(expected.values().stream().mapToLong(Integer::longValue).sum());
    int ledger = reportAmount(total);
    var differences = new ArrayList<SubledgerControlReport.InvoiceDifference>();
    if (comparable) {
      var ids = new TreeSet<>(expected.keySet());
      ids.addAll(linked.keySet());
      for (Long id : ids) {
        int expectedAmount = expected.getOrDefault(id, 0);
        int ledgerAmount = reportAmount(linked.getOrDefault(id, 0L));
        int difference = reportAmount((long) ledgerAmount - expectedAmount);
        if (difference != 0) differences.add(new SubledgerControlReport.InvoiceDifference(id, expectedAmount, ledgerAmount, difference));
      }
    }
    Integer difference = comparable ? reportAmount((long) ledger - subledger) : null;
    String status = !comparable ? "UNSUPPORTED_METHOD"
        : unlinked > 0 || undated > 0 || !differences.isEmpty() || difference != 0 ? "REVIEW_REQUIRED"
        : rowCount == 0 && expected.isEmpty() ? "NO_DATA" : "MATCHED";
    return new SubledgerControlReport.AccountResult(account, subledger, ledger, difference, status, unlinked, undated, differences);
  }

  private Long sourceId(String account, JournalEntry entry) {
    if ("1510".equals(account) && entry.getInvoice() != null && entry.getSupplierInvoice() == null) {
      var invoice = entry.getInvoice();
      // Credits belong to the original invoice's control balance.
      return invoice.isCreditInvoice() ? invoice.getCreditedInvoiceId() : invoice.getId();
    }
    if ("2440".equals(account) && entry.getSupplierInvoice() != null && entry.getInvoice() == null) {
      return entry.getSupplierInvoice().getId();
    }
    return null;
  }
}
