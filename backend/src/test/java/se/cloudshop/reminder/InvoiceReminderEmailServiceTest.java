package se.cloudshop.reminder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.customer.Customer;
import se.cloudshop.email.InvoiceReminderEmailService;
import se.cloudshop.email.EmailDeliveryLedger;
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

  @Test
  void recordsReminderAttemptBeforeSmtpAndMarksItSent() throws Exception {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var ledger = mock(EmailDeliveryLedger.class);
    when(settings.getSettings()).thenReturn(AppSettings.defaults());
    when(ledger.startReminderAttempt(eq(8L), eq(false), eq("customer@example.invalid"),
        anyString(), anyString())).thenReturn(17L);
    var sender = new InvoiceReminderEmailService(mail, settings, ledger,
        "test.invalid", "sender@example.invalid");

    sender.sendReminder(invoice());

    InOrder order = inOrder(ledger, mail);
    order.verify(ledger).startReminderAttempt(eq(8L), eq(false), eq("customer@example.invalid"),
        anyString(), anyString());
    order.verify(mail).send(any(SimpleMailMessage.class));
    order.verify(ledger).markSent(17L);
  }

  @Test
  void smtpTimeoutLeavesAnUncertainDurableAttempt() throws Exception {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var ledger = mock(EmailDeliveryLedger.class);
    when(settings.getSettings()).thenReturn(AppSettings.defaults());
    when(ledger.startReminderAttempt(eq(8L), eq(true), eq("customer@example.invalid"),
        anyString(), anyString())).thenReturn(18L);
    var timeout = new IllegalStateException("SMTP timeout");
    doThrow(timeout).when(mail).send(any(SimpleMailMessage.class));
    var sender = new InvoiceReminderEmailService(mail, settings, ledger,
        "test.invalid", "sender@example.invalid");

    assertThatThrownBy(() -> sender.sendOverdueReminder(invoice())).isSameAs(timeout);

    verify(ledger).markUncertain(18L, timeout);
    verify(ledger, never()).markSent(18L);
  }

  @Test
  void refusesBlindResendWhileReminderDeliveryIsUnresolved() throws Exception {
    var mail = mock(JavaMailSender.class);
    var settings = mock(SettingsService.class);
    var ledger = mock(EmailDeliveryLedger.class);
    when(ledger.hasUnresolvedReminderAttempt(8L, false)).thenReturn(true);
    var sender = new InvoiceReminderEmailService(mail, settings, ledger,
        "test.invalid", "sender@example.invalid");

    assertThatThrownBy(() -> sender.sendReminder(invoice()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Reconcile the previous attempt");
    verify(mail, never()).send(any(SimpleMailMessage.class));
    verify(ledger, never()).startReminderAttempt(eq(8L), eq(false), anyString(), anyString(), anyString());
  }

  private Order invoice() throws Exception {
    var invoice = new Order(new Customer("Test", "customer@example.invalid", "", "", "", "", ""),
        new Product("Test", "", 100), Instant.now());
    invoice.setInvoiceNumber("F-TEST-8");
    invoice.setStatus("SENT");
    invoice.setPlusGiro("123456-7");
    Field id = Order.class.getDeclaredField("id");
    id.setAccessible(true);
    id.set(invoice, 8L);
    return invoice;
  }
}
