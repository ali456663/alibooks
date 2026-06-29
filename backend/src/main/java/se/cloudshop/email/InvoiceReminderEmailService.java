package se.cloudshop.email;

import org.springframework.beans.factory.annotation.Value;
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
  private final String mailHost;
  private final String mailUsername;

  public InvoiceReminderEmailService(
      JavaMailSender mailSender,
      SettingsService settingsService,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername
  ) {
    this.mailSender = mailSender;
    this.settingsService = settingsService;
    this.mailHost = mailHost;
    this.mailUsername = mailUsername;
  }

  public void sendReminder(Order invoice) {
    requireEmailConfigured();

    if (invoice.getCustomer() == null || invoice.getCustomer().getEmail() == null || invoice.getCustomer().getEmail().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has no email address.");
    }

    AppSettings settings = settingsService.getSettings();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(invoice.getCustomer().getEmail());
    message.setReplyTo(settings.getContactEmail());
    message.setSubject("Paminnelse faktura " + invoice.getInvoiceNumber());
    message.setText(createReminderText(invoice, settings, false));
    mailSender.send(message);
  }

  public void sendOverdueReminder(Order invoice) {
    requireEmailConfigured();

    if (invoice.getCustomer() == null || invoice.getCustomer().getEmail() == null || invoice.getCustomer().getEmail().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has no email address.");
    }

    AppSettings settings = settingsService.getSettings();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(invoice.getCustomer().getEmail());
    message.setReplyTo(settings.getContactEmail());
    message.setSubject("Forfallen faktura " + invoice.getInvoiceNumber());
    message.setText(createReminderText(invoice, settings, true));
    mailSender.send(message);
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
