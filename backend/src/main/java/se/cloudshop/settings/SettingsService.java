package se.cloudshop.settings;

import java.time.LocalDate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.JournalEntryRepository;
import se.cloudshop.invoice.InvoiceVatPolicy;

@Service
public class SettingsService {

  private final AppSettingsRepository appSettingsRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final EntityManager entityManager;

  public SettingsService(AppSettingsRepository appSettingsRepository, JournalEntryRepository journalEntryRepository, EntityManager entityManager) {
    this.appSettingsRepository = appSettingsRepository;
    this.journalEntryRepository = journalEntryRepository;
    this.entityManager = entityManager;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public AppSettings lockSettingsForAccounting() {
    AppSettings settings = appSettingsRepository.findById(1L).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.CONFLICT, "Accounting settings must be initialized before writing."));
    // The shared row lock lasts through commit; refresh also discards stale JPA reads.
    entityManager.refresh(settings, LockModeType.PESSIMISTIC_WRITE);
    return settings;
  }

  public AppSettings getSettings() {
    return appSettingsRepository.findById(1L)
        .orElseGet(() -> appSettingsRepository.save(AppSettings.defaults()));
  }

  @Transactional
  public AppSettings updateSettings(AppSettings updatedSettings) {
    if (updatedSettings == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settings payload is required.");
    }
    AppSettings settings = lockSettingsForAccounting();
    // Validate every update, including a legacy row that already contains an unsupported rate.
    // A bad stored value must never be accepted merely because it was not changed in this request.
    InvoiceVatPolicy.requireSupportedRate(updatedSettings.getVatPercent());
    LocalDate currentLockedThroughDate = settings.getAccountingLockedThroughDate();
    validateAccountingLockChange(currentLockedThroughDate, updatedSettings.getAccountingLockedThroughDate());
    validateAccountingPolicyChange(settings, updatedSettings);
    settings.setCompanyName(updatedSettings.getCompanyName());
    settings.setCompanyAddress(updatedSettings.getCompanyAddress());
    settings.setCompanyPostalCode(updatedSettings.getCompanyPostalCode());
    settings.setCompanyCity(updatedSettings.getCompanyCity());
    settings.setCompanyOrganizationNumber(updatedSettings.getCompanyOrganizationNumber());
    settings.setVatRegistrationNumber(updatedSettings.getVatRegistrationNumber());
    settings.setContactEmail(updatedSettings.getContactEmail());
    settings.setPlusGiro(updatedSettings.getPlusGiro());
    settings.setDefaultOcr(updatedSettings.getDefaultOcr());
    settings.setPaymentRecipient(updatedSettings.getPaymentRecipient());
    settings.setCompanyType(normalizeCompanyType(updatedSettings.getCompanyType()));
    settings.setAccountingMethod(normalizeAccountingMethod(updatedSettings.getAccountingMethod()));
    settings.setVatReportingPeriod(normalizeVatReportingPeriod(updatedSettings.getVatReportingPeriod()));
    settings.setFiscalYearStartMonth(normalizeMonth(updatedSettings.getFiscalYearStartMonth(), 1));
    settings.setFiscalYearEndMonth(normalizeMonth(updatedSettings.getFiscalYearEndMonth(), 12));
    settings.setVatPercent(updatedSettings.getVatPercent());
    settings.setPaymentTermsDays(updatedSettings.getPaymentTermsDays() <= 0 ? 30 : updatedSettings.getPaymentTermsDays());
    settings.setFTaxApproved(updatedSettings.isFTaxApproved());
    settings.setInvoiceEmailTemplate(normalizeInvoiceEmailTemplate(updatedSettings.getInvoiceEmailTemplate()));
    settings.setAutomaticInvoiceRemindersEnabled(updatedSettings.isAutomaticInvoiceRemindersEnabled());
    settings.setInvoiceReminderDaysBeforeDue(normalizeReminderDays(updatedSettings.getInvoiceReminderDaysBeforeDue()));
    settings.setInvoiceReminderTemplate(normalizeReminderTemplate(updatedSettings.getInvoiceReminderTemplate()));
    settings.setOverdueInvoiceRemindersEnabled(updatedSettings.isOverdueInvoiceRemindersEnabled());
    settings.setOverdueInvoiceReminderDaysAfterDue(normalizeReminderDays(updatedSettings.getOverdueInvoiceReminderDaysAfterDue()));
    settings.setOverdueInvoiceReminderTemplate(normalizeOverdueReminderTemplate(updatedSettings.getOverdueInvoiceReminderTemplate()));
    settings.setAccountingLockedThroughDate(currentLockedThroughDate);
    return appSettingsRepository.save(settings);
  }

  @Transactional
  public AppSettings lockAccountingThroughDate(LocalDate lockedThroughDate) {
    if (lockedThroughDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accounting lock date is required.");
    }

    AppSettings settings = lockSettingsForAccounting();
    LocalDate currentLockedThroughDate = settings.getAccountingLockedThroughDate();
    if (currentLockedThroughDate != null && lockedThroughDate.isBefore(currentLockedThroughDate)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Accounting lock date cannot be moved backwards."
      );
    }

    settings.setAccountingLockedThroughDate(lockedThroughDate);
    return appSettingsRepository.save(settings);
  }

  private void validateAccountingLockChange(LocalDate currentLockedThroughDate, LocalDate requestedLockedThroughDate) {
    if (currentLockedThroughDate == null && requestedLockedThroughDate == null) {
      return;
    }

    if (currentLockedThroughDate == null || requestedLockedThroughDate == null || !currentLockedThroughDate.equals(requestedLockedThroughDate)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Accounting lock date must be changed from Period lock, not regular settings."
      );
    }
  }

  private void validateAccountingPolicyChange(AppSettings currentSettings, AppSettings requestedSettings) {
    if (journalEntryRepository.count() == 0) {
      return;
    }

    String currentCompanyType = normalizeCompanyType(currentSettings.getCompanyType());
    String requestedCompanyType = normalizeCompanyType(requestedSettings.getCompanyType());
    String currentAccountingMethod = normalizeAccountingMethod(currentSettings.getAccountingMethod());
    String requestedAccountingMethod = normalizeAccountingMethod(requestedSettings.getAccountingMethod());

    if (!currentCompanyType.equals(requestedCompanyType)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Company type cannot be changed after bookkeeping has been created. Create a migration note or a new company setup instead."
      );
    }

    if (!currentAccountingMethod.equals(requestedAccountingMethod)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Accounting method cannot be changed after bookkeeping has been created. Create a controlled migration instead."
      );
    }
  }

  private String normalizeCompanyType(String companyType) {
    if ("LIMITED_COMPANY".equals(companyType)) {
      return "LIMITED_COMPANY";
    }

    return "SOLE_TRADER";
  }

  private String normalizeAccountingMethod(String accountingMethod) {
    if ("CASH_METHOD".equals(accountingMethod)) {
      return "CASH_METHOD";
    }

    return "INVOICE_METHOD";
  }

  private String normalizeVatReportingPeriod(String vatReportingPeriod) {
    if ("MONTHLY".equals(vatReportingPeriod) || "YEARLY".equals(vatReportingPeriod)) {
      return vatReportingPeriod;
    }

    return "QUARTERLY";
  }

  private int normalizeMonth(int month, int fallback) {
    if (month < 1 || month > 12) {
      return fallback;
    }

    return month;
  }

  private int normalizeReminderDays(int days) {
    if (days < 1) {
      return 5;
    }

    return Math.min(days, 30);
  }

  private String normalizeReminderTemplate(String template) {
    if (template == null || template.isBlank()) {
      return AppSettings.defaultInvoiceReminderTemplate();
    }

    return template;
  }

  private String normalizeInvoiceEmailTemplate(String template) {
    if (template == null || template.isBlank()) {
      return AppSettings.defaultInvoiceEmailTemplate();
    }

    return template;
  }

  private String normalizeOverdueReminderTemplate(String template) {
    if (template == null || template.isBlank()) {
      return AppSettings.defaultOverdueInvoiceReminderTemplate();
    }

    return template;
  }
}
