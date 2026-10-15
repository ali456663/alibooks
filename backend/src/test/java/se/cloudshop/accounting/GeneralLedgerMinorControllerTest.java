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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class GeneralLedgerMinorControllerTest {
  private final GeneralLedgerMinorService service = mock(GeneralLedgerMinorService.class);
  private final AuditService audit = mock(AuditService.class);
  private final JwtService jwt = new JwtService("test_secret");
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new GeneralLedgerMinorController(service, new AuthHeader(jwt), audit)).build();
  private final String authorization = "Bearer " + jwt.createToken("accountant@example.test");
  private final LocalDate from = LocalDate.of(2026, 7, 1);
  private final LocalDate to = LocalDate.of(2026, 7, 31);

  private GeneralLedgerMinorReport fixture() {
    GeneralLedgerMinorReport.Entry entry = new GeneralLedgerMinorReport.Entry(
        "31", from, "A-1", Instant.parse("2026-07-02T09:30:00Z"), "=BAD()", "=BAD()", 1, 0, 125051);
    GeneralLedgerMinorReport.Account account = new GeneralLedgerMinorReport.Account(
        "1930", "=BAD()", 125050, 1, 0, 125051, List.of(entry));
    return new GeneralLedgerMinorReport(from, to, "SEK", 2, null, List.of(account), 1, 1, 1, 0);
  }

  @Test
  void jsonUsesExactMinorFields() throws Exception {
    when(service.create(from, to, null)).thenReturn(fixture());
    mvc.perform(get("/v2/general-ledger").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accounts[0].entries[0].runningBalanceMinor").value(125051))
        .andExpect(jsonPath("$.accounts[0].entries[0].createdAt").value("2026-07-02T09:30:00Z"))
        .andExpect(jsonPath("$.accounts[0].entries[0].correctionOfVoucherNumber").value("=BAD()"))
        .andExpect(jsonPath("$.currencyCode").value("SEK"))
        .andExpect(jsonPath("$.minorUnitExponent").value(2))
        .andExpect(jsonPath("$.accounts[0].entries[0].debit").doesNotExist());
  }

  @Test
  void rejectsAnonymousReportAndExport() throws Exception {
    mvc.perform(get("/v2/general-ledger")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v2/general-ledger/export")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service, audit);
  }

  @Test
  void exportPreservesOreNeutralizesFormulasAndRecordsAudit() throws Exception {
    when(service.create(from, to, null)).thenReturn(fixture());
    byte[] bytes = mvc.perform(get("/v2/general-ledger/export").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("RunningBalanceMinor")
        .contains("SEK,2,\"1930\",\"'=BAD()\",\"31\",2026-07-01,\"A-1\",2026-07-02T09:30:00Z,\"'=BAD()\",\"'=BAD()\",125050,1,0,125051,125051");
    verify(audit).record(eq("export"), eq("general_ledger"), any(), eq("general_ledger_minor_exported"),
        any(), any(), eq(1), eq(authorization));
  }

  @Test
  void openingOnlyAccountKeepsCsvColumnsAligned() throws Exception {
    GeneralLedgerMinorReport.Account account = new GeneralLedgerMinorReport.Account(
        "1930", "Bank", 100, 0, 0, 100, List.of());
    when(service.create(from, to, null)).thenReturn(new GeneralLedgerMinorReport(
        from, to, "SEK", 2, null, List.of(account), 1, 0, 0, 0));
    String csv = mvc.perform(get("/v2/general-ledger/export").header("Authorization", authorization)
        .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String[] lines = csv.strip().split("\\R");
    assertThat(lines).hasSize(2);
    assertThat(lines[1].split(",", -1)).hasSameSizeAs(lines[0].split(",", -1));
    assertThat(lines[1]).contains("\"1930\",\"Bank\",-,,,,,,100,0,0,,100");
  }
}
