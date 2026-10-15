package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class ProfitAndLossMinorControllerTest {
  private final ProfitAndLossMinorService service = mock(ProfitAndLossMinorService.class);
  private final AuditService audit = mock(AuditService.class);
  private final JwtService jwt = new JwtService("test_secret");
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new ProfitAndLossMinorController(service, new AuthHeader(jwt), audit)).build();
  private final String authorization = "Bearer " + jwt.createToken("accountant@example.test");
  private final LocalDate from = LocalDate.of(2026, 7, 1);
  private final LocalDate to = LocalDate.of(2026, 7, 31);

  @Test
  void reportUsesExplicitMinorFieldsAndRequiresAuthentication() throws Exception {
    mvc.perform(get("/v2/profit-and-loss")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v2/profit-and-loss/export")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service, audit);

    when(service.create(from, to)).thenReturn(fixture());
    mvc.perform(get("/v2/profit-and-loss").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencyCode").value("SEK"))
        .andExpect(jsonPath("$.totalRevenueMinor").value(125050))
        .andExpect(jsonPath("$.resultMinor").value(95049))
        .andExpect(jsonPath("$.totalRevenue").doesNotExist());
  }

  @Test
  void exportKeepsOreEscapesSpreadsheetFormulaAndRecordsAudit() throws Exception {
    when(service.create(from, to)).thenReturn(fixture());
    byte[] bytes = mvc.perform(get("/v2/profit-and-loss/export").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .contains("AmountMinor")
        .contains("SEK,2,Revenue,\"3041\",\"'=BAD()\",125050")
        .contains("SEK,2,Expense,\"5010\",\"Rent\",30001")
        .contains("SEK,2,RESULT,,,95049");
    verify(audit).record(eq("export"), eq("profit_and_loss"), any(),
        eq("profit_and_loss_minor_exported"), any(), any(), eq(2), eq(authorization));
  }

  private ProfitAndLossMinorReport fixture() {
    return new ProfitAndLossMinorReport(from, to, "SEK", 2,
        List.of(new ProfitAndLossMinorReport.Line("3041", "=BAD()", 125050)),
        List.of(new ProfitAndLossMinorReport.Line("5010", "Rent", 30001)),
        125050, 30001, 95049);
  }
}
