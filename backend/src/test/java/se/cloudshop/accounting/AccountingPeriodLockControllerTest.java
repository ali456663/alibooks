package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class AccountingPeriodLockControllerTest {

  private final JwtService jwtService = new JwtService("test_secret");
  private final AuthHeader authHeader = new AuthHeader(jwtService);
  private final AccountingPeriodLockService accountingPeriodLockService = mock(AccountingPeriodLockService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final AccountingPeriodLockController controller = new AccountingPeriodLockController(
      authHeader,
      accountingPeriodLockService,
      auditService
  );

  @Test
  void closeCheckExportRecordsAuditEventWithRiskCounts() {
    LocalDate lockedThroughDate = LocalDate.of(2026, 7, 31);
    PeriodCloseCheckResult result = periodCloseCheckResult(lockedThroughDate);
    when(accountingPeriodLockService.checkPeriod(lockedThroughDate)).thenReturn(result);
    String authorizationHeader = "Bearer " + jwtService.createToken("ali@example.com");

    ResponseEntity<byte[]> response = controller.exportPeriodCheck(authorizationHeader, lockedThroughDate);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("AliBooks periodlasningskontroll")
        .contains("Sena verifikat")
        .contains("M-55");
    verify(auditService).record(
        "export",
        "period_close_check",
        lockedThroughDate,
        "period_close_check_exported",
        "2026-07-31",
        "Period close check exported. Ready to lock: false. Blockers: 1. Warnings: 1. Late vouchers: 1.",
        3,
        authorizationHeader
    );
  }

  private PeriodCloseCheckResult periodCloseCheckResult(LocalDate lockedThroughDate) {
    return new PeriodCloseCheckResult(
        lockedThroughDate,
        false,
        List.of("Voucher approval is missing."),
        List.of("Late voucher found."),
        3,
        0,
        0,
        0,
        1,
        42,
        List.of(new LateBookedVoucher(
            "M-55",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 13),
            42,
            500,
            500,
            "Late bookkeeping example"
        )),
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        2,
        1,
        0,
        0,
        "PERIODHASH",
        "CHAINHASH",
        false,
        false
    );
  }
}
