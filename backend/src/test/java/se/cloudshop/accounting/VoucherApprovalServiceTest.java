package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class VoucherApprovalServiceTest {

  private final VoucherApprovalRepository voucherApprovalRepository = mock(VoucherApprovalRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final VoucherApprovalService voucherApprovalService = new VoucherApprovalService(
      voucherApprovalRepository,
      journalEntryRepository,
      accountingService
  );

  @Test
  void createsVoucherApprovalDecision() {
    when(voucherApprovalRepository.findByVoucherNumber("F-2026-0001")).thenReturn(Optional.empty());
    when(voucherApprovalRepository.save(any(VoucherApproval.class))).thenAnswer(invocation -> invocation.getArgument(0));

    VoucherApproval approval = voucherApprovalService.update(
        "F-2026-0001",
        new UpdateVoucherApprovalRequest("approved", "Checked against invoice and bank.", "Ali")
    );

    assertThat(approval.getVoucherNumber()).isEqualTo("F-2026-0001");
    assertThat(approval.getStatus()).isEqualTo("approved");
    assertThat(approval.getNote()).isEqualTo("Checked against invoice and bank.");
    assertThat(approval.getReviewer()).isEqualTo("Ali");
    assertThat(approval.getReviewedAt()).isNotNull();
  }

  @Test
  void rejectsInvalidVoucherApprovalStatus() {
    assertThatThrownBy(() -> voucherApprovalService.update(
        "F-2026-0001",
        new UpdateVoucherApprovalRequest("done", "", "Ali")
    )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("approved, pending or blocked");
  }

  @Test
  void rejectsApprovalChangeWhenVoucherDateIsLocked() {
    LocalDate voucherDate = LocalDate.of(2026, 6, 30);
    JournalEntry entry = new JournalEntry(
        null,
        new Account("1930", "Foretagskonto"),
        "F-2026-0001",
        100,
        0,
        "Invoice paid",
        voucherDate
    );
    when(journalEntryRepository.findByVoucherNumber("F-2026-0001")).thenReturn(List.of(entry));
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Bokforingen ar last"))
        .when(accountingService)
        .requireUnlockedAccountingDate(voucherDate);

    assertThatThrownBy(() -> voucherApprovalService.update(
        "F-2026-0001",
        new UpdateVoucherApprovalRequest("approved", "Checked", "Ali")
    )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Bokforingen ar last");

    verify(voucherApprovalRepository, never()).save(any(VoucherApproval.class));
  }
}
