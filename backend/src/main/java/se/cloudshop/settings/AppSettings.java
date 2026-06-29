package se.cloudshop.settings;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "app_settings")
public class AppSettings {

  @Id
  private Long id = 1L;

  private String companyName;
  private String contactEmail;
  private String plusGiro;
  private String defaultOcr;
  private String paymentRecipient;
  private String companyType;
  private int vatPercent;
  private int paymentTermsDays;
  private boolean fTaxApproved;
  @Column(columnDefinition = "text")
  private String invoiceEmailTemplate;
  private boolean automaticInvoiceRemindersEnabled;
  private int invoiceReminderDaysBeforeDue;
  @Column(columnDefinition = "text")
  private String invoiceReminderTemplate;
  private boolean overdueInvoiceRemindersEnabled;
  private int overdueInvoiceReminderDaysAfterDue;
  @Column(columnDefinition = "text")
  private String overdueInvoiceReminderTemplate;
  private LocalDate accountingLockedThroughDate;

  public AppSettings() {
  }

  public static AppSettings defaults() {
    AppSettings settings = new AppSettings();
    settings.companyName = "Muscle&Focus";
    settings.contactEmail = "ali.wafa17943@gmail.com";
    settings.plusGiro = "418 76 01-2";
    settings.defaultOcr = "1055065900139";
    settings.paymentRecipient = "Bank Norwegian";
    settings.companyType = "SOLE_TRADER";
    settings.vatPercent = 25;
    settings.paymentTermsDays = 30;
    settings.fTaxApproved = true;
    settings.invoiceEmailTemplate = defaultInvoiceEmailTemplate();
    settings.automaticInvoiceRemindersEnabled = true;
    settings.invoiceReminderDaysBeforeDue = 5;
    settings.invoiceReminderTemplate = defaultInvoiceReminderTemplate();
    settings.overdueInvoiceRemindersEnabled = true;
    settings.overdueInvoiceReminderDaysAfterDue = 3;
    settings.overdueInvoiceReminderTemplate = defaultOverdueInvoiceReminderTemplate();
    return settings;
  }

  public static String defaultInvoiceReminderTemplate() {
    return String.join("\n",
        "Hej {kundnamn},",
        "",
        "Vi vill paminna om faktura {fakturanummer}.",
        "Forfallodatum: {forfallodatum}.",
        "Kvar att betala: {belopp} SEK.",
        "",
        "Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.",
        "Betalningsmottagare: {betalningsmottagare}.",
        "",
        "Vanliga halsningar,",
        "{foretag}",
        "{kontaktEpost}"
    );
  }

  public static String defaultInvoiceEmailTemplate() {
    return String.join("\n",
        "Hej {kundnamn},",
        "",
        "Bifogat finns faktura {fakturanummer}.",
        "Forfallodatum: {forfallodatum}.",
        "Att betala: {belopp} SEK.",
        "",
        "Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.",
        "Betalningsmottagare: {betalningsmottagare}.",
        "",
        "Vanliga halsningar,",
        "{foretag}",
        "{kontaktEpost}"
    );
  }

  public static String defaultOverdueInvoiceReminderTemplate() {
    return String.join("\n",
        "Hej {kundnamn},",
        "",
        "Vi saknar fortfarande betalning for faktura {fakturanummer}.",
        "Fakturan forfoll {forfallodatum}.",
        "Kvar att betala: {belopp} SEK.",
        "",
        "Betala till PlusGiro {plusgiro} med OCR {ocr}.",
        "Betalningsmottagare: {betalningsmottagare}.",
        "",
        "Kontakta oss om betalningen redan ar gjord.",
        "",
        "Vanliga halsningar,",
        "{foretag}",
        "{kontaktEpost}"
    );
  }

  public Long getId() {
    return id;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public void setContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
  }

  public String getPlusGiro() {
    return plusGiro;
  }

  public void setPlusGiro(String plusGiro) {
    this.plusGiro = plusGiro;
  }

  public String getDefaultOcr() {
    return defaultOcr;
  }

  public void setDefaultOcr(String defaultOcr) {
    this.defaultOcr = defaultOcr;
  }

  public String getPaymentRecipient() {
    return paymentRecipient;
  }

  public void setPaymentRecipient(String paymentRecipient) {
    this.paymentRecipient = paymentRecipient;
  }

  public String getCompanyType() {
    return companyType;
  }

  public void setCompanyType(String companyType) {
    this.companyType = companyType;
  }

  public int getVatPercent() {
    return vatPercent;
  }

  public void setVatPercent(int vatPercent) {
    this.vatPercent = vatPercent;
  }

  public int getPaymentTermsDays() {
    return paymentTermsDays;
  }

  public void setPaymentTermsDays(int paymentTermsDays) {
    this.paymentTermsDays = paymentTermsDays;
  }

  public boolean isFTaxApproved() {
    return fTaxApproved;
  }

  public void setFTaxApproved(boolean fTaxApproved) {
    this.fTaxApproved = fTaxApproved;
  }

  public boolean isAutomaticInvoiceRemindersEnabled() {
    return automaticInvoiceRemindersEnabled;
  }

  public String getInvoiceEmailTemplate() {
    return invoiceEmailTemplate;
  }

  public void setInvoiceEmailTemplate(String invoiceEmailTemplate) {
    this.invoiceEmailTemplate = invoiceEmailTemplate;
  }

  public void setAutomaticInvoiceRemindersEnabled(boolean automaticInvoiceRemindersEnabled) {
    this.automaticInvoiceRemindersEnabled = automaticInvoiceRemindersEnabled;
  }

  public int getInvoiceReminderDaysBeforeDue() {
    return invoiceReminderDaysBeforeDue;
  }

  public void setInvoiceReminderDaysBeforeDue(int invoiceReminderDaysBeforeDue) {
    this.invoiceReminderDaysBeforeDue = invoiceReminderDaysBeforeDue;
  }

  public String getInvoiceReminderTemplate() {
    return invoiceReminderTemplate;
  }

  public void setInvoiceReminderTemplate(String invoiceReminderTemplate) {
    this.invoiceReminderTemplate = invoiceReminderTemplate;
  }

  public boolean isOverdueInvoiceRemindersEnabled() {
    return overdueInvoiceRemindersEnabled;
  }

  public void setOverdueInvoiceRemindersEnabled(boolean overdueInvoiceRemindersEnabled) {
    this.overdueInvoiceRemindersEnabled = overdueInvoiceRemindersEnabled;
  }

  public int getOverdueInvoiceReminderDaysAfterDue() {
    return overdueInvoiceReminderDaysAfterDue;
  }

  public void setOverdueInvoiceReminderDaysAfterDue(int overdueInvoiceReminderDaysAfterDue) {
    this.overdueInvoiceReminderDaysAfterDue = overdueInvoiceReminderDaysAfterDue;
  }

  public String getOverdueInvoiceReminderTemplate() {
    return overdueInvoiceReminderTemplate;
  }

  public void setOverdueInvoiceReminderTemplate(String overdueInvoiceReminderTemplate) {
    this.overdueInvoiceReminderTemplate = overdueInvoiceReminderTemplate;
  }

  public LocalDate getAccountingLockedThroughDate() {
    return accountingLockedThroughDate;
  }

  public void setAccountingLockedThroughDate(LocalDate accountingLockedThroughDate) {
    this.accountingLockedThroughDate = accountingLockedThroughDate;
  }
}
