package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

class AccountingServiceTest {

  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final StripePayoutRepository stripePayoutRepository = mock(StripePayoutRepository.class);
  private final VoucherNumberService voucherNumberService = mock(VoucherNumberService.class);
  private final SettingsService settingsService = mock(SettingsService.class);
  private final AccountingService accountingService = new AccountingService(
      accountRepository,
      journalEntryRepository,
      stripePayoutRepository,
      voucherNumberService,
      settingsService
  );

  @BeforeEach
  void setUp() {
    when(settingsService.getSettings()).thenReturn(AppSettings.defaults());
    when(journalEntryRepository.findAll()).thenReturn(List.of());
    when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(stripePayoutRepository.save(any(StripePayout.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(stripePayoutRepository.findByReference(any())).thenReturn(Optional.empty());

    mockAccount("1580", "Fordran hos Stripe");
    mockAccount("1930", "Foretagskonto");
    mockAccount("2611", "Utgaende moms");
    mockAccount("3041", "Forsaljning tjanster 25 procent");
    mockAccount("6570", "Bankkostnader");
  }

  @Test
  void createsJournalEntriesForStripeWebsiteSale() {
    when(voucherNumberService.nextVoucherNumber("S")).thenReturn("S-1");

    accountingService.createStripeExternalSaleEntries(1250, "pi_test_123", LocalDate.of(2026, 6, 29));

    List<JournalEntry> entries = savedJournalEntries();

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1580");
      assertThat(entry.getDebit()).isEqualTo(1250);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("3041");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1000);
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("2611");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(250);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("S-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 6, 29));
      assertThat(entry.getDescription()).contains("pi_test_123");
    });
  }

  @Test
  void createsJournalEntriesForStripePayout() {
    when(voucherNumberService.nextVoucherNumber("SU")).thenReturn("SU-1");

    List<JournalEntry> entries = accountingService.createStripePayoutEntry(new CreateStripePayoutRequest(
        LocalDate.of(2026, 6, 30),
        1250,
        39,
        "po_test_123"
    ));

    assertThat(entries).hasSize(3);
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1930");
      assertThat(entry.getDebit()).isEqualTo(1211);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("6570");
      assertThat(entry.getDebit()).isEqualTo(39);
      assertThat(entry.getCredit()).isZero();
    });
    assertThat(entries).anySatisfy(entry -> {
      assertThat(entry.getAccountNumber()).isEqualTo("1580");
      assertThat(entry.getDebit()).isZero();
      assertThat(entry.getCredit()).isEqualTo(1250);
    });
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.getVoucherNumber()).isEqualTo("SU-1");
      assertThat(entry.getVoucherDate()).isEqualTo(LocalDate.of(2026, 6, 30));
      assertThat(entry.getDescription()).contains("po_test_123");
    });
  }

  private List<JournalEntry> savedJournalEntries() {
    ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
    org.mockito.Mockito.verify(journalEntryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return new ArrayList<>(captor.getAllValues());
  }

  private void mockAccount(String number, String name) {
    when(accountRepository.findByNumber(number)).thenReturn(Optional.of(new Account(number, name)));
  }
}
