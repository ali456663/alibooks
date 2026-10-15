package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class JournalMoneyIntegrityTest {
  private JournalEntry entry() {
    return new JournalEntry(null, new Account("1930", "Bank"), "M-1", 1250, 0,
        "Test", LocalDate.of(2026, 9, 24));
  }

  @Test
  void validPersistedRowAndHashStayUnchangedAcrossCallbacks() {
    JournalEntry row = entry();
    String hash = row.getIntegrityHash();
    row.prepareMoneyForInsert();
    row.validatePersistedMoney();
    row.setCorrectionOfVoucherNumber("M-0");
    row.validatePersistedMoney();
    assertThat(row.getDebitMinor()).isEqualTo(125000L);
    assertThat(row.getCreditMinor()).isZero();
    row.setCorrectionOfVoucherNumber(null);
    assertThat(row.getIntegrityHash()).isEqualTo(hash);
  }

  @ParameterizedTest
  @ValueSource(strings = {"debitMinor", "creditMinor"})
  void missingPersistedShadowIsNotReconstructed(String field) {
    JournalEntry row = entry();
    ReflectionTestUtils.setField(row, field, null);
    assertThatThrownBy(row::validatePersistedMoney).isInstanceOf(JournalMoneyIntegrityException.class);
    assertThat(ReflectionTestUtils.getField(row, field)).isNull();
    assertThatThrownBy(row::getIntegrityHash).isInstanceOf(JournalMoneyIntegrityException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {125001L, 125100L, -1L, Long.MAX_VALUE})
  void mismatchIsNotOverwrittenOnReadOrWrite(long shadow) {
    JournalEntry row = entry();
    ReflectionTestUtils.setField(row, "debitMinor", shadow);
    assertThatThrownBy(row::validatePersistedMoney).isInstanceOf(JournalMoneyIntegrityException.class);
    assertThatThrownBy(row::prepareMoneyForInsert).isInstanceOf(JournalMoneyIntegrityException.class);
    assertThat(row.getDebitMinor()).isEqualTo(shadow);
    assertThat(row.getDebit()).isEqualTo(1250);
  }

  @Test
  void onlyNewRowsMayInitializeMissingShadows() {
    JournalEntry row = entry();
    ReflectionTestUtils.setField(row, "debitMinor", null);
    ReflectionTestUtils.setField(row, "creditMinor", null);
    row.prepareMoneyForInsert();
    assertThat(row.getDebitMinorValue()).isEqualTo(125000L);
    assertThat(row.getCreditMinorValue()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"EUR", "", "sek"})
  void wrongCurrencyIsNotSilentlyChangedToSek(String currency) {
    JournalEntry row = entry();
    ReflectionTestUtils.setField(row, "currencyCode", currency);
    assertThatThrownBy(row::prepareMoneyForInsert).isInstanceOf(JournalMoneyIntegrityException.class);
    assertThatThrownBy(row::validatePersistedMoney).isInstanceOf(JournalMoneyIntegrityException.class);
    assertThat(row.getCurrencyCode()).isEqualTo(currency);
  }

  @Test
  void missingCurrencyIsRejected() {
    JournalEntry row = entry();
    ReflectionTestUtils.setField(row, "currencyCode", null);
    assertThatThrownBy(row::prepareMoneyForInsert).isInstanceOf(JournalMoneyIntegrityException.class);
    assertThatThrownBy(row::validatePersistedMoney).isInstanceOf(JournalMoneyIntegrityException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 1})
  void negativeOrTwoSidedRowsAreRejected(int credit) {
    JournalEntry row = new JournalEntry(null, new Account("1930", "Bank"), "M-1", 100, credit, "Test");
    assertThatThrownBy(row::prepareMoneyForInsert).isInstanceOf(JournalMoneyIntegrityException.class);
    assertThatThrownBy(row::validatePersistedMoney).isInstanceOf(JournalMoneyIntegrityException.class);
  }

  @Test
  void zeroVatRowsAndLargestLegacyValueAreSupportedExactly() {
    for (int value : new int[] {0, Integer.MAX_VALUE}) {
      JournalEntry row = new JournalEntry(null, new Account("1930", "Bank"), "M-1", value, 0, "Test");
      row.prepareMoneyForInsert();
      row.validatePersistedMoney();
      assertThat(row.getDebitMinorValue()).isEqualTo((long) value * 100L);
    }
  }
}
