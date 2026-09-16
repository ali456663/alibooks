package se.cloudshop.invoice;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.cloudshop.customer.Customer;
import se.cloudshop.customer.CreateCustomerRequest;
import se.cloudshop.order.Order;
import se.cloudshop.product.Product;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

class InvoiceDocumentTest {
  private final SettingsService service = mock(SettingsService.class);
  private final InvoicePdfService pdf = new InvoicePdfService(service);
  private final AppSettings settings = new AppSettings();
  private final Customer customer = new Customer("Original customer", "original@example.invalid", "test-number",
      "Original address", "111", "12345", "Original city");
  private final Product product = new Product("Original service", "Test", 100);

  private Order invoice(boolean snapshot) {
    settings.setCompanyName("Original issuer");
    settings.setContactEmail("issuer@example.invalid");
    settings.setFTaxApproved(false);
    when(service.getSettings()).thenReturn(settings);
    Order invoice = new Order(customer, product, Instant.now());
    invoice.setInvoiceNumber("F-TEST-1");
    invoice.setPlusGiro("Original account");
    invoice.setOcrNumber("Original OCR");
    invoice.setPaymentRecipient("Original recipient");
    if (snapshot) invoice.captureDocumentSnapshot(settings);
    return invoice;
  }

  @ParameterizedTest
  @ValueSource(strings = {"customer", "product-name", "product-price", "issuer", "contact", "f-tax", "payment-details"})
  void registerChangesDoNotRewriteInvoicePdf(String field) throws Exception {
    Order invoice = invoice(true);
    String original = text(pdf.createInvoicePdf(invoice));
    switch (field) {
      case "customer" -> customer.updateFrom(new CreateCustomerRequest("New customer", "new@example.invalid", "new-number", "New address", "222", "98765", "New city"));
      case "product-name" -> product.setName("New service");
      case "product-price" -> product.setPrice(999);
      case "issuer" -> settings.setCompanyName("New issuer");
      case "contact" -> settings.setContactEmail("new@example.invalid");
      case "f-tax" -> settings.setFTaxApproved(true);
      case "payment-details" -> { invoice.setPlusGiro("New account"); invoice.setOcrNumber("New OCR"); invoice.setPaymentRecipient("New recipient"); }
    }
    assertThat(text(pdf.createInvoicePdf(invoice))).isEqualTo(original);
    verify(service, never()).getSettings();
    assertThat(original).contains("Original customer", "Original service", "Original issuer", "Original account");
    assertThat(original).doesNotContain("Godkand for F-skatt", "Reconstructed copy");
  }

  @Test
  void nameOnlyCustomerIsNotLost() throws Exception {
    Order invoice = new Order("Name only customer", product, Instant.now());
    invoice.captureDocumentSnapshot(settings);
    assertThat(text(pdf.createInvoicePdf(invoice))).contains("Name only customer");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 50, 125})
  void paymentStatusDoesNotAskForTheOriginalTotalAgain(int paid) throws Exception {
    Order invoice = invoice(true);
    invoice.setStatus("SENT");
    if (paid > 0) invoice.registerPayment(LocalDate.now(), paid, "test");
    String content = text(pdf.createInvoicePdf(invoice));
    assertThat(content).contains("Paid: " + paid + " SEK", "Remaining: " + (125 - paid) + " SEK");
  }

  @Test
  void creditUsesOriginalSnapshotAndIsNotAPaymentRequest() throws Exception {
    Order original = invoice(true);
    product.setName("Changed after original");
    Order credit = Order.draftFromInvoiceSnapshot(original, Instant.now());
    credit.setCreditInvoice(true);
    credit.setCreditedInvoiceId(123L);
    credit.setAmounts(-100, -25, -125);
    credit.setStatus("SENT");
    String content = text(pdf.createInvoicePdf(credit));
    assertThat(content).contains("Credit note", "Original service", "Credits invoice ID: 123", "-125 SEK", "Not a payment request");
    assertThat(content).doesNotContain("Remaining:", "Changed after original");
  }

  @Test
  void legacyPdfIsMarkedReconstructedWithoutWritingSnapshot() throws Exception {
    Order legacy = invoice(false);
    assertThat(text(pdf.createInvoicePdf(legacy))).contains("Reconstructed copy", "Draft invoice");
    assertThat(legacy.getDocumentSnapshot()).isNull();
  }

  @Test
  void snapshotCannotBeReplaced() {
    Order invoice = invoice(true);
    assertThatThrownBy(() -> invoice.captureDocumentSnapshot(settings)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void historicalSentInvoiceCannotInventSnapshot() {
    Order invoice = invoice(false);
    invoice.setStatus("SENT");
    assertThatThrownBy(() -> invoice.captureDocumentSnapshot(settings)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void snapshotRoundTripsAndIsExcludedFromGeneralApiJson() throws Exception {
    Order invoice = invoice(true);
    var converter = new InvoiceDocumentSnapshotConverter();
    assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(invoice.getDocumentSnapshot())))
        .isEqualTo(invoice.getDocumentSnapshot());
    var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(invoice);
    assertThat(json.has("documentSnapshot")).isFalse();
    assertThat(json.path("documentSnapshotAvailable").asBoolean()).isTrue();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-private-data", "null", "{}", "{\"version\":2}"})
  void corruptSnapshotsFailWithoutLeakingOrFallback(String value) {
    assertThatThrownBy(() -> new InvoiceDocumentSnapshotConverter().convertToEntityAttribute(value))
        .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-data");
  }

  @Test
  void writesSyntheticPdfProofs() throws Exception {
    Order invoice = invoice(true);
    invoice.setStatus("SENT");
    invoice.registerPayment(LocalDate.now(), 50, "test");
    Path directory = Path.of("target/pdf-proof");
    Files.createDirectories(directory);
    Files.write(directory.resolve("partial-payment.pdf"), pdf.createInvoicePdf(invoice));
    Order credit = Order.draftFromInvoiceSnapshot(invoice, Instant.now());
    credit.setCreditInvoice(true);
    credit.setCreditedInvoiceId(123L);
    credit.setAmounts(-100, -25, -125);
    credit.setInvoiceNumber("K-TEST-1");
    credit.setStatus("SENT");
    Files.write(directory.resolve("credit.pdf"), pdf.createInvoicePdf(credit));
  }

  private String text(byte[] bytes) throws Exception {
    PdfReader reader = new PdfReader(bytes);
    try {
      StringBuilder text = new StringBuilder();
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
      return text.toString().replaceAll("\\s+", " ").trim();
    } finally { reader.close(); }
  }
}
