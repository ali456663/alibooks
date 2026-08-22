package se.cloudshop.supplier;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.export.CsvEscaper;

@RestController
public class SupplierController {

  private final AuthHeader authHeader;
  private final SupplierRepository supplierRepository;
  private final SupplierInvoiceRepository supplierInvoiceRepository;
  private final AuditService auditService;
  private final AccountingService accountingService;

  public SupplierController(
      AuthHeader authHeader,
      SupplierRepository supplierRepository,
      SupplierInvoiceRepository supplierInvoiceRepository,
      AuditService auditService,
      AccountingService accountingService
  ) {
    this.authHeader = authHeader;
    this.supplierRepository = supplierRepository;
    this.supplierInvoiceRepository = supplierInvoiceRepository;
    this.auditService = auditService;
    this.accountingService = accountingService;
  }

  @GetMapping("/suppliers")
  public List<Supplier> getSuppliers(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return supplierRepository.findAllByOrderByNameAsc();
  }

  @PostMapping("/suppliers")
  @ResponseStatus(HttpStatus.CREATED)
  public Supplier createSupplier(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateSupplierRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    if (request.name() == null || request.name().trim().length() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier name is required.");
    }

    Supplier supplier = supplierRepository.save(new Supplier(
        request.name().trim(),
        clean(request.email()),
        clean(request.orgNumber()),
        clean(request.phone()),
        clean(request.paymentInfo())
    ));

    auditService.record("supplier", "supplier", supplier.getId(), "created", supplier.getName(), "Supplier created", 0, authorizationHeader);
    return supplier;
  }

  @GetMapping("/supplier-invoices")
  public List<SupplierInvoice> getSupplierInvoices(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return supplierInvoiceRepository.findAllByOrderByDueDateAscIdAsc();
  }

  @PostMapping("/supplier-invoices")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public SupplierInvoice createSupplierInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateSupplierInvoiceRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    if (request.supplierId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier is required.");
    }

