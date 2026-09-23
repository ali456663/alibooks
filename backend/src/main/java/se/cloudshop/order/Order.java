package se.cloudshop.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import se.cloudshop.customer.Customer;
import se.cloudshop.product.Product;
import se.cloudshop.money.WholeKronaMath;

@Entity
@Table(name = "customer_orders")
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String customerName;
  private String invoiceNumber;
  private LocalDate invoiceDate;
  private LocalDate dueDate;
  private int paymentTermsDays;
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean fTaxApproved = true;
  private String ocrNumber;
  private String plusGiro;
  private String paymentRecipient;
  private LocalDate paidDate;
  private int paidAmount;
  @Column(name = "paid_amount_minor")
  private Long paidAmountMinor;
  private String paymentReference;
  private LocalDate refundDate;
  private int refundedAmount;
  @Column(name = "refunded_amount_minor")
  private Long refundedAmountMinor;
  private String refundReference;
  private LocalDate reminderSentDate;

  @ManyToOne
  private Customer customer;

  @ManyToOne
  private Product product;

  private Instant createdAt;
  private String status;
  private int netAmount;
  @Column(name = "net_amount_minor")
  private Long netAmountMinor;
  private int vatAmount;
  @Column(name = "vat_amount_minor")
  private Long vatAmountMinor;
  private int totalAmount;
  @Column(name = "total_amount_minor")
  private Long totalAmountMinor;
  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode = "SEK";
  @jakarta.persistence.Column(nullable = false, columnDefinition = "integer default 25")
  private int vatPercent = 25;
  private int quantity = 1;
  private int ordinaryPrice;
  @Column(name = "ordinary_price_minor")
  private Long ordinaryPriceMinor;
  private int discountAmount;
  @Column(name = "discount_amount_minor")
  private Long discountAmountMinor;
  private String discountLabel;
  private String stripeCheckoutSessionId;
  @Column(nullable = false, columnDefinition = "boolean default false")
  private boolean creditInvoice = false;
  private Long creditedInvoiceId;
  @Transient
  private String creditedInvoiceNumber;

  @jakarta.persistence.Convert(converter = se.cloudshop.invoice.InvoiceDocumentSnapshotConverter.class)
  @Column(name = "document_snapshot", columnDefinition = "text")
  private se.cloudshop.invoice.InvoiceDocumentSnapshot documentSnapshot;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public se.cloudshop.invoice.InvoiceDocumentSnapshot getDocumentSnapshot() {
    return documentSnapshot;
  }

  public boolean isDocumentSnapshotAvailable() {
    return documentSnapshot != null;
  }

  public void captureDocumentSnapshot(se.cloudshop.settings.AppSettings settings) {
    if (documentSnapshot != null || !"DRAFT".equals(status)) {
      throw new IllegalStateException("Only a new draft may capture invoice document data once.");
    }
    documentSnapshot = se.cloudshop.invoice.InvoiceDocumentSnapshot.capture(this, settings);
    fTaxApproved = documentSnapshot.fTaxApproved();
  }

  public void refreshDraftIssuerSnapshot(se.cloudshop.settings.AppSettings settings) {
    if (!"DRAFT".equals(status)) {
      throw new IllegalStateException("Issuer details can only be refreshed before invoice issuance.");
    }
    se.cloudshop.invoice.InvoiceDocumentSnapshot current = se.cloudshop.invoice.InvoiceDocumentSnapshot.capture(this, settings);
    documentSnapshot = documentSnapshot == null ? current : documentSnapshot.withIssuerDetails(current);
    fTaxApproved = documentSnapshot.fTaxApproved();
  }

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private List<InvoicePayment> payments = new ArrayList<>();

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private Set<InvoiceReminder> reminders = new HashSet<>();

  public Order() {
  }

  public Order(String customerName, Product product, Instant createdAt) {
    this(customerName, product, createdAt, 1);
  }

  public Order(String customerName, Product product, Instant createdAt, int quantity) {
    this.customerName = customerName;
    this.product = product;
    this.createdAt = createdAt;
    this.status = "DRAFT";
    this.invoiceDate = LocalDate.now();
    this.paymentTermsDays = 30;
    this.dueDate = this.invoiceDate.plusDays(paymentTermsDays);
    this.fTaxApproved = true;
    this.quantity = normalizeQuantity(quantity);
    calculateAmounts(product);
    this.paidAmountMinor = 0L;
    this.refundedAmountMinor = 0L;
  }

  public Order(Customer customer, Product product, Instant createdAt) {
    this(customer, product, createdAt, 1);
  }

  public Order(Customer customer, Product product, Instant createdAt, int quantity) {
    this.customer = customer;
    this.customerName = customer.getName();
    this.product = product;
    this.createdAt = createdAt;
    this.status = "DRAFT";
    this.invoiceDate = LocalDate.now();
    this.paymentTermsDays = 30;
    this.dueDate = this.invoiceDate.plusDays(paymentTermsDays);
    this.fTaxApproved = true;
    this.quantity = normalizeQuantity(quantity);
    calculateAmounts(product);
    this.paidAmountMinor = 0L;
    this.refundedAmountMinor = 0L;
  }

  public Long getId() {
    return id;
  }

  public static Order draftFromInvoiceSnapshot(Order original, Instant createdAt) {
    // A credit must use historical invoice values, not the product's current price.
    Order copy = new Order();
    copy.customer = original.customer;
    copy.customerName = original.customerName;
    copy.product = original.product;
    copy.createdAt = createdAt;
    copy.status = "DRAFT";
    copy.invoiceDate = LocalDate.now();
    copy.dueDate = original.dueDate;
    copy.paymentTermsDays = original.paymentTermsDays;
    copy.fTaxApproved = original.fTaxApproved;
    copy.quantity = original.getQuantity();
    copy.ordinaryPrice = original.ordinaryPrice;
    copy.discountAmount = original.discountAmount;
    copy.discountLabel = original.discountLabel;
    copy.vatPercent = original.vatPercent;
    copy.creditedInvoiceNumber = original.invoiceNumber;
    copy.documentSnapshot = original.documentSnapshot == null ? null
        : original.documentSnapshot.withCreditedInvoiceNumber(original.invoiceNumber);
    copy.setAmounts(original.netAmount, original.vatAmount, original.totalAmount);
    copy.paidAmountMinor = 0L;
    copy.refundedAmountMinor = 0L;
    return copy;
  }

  public String getCustomerName() {
    return customerName;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public LocalDate getInvoiceDate() {
    return invoiceDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public int getPaymentTermsDays() {
    return paymentTermsDays;
  }

  public void setPaymentTermsDays(int paymentTermsDays) {
    this.paymentTermsDays = paymentTermsDays;
  }

  public boolean isFTaxApproved() {
    return fTaxApproved;
  }

  public String getOcrNumber() {
    return ocrNumber;
  }

  public void setOcrNumber(String ocrNumber) {
    this.ocrNumber = ocrNumber;
  }

  public String getPlusGiro() {
    return plusGiro;
  }

  public void setPlusGiro(String plusGiro) {
    this.plusGiro = plusGiro;
  }

  public String getPaymentRecipient() {
    return paymentRecipient;
  }

  public void setPaymentRecipient(String paymentRecipient) {
    this.paymentRecipient = paymentRecipient;
  }

  public LocalDate getPaidDate() {
    return paidDate;
  }

  public int getPaidAmount() {
    return paidAmount;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public LocalDate getRefundDate() {
    return refundDate;
  }

  public int getRefundedAmount() {
    return refundedAmount;
  }

  public String getRefundReference() {
    return refundReference;
  }

  public LocalDate getReminderSentDate() {
    return reminderSentDate;
  }

  public void setReminderSentDate(LocalDate reminderSentDate) {
    this.reminderSentDate = reminderSentDate;
  }

  public void addReminder(String method, String status, String recipientEmail) {
    this.reminderSentDate = LocalDate.now();
    addReminderHistory(method, status, recipientEmail);
  }

  public void addReminderHistory(String method, String status, String recipientEmail) {
    this.reminders.add(new InvoiceReminder(this, method, status, recipientEmail));
  }

  public boolean hasReminder(String method, String status) {
    return this.reminders.stream()
        .anyMatch(reminder -> method.equals(reminder.getMethod()) && status.equals(reminder.getStatus()));
  }

  public int getRemainingAmount() {
    if ("DRAFT".equals(status) || "CREDITED".equals(status) || creditInvoice) {
      return 0;
    }

    return wholeKrona(getRemainingAmountMinor(), "remainingAmount");
  }

  public boolean hasRemainingAmount() {
    if ("DRAFT".equals(status) || "CREDITED".equals(status) || creditInvoice) {
      return false;
    }
    return getRemainingAmountMinor() > 0;
  }

  public int getRefundableAmount() {
    if (!"CREDITED".equals(status)) {
      return 0;
    }

    return wholeKrona(Math.max(paidAmountMinorValue() - refundedAmountMinorValue(), 0L), "refundableAmount");
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public long getRemainingAmountMinor() {
    return Math.max(totalAmountMinorValue() - paidAmountMinorValue(), 0L);
  }

  public void registerPayment(LocalDate paidDate, int paymentAmount, String paymentReference) {
    if (paymentAmount <= 0) {
      throw new IllegalArgumentException("Payment amount must be greater than zero.");
    }
    long newPaidAmountMinor = Math.addExact(paidAmountMinorValue(), toMinorUnits(paymentAmount, "paymentAmount"));
    if (newPaidAmountMinor > totalAmountMinorValue()) {
      throw new IllegalArgumentException("Payment amount cannot be greater than the remaining invoice amount.");
    }
    this.paidDate = paidDate;
    this.paidAmountMinor = newPaidAmountMinor;
    this.paidAmount = wholeKrona(newPaidAmountMinor, "paidAmount");
    this.paymentReference = paymentReference;
    this.status = newPaidAmountMinor >= totalAmountMinorValue() ? "PAID" : "PARTIALLY_PAID";
    this.payments.add(new InvoicePayment(this, paidDate, paymentAmount, paymentReference));
  }

  public boolean hasPayment(LocalDate paymentDate, int paymentAmount, String paymentReference) {
    String normalizedReference = normalizePaymentReference(paymentReference);
    if (normalizedReference.isBlank()) {
      return false;
    }

    return this.payments.stream()
        .anyMatch(payment -> payment.getPaymentDate() != null
            && payment.getPaymentDate().equals(paymentDate)
            && payment.getAmountMinorValue() == toMinorUnits(paymentAmount, "paymentAmount")
            && normalizePaymentReference(payment.getReference()).equals(normalizedReference));
  }

  public void registerRefund(LocalDate refundDate, int refundAmount, String refundReference) {
    if (refundAmount <= 0) {
      throw new IllegalArgumentException("Refund amount must be greater than zero.");
    }
    long newRefundedAmountMinor = Math.addExact(refundedAmountMinorValue(), toMinorUnits(refundAmount, "refundAmount"));
    if (newRefundedAmountMinor > paidAmountMinorValue()) {
      throw new IllegalArgumentException("Refund amount cannot be greater than the refundable invoice amount.");
    }
    this.refundDate = refundDate;
    this.refundedAmountMinor = newRefundedAmountMinor;
    this.refundedAmount = wholeKrona(newRefundedAmountMinor, "refundedAmount");
    this.refundReference = refundReference;
  }

  public List<InvoicePayment> getPayments() {
    return payments;
  }

  public Set<InvoiceReminder> getReminders() {
    return reminders;
  }

  public Customer getCustomer() {
    return customer;
  }

  public Product getProduct() {
    return product;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getNetAmount() {
    return netAmount;
  }

  public int getVatAmount() {
    return vatAmount;
  }

  public int getVatPercent() {
    return vatPercent;
  }

  public int getTotalAmount() {
    return totalAmount;
  }

  public int getQuantity() {
    return quantity <= 0 ? 1 : quantity;
  }

  public int getOrdinaryPrice() {
    return ordinaryPrice;
  }

  public int getDiscountAmount() {
    return discountAmount;
  }

  public Long getNetAmountMinor() {
    return netAmountMinor;
  }

  public Long getVatAmountMinor() {
    return vatAmountMinor;
  }

  public Long getTotalAmountMinor() {
    return totalAmountMinor;
  }

  public Long getPaidAmountMinor() {
    return paidAmountMinor;
  }

  public Long getRefundedAmountMinor() {
    return refundedAmountMinor;
  }

  public Long getOrdinaryPriceMinor() {
    return ordinaryPriceMinor;
  }

  public Long getDiscountAmountMinor() {
    return discountAmountMinor;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public String getDiscountLabel() {
    return discountLabel;
  }

  public boolean isCreditInvoice() {
    return creditInvoice;
  }

  public void setCreditInvoice(boolean creditInvoice) {
    this.creditInvoice = creditInvoice;
  }

  public Long getCreditedInvoiceId() {
    return creditedInvoiceId;
  }

  public void setCreditedInvoiceId(Long creditedInvoiceId) {
    this.creditedInvoiceId = creditedInvoiceId;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getCreditedInvoiceNumber() {
    return creditedInvoiceNumber;
  }

  public void setCreditedInvoiceNumber(String creditedInvoiceNumber) {
    this.creditedInvoiceNumber = creditedInvoiceNumber;
  }

  public void setAmounts(int netAmount, int vatAmount, int totalAmount) {
    if ((long) netAmount + vatAmount != totalAmount) {
      throw new IllegalArgumentException("Invoice total must equal net amount plus VAT amount.");
    }

    boolean hasPositiveAmount = netAmount > 0 || vatAmount > 0 || totalAmount > 0;
    boolean hasNegativeAmount = netAmount < 0 || vatAmount < 0 || totalAmount < 0;
    if (hasPositiveAmount && hasNegativeAmount) {
      throw new IllegalArgumentException("Invoice amounts must not mix positive and negative values.");
    }

    this.netAmount = netAmount;
    this.vatAmount = vatAmount;
    this.totalAmount = totalAmount;
    this.netAmountMinor = toMinorUnits(netAmount, "netAmount");
    this.vatAmountMinor = toMinorUnits(vatAmount, "vatAmount");
    this.totalAmountMinor = toMinorUnits(totalAmount, "totalAmount");
  }

  public String getStripeCheckoutSessionId() {
    return stripeCheckoutSessionId;
  }

  public void setStripeCheckoutSessionId(String stripeCheckoutSessionId) {
    this.stripeCheckoutSessionId = stripeCheckoutSessionId;
  }

  private void calculateAmounts(Product product) {
    int invoiceQuantity = getQuantity();
    if (product.getPrice() < 0 || product.getEffectivePrice() < 0) {
      throw new IllegalArgumentException("Invoice prices must not be negative.");
    }
    this.ordinaryPrice = Math.multiplyExact(product.getPrice(), invoiceQuantity);
    this.netAmount = Math.multiplyExact(product.getEffectivePrice(), invoiceQuantity);
    this.discountAmount = Math.max(ordinaryPrice - netAmount, 0);
    this.ordinaryPriceMinor = toMinorUnits(this.ordinaryPrice, "ordinaryPrice");
    this.discountAmountMinor = toMinorUnits(this.discountAmount, "discountAmount");
    this.discountLabel = product.getDiscountLabel();
    if (!se.cloudshop.product.VatRate.isSupported(product.getVatPercent())) {
      throw new IllegalArgumentException("Unsupported invoice VAT rate.");
    }
    this.vatPercent = product.getVatPercent();
    long calculatedVatAmountMinor = Math.multiplyExact((long) netAmount, vatPercent);
    if (calculatedVatAmountMinor % 100L != 0L) {
      throw new IllegalArgumentException(
          "Invoice VAT contains ore that the current whole-krona invoice model cannot represent. Complete the minor-unit migration before issuing this invoice.");
    }
    this.vatAmount = Math.toIntExact(calculatedVatAmountMinor / 100L);
    this.totalAmount = Math.addExact(netAmount, vatAmount);
    this.netAmountMinor = toMinorUnits(this.netAmount, "netAmount");
    this.vatAmountMinor = calculatedVatAmountMinor;
    this.totalAmountMinor = Math.addExact(this.netAmountMinor, this.vatAmountMinor);
  }

  private static long toMinorUnits(int amount, String field) {
    try {
      return Math.multiplyExact((long) amount, 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Invoice " + field + " is outside the supported money range.", exception);
    }
  }

  private static int wholeKrona(long amountMinor, String field) {
    if (amountMinor % 100L != 0) {
      throw new IllegalStateException("Invoice " + field + " contains ore that the legacy API cannot represent.");
    }
    try {
      return Math.toIntExact(amountMinor / 100L);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("Invoice " + field + " is outside the supported whole-krona API range.", exception);
    }
  }

  private long totalAmountMinorValue() {
    return minorValue(totalAmountMinor, totalAmount, "totalAmount");
  }

  private long paidAmountMinorValue() {
    return minorValue(paidAmountMinor, paidAmount, "paidAmount");
  }

  private long refundedAmountMinorValue() {
    return minorValue(refundedAmountMinor, refundedAmount, "refundedAmount");
  }

  private static long minorValue(Long shadow, int legacy, String field) {
    return shadow == null ? toMinorUnits(legacy, field) : shadow;
  }

  @PostLoad
  private void validateMinorUnitShadow() {
    // The legacy whole-krona columns remain authoritative during the staged migration.
    // Rebuild shadows on read so older imports and controlled reconciliation updates
    // cannot leave reports using stale shadow values.
    synchronizeMinorUnits();
  }

  @PrePersist
  @PreUpdate
  private void synchronizeMinorUnits() {
    netAmountMinor = toMinorUnits(netAmount, "netAmount");
    vatAmountMinor = toMinorUnits(vatAmount, "vatAmount");
    totalAmountMinor = toMinorUnits(totalAmount, "totalAmount");
    paidAmountMinor = toMinorUnits(paidAmount, "paidAmount");
    refundedAmountMinor = toMinorUnits(refundedAmount, "refundedAmount");
    ordinaryPriceMinor = toMinorUnits(ordinaryPrice, "ordinaryPrice");
    discountAmountMinor = toMinorUnits(discountAmount, "discountAmount");
  }

  private int normalizeQuantity(int quantity) {
    return quantity <= 0 ? 1 : quantity;
  }

  private String normalizePaymentReference(String reference) {
    return reference == null ? "" : reference.trim().toLowerCase();
  }
}
