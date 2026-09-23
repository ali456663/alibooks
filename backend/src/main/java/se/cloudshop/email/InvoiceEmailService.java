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
import se.cloudshop.invoice.InvoiceOriginalService;
import se.cloudshop.order.Order;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@Service
public class InvoiceEmailService {

  private final JavaMailSender mailSender;
  private final SettingsService settingsService;
  private final InvoiceOriginalService invoiceOriginalService;
  private final EmailDeliveryLedger deliveryLedger;
  private final String mailHost;
  private final String mailUsername;

  @org.springframework.beans.factory.annotation.Autowired
  public InvoiceEmailService(
      JavaMailSender mailSender,
      SettingsService settingsService,
      InvoiceOriginalService invoiceOriginalService,
      EmailDeliveryLedger deliveryLedger,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername
  ) {
    this.mailSender = mailSender;
    this.settingsService = settingsService;
    this.invoiceOriginalService = invoiceOriginalService;
    this.deliveryLedger = deliveryLedger;
    this.mailHost = mailHost;
    this.mailUsername = mailUsername;
  }

  // Kept for focused unit tests that exercise the SMTP renderer without persistence.
  public InvoiceEmailService(
      JavaMailSender mailSender,
      SettingsService settingsService,
      InvoiceOriginalService invoiceOriginalService,
      String mailHost,
      String mailUsername
  ) {
    this(mailSender, settingsService, invoiceOriginalService, null, mailHost, mailUsername);
  }

  public void sendInvoice(Order invoice) {
    requireEmailConfigured();

    if (invoice.getCustomer() == null || invoice.getCustomer().getEmail() == null || invoice.getCustomer().getEmail().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has no email address.");
    }

    AppSettings settings = settingsService.getSettings();
    requireInvoicePaymentDetails(invoice);
    String filename = (invoice.getInvoiceNumber() == null ? "invoice-" + invoice.getId() : invoice.getInvoiceNumber()) + ".pdf";
    byte[] pdf = invoiceOriginalService.read(invoice).pdf();
    String subject = "Faktura " + invoice.getInvoiceNumber();
    String body = createInvoiceText(invoice, settings);
    Long deliveryAttemptId = startDeliveryAttempt(invoice, subject, body, filename, pdf);

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(mailUsername);
      helper.setTo(invoice.getCustomer().getEmail());
      if (hasText(settings.getContactEmail())) {
        helper.setReplyTo(settings.getContactEmail());
      }
      helper.setSubject(subject);
      helper.setText(body);
      helper.addAttachment(filename, new ByteArrayResource(pdf), "application/pdf");
      mailSender.send(message);
      markDeliverySent(deliveryAttemptId);
    } catch (MessagingException exception) {
      markDeliveryUncertain(deliveryAttemptId, exception);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create invoice email.");
    } catch (RuntimeException exception) {
      markDeliveryUncertain(deliveryAttemptId, exception);
      throw exception;
    }
  }

  private Long startDeliveryAttempt(Order invoice, String subject, String body, String filename, byte[] pdf) {
    if (deliveryLedger == null) {
      return null;
    }
    return deliveryLedger.startInvoiceAttempt(
        invoice.getId(), invoice.getCustomer().getEmail(), subject, body, filename, pdf
    );
  }

  private void markDeliverySent(Long attemptId) {
    if (attemptId == null) {
      return;
    }
    try {
      deliveryLedger.markSent(attemptId);
    } catch (RuntimeException ignored) {
      // SMTP already accepted the message. Keep the original success result and expose the
      // pending ledger row through system status rather than sending a duplicate message.
    }
  }

  private void markDeliveryUncertain(Long attemptId, Throwable exception) {
    if (attemptId == null) {
      return;
    }
    try {
      deliveryLedger.markUncertain(attemptId, exception);
    } catch (RuntimeException ignored) {
      // Never mask the original transport failure with a secondary ledger failure.
    }
  }

  private void requireEmailConfigured() {
    if (mailHost == null || mailHost.isBlank() || mailUsername == null || mailUsername.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is not configured. Add SMTP settings first.");
    }
  }

  private void requireInvoicePaymentDetails(Order invoice) {
    if (!hasText(invoice.getPlusGiro())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Payment details are missing. Add the company's PlusGiro or bank payment details before sending the invoice."
      );
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
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
