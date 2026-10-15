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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class JournalMinorControllerTest {
  private final JournalMinorService service = mock(JournalMinorService.class);
  private final AuditService audit = mock(AuditService.class);
  private final JwtService jwt = new JwtService("test_secret");
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new JournalMinorController(service, new AuthHeader(jwt), audit)).build();
  private final String authorization = "Bearer " + jwt.createToken("accountant@example.test");
  private final LocalDate date = LocalDate.of(2026, 7, 2);

  private JournalMinorReport fixture() {
    var entry = new JournalMinorReport.Entry("31", date, "M-1",
        Instant.parse("2026-07-02T09:30:00Z"), "1930", "=BAD()", "=BAD()", null, 9L, 1, 0);
    return new JournalMinorReport(date, date, "SEK", 2, List.of(entry), 1, 0, 1,
        List.of(new JournalMinorReport.VoucherDifference("M-1", 1)));
  }

  private JournalMinorReport balancedFixture() {
    var credit = new JournalMinorReport.Entry("32", date, "M-1",
        Instant.parse("2026-07-02T09:30:00Z"), "3041", "Income", "Sale", null, 9L, 0, 1);
    return new JournalMinorReport(date, date, "SEK", 2,
        List.of(fixture().entries().getFirst(), credit), 1, 1, 0, List.of());
  }

  @Test
  void jsonExposesOnlyExactMinorAmounts() throws Exception {
    when(service.create(date, date)).thenReturn(fixture());
    mvc.perform(get("/v2/journal-entries").header("Authorization", authorization)
        .param("from", date.toString()).param("to", date.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[0].debitMinor").value(1))
        .andExpect(jsonPath("$.entries[0].debit").doesNotExist())
        .andExpect(jsonPath("$.entries[0].createdAt").value("2026-07-02T09:30:00Z"))
        .andExpect(jsonPath("$.voucherDifferences[0].voucherNumber").value("M-1"))
        .andExpect(jsonPath("$.voucherDifferences[0].differenceMinor").value(1))
        .andExpect(jsonPath("$.currencyCode").value("SEK"))
        .andExpect(jsonPath("$.minorUnitExponent").value(2));
  }

  @Test
  void rejectsAnonymousReportAndExport() throws Exception {
    mvc.perform(get("/v2/journal-entries")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v2/journal-entries/export")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service, audit);
  }

  @Test
  void csvKeepsColumnAlignmentAndNeutralizesFormulas() throws Exception {
    when(service.create(date, date)).thenReturn(balancedFixture());
    var response = mvc.perform(get("/v2/journal-entries/export").header("Authorization", authorization)
        .param("from", date.toString()).param("to", date.toString()))
        .andExpect(status().isOk()).andReturn().getResponse();
    String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    String[] rows = csv.strip().split("\\R");
    assertThat(rows).hasSize(4);
    assertThat(rows[1]).contains("\"'=BAD()\"").contains(",9,1,0,");
    assertThat(rows[0].split(",", -1)).hasSameSizeAs(rows[3].split(",", -1));
    assertThat(rows[3]).endsWith(",1,1,0");
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    verify(audit).record(eq("export"), eq("journal_entries"), any(), eq("journal_minor_exported"),
        any(), any(), eq(2), eq(authorization));
  }

  @Test
  void exportStopsOnUnbalancedVoucherWithoutAuditSuccess() throws Exception {
    when(service.create(date, date)).thenReturn(fixture());
    mvc.perform(get("/v2/journal-entries/export").header("Authorization", authorization)
        .param("from", date.toString()).param("to", date.toString()))
        .andExpect(status().isUnprocessableEntity());
    verifyNoInteractions(audit);
  }
}
