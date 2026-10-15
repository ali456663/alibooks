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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.customer.Customer;
import se.cloudshop.email.InvoiceEmailService;
import se.cloudshop.email.EmailDeliveryLedger;
import se.cloudshop.order.Order;
import se.cloudshop.product.Product;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

class InvoiceEmailOriginalTest {
  @Test
  void refusesBlindResendAfterUnresolvedAttempt() {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var originals = mock(InvoiceOriginalService.class);
    var ledger = mock(EmailDeliveryLedger.class);
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    when(ledger.hasUnresolvedInvoiceAttempt(invoice.getId())).thenReturn(true);

    var sender = new InvoiceEmailService(mail, settings, originals, ledger,
        "test.invalid", "sender@example.invalid");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> sender.sendInvoice(invoice))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Reconcile the previous attempt");
    verifyNoInteractions(originals);
    verify(mail, never()).send(any(MimeMessage.class));
  }

  @Test
  void refusesResendWhenDeliverySucceededButInvoiceHistoryRolledBack() {
    var mail = mock(JavaMailSender.class);
    var ledger = mock(EmailDeliveryLedger.class);
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    when(ledger.hasInvoiceAttempt(invoice.getId())).thenReturn(true);

    var sender = new InvoiceEmailService(mail, mock(SettingsService.class),
        mock(InvoiceOriginalService.class), ledger, "test.invalid", "sender@example.invalid");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> sender.sendInvoice(invoice))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Reconcile the previous attempt");
    verify(mail, never()).send(any(MimeMessage.class));
  }

  @Test
  void permitsIntentionalResendOfReconciledInvoice() {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var originals = mock(InvoiceOriginalService.class);
    var ledger = mock(EmailDeliveryLedger.class);
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    invoice.setInvoiceNumber("F-RESEND");
    invoice.setStatus("SENT");
    invoice.setPlusGiro("123 45 67-8");
    invoice.addReminderHistory("INVOICE_EMAIL", "SENT", "customer@example.invalid");
    when(ledger.hasInvoiceAttempt(invoice.getId())).thenReturn(true);
    when(settings.getSettings()).thenReturn(AppSettings.defaults());
    byte[] pdf = "%PDF-1.4 archived".getBytes(StandardCharsets.US_ASCII);
    when(originals.read(invoice)).thenReturn(new InvoiceOriginalService.InvoicePdfDocument(
        pdf, "original", InvoiceOriginal.digest(pdf)));
    when(mail.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

    var sender = new InvoiceEmailService(mail, settings, originals, ledger,
        "test.invalid", "sender@example.invalid");
    sender.sendInvoice(invoice);

    verify(mail).send(any(MimeMessage.class));
    verify(ledger).startInvoiceAttempt(org.mockito.ArgumentMatchers.eq(invoice.getId()),
        org.mockito.ArgumentMatchers.eq("customer@example.invalid"),
        org.mockito.ArgumentMatchers.eq("Faktura F-RESEND"), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.eq("F-RESEND.pdf"), org.mockito.ArgumentMatchers.eq(pdf));
  }

  @Test
  void permitsRetryOnlyAfterAllPriorAttemptsWereConfirmedNotDelivered() {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var originals = mock(InvoiceOriginalService.class);
    var ledger = mock(EmailDeliveryLedger.class);
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    invoice.setInvoiceNumber("F-RETRY");
    invoice.setStatus("SENT");
    invoice.setPlusGiro("123 45 67-8");
    when(ledger.hasInvoiceAttempt(invoice.getId())).thenReturn(true);
    when(ledger.hasOnlyConfirmedNotDeliveredInvoiceAttempts(invoice.getId())).thenReturn(true);
    when(settings.getSettings()).thenReturn(AppSettings.defaults());
    byte[] pdf = "%PDF-1.4 archived".getBytes(StandardCharsets.US_ASCII);
    when(originals.read(invoice)).thenReturn(new InvoiceOriginalService.InvoicePdfDocument(
        pdf, "original", InvoiceOriginal.digest(pdf)));
    when(mail.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

    new InvoiceEmailService(mail, settings, originals, ledger,
        "test.invalid", "sender@example.invalid").sendInvoice(invoice);

    verify(mail).send(any(MimeMessage.class));
  }

  @Test
  void refusesToSendWhenPaymentDetailsAreMissing() {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var originals = mock(InvoiceOriginalService.class);
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    invoice.setInvoiceNumber("F-TEST-MISSING-PAYMENT");
    invoice.setStatus("SENT");
    when(settings.getSettings()).thenReturn(AppSettings.defaults());

    var sender = new InvoiceEmailService(mail, settings, originals, "test.invalid", "sender@example.invalid");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> sender.sendInvoice(invoice))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Payment details are missing");
    verify(mail, never()).send(any(MimeMessage.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"reconstructed", "draft"})
  void refusesToEmailAnythingOtherThanArchivedOriginal(String source) {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var originals = mock(InvoiceOriginalService.class);
    var ledger = mock(EmailDeliveryLedger.class);
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    invoice.setInvoiceNumber("F-LEGACY");
    invoice.setStatus("SENT");
    invoice.setPlusGiro("123 45 67-8");
    when(settings.getSettings()).thenReturn(AppSettings.defaults());
    when(originals.read(invoice)).thenReturn(new InvoiceOriginalService.InvoicePdfDocument(
        "%PDF-1.4 rebuilt".getBytes(StandardCharsets.US_ASCII), source, "synthetic-hash"));

    var sender = new InvoiceEmailService(mail, settings, originals, ledger,
        "test.invalid", "sender@example.invalid");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> sender.sendInvoice(invoice))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invoice original is missing");
    verify(ledger, never()).startInvoiceAttempt(any(), any(), any(), any(), any(), any());
    verify(mail, never()).send(any(MimeMessage.class));
  }

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
    invoice.setPlusGiro("123 45 67-8");
    invoice.setOcrNumber("OCR-TEST-1");
    invoice.setPaymentRecipient("Test company");
    byte[] bytes = "%PDF-1.4 original archive test".getBytes(StandardCharsets.US_ASCII);
    var configuredSettings = AppSettings.defaults();
    configuredSettings.setContactEmail("issuer@example.invalid");
    configuredSettings.setPlusGiro("123 45 67-8");
    configuredSettings.setDefaultOcr("OCR-TEST-1");
    configuredSettings.setPaymentRecipient("Test company");
    when(settings.getSettings()).thenReturn(configuredSettings);
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
