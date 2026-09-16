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
  private String accountingMethod;
  private String vatReportingPeriod;
  private int fiscalYearStartMonth;
  private int fiscalYearEndMonth;
  private int vatPercent;
  private int paymentTermsDays;
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean fTaxApproved = true;
  @Column(columnDefinition = "text")
  private String invoiceEmailTemplate;
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean automaticInvoiceRemindersEnabled = true;
  private int invoiceReminderDaysBeforeDue;
  @Column(columnDefinition = "text")
  private String invoiceReminderTemplate;
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean overdueInvoiceRemindersEnabled = true;
  private int overdueInvoiceReminderDaysAfterDue;
  @Column(columnDefinition = "text")
  private String overdueInvoiceReminderTemplate;
  private LocalDate accountingLockedThroughDate;

  public AppSettings() {
  }

  public static AppSettings defaults() {
    AppSettings settings = new AppSettings();
    settings.companyName = "Muscle&Focus";
    // Company-specific contact and payment details must be entered by the owner.
    // Never ship personal or potentially stale payment data as defaults.
    settings.contactEmail = "";
    settings.plusGiro = "";
    settings.defaultOcr = "";
    settings.paymentRecipient = "";
    settings.companyType = "SOLE_TRADER";
    settings.accountingMethod = "INVOICE_METHOD";
    settings.vatReportingPeriod = "QUARTERLY";
    settings.fiscalYearStartMonth = 1;
    settings.fiscalYearEndMonth = 12;
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

  public String getAccountingMethod() {
    return accountingMethod;
  }

  public void setAccountingMethod(String accountingMethod) {
    this.accountingMethod = accountingMethod;
  }

  public String getVatReportingPeriod() {
    return vatReportingPeriod;
  }

  public void setVatReportingPeriod(String vatReportingPeriod) {
    this.vatReportingPeriod = vatReportingPeriod;
  }

  public int getFiscalYearStartMonth() {
    return fiscalYearStartMonth;
  }

  public void setFiscalYearStartMonth(int fiscalYearStartMonth) {
    this.fiscalYearStartMonth = fiscalYearStartMonth;
  }

  public int getFiscalYearEndMonth() {
    return fiscalYearEndMonth;
  }

  public void setFiscalYearEndMonth(int fiscalYearEndMonth) {
    this.fiscalYearEndMonth = fiscalYearEndMonth;
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
