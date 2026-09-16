package se.cloudshop.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.JournalEntryRepository;

class SettingsServiceTest {

  private final AppSettingsRepository appSettingsRepository = mock(AppSettingsRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final SettingsService settingsService = new SettingsService(appSettingsRepository, journalEntryRepository, entityManager);

  @Test
  void refreshesSettingsUnderDatabaseWriteLock() {
    AppSettings current = AppSettings.defaults();
    when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(current));
    assertThat(settingsService.lockSettingsForAccounting()).isSameAs(current);
    org.mockito.Mockito.verify(entityManager).refresh(current, LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  void rejectsMissingSettingsInsteadOfCreatingUnlockedDefaults() {
    assertThatThrownBy(settingsService::lockSettingsForAccounting)
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("initialized");
    org.mockito.Mockito.verify(appSettingsRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void allowsAccountingLockToMoveForward() {
    AppSettings settings = AppSettings.defaults();
    settings.setAccountingLockedThroughDate(LocalDate.of(2026, 6, 30));
    when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
    when(appSettingsRepository.save(settings)).thenReturn(settings);

    AppSettings updated = settingsService.lockAccountingThroughDate(LocalDate.of(2026, 7, 31));

    assertThat(updated.getAccountingLockedThroughDate()).isEqualTo(LocalDate.of(2026, 7, 31));
  }

  @Test
  void rejectsAccountingLockDateMovingBackwards() {
    AppSettings settings = AppSettings.defaults();
    settings.setAccountingLockedThroughDate(LocalDate.of(2026, 7, 31));
    when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

    assertThatThrownBy(() -> settingsService.lockAccountingThroughDate(LocalDate.of(2026, 6, 30)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("cannot be moved backwards");
  }

  @Test
  void rejectsEmptyAccountingLockDate() {
    assertThatThrownBy(() -> settingsService.lockAccountingThroughDate(null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("is required");
  }

  @Test
  void rejectsCompanyTypeChangeAfterBookkeepingExists() {
    AppSettings current = AppSettings.defaults();
    AppSettings requested = AppSettings.defaults();
    requested.setCompanyType("LIMITED_COMPANY");
    when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(current));
    when(journalEntryRepository.count()).thenReturn(1L);

    assertThatThrownBy(() -> settingsService.updateSettings(requested))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Company type cannot be changed");
  }

  @Test
  void rejectsAccountingMethodChangeAfterBookkeepingExists() {
    AppSettings current = AppSettings.defaults();
    AppSettings requested = AppSettings.defaults();
    requested.setAccountingMethod("CASH_METHOD");
    when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(current));
    when(journalEntryRepository.count()).thenReturn(1L);

    assertThatThrownBy(() -> settingsService.updateSettings(requested))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Accounting method cannot be changed");
  }

  @Test
  void allowsAccountingPolicyChangeBeforeBookkeepingExists() {
    AppSettings current = AppSettings.defaults();
    AppSettings requested = AppSettings.defaults();
    requested.setCompanyType("LIMITED_COMPANY");
    requested.setAccountingMethod("CASH_METHOD");
    when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(current));
    when(appSettingsRepository.save(current)).thenReturn(current);
    when(journalEntryRepository.count()).thenReturn(0L);

    AppSettings updated = settingsService.updateSettings(requested);

    assertThat(updated.getCompanyType()).isEqualTo("LIMITED_COMPANY");
    assertThat(updated.getAccountingMethod()).isEqualTo("CASH_METHOD");
  }
}
