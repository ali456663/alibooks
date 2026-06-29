package se.cloudshop.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import se.cloudshop.customer.Customer;
import se.cloudshop.product.Product;

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
  private boolean fTaxApproved;
  private String ocrNumber;
  private String plusGiro;
  private String paymentRecipient;
  private LocalDate paidDate;
  private int paidAmount;
  private String paymentReference;
  private LocalDate reminderSentDate;

  @ManyToOne
  private Customer customer;

  @ManyToOne
  private Product product;

  private Instant createdAt;
  private String status;
  private int netAmount;
  private int vatAmount;
  private int totalAmount;
  private int quantity = 1;
  private int ordinaryPrice;
  private int discountAmount;
  private String discountLabel;
  private String stripeCheckoutSessionId;
  private boolean creditInvoice;
  private Long creditedInvoiceId;

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
  }

  public Long getId() {
    return id;
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
    return Math.max(totalAmount - paidAmount, 0);
  }

  public boolean hasRemainingAmount() {
    return getRemainingAmount() > 0;
  }

  public void registerPayment(LocalDate paidDate, int paymentAmount, String paymentReference) {
    this.paidDate = paidDate;
    this.paidAmount += paymentAmount;
    this.paymentReference = paymentReference;
    this.status = this.paidAmount >= this.totalAmount ? "PAID" : "PARTIALLY_PAID";
    this.payments.add(new InvoicePayment(this, paidDate, paymentAmount, paymentReference));
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

  public void setAmounts(int netAmount, int vatAmount, int totalAmount) {
    this.netAmount = netAmount;
    this.vatAmount = vatAmount;
    this.totalAmount = totalAmount;
  }

  public String getStripeCheckoutSessionId() {
    return stripeCheckoutSessionId;
  }

  public void setStripeCheckoutSessionId(String stripeCheckoutSessionId) {
    this.stripeCheckoutSessionId = stripeCheckoutSessionId;
  }

  private void calculateAmounts(Product product) {
    int invoiceQuantity = getQuantity();
    this.ordinaryPrice = product.getPrice() * invoiceQuantity;
    this.netAmount = product.getEffectivePrice() * invoiceQuantity;
    this.discountAmount = Math.max(ordinaryPrice - netAmount, 0);
    this.discountLabel = product.getDiscountLabel();
    this.vatAmount = Math.round(netAmount * 0.25f);
    this.totalAmount = netAmount + vatAmount;
  }

  private int normalizeQuantity(int quantity) {
    return quantity <= 0 ? 1 : quantity;
  }
}
