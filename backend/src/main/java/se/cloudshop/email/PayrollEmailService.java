package se.cloudshop.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.payroll.PayrollPayslipEmailRequest;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@Service
public class PayrollEmailService {

  private final JavaMailSender mailSender;
  private final SettingsService settingsService;
  private final String mailHost;
  private final String mailUsername;

  public PayrollEmailService(
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

  public void sendPayslip(PayrollPayslipEmailRequest request) {
    requireEmailConfigured();
    validateRequest(request);

    AppSettings settings = settingsService.getSettings();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(request.recipientEmail());
    message.setReplyTo(settings.getContactEmail());
    message.setSubject(request.subject() == null || request.subject().isBlank()
        ? "Lonebesked " + safe(request.period())
        : request.subject());
    message.setText(request.body());
    mailSender.send(message);
  }

  private void requireEmailConfigured() {
    if (mailHost == null || mailHost.isBlank() || mailUsername == null || mailUsername.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is not configured. Add SMTP settings first.");
    }
  }

  private void validateRequest(PayrollPayslipEmailRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payslip email request is required.");
    }

    if (request.recipientEmail() == null || request.recipientEmail().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee has no email address.");
    }

    if (!request.recipientEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee email address is invalid.");
    }

    if (request.body() == null || request.body().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payslip email body is required.");
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
