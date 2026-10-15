package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class VoucherEvidenceServiceTest {

  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final VoucherEvidenceRepository voucherEvidenceRepository = mock(VoucherEvidenceRepository.class);
  private final VoucherEvidenceContentRepository contentRepository = mock(VoucherEvidenceContentRepository.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final VoucherEvidenceService service = new VoucherEvidenceService(
      journalEntryRepository, voucherEvidenceRepository, contentRepository, accountingService);

  @BeforeEach
  void setUp() {
    when(voucherEvidenceRepository.saveAndFlush(any(VoucherEvidence.class)))
        .thenAnswer(invocation -> {
          VoucherEvidence evidence = invocation.getArgument(0);
          ReflectionTestUtils.setField(evidence, "id", 1L);
          return evidence;
        });
    when(contentRepository.saveAndFlush(any(VoucherEvidenceContent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void archivesDocumentAndLinksAllJournalLines() {
    JournalEntry debit = entry("M-1", 100, 0);
    JournalEntry credit = entry("M-1", 0, 100);
    when(journalEntryRepository.findByVoucherNumberForCorrection("M-1")).thenReturn(List.of(debit, credit));

    VoucherEvidence evidence = service.attach("M-1", pdf("memo.pdf"));

    assertThat(evidence.getVoucherNumber()).isEqualTo("M-1");
    assertThat(evidence.getSha256()).hasSize(64).matches("[0-9A-F]+?");
    ArgumentCaptor<VoucherEvidenceContent> content = ArgumentCaptor.forClass(VoucherEvidenceContent.class);
    verify(contentRepository).saveAndFlush(content.capture());
    assertThat(content.getValue().getContent()).isEqualTo("%PDF-memo".getBytes(StandardCharsets.UTF_8));
    assertThat(debit.getEvidenceStatus()).isEqualTo("traceable");
    assertThat(credit.getEvidenceHash()).isEqualTo(evidence.getSha256());
    verify(journalEntryRepository).saveAllAndFlush(List.of(debit, credit));

    when(voucherEvidenceRepository.findByVoucherNumber("M-1")).thenReturn(Optional.of(evidence));
    when(contentRepository.findById(1L)).thenReturn(Optional.of(content.getValue()));
    assertThat(service.download("M-1").content()).isEqualTo("%PDF-memo".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void rejectsReplacementAndUnrelatedVoucherTypes() {
    JournalEntry entry = entry("M-2", 100, 0);
    when(journalEntryRepository.findByVoucherNumberForCorrection("M-2")).thenReturn(List.of(entry));
    service.attach("M-2", pdf("memo.pdf"));

    assertThatThrownBy(() -> service.attach("M-2", pdf("replacement.pdf")))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    when(journalEntryRepository.findByVoucherNumberForCorrection("F-1")).thenReturn(List.of(entry("F-1", 100, 0)));
    assertThatThrownBy(() -> service.attach("F-1", pdf("invoice.pdf")))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void rejectsMimeMismatchBeforeSavingAnything() {
    MockMultipartFile fake = new MockMultipartFile("file", "memo.pdf", "application/pdf",
        "not-a-pdf".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> service.attach("M-1", fake))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(voucherEvidenceRepository, never()).saveAndFlush(any(VoucherEvidence.class));
  }

  private JournalEntry entry(String number, int debit, int credit) {
    return new JournalEntry(null, new Account("1930", "Bank"), number, debit, credit,
        "Adjustment", LocalDate.of(2026, 7, 4));
  }

  private MockMultipartFile pdf(String name) {
    return new MockMultipartFile("file", name, "application/pdf",
        "%PDF-memo".getBytes(StandardCharsets.UTF_8));
  }
}
