package se.cloudshop.supplier;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PayablesReportService {

  private final SupplierInvoiceRepository supplierInvoiceRepository;

  public PayablesReportService(SupplierInvoiceRepository supplierInvoiceRepository) {
    this.supplierInvoiceRepository = supplierInvoiceRepository;
  }

  public PayablesAgingReport createAgingReport(LocalDate asOfDate) {
    LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
    List<PayablesAgingInvoice> invoices = supplierInvoiceRepository.findAll()
        .stream()
        .filter(this::isOpenPayable)
        .map(invoice -> toAgingInvoice(invoice, asOf))
        .sorted(Comparator
            .comparing((PayablesAgingInvoice invoice) -> invoice.dueDate() == null)
            .thenComparing(invoice -> invoice.dueDate() == null ? LocalDate.MAX : invoice.dueDate())
            .thenComparing(PayablesAgingInvoice::supplierName, Comparator.nullsLast(String::compareTo)))
        .toList();

    Map<String, BucketSummary> summaries = new LinkedHashMap<>();
    addBucket(summaries, "no-due-date", "Utan forfallodatum");
    addBucket(summaries, "not-due", "Ej forfallen");
    addBucket(summaries, "overdue-1-30", "Forfallen 1-30 dagar");
    addBucket(summaries, "overdue-31-60", "Forfallen 31-60 dagar");
    addBucket(summaries, "overdue-61-90", "Forfallen 61-90 dagar");
    addBucket(summaries, "overdue-90-plus", "Forfallen over 90 dagar");

    invoices.forEach(invoice -> summaries.get(invoice.bucketKey()).add(invoice));

    List<PayablesAgingBucket> buckets = summaries.entrySet()
        .stream()
        .map(entry -> entry.getValue().toBucket(entry.getKey()))
        .toList();

    int totalOutstanding = invoices.stream().mapToInt(PayablesAgingInvoice::remainingAmount).sum();
    int overdueOutstanding = invoices.stream()
        .filter(invoice -> invoice.daysOverdue() > 0)
        .mapToInt(PayablesAgingInvoice::remainingAmount)
        .sum();
    int notDueOutstanding = invoices.stream()
        .filter(invoice -> invoice.daysOverdue() <= 0 && invoice.dueDate() != null)
        .mapToInt(PayablesAgingInvoice::remainingAmount)
        .sum();
    int noDueDateOutstanding = invoices.stream()
        .filter(invoice -> invoice.dueDate() == null)
        .mapToInt(PayablesAgingInvoice::remainingAmount)
        .sum();
    int dueSoonOutstanding = invoices.stream()
        .filter(invoice -> invoice.dueDate() != null)
        .filter(invoice -> {
          long daysUntilDue = ChronoUnit.DAYS.between(asOf, invoice.dueDate());
          return daysUntilDue >= 0 && daysUntilDue <= 5;
        })
        .mapToInt(PayablesAgingInvoice::remainingAmount)
        .sum();
    int inputVatOutstanding = invoices.stream().mapToInt(PayablesAgingInvoice::vatAmount).sum();

    return new PayablesAgingReport(
        asOf,
        invoices.size(),
        totalOutstanding,
        notDueOutstanding,
        overdueOutstanding,
        dueSoonOutstanding,
        noDueDateOutstanding,
        inputVatOutstanding,
        buckets,
        invoices
    );
  }

  private boolean isOpenPayable(SupplierInvoice invoice) {
    if (invoice.getRemainingAmount() <= 0) {
      return false;
    }

    String status = invoice.getStatus() == null ? "unpaid" : invoice.getStatus();
    return !"cancelled".equals(status) && !"paid".equals(status);
  }

  private PayablesAgingInvoice toAgingInvoice(SupplierInvoice invoice, LocalDate asOf) {
    long daysOverdue = invoice.getDueDate() == null ? 0 : ChronoUnit.DAYS.between(invoice.getDueDate(), asOf);
    String bucketKey = bucketKey(invoice.getDueDate(), daysOverdue);
    String bucketTitle = bucketTitle(bucketKey);
    boolean paymentRecommended = invoice.getDueDate() != null && ChronoUnit.DAYS.between(asOf, invoice.getDueDate()) <= 5;

    return new PayablesAgingInvoice(
        invoice.getId(),
        invoice.getSupplierName(),
        invoice.getSupplierEmail(),
        invoice.getSupplierOrgNumber(),
        invoice.getInvoiceDate(),
        invoice.getDueDate(),
        invoice.getStatus(),
        invoice.getReference(),
        invoice.getDescription(),
        invoice.getCategory(),
        invoice.getNetAmount(),
        invoice.getVatAmount(),
        invoice.getTotalAmount(),
        invoice.getRemainingAmount(),
        Math.max(Math.toIntExact(daysOverdue), 0),
        bucketKey,
        bucketTitle,
        paymentRecommended
    );
  }

  private String bucketKey(LocalDate dueDate, long daysOverdue) {
    if (dueDate == null) {
      return "no-due-date";
    }
    if (daysOverdue <= 0) {
      return "not-due";
    }
    if (daysOverdue <= 30) {
      return "overdue-1-30";
    }
    if (daysOverdue <= 60) {
      return "overdue-31-60";
    }
    if (daysOverdue <= 90) {
      return "overdue-61-90";
    }
    return "overdue-90-plus";
  }

  private String bucketTitle(String bucketKey) {
    return switch (bucketKey) {
      case "no-due-date" -> "Utan forfallodatum";
      case "not-due" -> "Ej forfallen";
      case "overdue-1-30" -> "Forfallen 1-30 dagar";
      case "overdue-31-60" -> "Forfallen 31-60 dagar";
      case "overdue-61-90" -> "Forfallen 61-90 dagar";
      default -> "Forfallen over 90 dagar";
    };
  }

  private void addBucket(Map<String, BucketSummary> summaries, String key, String title) {
    summaries.put(key, new BucketSummary(title));
  }

  private static class BucketSummary {
    private final String title;
    private int invoiceCount;
    private int totalRemaining;
    private LocalDate oldestDueDate;

    BucketSummary(String title) {
      this.title = title;
    }

    void add(PayablesAgingInvoice invoice) {
      invoiceCount++;
      totalRemaining += invoice.remainingAmount();
      if (invoice.dueDate() != null && (oldestDueDate == null || invoice.dueDate().isBefore(oldestDueDate))) {
        oldestDueDate = invoice.dueDate();
      }
    }

    PayablesAgingBucket toBucket(String key) {
      return new PayablesAgingBucket(key, title, invoiceCount, totalRemaining, oldestDueDate);
    }
  }
}
