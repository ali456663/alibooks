package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import se.cloudshop.accounting.StripePayout;
import se.cloudshop.accounting.VatFiling;
import se.cloudshop.bank.BankReconciliationEntry;
import se.cloudshop.card.CardPurchase;
import se.cloudshop.expense.Expense;
import se.cloudshop.supplier.SupplierInvoice;

class MoneyApiContractTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void exposesMinorUnitsAndCurrencyForCoreMoneyResponses() throws Exception {
    assertMoneyFields(new CardPurchase(), "netAmountMinor", "vatAmountMinor", "totalAmountMinor");
    assertMoneyFields(new Expense(), "netAmountMinor", "vatAmountMinor", "totalAmountMinor");
    assertMoneyFields(new BankReconciliationEntry(), "amountMinor");
    assertMoneyFields(new SupplierInvoice(), "totalAmountMinor", "vatAmountMinor", "netAmountMinor", "paidAmountMinor");
    assertMoneyFields(new StripePayout(), "grossAmountMinor", "feeAmountMinor", "netAmountMinor");
    assertMoneyFields(new VatFiling(), "outputVatMinor", "inputVatMinor", "vatToPayMinor");
  }

  private void assertMoneyFields(Object value, String... fields) throws Exception {
    String json = objectMapper.writeValueAsString(value);

    assertThat(json).contains("\"currencyCode\":\"SEK\"");
    for (String field : fields) {
      assertThat(json).contains("\"" + field + "\"");
    }
  }
}
