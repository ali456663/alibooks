package se.cloudshop.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import se.cloudshop.customer.Customer;
import se.cloudshop.email.InvoiceEmailService;
import se.cloudshop.order.Order;
import se.cloudshop.product.Product;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

class InvoiceEmailOriginalTest {
  @ParameterizedTest
  @ValueSource(strings = {"SENT", "PARTIALLY_PAID", "PAID"})
  void emailAttachmentIsExactlyTheArchivedPdf(String status) throws Exception {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var originals = mock(InvoiceOriginalService.class);
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    invoice.setInvoiceNumber("F-TEST-1");
    invoice.setStatus(status);
    byte[] bytes = "%PDF-1.4 original archive test".getBytes(StandardCharsets.US_ASCII);
    when(settings.getSettings()).thenReturn(AppSettings.defaults());
    when(originals.read(invoice)).thenReturn(new InvoiceOriginalService.InvoicePdfDocument(bytes, "original", InvoiceOriginal.digest(bytes)));
    when(mail.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    var sender = new InvoiceEmailService(mail, settings, originals, "test.invalid", "sender@example.invalid");
    sender.sendInvoice(invoice);
    var message = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mail).send(message.capture());
    message.getValue().saveChanges();
    assertThat(attachment(message.getValue())).isEqualTo(bytes);
    verify(originals).read(invoice);
  }

  private byte[] attachment(Part part) throws Exception {
    if ("F-TEST-1.pdf".equals(part.getFileName())) return part.getInputStream().readAllBytes();
    Object content = part.getContent();
    if (content instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        byte[] found = attachment(multipart.getBodyPart(i));
        if (found != null) return found;
      }
    }
    return null;
  }
}
