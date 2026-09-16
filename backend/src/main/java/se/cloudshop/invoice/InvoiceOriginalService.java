package se.cloudshop.invoice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import se.cloudshop.order.Order;

@Service
public class InvoiceOriginalService {
  private final InvoiceOriginalRepository originals;
  private final InvoicePdfService renderer;

  public InvoiceOriginalService(InvoiceOriginalRepository originals, InvoicePdfService renderer) {
    this.originals = originals;
    this.renderer = renderer;
  }

  // The caller holds the invoice row lock (or just inserted a new credit).
  @Transactional(propagation = Propagation.MANDATORY)
  public void archiveAtIssuance(Order invoice) {
    if (originals.existsById(invoice.getId())) {
      throw new IllegalStateException("Invoice original already exists and cannot be replaced.");
    }
    originals.saveAndFlush(new InvoiceOriginal(invoice, renderer.createInvoicePdf(invoice)));
  }

  @Transactional(readOnly = true)
  public InvoicePdfDocument read(Order invoice) {
    var original = originals.findById(invoice.getId());
    if (original.isPresent()) {
      var stored = original.get();
      return new InvoicePdfDocument(stored.verifiedPdf(), "original", stored.getSha256());
    }
    boolean draft = "DRAFT".equals(invoice.getStatus());
    byte[] pdf = renderer.createInvoicePdf(invoice, !draft);
    return new InvoicePdfDocument(pdf, draft ? "draft" : "reconstructed", InvoiceOriginal.digest(pdf));
  }

  public record InvoicePdfDocument(byte[] pdf, String source, String sha256) {}
}
