package se.cloudshop.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SupplierPaymentSafetyTest {
  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 76, Integer.MAX_VALUE})
  void invalidPaymentCannotClampOrOverflowExistingPaidAmount(int amount) {
    Supplier supplier = new Supplier("Test", "test@example.invalid", "", "", "");
    SupplierInvoice invoice = new SupplierInvoice(supplier, LocalDate.now(), LocalDate.now(), "Test", "ref", 125, 25, "5420");
    invoice.registerPayment(LocalDate.now(), 50, "first");
    String history = invoice.getPaymentHistory();
    assertThatThrownBy(() -> invoice.registerPayment(LocalDate.now(), amount, "bad")).isInstanceOf(IllegalArgumentException.class);
    assertThat(invoice.getPaidAmount()).isEqualTo(50);
    assertThat(invoice.getPaymentHistory()).isEqualTo(history);
    assertThat(invoice.getStatus()).isEqualTo("partial");
  }
}
