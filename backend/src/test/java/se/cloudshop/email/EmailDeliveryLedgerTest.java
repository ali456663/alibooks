package se.cloudshop.email;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class EmailDeliveryLedgerTest {

  @Test
  void unresolvedCountIncludesPendingAndUncertainAttempts() {
    EmailDeliveryAttemptRepository repository = mock(EmailDeliveryAttemptRepository.class);
    when(repository.countByStatus("PENDING")).thenReturn(1L);
    when(repository.countByStatus("UNCERTAIN")).thenReturn(2L);
    when(repository.countByStatusIn(List.of("PENDING", "UNCERTAIN"))).thenReturn(3L);
    EmailDeliveryLedger ledger = new EmailDeliveryLedger(repository);

    assertThat(ledger.countPending()).isEqualTo(1);
    assertThat(ledger.countUncertain()).isEqualTo(2);
    assertThat(ledger.countUnresolved()).isEqualTo(3);
    verify(repository).countByStatusIn(List.of("PENDING", "UNCERTAIN"));
  }

  @Test
  void reminderAttemptIsDurableAndDistinguishesOverdueMessages() {
    EmailDeliveryAttemptRepository repository = mock(EmailDeliveryAttemptRepository.class);
    EmailDeliveryAttempt attempt = mock(EmailDeliveryAttempt.class);
    when(attempt.getId()).thenReturn(17L);
    when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(EmailDeliveryAttempt.class))).thenReturn(attempt);
    when(repository.existsByDeliveryTypeAndInvoiceId("OVERDUE_REMINDER", 8L)).thenReturn(true);
    EmailDeliveryLedger ledger = new EmailDeliveryLedger(repository);

    assertThat(ledger.startReminderAttempt(8L, true, "customer@example.invalid", "Overdue", "Body"))
        .isEqualTo(17L);
    assertThat(ledger.hasReminderAttempt(8L, true)).isTrue();
    verify(repository).existsByDeliveryTypeAndInvoiceId("OVERDUE_REMINDER", 8L);
  }

  @Test
  void invoiceAttemptChecksSeparateUnresolvedFromAnyPriorAttempt() {
    EmailDeliveryAttemptRepository repository = mock(EmailDeliveryAttemptRepository.class);
    when(repository.existsByDeliveryTypeAndInvoiceId("INVOICE_EMAIL", 9L)).thenReturn(true);
    when(repository.existsByDeliveryTypeAndInvoiceIdAndStatusIn("INVOICE_EMAIL", 9L,
        List.of("PENDING", "UNCERTAIN")))
        .thenReturn(true);
    EmailDeliveryLedger ledger = new EmailDeliveryLedger(repository);

    assertThat(ledger.hasInvoiceAttempt(9L)).isTrue();
    assertThat(ledger.hasUnresolvedInvoiceAttempt(9L)).isTrue();
  }

  @Test
  void unresolvedReminderCheckUsesItsOwnDeliveryType() {
    EmailDeliveryAttemptRepository repository = mock(EmailDeliveryAttemptRepository.class);
    when(repository.existsByDeliveryTypeAndInvoiceIdAndStatusIn("OVERDUE_REMINDER", 9L,
        List.of("PENDING", "UNCERTAIN")))
        .thenReturn(true);
    EmailDeliveryLedger ledger = new EmailDeliveryLedger(repository);

    assertThat(ledger.hasUnresolvedReminderAttempt(9L, true)).isTrue();
    assertThat(ledger.hasUnresolvedReminderAttempt(9L, false)).isFalse();
  }

  @Test
  void resolvedNotDeliveredInvoiceAttemptAllowsOnlyAnExplicitRetry() {
    EmailDeliveryAttemptRepository repository = mock(EmailDeliveryAttemptRepository.class);
    when(repository.existsByDeliveryTypeAndInvoiceIdAndStatus("INVOICE_EMAIL", 9L, "NOT_DELIVERED"))
        .thenReturn(true);
    EmailDeliveryLedger ledger = new EmailDeliveryLedger(repository);

    assertThat(ledger.hasOnlyConfirmedNotDeliveredInvoiceAttempts(9L)).isTrue();
    when(repository.existsByDeliveryTypeAndInvoiceIdAndStatusNot("INVOICE_EMAIL", 9L, "NOT_DELIVERED"))
        .thenReturn(true);
    assertThat(ledger.hasOnlyConfirmedNotDeliveredInvoiceAttempts(9L)).isFalse();
  }
}
