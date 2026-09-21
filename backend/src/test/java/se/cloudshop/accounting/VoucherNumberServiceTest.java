package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

class VoucherNumberServiceTest {

  private final JournalEntryRepository journalEntries = mock(JournalEntryRepository.class);
  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final VoucherNumberService service = new VoucherNumberService(journalEntries, jdbcTemplate);

  @Test
  void locksVoucherSeriesBeforeReadingLatestSequenceInDatabase() {
    allowAdvisoryLock();
    when(journalEntries.findLatestVoucherSequence("F", 3)).thenReturn(12L);

    assertThat(service.nextVoucherNumber(" f ")).isEqualTo("F-13");

    InOrder order = inOrder(jdbcTemplate, journalEntries);
    order.verify(jdbcTemplate).queryForObject(anyString(), eq(Integer.class), any(), any());
    order.verify(journalEntries).findLatestVoucherSequence("F", 3);
    verify(journalEntries, never()).findAll();
  }

  @Test
  void usesDefaultSeriesWhenMissing() {
    allowAdvisoryLock();
    when(journalEntries.findLatestVoucherSequence("V", 3)).thenReturn(0L);

    assertThat(service.nextVoucherNumber(" ")).isEqualTo("V-1");
  }

  @Test
  void refusesToWrapVoucherSequenceAtIntegerLimit() {
    allowAdvisoryLock();
    when(journalEntries.findLatestVoucherSequence("M", 3)).thenReturn((long) Integer.MAX_VALUE);

    assertThatThrownBy(() -> service.nextVoucherNumber("M"))
        .isInstanceOf(ArithmeticException.class);
  }

  private void allowAdvisoryLock() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
  }
}
