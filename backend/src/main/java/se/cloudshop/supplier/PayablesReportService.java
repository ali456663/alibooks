package se.cloudshop.supplier;

import static se.cloudshop.accounting.ReportAmounts.reportAmount;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.SettlementSnapshot;
import se.cloudshop.audit.AuditEventRepository;

@Service
public class PayablesReportService {

  private final SupplierInvoiceRepository supplierInvoiceRepository;
  private final AuditEventRepository auditEventRepository;

  public PayablesReportService(SupplierInvoiceRepository supplierInvoiceRepository, AuditEventRepository auditEventRepository) {
    this.supplierInvoiceRepository = supplierInvoiceRepository;
    this.auditEventRepository = auditEventRepository;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PayablesAgingReport createAgingReport(LocalDate asOfDate) {
    LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
    // Older versions allowed deleting cash-method invoices; their balances cannot be reconstructed.
    if (asOf.isBefore(LocalDate.now()) && auditEventRepository.existsByEntityTypeAndAction("supplier_invoice", "deleted")) {
      throw SettlementSnapshot.incomplete();
    }
    List<PayablesAgingInvoice> invoices = supplierInvoiceRepository.findAll()
        .stream()
        .map(invoice -> toAgingInvoice(invoice, asOf))
        .filter(invoice -> invoice.remainingAmount() > 0)
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

    int totalOutstanding = reportAmount(invoices.stream().mapToLong(PayablesAgingInvoice::remainingAmount).sum());
    int overdueOutstanding = reportAmount(invoices.stream()
        .filter(invoice -> invoice.daysOverdue() > 0)
        .mapToLong(PayablesAgingInvoice::remainingAmount)
        .sum());
    int notDueOutstanding = reportAmount(invoices.stream()
        .filter(invoice -> invoice.daysOverdue() <= 0 && invoice.dueDate() != null)
        .mapToLong(PayablesAgingInvoice::remainingAmount)
        .sum());
    int noDueDateOutstanding = reportAmount(invoices.stream()
        .filter(invoice -> invoice.dueDate() == null)
        .mapToLong(PayablesAgingInvoice::remainingAmount)
        .sum());
    int dueSoonOutstanding = reportAmount(invoices.stream()
        .filter(invoice -> invoice.dueDate() != null)
        .filter(invoice -> {
          long daysUntilDue = ChronoUnit.DAYS.between(asOf, invoice.dueDate());
          return daysUntilDue >= 0 && daysUntilDue <= 5;
        })
        .mapToLong(PayablesAgingInvoice::remainingAmount)
        .sum());
    int inputVatOutstanding = reportAmount(invoices.stream().mapToLong(PayablesAgingInvoice::vatAmount).sum());

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

  private PayablesAgingInvoice toAgingInvoice(SupplierInvoice invoice, LocalDate asOf) {
    String status = String.valueOf(invoice.getStatus());
    long totalAmountMinor = minorOrWholeKrona(invoice.getTotalAmountMinor(), invoice.getTotalAmount());
    long savedPaidAmountMinor = minorOrWholeKrona(invoice.getPaidAmountMinor(), invoice.getPaidAmount());
    if (!List.of("unpaid", "prepared", "booked", "partial", "paid", "cancelled").contains(status)
        || ("paid".equals(status) && savedPaidAmountMinor != totalAmountMinor)
        || ("partial".equals(status) && (savedPaidAmountMinor <= 0 || savedPaidAmountMinor >= totalAmountMinor))
        || (!List.of("paid", "partial").contains(status) && savedPaidAmountMinor != 0)
        || ("cancelled".equals(status) != (invoice.getCancelledAt() != null))) {
      throw SettlementSnapshot.incomplete();
    }
    List<SettlementSnapshot.Payment> payments = invoice.getPaymentRows().isEmpty()
        ? SupplierPaymentHistory.read(invoice.getPaymentHistory())
        : invoice.getPaymentRows().stream()
            .map(payment -> new SettlementSnapshot.Payment(payment.getPaymentDate(), payment.getAmount()))
            .toList();
    SettlementSnapshot.MinorSettlement balance = SettlementSnapshot.atMinor(totalAmountMinor, savedPaidAmountMinor,
        invoice.getInvoiceDate(), invoice.getCancelledAt(), payments.stream()
            .map(payment -> new SettlementSnapshot.MinorPayment(payment.date(), Math.multiplyExact((long) payment.amount(), 100L))).toList(), asOf);
    long daysOverdue = invoice.getDueDate() == null ? 0 : ChronoUnit.DAYS.between(invoice.getDueDate(), asOf);
    String bucketKey = bucketKey(invoice.getDueDate(), daysOverdue);
    String bucketTitle = bucketTitle(bucketKey);
    boolean paymentRecommended = asOf.equals(LocalDate.now()) && !"cancelled".equals(status)
        && balance.remainingAmountMinor() > 0 && invoice.getDueDate() != null
        && ChronoUnit.DAYS.between(asOf, invoice.getDueDate()) <= 5;

    return new PayablesAgingInvoice(
        invoice.getId(),
        invoice.getSupplierName(),
        invoice.getSupplierEmail(),
        invoice.getSupplierOrgNumber(),
        invoice.getInvoiceDate(),
        invoice.getDueDate(),
        balance.paidAmountMinor() > 0 ? "partial" : "unpaid",
        invoice.getReference(),
        invoice.getDescription(),
        invoice.getCategory(),
        reportWholeKrona(minorOrWholeKrona(invoice.getNetAmountMinor(), invoice.getNetAmount()), "leverantörsfakturans nettobelopp"),
        reportWholeKrona(minorOrWholeKrona(invoice.getVatAmountMinor(), invoice.getVatAmount()), "leverantörsfakturans momsbelopp"),
        reportWholeKrona(totalAmountMinor, "leverantörsfakturans totalbelopp"),
        reportWholeKrona(balance.remainingAmountMinor(), "leverantörsfakturans kvarvarande belopp"),
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

  private long minorOrWholeKrona(Long minor, int wholeKrona) {
    return minor == null ? Math.multiplyExact((long) wholeKrona, 100L) : minor;
  }

  private int reportWholeKrona(long amountMinor, String field) {
    if (amountMinor % 100L != 0) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Leverantörsreskontran innehåller ören i " + field + ". Rapporten har stoppats för att undvika avrundningsfel.");
    }
    return reportAmount(amountMinor / 100L);
  }

  private static class BucketSummary {
    private final String title;
    private int invoiceCount;
    private long totalRemaining;
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
      return new PayablesAgingBucket(key, title, invoiceCount, reportAmount(totalRemaining), oldestDueDate);
    }
  }
}
