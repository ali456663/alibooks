package se.cloudshop.bank;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class BankReconciliationControllerTest {

  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final BankReconciliationEntryRepository bankReconciliationEntryRepository = mock(BankReconciliationEntryRepository.class);
  private final BankReconciliationService bankReconciliationService = mock(BankReconciliationService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final BankImportBookingService bankImport = new BankImportBookingService(bankReconciliationEntryRepository,
      mock(se.cloudshop.accounting.AccountingService.class), auditService);
  private final BankReconciliationController bankReconciliationController = controller(true);

  @Test
  void createBankReconciliationRecordsAuditEvent() {
    CreateBankReconciliationEntryRequest request = new CreateBankReconciliationEntryRequest(
        "bank-row-1",
        LocalDate.of(2026, 7, 15),
        "Stripe payout",
        "txn_123",
        1250,
        "payment",
        "skipped",
        "Matched invoice F-2026-0001"
    );
    when(bankReconciliationEntryRepository.save(any(BankReconciliationEntry.class))).thenAnswer(invocation -> {
      BankReconciliationEntry entry = invocation.getArgument(0);
      setEntryId(entry, 44L);
      return entry;
    });

    bankReconciliationController.createBankReconciliation("Bearer " + authHeaderToken(), request);

    verify(auditService).record(
        eq("bank"),
        eq("bank_reconciliation_entry"),
        eq(44L),
        eq("bank_reconciliation_entry_created"),
        eq("bank-row-1"),
        eq("Bank reconciliation entry created."),
        eq(1250),
        any(String.class)
    );
  }

  @Test
  void clearBankReconciliationsRecordsAuditEventWithRowCount() {
    when(bankReconciliationEntryRepository.count()).thenReturn(3L);

    bankReconciliationController.clearBankReconciliations(
        "Bearer " + authHeaderToken(),
        "DELETE_ALIBOOKS_BANK_RECONCILIATION_HISTORY"
    );

    verify(bankReconciliationEntryRepository).deleteAll();
    verify(auditService).record(
        eq("bank"),
        eq("bank_reconciliation"),
        eq("all"),
        eq("bank_reconciliations_cleared"),
        eq("all"),
        eq("Bank reconciliation entries cleared."),
        eq(3),
        any(String.class)
    );
  }

  @Test
  void clearBankReconciliationsRejectsResetWhenFeatureFlagIsDisabled() {
    BankReconciliationController controller = controller(false);

    assertThatThrownBy(() -> controller.clearBankReconciliations(
        "Bearer " + authHeaderToken(),
        "DELETE_ALIBOOKS_BANK_RECONCILIATION_HISTORY"
    )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Bank reconciliation reset is disabled");

    verify(bankReconciliationEntryRepository, never()).deleteAll();
  }

  @Test
  void clearBankReconciliationsRejectsMissingConfirmationHeader() {
    assertThatThrownBy(() -> bankReconciliationController.clearBankReconciliations(
        "Bearer " + authHeaderToken(),
        ""
    )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Missing bank reconciliation reset confirmation header");

    verify(bankReconciliationEntryRepository, never()).deleteAll();
  }

  @Test
  void removeSkippedBankReconciliationRecordsAuditEventWithDeletedRows() {
    when(bankReconciliationEntryRepository.deleteByBankRowIdAndStatus("bank-row-2", "skipped")).thenReturn(1L);

    bankReconciliationController.removeSkippedBankReconciliation("Bearer " + authHeaderToken(), "bank-row-2");

    verify(auditService).record(
        eq("bank"),
        eq("bank_reconciliation_entry"),
        eq("bank-row-2"),
        eq("skipped_bank_reconciliation_removed"),
        eq("bank-row-2"),
        eq("Skipped bank reconciliation entry removed."),
        eq(1),
        any(String.class)
    );
  }

  private String authHeaderToken() {
    return new JwtService("test_secret").createToken("ali@example.com");
  }

  private BankReconciliationController controller(boolean bankReconciliationResetEnabled) {
    return new BankReconciliationController(
        authHeader,
        bankReconciliationEntryRepository,
        bankReconciliationService,
        auditService,
        bankReconciliationResetEnabled,
        bankImport
    );
  }

  private void setEntryId(BankReconciliationEntry entry, Long id) {
    try {
      Field field = BankReconciliationEntry.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entry, id);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