    Supplier supplier = supplierRepository.findById(request.supplierId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found."));

    if (request.description() == null || request.description().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
    }

    if (request.totalAmount() <= 0 || request.vatAmount() < 0 || request.vatAmount() > request.totalAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check total amount and VAT.");
    }

    LocalDate invoiceDate = request.invoiceDate() == null ? LocalDate.now() : request.invoiceDate();
    LocalDate dueDate = request.dueDate() == null ? invoiceDate.plusDays(30) : request.dueDate();
    if (dueDate.isBefore(invoiceDate)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date cannot be before supplier invoice date.");
    }

    accountingService.requireUnlockedAccountingDate(invoiceDate);
    String reference = clean(request.reference());
    boolean selfBilling = Boolean.TRUE.equals(request.selfBilling());
    String buyerName = clean(request.buyerName());
    String buyerReference = clean(request.buyerReference());
    String approvalReference = clean(request.approvalReference());

    if (selfBilling) {
      if (buyerName.length() < 2) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buyer name is required for self-billing invoices.");
      }
      if (approvalReference.length() < 3) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval or agreement reference is required for self-billing invoices.");
      }
    }

    if (!reference.isBlank() && supplierInvoiceRepository.existsBySupplier_IdAndReferenceIgnoreCase(supplier.getId(), reference)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier invoice reference already exists for this supplier.");
    }

    SupplierInvoice invoice = supplierInvoiceRepository.save(new SupplierInvoice(
        supplier,
        invoiceDate,
        dueDate,
        request.description().trim(),
        reference,
        request.totalAmount(),
        request.vatAmount(),
        request.category() == null || request.category().isBlank() ? "5420" : request.category(),
        selfBilling,
        buyerName,
        buyerReference,
        approvalReference
    ));

    accountingService.createSupplierInvoiceEntries(invoice);
    auditService.record("supplier_invoice", "supplier_invoice", invoice.getId(), "created", invoice.getReference(), selfBilling ? "Self-billing supplier invoice created" : "Supplier invoice created", invoice.getTotalAmount(), authorizationHeader);
    return invoice;
  }

  @PatchMapping("/supplier-invoices/{id}/status")
  @Transactional
  public SupplierInvoice updateSupplierInvoiceStatus(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody UpdateSupplierInvoiceStatusRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    SupplierInvoice invoice = supplierInvoiceRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier invoice not found."));

    String status = normalizeStatus(request == null ? "" : request.status());
    requireSupplierInvoiceStatusChangeAllowed(invoice, status);
    if ("paid".equals(status)) {
      LocalDate paymentDate = request.paidAt() == null ? LocalDate.now() : request.paidAt();
      int paidAmount = request.paidAmount() == null ? invoice.getRemainingAmount() : request.paidAmount();
      if (paidAmount <= 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid amount must be greater than zero.");
      }
      if (paidAmount > invoice.getRemainingAmount()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid amount cannot be greater than remaining amount.");
      }
      if (invoice.hasPayment(paymentDate, paidAmount, request.paymentReference())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "This supplier payment is already registered on the invoice.");
      }
      accountingService.createSupplierInvoiceEntries(invoice);
      accountingService.createSupplierInvoicePaymentEntries(invoice, paymentDate, paidAmount, request.paymentReference());
      invoice.registerPayment(paymentDate, paidAmount, request.paymentReference());
    } else {
      invoice.updateStatus(status, request.paidAt());
    }
    if ("booked".equals(status)) {
      accountingService.createSupplierInvoiceEntries(invoice);
    }
    SupplierInvoice savedInvoice = supplierInvoiceRepository.save(invoice);
    auditService.record("supplier_invoice", "supplier_invoice", savedInvoice.getId(), "status_updated", status, "Supplier invoice status updated", savedInvoice.getTotalAmount(), authorizationHeader);
    return savedInvoice;
  }

  private void requireSupplierInvoiceStatusChangeAllowed(SupplierInvoice invoice, String nextStatus) {
    if ("paid".equals(nextStatus) || "booked".equals(nextStatus)) {
      return;
    }

    boolean hasPayments = invoice.getPaidAmount() > 0 || "paid".equals(invoice.getStatus()) || "partial".equals(invoice.getStatus());
    if (hasPayments) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Supplier invoice has payments. Register a correction instead of moving the status backwards."
      );
    }

    boolean hasBookkeeping = accountingService.hasSupplierInvoiceEntries(invoice);
    if (hasBookkeeping && ("cancelled".equals(nextStatus) || "unpaid".equals(nextStatus) || "prepared".equals(nextStatus))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Supplier invoice has bookkeeping. Create a correction instead of changing the status backwards."
      );
    }
  }

  @PostMapping("/supplier-invoices/{id}/cancel")
  @Transactional
  public SupplierInvoice cancelSupplierInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody(required = false) CancelSupplierInvoiceRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    SupplierInvoice invoice = supplierInvoiceRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier invoice not found."));

    if ("cancelled".equals(invoice.getStatus())) {
      return invoice;
    }

    LocalDate cancellationDate = request == null || request.cancellationDate() == null
        ? LocalDate.now()
        : request.cancellationDate();
    accountingService.requireUnlockedAccountingDate(cancellationDate);
    List<JournalEntry> correctionEntries = accountingService.hasSupplierInvoiceEntries(invoice)
        ? accountingService.createSupplierInvoiceCancellationEntries(
            invoice,
            cancellationDate
        )
        : List.of();
    String correctionVoucherNumber = correctionEntries.isEmpty() ? "" : correctionEntries.get(0).getVoucherNumber();
    invoice.markCancelled(cancellationDate, correctionVoucherNumber);
    SupplierInvoice savedInvoice = supplierInvoiceRepository.save(invoice);
    auditService.record("supplier_invoice", "supplier_invoice", savedInvoice.getId(), "cancelled", correctionVoucherNumber, "Supplier invoice cancelled with correction voucher", savedInvoice.getTotalAmount(), authorizationHeader);
    return savedInvoice;
  }

  @GetMapping("/supplier-invoices/export")
  public ResponseEntity<byte[]> exportSupplierInvoices(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);

    StringBuilder csv = new StringBuilder();
    csv.append("Fakturadatum;Forfallodatum;Status;Typ;Leverantor;E-post;Org/personnummer;Kopare;Koparreferens;Godkannande/avtal;Referens;Beskrivning;Kategori;Netto;Moms;Totalt;Betalt;Kvar;Betaldatum;Betalreferens;Betalhistorik;Makulerad datum;Rattelseverifikat\r\n");
    List<SupplierInvoice> invoices = supplierInvoiceRepository.findAllByOrderByDueDateAscIdAsc();
    invoices.forEach(invoice -> csv.append(String.join(";",
        cell(invoice.getInvoiceDate()),
        cell(invoice.getDueDate()),
        cell(invoice.getStatus()),
        cell(invoice.isSelfBilling() ? "Sjalvfaktura" : "Leverantorsfaktura"),
        cell(invoice.getSupplierName()),
        cell(invoice.getSupplierEmail()),
        cell(invoice.getSupplierOrgNumber()),
        cell(invoice.getBuyerName()),
        cell(invoice.getBuyerReference()),
        cell(invoice.getApprovalReference()),
        cell(invoice.getReference()),
        cell(invoice.getDescription()),
        cell(invoice.getCategory()),
        cell(invoice.getNetAmount()),
        cell(invoice.getVatAmount()),
        cell(invoice.getTotalAmount()),
        cell(invoice.getPaidAmount()),
        cell(invoice.getRemainingAmount()),
        cell(invoice.getPaidAt()),
        cell(invoice.getPaymentReference()),
        cell(invoice.getPaymentHistory()),
        cell(invoice.getCancelledAt()),
        cell(invoice.getCancellationVoucherNumber())
    )).append("\r\n"));
    int totalAmount = invoices.stream().mapToInt(SupplierInvoice::getTotalAmount).sum();

    auditService.record(
        "export",
        "supplier_invoices",
        "supplier_invoices",
        "supplier_invoices_exported",
        "supplier_invoices",
        "Supplier invoices exported. Rows: " + invoices.size() + ".",
        totalAmount,
        authorizationHeader
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
    headers.setContentDisposition(ContentDisposition.attachment().filename("leverantorsfakturor.csv").build());
    return new ResponseEntity<>(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
  }

  @DeleteMapping("/supplier-invoices/{id}")
  @Transactional
  public void deleteSupplierInvoice(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    SupplierInvoice invoice = supplierInvoiceRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier invoice not found."));

    if (accountingService.hasSupplierInvoiceEntries(invoice) || invoice.getPaidAmount() > 0 || "paid".equals(invoice.getStatus()) || "partial".equals(invoice.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier invoice has bookkeeping or payments. Create a correction or cancellation instead of deleting it.");
    }

    accountingService.requireUnlockedAccountingDate(invoice.getInvoiceDate());
    supplierInvoiceRepository.delete(invoice);
    auditService.record("supplier_invoice", "supplier_invoice", id, "deleted", invoice.getReference(), "Supplier invoice deleted", invoice.getTotalAmount(), authorizationHeader);
  }

  private String normalizeStatus(String status) {
    String normalized = status == null ? "" : status.trim().toLowerCase();
    if (List.of("unpaid", "partial", "paid", "prepared", "booked", "cancelled").contains(normalized)) {
      return normalized;
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid supplier invoice status.");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String cell(Object value) {
    return CsvEscaper.escape(String.valueOf(value == null ? "" : value));
  }
}
