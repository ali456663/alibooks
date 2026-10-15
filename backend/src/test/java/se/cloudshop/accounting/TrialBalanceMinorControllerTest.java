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

class TrialBalanceMinorControllerTest {
  private final TrialBalanceMinorService service = mock(TrialBalanceMinorService.class);
  private final AuditService audit = mock(AuditService.class);
  private final JwtService jwt = new JwtService("test_secret");
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new TrialBalanceMinorController(service, new AuthHeader(jwt), audit)).build();
  private final String authorization = "Bearer " + jwt.createToken("accountant@example.test");
  private final LocalDate from = LocalDate.of(2026, 7, 1);
  private final LocalDate to = LocalDate.of(2026, 7, 31);

  private TrialBalanceMinorReport fixture() {
    return new TrialBalanceMinorReport(from, to, "SEK", 2,
        List.of(new TrialBalanceMinorReport.Line("1930", "=BAD()", 0, 0, 125050, 0, 125050, 0)),
        0, 0, 125050, 0, 125050, 0, 125050);
  }

  @Test
  void jsonUsesExplicitIntegerMinorFieldsAndCurrency() throws Exception {
    when(service.create(from, to)).thenReturn(fixture());
    mvc.perform(get("/v2/trial-balance").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.periodDebitTotalMinor").value(125050))
        .andExpect(jsonPath("$.currencyCode").value("SEK"))
        .andExpect(jsonPath("$.minorUnitExponent").value(2))
        .andExpect(jsonPath("$.periodDebitTotal").doesNotExist());
  }

  @Test
  void requiresAuthenticationForReportAndExport() throws Exception {
    mvc.perform(get("/v2/trial-balance")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v2/trial-balance/export")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service, audit);
  }

  @Test
  void exportPreservesOreNeutralizesFormulasAndRecordsAudit() throws Exception {
    when(service.create(from, to)).thenReturn(fixture());
    byte[] bytes = mvc.perform(get("/v2/trial-balance/export").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("OpeningDebitMinor")
        .contains("SEK,2,\"1930\",\"'=BAD()\",0,0,125050,0,125050,0,")
        .contains("SEK,2,TOTAL,,0,0,125050,0,125050,0,125050");
    verify(audit).record(eq("export"), eq("trial_balance"), any(), eq("trial_balance_minor_exported"),
        any(), any(), eq(1), eq(authorization));
  }
}
