package se.cloudshop.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.order.Order;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@Service
public class InvoiceReminderEmailService {

  private final JavaMailSender mailSender;
  private final SettingsService settingsService;
  private final EmailDeliveryLedger deliveryLedger;
  private final String mailHost;
  private final String mailUsername;

  @Autowired
  public InvoiceReminderEmailService(
      JavaMailSender mailSender,
      SettingsService settingsService,
      EmailDeliveryLedger deliveryLedger,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername
  ) {
    this.mailSender = mailSender;
    this.settingsService = settingsService;
    this.deliveryLedger = deliveryLedger;
    this.mailHost = mailHost;
    this.mailUsername = mailUsername;
  }

  public InvoiceReminderEmailService(
      JavaMailSender mailSender,
      SettingsService settingsService,
      String mailHost,
      String mailUsername
  ) {
    this(mailSender, settingsService, null, mailHost, mailUsername);
  }

  public void sendReminder(Order invoice) {
    send(invoice, false);
  }

  public void sendOverdueReminder(Order invoice) {
    send(invoice, true);
  }

  public boolean hasDeliveryAttempt(Long invoiceId, boolean overdue) {
    return deliveryLedger != null && deliveryLedger.hasReminderAttempt(invoiceId, overdue);
  }

  private void send(Order invoice, boolean overdue) {
    requireEmailConfigured();

    if (invoice.getCustomer() == null || invoice.getCustomer().getEmail() == null || invoice.getCustomer().getEmail().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has no email address.");
    }

    if (deliveryLedger != null && deliveryLedger.hasUnresolvedReminderAttempt(invoice.getId(), overdue)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Reminder delivery is unresolved. Reconcile the previous attempt before sending again.");
    }

    AppSettings settings = settingsService.getSettings();
    requireInvoicePaymentDetails(invoice);
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(invoice.getCustomer().getEmail());
    if (hasText(settings.getContactEmail())) {
      message.setReplyTo(settings.getContactEmail());
    }
    message.setSubject((overdue ? "Forfallen faktura " : "Paminnelse faktura ") + invoice.getInvoiceNumber());
    message.setText(createReminderText(invoice, settings, overdue));
    Long attemptId = deliveryLedger == null ? null : deliveryLedger.startReminderAttempt(
        invoice.getId(), overdue, invoice.getCustomer().getEmail(), message.getSubject(), message.getText());
    try {
      mailSender.send(message);
    } catch (RuntimeException exception) {
      if (attemptId != null) {
        try {
          deliveryLedger.markUncertain(attemptId, exception);
        } catch (RuntimeException ignored) {
          // The durable PENDING row still blocks automatic retries.
        }
      }
      throw exception;
    }
    if (attemptId != null) {
      deliveryLedger.markSent(attemptId);
    }
  }

  public void sendSettingsTestEmail() {
    requireEmailConfigured();
    AppSettings settings = settingsService.getSettings();

    if (settings.getContactEmail() == null || settings.getContactEmail().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact email is missing in settings.");
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(settings.getContactEmail());
    message.setReplyTo(settings.getContactEmail());
    message.setSubject("AliBooks e-posttest");
    message.setText(String.join("\n",
        "Hej!",
        "",
        "Detta ar ett testmail fran AliBooks.",
        "Om du fick detta fungerar SMTP-installningarna.",
        "",
        "AliBooks"
    ));
    mailSender.send(message);
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
          "Payment details are missing. Add the company's PlusGiro or bank payment details before sending the reminder."
      );
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String createReminderText(Order invoice, AppSettings settings, boolean overdue) {
    String template = overdue ? settings.getOverdueInvoiceReminderTemplate() : settings.getInvoiceReminderTemplate();

    if (template == null || template.isBlank()) {
      template = overdue ? AppSettings.defaultOverdueInvoiceReminderTemplate() : AppSettings.defaultInvoiceReminderTemplate();
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
