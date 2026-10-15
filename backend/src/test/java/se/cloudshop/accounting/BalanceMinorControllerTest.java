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

class BalanceMinorControllerTest {
  private final BalanceMinorService service = mock(BalanceMinorService.class);
  private final AuditService audit = mock(AuditService.class);
  private final JwtService jwt = new JwtService("test_secret");
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new BalanceMinorController(service, new AuthHeader(jwt), audit)).build();
  private final String authorization = "Bearer " + jwt.createToken("accountant@example.test");
  private final LocalDate to = LocalDate.of(2026, 7, 31);

  @Test
  void requiresAuthenticationAndUsesExplicitMinorFields() throws Exception {
    mvc.perform(get("/v2/balance-report")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v2/balance-report/export")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service, audit);
    when(service.create(to)).thenReturn(fixture(0));
    mvc.perform(get("/v2/balance-report").header("Authorization", authorization)
        .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencyCode").value("SEK"))
        .andExpect(jsonPath("$.totalAssetsMinor").value(125050))
        .andExpect(jsonPath("$.differenceMinor").value(0))
        .andExpect(jsonPath("$.totalAssets").doesNotExist());
  }

  @Test
  void exportsBalancedCsvWithEscapedNamesAndAudit() throws Exception {
    when(service.create(to)).thenReturn(fixture(0));
    byte[] bytes = mvc.perform(get("/v2/balance-report/export").header("Authorization", authorization)
        .param("to", to.toString()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .contains("AmountMinor")
        .contains("SEK,2,Asset,\"1930\",\"'=BAD()\",125050")
        .contains("SEK,2,LiabilityOrEquity,\"2019\",\"Arets resultat\",125050")
        .contains("SEK,2,DIFFERENCE,,,0");
    verify(audit).record(eq("export"), eq("balance_report"), any(),
        eq("balance_report_minor_exported"), any(), any(), eq(2), eq(authorization));
  }

  @Test
  void rejectsUnbalancedCsvWithoutAudit() throws Exception {
    when(service.create(to)).thenReturn(fixture(1));
    mvc.perform(get("/v2/balance-report/export").header("Authorization", authorization)
        .param("to", to.toString()))
        .andExpect(status().isUnprocessableEntity());
    verifyNoInteractions(audit);
  }

  private BalanceMinorReport fixture(long difference) {
    return new BalanceMinorReport(to, "SEK", 2,
        List.of(new BalanceMinorReport.Line("1930", "=BAD()", 125050)),
        List.of(new BalanceMinorReport.Line("2019", "Arets resultat", 125050)),
        125050, 125050, difference);
  }
}
