package se.cloudshop.order;

import static se.cloudshop.accounting.ReportAmounts.reportAmount;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import se.cloudshop.accounting.SettlementSnapshot;
import se.cloudshop.customer.Customer;

@Service
public class ReceivablesReportService {

  private final OrderRepository orderRepository;

  public ReceivablesReportService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReceivablesAgingReport createAgingReport(LocalDate asOfDate) {
    LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
    List<Order> allInvoices = orderRepository.findAll();
    Map<Long, List<Order>> credits = allInvoices.stream()
        .filter(Order::isCreditInvoice)
        .filter(invoice -> invoice.getCreditedInvoiceId() != null)
        .collect(Collectors.groupingBy(Order::getCreditedInvoiceId));
    List<ReceivablesAgingInvoice> invoices = allInvoices.stream()
        .filter(invoice -> !invoice.isCreditInvoice() && !"DRAFT".equals(invoice.getStatus()))
        .map(invoice -> toAgingInvoice(invoice, asOf, credits.getOrDefault(invoice.getId(), List.of())))
        .filter(invoice -> invoice.remainingAmount() > 0)
        .sorted(Comparator
            .comparing((ReceivablesAgingInvoice invoice) -> invoice.dueDate() == null)
            .thenComparing(invoice -> invoice.dueDate() == null ? LocalDate.MAX : invoice.dueDate())
            .thenComparing(ReceivablesAgingInvoice::invoiceNumber, Comparator.nullsLast(String::compareTo)))
        .toList();

    Map<String, BucketSummary> summaries = new LinkedHashMap<>();
    addBucket(summaries, "no-due-date", "Utan forfallodatum");
    addBucket(summaries, "not-due", "Ej forfallen");
    addBucket(summaries, "overdue-1-30", "Forfallen 1-30 dagar");
    addBucket(summaries, "overdue-31-60", "Forfallen 31-60 dagar");
    addBucket(summaries, "overdue-61-90", "Forfallen 61-90 dagar");
    addBucket(summaries, "overdue-90-plus", "Forfallen over 90 dagar");

    invoices.forEach(invoice -> summaries.get(invoice.bucketKey()).add(invoice));

    List<ReceivablesAgingBucket> buckets = summaries.entrySet()
        .stream()
        .map(entry -> entry.getValue().toBucket(entry.getKey()))
        .toList();

    int totalOutstanding = reportAmount(invoices.stream().mapToLong(ReceivablesAgingInvoice::remainingAmount).sum());
    int overdueOutstanding = reportAmount(invoices.stream()
        .filter(invoice -> invoice.daysOverdue() > 0)
        .mapToLong(ReceivablesAgingInvoice::remainingAmount)
        .sum());
    int notDueOutstanding = reportAmount(invoices.stream()
        .filter(invoice -> invoice.daysOverdue() <= 0 && invoice.dueDate() != null)
        .mapToLong(ReceivablesAgingInvoice::remainingAmount)
        .sum());
    int noDueDateOutstanding = reportAmount(invoices.stream()
        .filter(invoice -> invoice.dueDate() == null)
        .mapToLong(ReceivablesAgingInvoice::remainingAmount)
        .sum());
    int dueSoonOutstanding = reportAmount(invoices.stream()
        .filter(invoice -> invoice.dueDate() != null)
        .filter(invoice -> {
          long daysUntilDue = ChronoUnit.DAYS.between(asOf, invoice.dueDate());
          return daysUntilDue >= 0 && daysUntilDue <= 5;
        })
        .mapToLong(ReceivablesAgingInvoice::remainingAmount)
        .sum());

    return new ReceivablesAgingReport(
        asOf,
        invoices.size(),
        totalOutstanding,
        notDueOutstanding,
        overdueOutstanding,
        dueSoonOutstanding,
        noDueDateOutstanding,
        buckets,
        invoices
    );
  }

  private ReceivablesAgingInvoice toAgingInvoice(Order invoice, LocalDate asOf, List<Order> credits) {
    if (!List.of("SENT", "PARTIALLY_PAID", "PAID", "CREDITED").contains(String.valueOf(invoice.getStatus()))
        || ("PAID".equals(invoice.getStatus()) && invoice.getPaidAmount() != invoice.getTotalAmount())
        || ("SENT".equals(invoice.getStatus()) && invoice.getPaidAmount() != 0)
        || ("PARTIALLY_PAID".equals(invoice.getStatus())
            && (invoice.getPaidAmount() <= 0 || invoice.getPaidAmount() >= invoice.getTotalAmount()))) {
      throw SettlementSnapshot.incomplete();
    }
    LocalDate closedDate = null;
    if ("CREDITED".equals(invoice.getStatus())) {
      if (credits.size() != 1 || credits.get(0).getInvoiceDate() == null || !"SENT".equals(credits.get(0).getStatus())
          || (long) credits.get(0).getTotalAmount() != -(long) invoice.getTotalAmount()) {
        throw SettlementSnapshot.incomplete();
      }
      closedDate = credits.get(0).getInvoiceDate();
    } else if (!credits.isEmpty()) {
      throw SettlementSnapshot.incomplete();
    }
    SettlementSnapshot balance = SettlementSnapshot.at(invoice.getTotalAmount(), invoice.getPaidAmount(),
        invoice.getInvoiceDate(), closedDate, invoice.getPayments().stream()
            .map(payment -> new SettlementSnapshot.Payment(payment.getPaymentDate(), payment.getAmount())).toList(), asOf);
    long daysOverdue = invoice.getDueDate() == null ? 0 : ChronoUnit.DAYS.between(invoice.getDueDate(), asOf);
    String bucketKey = bucketKey(invoice.getDueDate(), daysOverdue);
    String bucketTitle = bucketTitle(bucketKey);
    Customer customer = invoice.getCustomer();
    String customerName = customer == null ? invoice.getCustomerName() : customer.getName();
    String customerEmail = customer == null ? "" : customer.getEmail();
    boolean reminderRecommended = asOf.equals(LocalDate.now()) && invoice.hasRemainingAmount()
        && invoice.getDueDate() != null && daysOverdue > 0 && customerEmail != null && !customerEmail.isBlank();

    return new ReceivablesAgingInvoice(
        invoice.getId(),
        invoice.getInvoiceNumber(),
        customerName,
        customerEmail,
        invoice.getInvoiceDate(),
        invoice.getDueDate(),
        balance.paidAmount() > 0 ? "PARTIALLY_PAID" : "SENT",
        invoice.getTotalAmount(),
        balance.paidAmount(),
        balance.remainingAmount(),
        Math.max(daysOverdue, 0),
        bucketKey,
        bucketTitle,
        reminderRecommended
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
    private long totalRemaining;
    private LocalDate oldestDueDate;

    BucketSummary(String title) {
      this.title = title;
    }

    void add(ReceivablesAgingInvoice invoice) {
      invoiceCount++;
      totalRemaining += invoice.remainingAmount();
      if (invoice.dueDate() != null && (oldestDueDate == null || invoice.dueDate().isBefore(oldestDueDate))) {
        oldestDueDate = invoice.dueDate();
      }
    }

    ReceivablesAgingBucket toBucket(String key) {
      return new ReceivablesAgingBucket(key, title, invoiceCount, reportAmount(totalRemaining), oldestDueDate);
    }
  }
}
