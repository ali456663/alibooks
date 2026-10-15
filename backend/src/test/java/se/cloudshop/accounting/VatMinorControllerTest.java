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
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class VatMinorControllerTest {
  private final VatMinorService service = mock(VatMinorService.class);
  private final AuditService audit = mock(AuditService.class);
  private final JwtService jwt = new JwtService("test_secret");
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new VatMinorController(service, new AuthHeader(jwt), audit)).build();
  private final String authorization = "Bearer " + jwt.createToken("accountant@example.test");
  private final LocalDate from = LocalDate.of(2026, 7, 1);
  private final LocalDate to = LocalDate.of(2026, 7, 31);

  @Test
  void reportRequiresAuthenticationAndExposesOnlyMinorAmounts() throws Exception {
    mvc.perform(get("/v2/vat-report")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v2/vat-report/export")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service, audit);

    when(service.create(from, to)).thenReturn(fixture());
    mvc.perform(get("/v2/vat-report").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencyCode").value("SEK"))
        .andExpect(jsonPath("$.outputVatMinor").value(30015))
        .andExpect(jsonPath("$.vatToPayMinor").value(28014))
        .andExpect(jsonPath("$.outputVat").doesNotExist());
  }

  @Test
  void exportPreservesOreAndRecordsAudit() throws Exception {
    when(service.create(from, to)).thenReturn(fixture());
    byte[] bytes = mvc.perform(get("/v2/vat-report/export").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .contains("OutputVat25Minor")
        .contains("2026-07-01,2026-07-31,SEK,2,100050,25013,50000,5002,0,0,30015,2001,28014,false");
    verify(audit).record(eq("export"), eq("vat_report"), any(),
        eq("vat_minor_report_exported"), any(), any(), eq(1), eq(authorization));
  }

  private VatMinorReport fixture() {
    return new VatMinorReport(from, to, "SEK", 2,
        100050, 25013, 50000, 5002, 0, 0, 30015, 2001, 28014, false);
  }
}
