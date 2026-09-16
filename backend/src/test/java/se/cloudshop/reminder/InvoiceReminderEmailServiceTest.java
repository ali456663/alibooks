package se.cloudshop.reminder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.customer.Customer;
import se.cloudshop.email.InvoiceReminderEmailService;
import se.cloudshop.order.Order;
import se.cloudshop.product.Product;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

class InvoiceReminderEmailServiceTest {

  @Test
  void refusesReminderWhenPaymentDetailsAreMissing() {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    invoice.setInvoiceNumber("F-TEST-MISSING-PAYMENT");
    invoice.setStatus("SENT");
    when(settings.getSettings()).thenReturn(AppSettings.defaults());

    var sender = new InvoiceReminderEmailService(mail, settings, "test.invalid", "sender@example.invalid");

    assertThatThrownBy(() -> sender.sendReminder(invoice))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Payment details are missing");
    verify(mail, never()).send(any(SimpleMailMessage.class));
  }
}
