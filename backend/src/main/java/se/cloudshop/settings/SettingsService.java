package se.cloudshop.settings;

import org.springframework.stereotype.Service;

@Service
public class SettingsService {

  private final AppSettingsRepository appSettingsRepository;

  public SettingsService(AppSettingsRepository appSettingsRepository) {
    this.appSettingsRepository = appSettingsRepository;
  }

  public AppSettings getSettings() {
    return appSettingsRepository.findById(1L)
        .orElseGet(() -> appSettingsRepository.save(AppSettings.defaults()));
  }

  public AppSettings updateSettings(AppSettings updatedSettings) {
    AppSettings settings = getSettings();
    settings.setCompanyName(updatedSettings.getCompanyName());
    settings.setContactEmail(updatedSettings.getContactEmail());
    settings.setPlusGiro(updatedSettings.getPlusGiro());
    settings.setDefaultOcr(updatedSettings.getDefaultOcr());
    settings.setPaymentRecipient(updatedSettings.getPaymentRecipient());
    settings.setCompanyType(normalizeCompanyType(updatedSettings.getCompanyType()));
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
    settings.setAccountingLockedThroughDate(updatedSettings.getAccountingLockedThroughDate());
    return appSettingsRepository.save(settings);
  }

  private String normalizeCompanyType(String companyType) {
    if ("LIMITED_COMPANY".equals(companyType)) {
      return "LIMITED_COMPANY";
    }

    return "SOLE_TRADER";
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
