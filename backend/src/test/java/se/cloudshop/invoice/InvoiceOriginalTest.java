package se.cloudshop.invoice;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import se.cloudshop.order.Order;
import se.cloudshop.product.Product;

class InvoiceOriginalTest {
  private final byte[] bytes = "%PDF-1.4 synthetic original".getBytes(StandardCharsets.US_ASCII);
  private final InvoiceOriginalRepository repository = mock(InvoiceOriginalRepository.class);
  private final InvoicePdfService renderer = mock(InvoicePdfService.class);
  private final InvoiceOriginalService service = new InvoiceOriginalService(repository, renderer);

  private Order invoice(String status) {
    Order invoice = new Order("Test", new Product("Test", "", 100), Instant.now());
    ReflectionTestUtils.setField(invoice, "id", 1L);
    invoice.setStatus(status);
    return invoice;
  }

  @ParameterizedTest
  @ValueSource(strings = {"DRAFT", "PAID", "PARTIALLY_PAID", "CREDITED", "unknown", ""})
  void onlyNewIssuanceCanBeArchived(String status) {
    assertThatThrownBy(() -> new InvoiceOriginal(invoice(status), bytes)).isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "%PDF", "not a pdf", "private customer text"})
  void invalidPdfCannotBeArchived(String pdf) {
    assertThatThrownBy(() -> new InvoiceOriginal(invoice("SENT"), pdf.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid invoice PDF.");
  }

  @Test
  void defensiveCopiesPreserveOriginalAndHash() {
    InvoiceOriginal original = new InvoiceOriginal(invoice("SENT"), bytes);
    byte[] expected = bytes.clone();
    bytes[5] = 'X';
    byte[] read = original.verifiedPdf();
    read[5] = 'Y';
    assertThat(original.verifiedPdf()).isEqualTo(expected);
    assertThat(original.getSha256()).isEqualTo(InvoiceOriginal.digest(expected)).hasSize(64);
    assertThat(original.getArchivedAt()).isNotNull();
  }

  @Test
  void corruptedArchiveFailsClosed() {
    InvoiceOriginal original = new InvoiceOriginal(invoice("SENT"), bytes);
    ReflectionTestUtils.setField(original, "pdf", "private corrupted bytes".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(original::verifiedPdf).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageNotContaining("private corrupted bytes");
  }

  @Test
  void originalIsReadWithoutRendering() {
    Order invoice = invoice("PAID");
    when(repository.findById(1L)).thenReturn(Optional.of(new InvoiceOriginal(invoice("SENT"), bytes)));
    var document = service.read(invoice);
    assertThat(document.pdf()).isEqualTo(bytes);
    assertThat(document.source()).isEqualTo("original");
    verifyNoInteractions(renderer);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SENT", "PAID", "CREDITED", "PARTIALLY_PAID"})
  void missingIssuedOriginalIsReconstructedWithoutSaving(String status) {
    Order invoice = invoice(status);
    when(repository.findById(1L)).thenReturn(Optional.empty());
    when(renderer.createInvoicePdf(invoice, true)).thenReturn(bytes);
    assertThat(service.read(invoice).source()).isEqualTo("reconstructed");
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void draftDownloadDoesNotArchive() {
    Order draft = invoice("DRAFT");
    when(renderer.createInvoicePdf(draft, false)).thenReturn(bytes);
    assertThat(service.read(draft).source()).isEqualTo("draft");
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void existingOriginalCannotBeReplaced() {
    when(repository.existsById(1L)).thenReturn(true);
    assertThatThrownBy(() -> service.archiveAtIssuance(invoice("SENT"))).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(renderer);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void originalIsFlushedBeforeIssuanceContinues() {
    Order invoice = invoice("SENT");
    when(renderer.createInvoicePdf(invoice)).thenReturn(bytes);
    service.archiveAtIssuance(invoice);
    verify(repository).saveAndFlush(argThat(original -> java.util.Arrays.equals(original.verifiedPdf(), bytes)));
  }
}
