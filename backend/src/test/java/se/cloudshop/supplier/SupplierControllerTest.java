package se.cloudshop.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.Account;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class SupplierControllerTest {

  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
  private final SupplierInvoiceRepository supplierInvoiceRepository = mock(SupplierInvoiceRepository.class);
  private final AuditService auditService = mock(AuditService.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final SupplierController supplierController = new SupplierController(
      authHeader,
      supplierRepository,
      supplierInvoiceRepository,
      auditService,
      accountingService
  );

  @Test
  void createSupplierRequiresJwtToken() {
    CreateSupplierRequest request = new CreateSupplierRequest("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");

    assertThatThrownBy(() -> supplierController.createSupplier(null, request))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void createSupplierInvoiceStoresNetVatAndTotal() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    setSupplierId(supplier, 1L);
    when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
    when(supplierInvoiceRepository.save(any(SupplierInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SupplierInvoice invoice = supplierController.createSupplierInvoice(
        "Bearer " + authHeaderToken(),
        new CreateSupplierInvoiceRequest(
            1L,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            "Adobe Creative Cloud",
            "OCR-123",
            1250,
            250,
            "5420"
        )
    );

    assertThat(invoice.getSupplierName()).isEqualTo("Adobe");
    assertThat(invoice.getNetAmount()).isEqualTo(1000);
    assertThat(invoice.getVatAmount()).isEqualTo(250);
    assertThat(invoice.getTotalAmount()).isEqualTo(1250);
    assertThat(invoice.getStatus()).isEqualTo("unpaid");
    verify(accountingService).requireUnlockedAccountingDate(LocalDate.of(2026, 7, 1));
    verify(accountingService).createSupplierInvoiceEntries(invoice);
  }

  @Test
  void createSupplierInvoiceRequiresExplicitInvoiceDate() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    setSupplierId(supplier, 1L);
    when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

    assertThatThrownBy(() -> supplierController.createSupplierInvoice(
        "Bearer " + authHeaderToken(),
        new CreateSupplierInvoiceRequest(
            1L,
            null,
            LocalDate.of(2026, 7, 31),
            "Adobe Creative Cloud",
            "OCR-DATE",
            1250,
            250,
            "5420"
        )
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Supplier invoice date is required");

    verify(supplierInvoiceRepository, never()).save(any(SupplierInvoice.class));
    verify(accountingService, never()).createSupplierInvoiceEntries(any(SupplierInvoice.class));
  }

  @Test
  void createSupplierInvoiceRejectsDuplicateReferenceForSameSupplier() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    setSupplierId(supplier, 1L);
    when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
    when(supplierInvoiceRepository.existsBySupplier_IdAndReferenceIgnoreCase(1L, "OCR-123")).thenReturn(true);

    assertThatThrownBy(() -> supplierController.createSupplierInvoice(
        "Bearer " + authHeaderToken(),
        new CreateSupplierInvoiceRequest(
            1L,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            "Adobe Creative Cloud",
            "OCR-123",
            1250,
            250,
            "5420"
        )
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("reference already exists");

    verify(supplierInvoiceRepository, never()).save(any(SupplierInvoice.class));
    verify(accountingService, never()).createSupplierInvoiceEntries(any(SupplierInvoice.class));
  }

  @Test
  void createSupplierInvoiceRejectsDueDateBeforeInvoiceDate() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    setSupplierId(supplier, 1L);
    when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

    assertThatThrownBy(() -> supplierController.createSupplierInvoice(
        "Bearer " + authHeaderToken(),
        new CreateSupplierInvoiceRequest(
            1L,
            LocalDate.of(2026, 7, 31),
            LocalDate.of(2026, 7, 1),
            "Adobe Creative Cloud",
            "OCR-123",
            1250,
            250,
            "5420"
        )
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Due date cannot be before supplier invoice date");

    verify(supplierInvoiceRepository, never()).save(any(SupplierInvoice.class));
    verify(accountingService, never()).createSupplierInvoiceEntries(any(SupplierInvoice.class));
  }

  @Test
  void createSelfBillingSupplierInvoiceRequiresApprovalReference() {
    Supplier supplier = new Supplier("Muscle Partner", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    setSupplierId(supplier, 1L);
    when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

    assertThatThrownBy(() -> supplierController.createSupplierInvoice(
        "Bearer " + authHeaderToken(),
        new CreateSupplierInvoiceRequest(
            1L,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            "Commission payout",
            "SELF-123",
            1250,
            250,
            "4010",
            true,
            "AliBooks Buyer",
            "BUY-123",
            ""
        )
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Approval or agreement reference is required");

    verify(supplierInvoiceRepository, never()).save(any(SupplierInvoice.class));
    verify(accountingService, never()).createSupplierInvoiceEntries(any(SupplierInvoice.class));
  }

  @Test
  void createSelfBillingSupplierInvoiceStoresBuyerAndApprovalMetadata() {
    Supplier supplier = new Supplier("Muscle Partner", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    setSupplierId(supplier, 1L);
    when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
    when(supplierInvoiceRepository.save(any(SupplierInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SupplierInvoice invoice = supplierController.createSupplierInvoice(
        "Bearer " + authHeaderToken(),
        new CreateSupplierInvoiceRequest(
            1L,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            "Commission payout",
            "SELF-123",
            1250,
            250,
            "4010",
            true,
            "AliBooks Buyer",
            "BUY-123",
            "Agreement-2026-07"
        )
    );

    assertThat(invoice.isSelfBilling()).isTrue();
    assertThat(invoice.getBuyerName()).isEqualTo("AliBooks Buyer");
    assertThat(invoice.getBuyerReference()).isEqualTo("BUY-123");
    assertThat(invoice.getApprovalReference()).isEqualTo("Agreement-2026-07");
    verify(accountingService).createSupplierInvoiceEntries(invoice);
  }

  @Test
  void supplierInvoiceCanBePartlyPaid() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );
    setSupplierInvoiceId(invoice, 10L);
    when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
    when(supplierInvoiceRepository.saveAndFlush(any(SupplierInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SupplierInvoice updatedInvoice = supplierController.updateSupplierInvoiceStatus(
        "Bearer " + authHeaderToken(),
        10L,
        new UpdateSupplierInvoiceStatusRequest("paid", LocalDate.of(2026, 7, 20), 100, "BANK-1")
    );

    assertThat(updatedInvoice.getStatus()).isEqualTo("partial");
    assertThat(updatedInvoice.getPaidAmount()).isEqualTo(100);
    assertThat(updatedInvoice.getRemainingAmount()).isEqualTo(1150);
    assertThat(updatedInvoice.getPaymentReference()).isEqualTo("BANK-1");
    assertThat(updatedInvoice.getPaymentHistory()).contains("2026-07-20 - 100 SEK - BANK-1");
    verify(accountingService).createSupplierInvoiceEntries(invoice);
    verify(accountingService).createSupplierInvoicePaymentEntries(invoice, LocalDate.of(2026, 7, 20), 100, "BANK-1");
  }

  @Test
  void supplierPaymentRequiresReferenceForReliableRetryProtection() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-REF",
        1250,
        250,
        "5420"
    );
    setSupplierInvoiceId(invoice, 13L);
    when(supplierInvoiceRepository.findById(13L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> supplierController.updateSupplierInvoiceStatus(
        "Bearer " + authHeaderToken(),
        13L,
        new UpdateSupplierInvoiceStatusRequest("paid", LocalDate.of(2026, 7, 20), 100, " ")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Payment reference is required");

    assertThat(invoice.getPaidAmount()).isZero();
    verify(supplierInvoiceRepository, never()).save(invoice);
    verify(accountingService, never()).createSupplierInvoicePaymentEntries(any(SupplierInvoice.class), any(), anyInt(), any());
  }

  @Test
  void supplierPaymentRequiresExplicitPaymentDate() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-DATE",
        1250,
        250,
        "5420"
    );
    setSupplierInvoiceId(invoice, 12L);
    when(supplierInvoiceRepository.findById(12L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> supplierController.updateSupplierInvoiceStatus(
        "Bearer " + authHeaderToken(),
        12L,
        new UpdateSupplierInvoiceStatusRequest("paid", null, 100, "BANK-DATE")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Supplier payment date is required");

    assertThat(invoice.getPaidAmount()).isZero();
    verify(supplierInvoiceRepository, never()).save(any(SupplierInvoice.class));
    verify(accountingService, never()).createSupplierInvoiceEntries(invoice);
    verify(accountingService, never()).createSupplierInvoicePaymentEntries(any(SupplierInvoice.class), any(), anyInt(), any());
  }

  @Test
  void supplierInvoiceStatusCannotChangeAfterAccountingPeriodIsLocked() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-LOCKED",
        1250,
        250,
        "5420"
    );
    setSupplierInvoiceId(invoice, 11L);
    when(supplierInvoiceRepository.findById(11L)).thenReturn(Optional.of(invoice));
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Bokforingen ar last"))
        .when(accountingService)
        .requireUnlockedAccountingDate(LocalDate.of(2026, 7, 1));

    assertThatThrownBy(() -> supplierController.updateSupplierInvoiceStatus(
        "Bearer " + authHeaderToken(),
        11L,
        new UpdateSupplierInvoiceStatusRequest("prepared", null, null, "")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Bokforingen ar last");

    assertThat(invoice.getStatus()).isEqualTo("unpaid");
    verify(supplierInvoiceRepository, never()).save(any(SupplierInvoice.class));
    verify(accountingService, never()).createSupplierInvoiceEntries(invoice);
    verify(accountingService, never()).createSupplierInvoicePaymentEntries(any(SupplierInvoice.class), any(), anyInt(), any());
  }

  @Test
  void supplierInvoiceRejectsDuplicatePaymentReference() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );
    invoice.registerPayment(LocalDate.of(2026, 7, 20), 100, "BANK-1");
    setSupplierInvoiceId(invoice, 10L);
    when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> supplierController.updateSupplierInvoiceStatus(
        "Bearer " + authHeaderToken(),
        10L,
        new UpdateSupplierInvoiceStatusRequest("paid", LocalDate.of(2026, 7, 20), 100, "BANK-1")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("supplier payment is already registered");

    verify(accountingService, never()).createSupplierInvoiceEntries(invoice);
    verify(accountingService, never()).createSupplierInvoicePaymentEntries(invoice, LocalDate.of(2026, 7, 20), 100, "BANK-1");
  }

  @Test
  void supplierInvoiceExportRecordsAuditEventWithRowsAndTotalAmount() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );
    when(supplierInvoiceRepository.findAllByOrderByDueDateAscIdAsc()).thenReturn(List.of(invoice));
    String authorizationHeader = "Bearer " + authHeaderToken();

    ResponseEntity<byte[]> response = supplierController.exportSupplierInvoices(authorizationHeader);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("Leverantor")
        .contains("Adobe")
        .contains("OCR-123");
    verify(auditService).record(
        "export",
        "supplier_invoices",
        "supplier_invoices",
        "supplier_invoices_exported",
        "supplier_invoices",
        "Supplier invoices exported. Rows: 1.",
        1250,
        authorizationHeader
    );
  }

  @Test
  void supplierInvoiceExportStopsWhenMinorShadowContainsOre() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-ORE",
        1250,
        250,
        "5420"
    );
    setField(invoice, "netAmountMinor", 100050L);
    when(supplierInvoiceRepository.findAllByOrderByDueDateAscIdAsc()).thenReturn(List.of(invoice));

    assertThatThrownBy(() -> supplierController.exportSupplierInvoices("Bearer " + authHeaderToken()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("innehåller ören");

    verifyNoInteractions(auditService);
  }

  @Test
  void deleteSupplierInvoiceRejectsBookedInvoice() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );
    setSupplierInvoiceId(invoice, 10L);
    when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
    when(accountingService.hasSupplierInvoiceEntries(invoice)).thenReturn(true);

    assertThatThrownBy(() -> supplierController.deleteSupplierInvoice("Bearer " + authHeaderToken(), 10L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Create a correction or cancellation instead of deleting it");

    verify(supplierInvoiceRepository, never()).delete(invoice);
  }

  @Test
  void cancelSupplierInvoiceCreatesCorrectionVoucherForBookedInvoice() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-123",
        1250,
        250,
        "5420"
    );
    setSupplierInvoiceId(invoice, 10L);
    JournalEntry correctionEntry = new JournalEntry(null, null, invoice, new Account("2440", "Leverantorsskulder"), "R-2026-0001", 1250, 0, "Cancellation", LocalDate.of(2026, 7, 10));
    when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
    when(accountingService.hasSupplierInvoiceEntries(invoice)).thenReturn(true);
    when(accountingService.createSupplierInvoiceCancellationEntries(invoice, LocalDate.of(2026, 7, 10)))
        .thenReturn(List.of(correctionEntry));
    when(supplierInvoiceRepository.save(invoice)).thenReturn(invoice);

    SupplierInvoice cancelled = supplierController.cancelSupplierInvoice(
        "Bearer " + authHeaderToken(),
        10L,
        new CancelSupplierInvoiceRequest(LocalDate.of(2026, 7, 10))
    );

    assertThat(cancelled.getStatus()).isEqualTo("cancelled");
    assertThat(cancelled.getCancellationVoucherNumber()).isEqualTo("R-2026-0001");
    verify(accountingService).requireUnlockedAccountingDate(LocalDate.of(2026, 7, 10));
    verify(accountingService).createSupplierInvoiceCancellationEntries(invoice, LocalDate.of(2026, 7, 10));
    verify(supplierInvoiceRepository).save(invoice);
  }

  @Test
  void supplierCancellationRequiresExplicitCancellationDate() {
    Supplier supplier = new Supplier("Adobe", "invoice@example.com", "556000-0000", "", "Bankgiro 123-4567");
    SupplierInvoice invoice = new SupplierInvoice(
        supplier,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "Adobe Creative Cloud",
        "OCR-DATE",
        1250,
        250,
        "5420"
    );
    setSupplierInvoiceId(invoice, 13L);
    when(supplierInvoiceRepository.findById(13L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> supplierController.cancelSupplierInvoice(
        "Bearer " + authHeaderToken(),
        13L,
        new CancelSupplierInvoiceRequest(null)
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Supplier cancellation date is required");

    assertThat(invoice.getStatus()).isEqualTo("unpaid");
    verify(supplierInvoiceRepository, never()).save(any(SupplierInvoice.class));
    verify(accountingService, never()).createSupplierInvoiceCancellationEntries(any(SupplierInvoice.class), any());
  }

  private String authHeaderToken() {
    return new JwtService("test_secret").createToken("ali@example.com");
  }

  private void setSupplierId(Supplier supplier, Long id) {
    setField(supplier, "id", id);
  }

  private void setSupplierInvoiceId(SupplierInvoice invoice, Long id) {
    setField(invoice, "id", id);
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
