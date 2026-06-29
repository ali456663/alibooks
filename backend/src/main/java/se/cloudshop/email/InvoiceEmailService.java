package se.cloudshop.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.invoice.InvoicePdfService;
import se.cloudshop.order.Order;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@Service
public class InvoiceEmailService {

  private final JavaMailSender mailSender;
  private final SettingsService settingsService;
  private final InvoicePdfService invoicePdfService;
  private final String mailHost;
  private final String mailUsername;

  public InvoiceEmailService(
      JavaMailSender mailSender,
      SettingsService settingsService,
      InvoicePdfService invoicePdfService,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername
  ) {
    this.mailSender = mailSender;
    this.settingsService = settingsService;
    this.invoicePdfService = invoicePdfService;
    this.mailHost = mailHost;
    this.mailUsername = mailUsername;
  }

  public void sendInvoice(Order invoice) {
    requireEmailConfigured();

    if (invoice.getCustomer() == null || invoice.getCustomer().getEmail() == null || invoice.getCustomer().getEmail().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has no email address.");
    }

    AppSettings settings = settingsService.getSettings();
    String filename = (invoice.getInvoiceNumber() == null ? "invoice-" + invoice.getId() : invoice.getInvoiceNumber()) + ".pdf";
    byte[] pdf = invoicePdfService.createInvoicePdf(invoice);

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(mailUsername);
      helper.setTo(invoice.getCustomer().getEmail());
      helper.setReplyTo(settings.getContactEmail());
      helper.setSubject("Faktura " + invoice.getInvoiceNumber());
      helper.setText(createInvoiceText(invoice, settings));
      helper.addAttachment(filename, new ByteArrayResource(pdf), "application/pdf");
      mailSender.send(message);
    } catch (MessagingException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create invoice email.");
    }
  }

  private void requireEmailConfigured() {
    if (mailHost == null || mailHost.isBlank() || mailUsername == null || mailUsername.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is not configured. Add SMTP settings first.");
    }
  }

  private String createInvoiceText(Order invoice, AppSettings settings) {
    String template = settings.getInvoiceEmailTemplate();

    if (template == null || template.isBlank()) {
      template = AppSettings.defaultInvoiceEmailTemplate();
    }

    return template
        .replace("{kundnamn}", safe(invoice.getCustomerName()))
        .replace("{fakturanummer}", safe(invoice.getInvoiceNumber()))
        .replace("{forfallodatum}", safe(String.valueOf(invoice.getDueDate())))
        .replace("{belopp}", String.valueOf(invoice.getRemainingAmount()))
        .replace("{plusgiro}", safe(invoice.getPlusGiro()))
        .replace("{ocr}", safe(invoice.getOcrNumber()))
        .replace("{betalningsmottagare}", safe(invoice.getPaymentRecipient()))
        .replace("{foretag}", safe(settings.getCompanyName()))
        .replace("{kontaktEpost}", safe(settings.getContactEmail()));
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
