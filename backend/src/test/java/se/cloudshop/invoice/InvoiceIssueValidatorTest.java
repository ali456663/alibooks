package se.cloudshop.invoice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.order.Order;

class InvoiceIssueValidatorTest {

  @Test
  void refusesIssuanceWhenVatMinorShadowContainsOre() {
    Order invoice = invoiceWithCompleteSnapshot();
    when(invoice.getVatAmountMinor()).thenReturn(25_001L);
    when(invoice.getVatAmount()).thenReturn(250);

    assertThatThrownBy(() -> InvoiceIssueValidator.requireIssuable(invoice))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
          org.assertj.core.api.Assertions.assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
          org.assertj.core.api.Assertions.assertThat(exception.getReason()).contains("fakturans momsbelopp", "ören");
        });
  }

  @Test
  void refusesIssuanceWhenTotalMinorShadowContainsOre() {
    Order invoice = invoiceWithCompleteSnapshot();
    when(invoice.getVatAmountMinor()).thenReturn(0L);
    when(invoice.getVatAmount()).thenReturn(0);
    when(invoice.getTotalAmountMinor()).thenReturn(400_001L);
    when(invoice.getTotalAmount()).thenReturn(4_000);

    assertThatThrownBy(() -> InvoiceIssueValidator.requireIssuable(invoice))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
          org.assertj.core.api.Assertions.assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
          org.assertj.core.api.Assertions.assertThat(exception.getReason()).contains("fakturans totalbelopp", "ören");
        });
  }

  private Order invoiceWithCompleteSnapshot() {
    Order invoice = mock(Order.class);
    when(invoice.getDocumentSnapshot()).thenReturn(new InvoiceDocumentSnapshot(
        3,
        "Customer",
        "customer@example.invalid",
        "",
        "Street 1",
        "0700000000",
        "111 22",
        "Stockholm",
        "Consulting",
        "AliBooks",
        "Issuer Street 1",
        "111 22",
        "Stockholm",
        "556000-0000",
        "SE556000000001",
        "issuer@example.invalid",
        "",
        "",
        "",
        false,
        null
    ));
    return invoice;
  }
}
