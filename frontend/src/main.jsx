import React, { Suspense, lazy, useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import AiLoader from "./components/ui/AiLoader.jsx";
import AnimatedGlowingSearchBar from "./components/ui/AnimatedGlowingSearchBar.jsx";
import HeroErrorBoundary from "./components/ui/hero-error-boundary.jsx";
import SafeRenderBoundary from "./components/ui/SafeRenderBoundary.jsx";
import "./styles.css";

const apiUrl =
  window.__ALIBOOKS_CONFIG__?.apiUrl ||
  import.meta.env.VITE_API_URL ||
  "http://localhost:3000";
const LiquidMetalHero = lazy(() => import("./components/ui/liquid-metal-hero.jsx"));
const FlowingMenu = lazy(() => import("./components/ui/FlowingMenu.jsx"));
const LiquidEther = lazy(() => import("./components/ui/LiquidEther.jsx"));

const copy = {
  en: {
    dashboard: "Business dashboard",
    overview: "Overview",
    createNew: "Create new",
    customers: "Customers",
    invoices: "Invoices",
    contracts: "Contracts",
    services: "Services",
    activities: "Events",
    serviceAdmin: "Service admin",
    cashflow: "Cashflow",
    budget: "Budget & goals",
    payroll: "Payroll",
    serviceName: "Service name",
    serviceDescription: "Service description",
    ordinaryPrice: "Ordinary price",
    discountPrice: "Discount price",
    discountLabel: "Discount label",
    activeService: "Active service",
    saveService: "Save service",
    newService: "New service",
    edit: "Edit",
    inactive: "Inactive",
    serviceSaved: "Service saved.",
    payments: "Payments",
    uploaded: "Uploaded",
    bookkeeping: "Bookkeeping",
    chartOfAccounts: "Chart of accounts",
    vatReport: "VAT report",
    reports: "Reports",
    settings: "Settings",
    revenue: "Revenue demo",
    paid: "Paid",
    name: "Name",
    email: "Email",
    saveCustomer: "Save customer",
    customerName: "Customer name",
    customerEmail: "customer@example.com",
    personalNumber: "Personal number",
    address: "Address",
    phone: "Phone",
    postalCode: "Postal code",
    city: "City",
    createInvoice: "Create invoice",
    chooseCustomer: "Choose customer",
    chooseService: "Choose service",
    quantity: "Quantity",
    noInvoices: "No invoices yet.",
    noCustomers: "No customers yet.",
    noExpenses: "No expenses yet.",
    noJournalEntries: "No journal entries yet.",
    refresh: "Refresh",
    signedIn: "Signed in as",
    logout: "Log out",
    login: "Login",
    register: "Register",
    password: "Password",
    customerSaved: "Customer saved.",
    noServices: "No services loaded. Restart backend so demo services are seeded.",
    advisor: "AI Advisor",
    reminders: "AI reminders",
    searchCustomers: "Search customers",
    searchInvoices: "Search invoices",
    all: "All",
    draft: "Draft",
    sent: "Sent",
    unpaid: "Unpaid",
    overdue: "Overdue",
    dueSoon: "Due soon",
    expenses: "Expenses",
    description: "Description",
    net: "Net",
    vat: "VAT",
    total: "Total",
    category: "Category",
    saveExpense: "Save expense",
    receipt: "Receipt",
    uploadReceipt: "Upload receipt",
    attachReceipt: "Attach receipt",
    openReceipt: "Open receipt",
    receiptsSaved: "Receipts saved",
    receiptsMissing: "Missing receipts",
    noReceipts: "No receipts uploaded yet.",
    noMissingReceipts: "All expenses have receipts.",
    customerRequiredLogin: "Log in to see reminders.",
    settingsSaved: "Settings saved.",
    saveSettings: "Save settings",
    clearTestData: "Clear test data",
    testDataCleared: "Test data cleared.",
    company: "Company",
    companyType: "Company type",
    soleTrader: "Sole trader",
    limitedCompany: "Limited company",
    contactEmail: "Contact email",
    paymentRecipient: "Payment recipient",
    date: "Date",
    approvedForFtax: "Approved for F-tax",
    serviceMissing: "Service missing",
    payment: "Payment",
    markSent: "Mark sent",
    markPaid: "Register payment",
    outputVat: "Output VAT",
    inputVat: "Input VAT",
    vatToPay: "VAT to pay",
    exportCsv: "Export CSV",
    voucher: "Voucher",
    account: "Account",
    manualVoucher: "Manual voucher",
    debitAccount: "Debit account",
    creditAccount: "Credit account",
    amount: "Amount",
    saveVoucher: "Save voucher",
    accountNumber: "Account number",
    accountName: "Account name",
    appliesTo: "Applies to",
    debit: "Debit",
    credit: "Credit",
    vatPercent: "VAT percent",
    paymentTermsDays: "Payment terms days",
    noEmail: "No email",
    noPersonalNumber: "No personal number",
    dueDate: "Due date",
    paymentTerms: "Payment terms",
    days: "days",
    profitAndLoss: "Profit and loss",
    balanceReport: "Balance report",
    revenueLines: "Revenue",
    expenseLines: "Expenses",
    assetLines: "Assets",
    liabilitiesAndEquity: "Liabilities and equity",
    difference: "Difference",
    result: "Result"
  },
  sv: {
    dashboard: "Affarsdashboard",
    overview: "Oversikt",
    createNew: "Skapa ny",
    customers: "Kunder",
    invoices: "Fakturor",
    contracts: "Avtal",
    services: "Tjanster",
    activities: "Handelser",
    serviceAdmin: "Tjansteadministration",
    cashflow: "Likviditet",
    budget: "Budget & mal",
    payroll: "Lon",
    serviceName: "Tjanstens namn",
    serviceDescription: "Beskrivning",
    ordinaryPrice: "Ordinarie pris",
    discountPrice: "Rabattpris",
    discountLabel: "Rabatttext",
    activeService: "Aktiv tjanst",
    saveService: "Spara tjanst",
    newService: "Ny tjanst",
    edit: "Redigera",
    inactive: "Inaktiv",
    serviceSaved: "Tjansten sparades.",
    payments: "Betalningar",
    uploaded: "Underlag",
    bookkeeping: "Bokforing",
    chartOfAccounts: "Kontoplan",
    vatReport: "Momsrapport",
    reports: "Rapporter",
    settings: "Installningar",
    revenue: "Intakter demo",
    paid: "Betalda",
    name: "Namn",
    email: "E-post",
    saveCustomer: "Spara kund",
    customerName: "Kundnamn",
    customerEmail: "kund@example.com",
    personalNumber: "Personnummer",
    address: "Adress",
    phone: "Telefon",
    postalCode: "Postnummer",
    city: "Stad",
    createInvoice: "Skapa faktura",
    chooseCustomer: "Valj kund",
    chooseService: "Valj tjanst",
    quantity: "Antal",
    noInvoices: "Inga fakturor annu.",
    noCustomers: "Inga kunder annu.",
    noExpenses: "Inga kostnader annu.",
    noJournalEntries: "Inga bokforingsrader annu.",
    refresh: "Uppdatera",
    signedIn: "Inloggad som",
    logout: "Logga ut",
    login: "Logga in",
    register: "Registrera",
    password: "Losenord",
    customerSaved: "Kunden sparades.",
    noServices: "Inga tjanster laddades. Starta om backend sa demo-tjanster seedas.",
    advisor: "AI-radgivare",
    reminders: "AI-paminnelser",
    searchCustomers: "Sok kunder",
    searchInvoices: "Sok fakturor",
    all: "Alla",
    draft: "Utkast",
    sent: "Skickade",
    unpaid: "Obetalda",
    overdue: "Forfallna",
    dueSoon: "Forfaller snart",
    expenses: "Kostnader",
    description: "Beskrivning",
    net: "Exkl. moms",
    vat: "Moms",
    total: "Totalt",
    category: "Kategori",
    saveExpense: "Spara kostnad",
    receipt: "Kvitto",
    uploadReceipt: "Ladda upp kvitto",
    attachReceipt: "Lagg till kvitto",
    openReceipt: "Oppna kvitto",
    receiptsSaved: "Sparade underlag",
    receiptsMissing: "Saknar kvitto",
    noReceipts: "Inga kvitton uppladdade annu.",
    noMissingReceipts: "Alla kostnader har kvitto.",
    customerRequiredLogin: "Logga in for att se paminnelser.",
    settingsSaved: "Installningar sparade.",
    saveSettings: "Spara installningar",
    clearTestData: "Rensa testdata",
    testDataCleared: "Testdata rensad.",
    company: "Foretag",
    companyType: "Foretagsform",
    soleTrader: "Enskild firma",
    limitedCompany: "Aktiebolag",
    contactEmail: "Kontakt e-post",
    paymentRecipient: "Betalningsmottagare",
    date: "Datum",
    approvedForFtax: "Godkand for F-skatt",
    serviceMissing: "Tjanst saknas",
    payment: "Betalning",
    markSent: "Markera skickad",
    markPaid: "Registrera betalning",
    outputVat: "Utgaende moms",
    inputVat: "Ingaende moms",
    vatToPay: "Moms att betala",
    exportCsv: "Exportera CSV",
    voucher: "Verifikat",
    account: "Konto",
    manualVoucher: "Manuell verifikation",
    debitAccount: "Debetkonto",
    creditAccount: "Kreditkonto",
    amount: "Belopp",
    saveVoucher: "Spara verifikat",
    accountNumber: "Kontonummer",
    accountName: "Kontonamn",
    appliesTo: "Galler",
    debit: "Debet",
    credit: "Kredit",
    vatPercent: "Momsprocent",
    paymentTermsDays: "Betalningsvillkor dagar",
    noEmail: "Ingen e-post",
    noPersonalNumber: "Inget personnummer",
    dueDate: "Forfallodatum",
    paymentTerms: "Betalningsvillkor",
    days: "dagar",
    profitAndLoss: "Resultatrapport",
    balanceReport: "Balansrapport",
    revenueLines: "Intakter",
    expenseLines: "Kostnader",
    assetLines: "Tillgangar",
    liabilitiesAndEquity: "Skulder och eget kapital",
    difference: "Skillnad",
    result: "Resultat"
  }
};

function invoiceNetAmount(item) {
  return item.netAmount || item.product?.price || 0;
}

function servicePriceLabel(service, language) {
  const effectivePrice = serviceEffectivePrice(service);

  if (!effectivePrice || effectivePrice <= 0) {
    return language === "sv" ? "Kontakta for pris" : "Contact for price";
  }

  if (service.discountPrice > 0) {
    return language === "sv"
      ? `${service.discountPrice} SEK (ord. ${service.price} SEK)`
      : `${service.discountPrice} SEK (regular ${service.price} SEK)`;
  }

  return `${effectivePrice} SEK`;
}

function serviceEffectivePrice(service) {
  return service?.discountPrice > 0 ? service.discountPrice : service?.price || 0;
}

function serviceHasInvoicePrice(service) {
  return serviceEffectivePrice(service) > 0;
}

function invoicePreviewFor(service, quantityValue) {
  if (!service || !serviceHasInvoicePrice(service)) {
    return null;
  }

  const quantity = Math.max(Number(quantityValue || 1), 1);
  const ordinaryPrice = (service.price || 0) * quantity;
  const netAmount = serviceEffectivePrice(service) * quantity;
  const discountAmount = Math.max(ordinaryPrice - netAmount, 0);
  const vatAmount = Math.round(netAmount * 0.25);
  const totalAmount = netAmount + vatAmount;

  return {
    quantity,
    ordinaryPrice,
    discountAmount,
    discountLabel: service.discountLabel,
    netAmount,
    vatAmount,
    totalAmount
  };
}

function invoiceVatAmount(item) {
  return item.vatAmount || Math.round(invoiceNetAmount(item) * 0.25);
}

function invoiceTotalAmount(item) {
  return item.totalAmount || invoiceNetAmount(item) + invoiceVatAmount(item);
}

function invoiceOrdinaryPrice(item) {
  return item.ordinaryPrice || item.product?.price || invoiceNetAmount(item);
}

function invoiceDiscountAmount(item) {
  return item.discountAmount || Math.max(invoiceOrdinaryPrice(item) - invoiceNetAmount(item), 0);
}

function invoicePaidAmount(item) {
  return item.paidAmount || 0;
}

function invoiceRemainingAmount(item) {
  return Math.max(invoiceTotalAmount(item) - invoicePaidAmount(item), 0);
}

function invoiceIsOverdue(item) {
  if (!item.dueDate || invoiceRemainingAmount(item) <= 0) {
    return false;
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const dueDate = new Date(item.dueDate);
  dueDate.setHours(0, 0, 0, 0);
  return dueDate < today;
}

function invoiceDaysUntilDue(item) {
  if (!item.dueDate || invoiceRemainingAmount(item) <= 0) {
    return null;
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const dueDate = new Date(item.dueDate);
  dueDate.setHours(0, 0, 0, 0);
  return Math.ceil((dueDate.getTime() - today.getTime()) / 86400000);
}

function invoiceShouldSendReminder(item, daysBeforeDue = 5) {
  const daysUntilDue = invoiceDaysUntilDue(item);
  return daysUntilDue !== null && daysUntilDue <= daysBeforeDue;
}

function invoiceReminderRuleText(item, language) {
  const daysUntilDue = invoiceDaysUntilDue(item);

  if (daysUntilDue === null) return "";
  if (daysUntilDue < 0) return language === "sv" ? "Fakturan ar forfallen." : "Invoice is overdue.";
  if (daysUntilDue === 0) return language === "sv" ? "Fakturan forfaller idag." : "Invoice is due today.";
  return language === "sv"
    ? `Fakturan forfaller om ${daysUntilDue} dagar.`
    : `Invoice is due in ${daysUntilDue} days.`;
}

function invoiceDueStatus(item, language) {
  if (invoiceRemainingAmount(item) <= 0 || item.status === "PAID") {
    return {
      className: "due-status-paid",
      label: language === "sv" ? "Betald" : "Paid"
    };
  }

  const daysUntilDue = invoiceDaysUntilDue(item);

  if (daysUntilDue === null) {
    return {
      className: "due-status-neutral",
      label: language === "sv" ? "Inget forfallodatum" : "No due date"
    };
  }

  if (daysUntilDue < 0) {
    return {
      className: "due-status-overdue",
      label: language === "sv" ? `Forfallen ${Math.abs(daysUntilDue)} dagar` : `${Math.abs(daysUntilDue)} days overdue`
    };
  }

  if (daysUntilDue === 0) {
    return {
      className: "due-status-today",
      label: language === "sv" ? "Forfaller idag" : "Due today"
    };
  }

  if (daysUntilDue <= 5) {
    return {
      className: "due-status-soon",
      label: language === "sv" ? `Forfaller om ${daysUntilDue} dagar` : `Due in ${daysUntilDue} days`
    };
  }

  return {
    className: "due-status-ok",
    label: language === "sv" ? `Forfaller om ${daysUntilDue} dagar` : `Due in ${daysUntilDue} days`
  };
}

function invoiceSortPriority(item) {
  if (invoiceRemainingAmount(item) <= 0 || item.status === "PAID") {
    return 5;
  }

  const daysUntilDue = invoiceDaysUntilDue(item);

  if (daysUntilDue === null) {
    return 4;
  }

  if (daysUntilDue < 0) {
    return 0;
  }

  if (daysUntilDue === 0) {
    return 1;
  }

  if (daysUntilDue <= 5) {
    return 2;
  }

  return 3;
}

function compareInvoices(first, second) {
  const priorityDifference = invoiceSortPriority(first) - invoiceSortPriority(second);

  if (priorityDifference !== 0) {
    return priorityDifference;
  }

  const firstDueDate = first.dueDate ? new Date(first.dueDate).getTime() : Number.MAX_SAFE_INTEGER;
  const secondDueDate = second.dueDate ? new Date(second.dueDate).getTime() : Number.MAX_SAFE_INTEGER;

  if (firstDueDate !== secondDueDate) {
    return firstDueDate - secondDueDate;
  }

  return new Date(second.createdAt || 0) - new Date(first.createdAt || 0);
}

function customerInvoices(customer, invoices) {
  return invoices.filter((item) => String(item.customer?.id || "") === String(customer.id));
}

function customerOutstandingAmount(customer, invoices) {
  return customerInvoices(customer, invoices).reduce((sum, item) => sum + invoiceRemainingAmount(item), 0);
}

function customerUnpaidInvoiceCount(customer, invoices) {
  return customerInvoices(customer, invoices).filter((item) => invoiceRemainingAmount(item) > 0).length;
}

function compareCustomers(first, second, invoices) {
  const firstOutstanding = customerOutstandingAmount(first, invoices);
  const secondOutstanding = customerOutstandingAmount(second, invoices);

  if (firstOutstanding !== secondOutstanding) {
    return secondOutstanding - firstOutstanding;
  }

  if (first.archived !== second.archived) {
    return first.archived ? 1 : -1;
  }

  return String(first.name || "").localeCompare(String(second.name || ""), "sv");
}

function addDays(dateValue, days) {
  const date = new Date(dateValue);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function addMonths(dateValue, months) {
  const date = new Date(dateValue);
  date.setMonth(date.getMonth() + months);
  return date.toISOString().slice(0, 10);
}

function nextContractDate(dateValue, interval) {
  if (interval === "quarterly") return addMonths(dateValue, 3);
  if (interval === "yearly") return addMonths(dateValue, 12);
  return addMonths(dateValue, 1);
}

function invoiceHasSentReminder(item, method) {
  return item.reminders?.some((reminder) => reminder.method === method && reminder.status === "SENT");
}

function invoiceHistoryLabel(reminder, language) {
  if (reminder.method === "INVOICE_EMAIL") {
    return language === "sv" ? "Faktura skickad via e-post" : "Invoice sent by email";
  }

  if (reminder.method === "INVOICE_MARKED_SENT") {
    return language === "sv" ? "Faktura markerad som skickad" : "Invoice marked as sent";
  }

  if (reminder.method === "EMAIL") {
    return language === "sv" ? "Paminnelse skickad via e-post" : "Reminder sent by email";
  }

  if (reminder.method === "EMAIL_DRAFT") {
    return language === "sv" ? "E-postutkast oppnat" : "Email draft opened";
  }

  if (reminder.method === "COPY") {
    return language === "sv" ? "Paminnelsetext kopierad/sparad" : "Reminder text copied/saved";
  }

  if (reminder.method?.startsWith("AUTO_OVERDUE_EMAIL")) {
    return language === "sv" ? "Automatisk forfallen paminnelse" : "Automatic overdue reminder";
  }

  if (reminder.method?.startsWith("AUTO_EMAIL")) {
    return language === "sv" ? "Automatisk fakturapaminnelse" : "Automatic invoice reminder";
  }

  return reminder.method || "-";
}

function nextAutomaticReminderText(item, settings, language) {
  if (invoiceRemainingAmount(item) <= 0 || !item.dueDate) {
    return language === "sv" ? "Ingen automatisk paminnelse kvar." : "No automatic reminder remaining.";
  }

  if (settings?.automaticInvoiceRemindersEnabled === false) {
    return language === "sv" ? "Automatiska paminnelser ar avstangda." : "Automatic reminders are turned off.";
  }

  const beforeDays = settings?.invoiceReminderDaysBeforeDue || 5;
  const beforeMethod = `AUTO_EMAIL_${beforeDays}_DAYS`;
  const beforeDate = addDays(item.dueDate, -beforeDays);

  if (!invoiceHasSentReminder(item, beforeMethod) && !invoiceIsOverdue(item)) {
    return language === "sv"
      ? `Nasta auto-paminnelse: ${beforeDate} (${beforeDays} dagar fore forfallodatum).`
      : `Next auto reminder: ${beforeDate} (${beforeDays} days before due date).`;
  }

  if (settings?.overdueInvoiceRemindersEnabled === false) {
    return language === "sv" ? "Forfallen auto-paminnelse ar avstangd." : "Overdue auto reminder is turned off.";
  }

  const overdueDays = settings?.overdueInvoiceReminderDaysAfterDue || 3;
  const overdueMethod = `AUTO_OVERDUE_EMAIL_${overdueDays}_DAYS`;
  const overdueDate = addDays(item.dueDate, overdueDays);

  if (!invoiceHasSentReminder(item, overdueMethod)) {
    return language === "sv"
      ? `Nasta forfallna auto-paminnelse: ${overdueDate} (${overdueDays} dagar efter forfallodatum).`
      : `Next overdue auto reminder: ${overdueDate} (${overdueDays} days after due date).`;
  }

  return language === "sv" ? "Alla automatiska paminnelser ar skickade." : "All automatic reminders have been sent.";
}

function defaultInvoiceReminderTemplate() {
  return [
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
  ].join("\n");
}

function defaultInvoiceEmailTemplate() {
  return [
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
  ].join("\n");
}

function defaultOverdueInvoiceReminderTemplate() {
  return [
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
  ].join("\n");
}

function renderInvoiceEmailText(item, settings, templateOverride = null) {
  const template = templateOverride || settings?.invoiceEmailTemplate || defaultInvoiceEmailTemplate();

  return template
    .replaceAll("{kundnamn}", item.customerName || "")
    .replaceAll("{fakturanummer}", invoiceNumber(item))
    .replaceAll("{forfallodatum}", item.dueDate || "")
    .replaceAll("{belopp}", String(invoiceRemainingAmount(item)))
    .replaceAll("{plusgiro}", item.plusGiro || "418 76 01-2")
    .replaceAll("{ocr}", item.ocrNumber || "1055065900139")
    .replaceAll("{betalningsmottagare}", item.paymentRecipient || "Bank Norwegian")
    .replaceAll("{foretag}", settings?.companyName || "AliBooks")
    .replaceAll("{kontaktEpost}", settings?.contactEmail || "");
}

function renderInvoiceReminderText(item, settings, templateOverride = null) {
  const template = templateOverride || settings?.invoiceReminderTemplate || defaultInvoiceReminderTemplate();

  return template
    .replaceAll("{kundnamn}", item.customerName || "")
    .replaceAll("{fakturanummer}", invoiceNumber(item))
    .replaceAll("{forfallodatum}", item.dueDate || "")
    .replaceAll("{belopp}", String(invoiceRemainingAmount(item)))
    .replaceAll("{plusgiro}", item.plusGiro || "418 76 01-2")
    .replaceAll("{ocr}", item.ocrNumber || "1055065900139")
    .replaceAll("{betalningsmottagare}", item.paymentRecipient || "Bank Norwegian")
    .replaceAll("{foretag}", settings?.companyName || "AliBooks")
    .replaceAll("{kontaktEpost}", settings?.contactEmail || "");
}

function demoInvoiceForReminderPreview(settings) {
  return {
    id: 24,
    customerName: "Maria Pettersson",
    invoiceNumber: "F-2026-0024",
    dueDate: "2026-07-17",
    totalAmount: 1188,
    paidAmount: 104,
    plusGiro: settings?.plusGiro || "418 76 01-2",
    ocrNumber: settings?.defaultOcr || "1055065900139",
    paymentRecipient: settings?.paymentRecipient || "Bank Norwegian"
  };
}

function invoiceReminderSubject(item) {
  return `Paminnelse faktura ${invoiceNumber(item)}`;
}

function invoiceCustomerEmail(item) {
  return item.customer?.email || "";
}

function invoiceNumber(item) {
  return item.invoiceNumber || `F-${new Date().getFullYear()}-${String(item.id).padStart(4, "0")}`;
}

function expenseHasReceipt(expense) {
  return Boolean(expense.hasReceipt || expense.receiptFileName || expense.receiptStoragePath);
}

function expenseCategoryLabel(category) {
  const labels = {
    "5420": "5420 Programvaror",
    "5410": "5410 Forbrukningsinventarier",
    "5800": "5800 Resekostnader",
    "6570": "6570 Bankkostnader",
    "4010": "4010 Inkop"
  };

  return labels[category] || category || "-";
}

function accountCompanyTypeLabel(companyType, language) {
  if (companyType === "SOLE_TRADER") {
    return language === "sv" ? "Enskild firma" : "Sole trader";
  }

  if (companyType === "LIMITED_COMPANY") {
    return language === "sv" ? "Aktiebolag" : "Limited company";
  }

  return language === "sv" ? "Bada" : "Both";
}

function statusLabel(status, language) {
  const value = status || "DRAFT";

  if (language === "sv") {
    if (value === "PAID") return "Betald";
    if (value === "PARTIALLY_PAID") return "Delbetald";
    if (value === "SENT") return "Skickad";
    if (value === "CREDITED") return "Krediterad";
    return "Utkast";
  }

  if (value === "PAID") return "Paid";
  if (value === "PARTIALLY_PAID") return "Partially paid";
  if (value === "SENT") return "Sent";
  if (value === "CREDITED") return "Credited";
  return "Draft";
}

function apiErrorMessage(data, fallback) {
  return data?.message || data?.detail || data?.error || fallback;
}

function normalizeNameValue(value) {
  return value
    .toLowerCase()
    .replaceAll("\u00e5", "a")
    .replaceAll("\u00e4", "a")
    .replaceAll("\u00f6", "o")
    .replaceAll("\u00e5", "a")
    .replaceAll("\u00e4", "a")
    .replaceAll("\u00f6", "o")
    .replaceAll("å", "a")
    .replaceAll("ä", "a")
    .replaceAll("ö", "o")
    .replace(/[^a-z0-9\s]/g, " ");
}

function formatDateTime(value) {
  if (!value) return "-";

  return new Date(value).toLocaleString("sv-SE", {
    dateStyle: "short",
    timeStyle: "short"
  });
}

function formatDateOnly(value) {
  if (!value) return "-";

  return new Date(value).toLocaleDateString("sv-SE");
}

function formatMonthLabel(monthKey, language) {
  if (!monthKey) return "-";

  const date = new Date(`${monthKey}-01T12:00:00`);

  return date.toLocaleDateString(language === "sv" ? "sv-SE" : "en-GB", {
    year: "numeric",
    month: "long"
  });
}

function dateInputString(date) {
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return localDate.toISOString().slice(0, 10);
}

function addDaysInputString(value, days) {
  const date = value ? new Date(`${value}T12:00:00`) : new Date();
  date.setDate(date.getDate() + days);
  return dateInputString(date);
}

function addMonthsInputString(value, months, dayOfMonth = null) {
  const date = value ? new Date(`${value}T12:00:00`) : new Date();
  date.setMonth(date.getMonth() + months);

  if (dayOfMonth) {
    date.setDate(dayOfMonth);
  }

  return dateInputString(date);
}

function daysUntilInputDate(value, todayValue) {
  if (!value) return null;

  const target = new Date(`${value}T12:00:00`);
  const today = new Date(`${todayValue}T12:00:00`);
  return Math.ceil((target.getTime() - today.getTime()) / 86400000);
}

function currentMonthRange() {
  const now = new Date();
  return {
    from: dateInputString(new Date(now.getFullYear(), now.getMonth(), 1)),
    to: dateInputString(new Date(now.getFullYear(), now.getMonth() + 1, 0))
  };
}

function monthRangeFromKey(monthKey) {
  const [year, month] = String(monthKey || "").split("-").map(Number);

  if (!year || !month) {
    return { from: "", to: "" };
  }

  return {
    from: dateInputString(new Date(year, month - 1, 1)),
    to: dateInputString(new Date(year, month, 0))
  };
}

function splitCsvLine(line, separator) {
  const cells = [];
  let current = "";
  let quoted = false;

  for (const character of line) {
    if (character === "\"") {
      quoted = !quoted;
    } else if (character === separator && !quoted) {
      cells.push(current.trim().replace(/^"|"$/g, ""));
      current = "";
    } else {
      current += character;
    }
  }

  cells.push(current.trim().replace(/^"|"$/g, ""));
  return cells;
}

function normalizeHeader(value) {
  return String(value || "")
    .toLowerCase()
    .replaceAll("\u00e5", "a")
    .replaceAll("\u00e4", "a")
    .replaceAll("\u00f6", "o")
    .replaceAll("å", "a")
    .replaceAll("ä", "a")
    .replaceAll("ö", "o")
    .replace(/[^a-z0-9]/g, "");
}

function parseMoneyValue(value) {
  const normalized = String(value || "")
    .replace(/\s/g, "")
    .replace("SEK", "")
    .replace("kr", "")
    .replace(",", ".");
  const amount = Number(normalized.replace(/[^0-9.-]/g, ""));

  return Number.isFinite(amount) ? Math.round(amount) : 0;
}

function parseBankCsv(text) {
  const lines = String(text || "").split(/\r?\n/).map((line) => line.trim()).filter(Boolean);

  if (lines.length < 2) {
    return [];
  }

  const separator = lines[0].includes(";") ? ";" : ",";
  const headers = splitCsvLine(lines[0], separator).map(normalizeHeader);
  const findHeader = (names) => {
    return headers.findIndex((header) => names.some((name) => (name.length <= 2 ? header === name : header.includes(name))));
  };
  const dateIndex = findHeader(["datum", "date", "bokforingsdag", "transaktionsdag"]);
  const descriptionIndex = findHeader(["text", "beskrivning", "description", "meddelande", "namn"]);
  const referenceIndex = findHeader(["referens", "reference", "ocr", "meddelande"]);
  const amountIndex = findHeader(["belopp", "amount", "summa"]);
  const creditIndex = findHeader(["in", "credit", "kredit", "insattning", "insatt"]);
  const debitIndex = findHeader(["ut", "debit", "debet", "uttag"]);

  return lines.slice(1).map((line, index) => {
    const cells = splitCsvLine(line, separator);
    const fallbackDescription = cells.filter(Boolean).join(" ");
    const singleAmount = amountIndex >= 0 ? parseMoneyValue(cells[amountIndex]) : 0;
    const creditAmount = creditIndex >= 0 ? parseMoneyValue(cells[creditIndex]) : 0;
    const debitAmount = debitIndex >= 0 ? parseMoneyValue(cells[debitIndex]) : 0;
    const amount = singleAmount || creditAmount || -Math.abs(debitAmount) || parseMoneyValue(cells.at(-1));

    return {
      id: `bank-${Date.now()}-${index}`,
      date: cells[dateIndex] || "",
      description: cells[descriptionIndex] || fallbackDescription,
      reference: cells[referenceIndex] || "",
      amount,
      raw: line
    };
  }).filter((row) => row.description || row.amount);
}

function currentQuarterRange() {
  const now = new Date();
  const quarterStartMonth = Math.floor(now.getMonth() / 3) * 3;
  return {
    from: dateInputString(new Date(now.getFullYear(), quarterStartMonth, 1)),
    to: dateInputString(new Date(now.getFullYear(), quarterStartMonth + 3, 0))
  };
}

function currentYearRange() {
  const now = new Date();
  return {
    from: dateInputString(new Date(now.getFullYear(), 0, 1)),
    to: dateInputString(new Date(now.getFullYear(), 11, 31))
  };
}

const viewKeys = [
  "overview",
  "customers",
  "invoices",
  "contracts",
  "services",
  "activities",
  "cashflow",
  "budget",
  "payroll",
  "payments",
  "uploaded",
  "bookkeeping",
  "accounts",
  "vat",
  "reports",
  "settings"
];

const invoiceFilterKeys = ["all", "draft", "sent", "paid", "unpaid", "overdue", "dueSoon"];
const expenseFilterKeys = ["all", "withReceipt", "missingReceipt"];
const customerFilterKeys = ["active", "archived", "all", "outstanding"];
const paymentOverviewFilterKeys = ["open", "overdue", "dueSoon", "partiallyPaid", "sent"];
const bookkeepingFilterKeys = ["all", "original", "corrections", "corrected"];
const activityFilterKeys = ["all", "invoice", "payment", "expense", "voucher", "reminder", "bank", "stripe", "period"];
const expenseCategoryKeys = ["5420", "5410", "5800", "6570", "4010"];
const defaultBankImportRules = [
  { id: "rule-bank-fee", keyword: "bankavgift avgift fee", category: "6570", vatRate: 0, description: "Bankavgift", enabled: true },
  { id: "rule-software", keyword: "software programvara adobe google microsoft openai github vercel", category: "5420", vatRate: 25, description: "Programvara", enabled: true },
  { id: "rule-travel", keyword: "resa taxi uber flyg train tag hotell hotel", category: "5800", vatRate: 6, description: "Resekostnad", enabled: true },
  { id: "rule-equipment", keyword: "ikea elgiganten netonnet dator utrustning inventarie", category: "5410", vatRate: 25, description: "Forbrukningsinventarie", enabled: true },
  { id: "rule-purchase", keyword: "inkop purchase supplier leverantor", category: "4010", vatRate: 25, description: "Inkop", enabled: true }
];
const localPreferenceKeys = [
  "alibooks-language",
  "alibooks-active-view",
  "alibooks-invoice-filter",
  "alibooks-invoice-date-from",
  "alibooks-invoice-date-to",
  "alibooks-invoice-search",
  "alibooks-expense-filter",
  "alibooks-expense-category-filter",
  "alibooks-expense-date-from",
  "alibooks-expense-date-to",
  "alibooks-expense-search",
  "alibooks-customer-filter",
  "alibooks-customer-search",
  "alibooks-payment-overview-filter",
  "alibooks-payment-overview-search",
  "alibooks-activity-filter",
  "alibooks-activity-search",
  "alibooks-cashflow-horizon-days",
  "alibooks-budget-revenue-target",
  "alibooks-budget-expense-limit",
  "alibooks-budget-reserve-percent",
  "alibooks-payroll-drafts",
  "alibooks-payroll-tax-rate",
  "alibooks-payroll-employer-rate",
  "alibooks-payroll-salary-account",
  "alibooks-month-close-selected-month",
  "alibooks-bookkeeping-filter",
  "alibooks-bookkeeping-search",
  "alibooks-bookkeeping-date-from",
  "alibooks-bookkeeping-date-to",
  "alibooks-bank-import-rules",
  "alibooks-bokio-import-queue",
  "alibooks-selected-customer-id",
  "alibooks-selected-service-id",
  "alibooks-invoice-quantity",
  "alibooks-contracts",
  "alibooks-expense-category",
  "alibooks-ai-assistant-messages"
];

function App() {
  const authFormRef = useRef(null);
  const [services, setServices] = useState([]);
  const [adminServices, setAdminServices] = useState([]);
  const [invoices, setInvoices] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [journalEntries, setJournalEntries] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [vatReport, setVatReport] = useState(null);
  const [expenses, setExpenses] = useState([]);
  const [reminders, setReminders] = useState([]);
  const [advisorSummary, setAdvisorSummary] = useState(null);
  const [aiAssistantQuestion, setAiAssistantQuestion] = useState("");
  const [aiAssistantMessages, setAiAssistantMessages] = useState(() => {
    try {
      const savedMessages = JSON.parse(localStorage.getItem("alibooks-ai-assistant-messages") || "[]");
      return Array.isArray(savedMessages) ? savedMessages.slice(-8) : [];
    } catch {
      return [];
    }
  });
  const [aiAssistantOpen, setAiAssistantOpen] = useState(false);
  const [aiAssistantLoading, setAiAssistantLoading] = useState(false);
  const [bokioImportQueue, setBokioImportQueue] = useState(() => {
    try {
      const savedQueue = JSON.parse(localStorage.getItem("alibooks-bokio-import-queue") || "[]");
      return Array.isArray(savedQueue) ? savedQueue : [];
    } catch {
      return [];
    }
  });
  const [bokioImportFiles, setBokioImportFiles] = useState({});
  const [bokioImportMessage, setBokioImportMessage] = useState("");
  const [automaticReminderMessage, setAutomaticReminderMessage] = useState("");
  const [automaticReminderResult, setAutomaticReminderResult] = useState(null);
  const [settings, setSettings] = useState(null);
  const [systemStatus, setSystemStatus] = useState(null);
  const [generatedJwtSecret, setGeneratedJwtSecret] = useState("");
  const [profitAndLoss, setProfitAndLoss] = useState(null);
  const [balanceReport, setBalanceReport] = useState(null);
  const [customerLedgerSearch, setCustomerLedgerSearch] = useState("");
  const [auditTrailFilter, setAuditTrailFilter] = useState("all");
  const [auditTrailSearch, setAuditTrailSearch] = useState("");
  const [dataQualityFilter, setDataQualityFilter] = useState("all");
  const [dataQualitySearch, setDataQualitySearch] = useState("");
  const [backupValidation, setBackupValidation] = useState(null);
  const [vatPeriodFrom, setVatPeriodFrom] = useState(() => currentQuarterRange().from);
  const [vatPeriodTo, setVatPeriodTo] = useState(() => currentQuarterRange().to);
  const [settingsMessage, setSettingsMessage] = useState("");
  const [periodCloseMessage, setPeriodCloseMessage] = useState("");
  const [expenseDescription, setExpenseDescription] = useState("");
  const [expenseDate, setExpenseDate] = useState(new Date().toISOString().slice(0, 10));
  const [expenseNetAmount, setExpenseNetAmount] = useState("");
  const [expenseVatAmount, setExpenseVatAmount] = useState("");
  const [expenseCategory, setExpenseCategory] = useState(() => {
    const savedCategory = localStorage.getItem("alibooks-expense-category");
    return expenseCategoryKeys.includes(savedCategory) ? savedCategory : "5420";
  });
  const [expenseReceiptFile, setExpenseReceiptFile] = useState(null);
  const [manualVoucherDate, setManualVoucherDate] = useState(new Date().toISOString().slice(0, 10));
  const [manualDescription, setManualDescription] = useState("");
  const [manualDebitAccount, setManualDebitAccount] = useState("");
  const [manualCreditAccount, setManualCreditAccount] = useState("");
  const [manualAmount, setManualAmount] = useState("");
  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [customerPersonalNumber, setCustomerPersonalNumber] = useState("");
  const [customerAddress, setCustomerAddress] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [customerPostalCode, setCustomerPostalCode] = useState("");
  const [customerCity, setCustomerCity] = useState("");
  const [editingCustomerId, setEditingCustomerId] = useState(null);
  const [selectedCustomerId, setSelectedCustomerId] = useState(() => localStorage.getItem("alibooks-selected-customer-id") || "");
  const [selectedServiceId, setSelectedServiceId] = useState(() => localStorage.getItem("alibooks-selected-service-id") || "");
  const [invoiceQuantity, setInvoiceQuantity] = useState(() => {
    const savedQuantity = localStorage.getItem("alibooks-invoice-quantity");
    return Number(savedQuantity) > 0 ? savedQuantity : "1";
  });
  const [contracts, setContracts] = useState([]);
  const [contractCustomerId, setContractCustomerId] = useState("");
  const [contractServiceId, setContractServiceId] = useState("");
  const [contractQuantity, setContractQuantity] = useState("1");
  const [contractInterval, setContractInterval] = useState("monthly");
  const [contractNextDate, setContractNextDate] = useState(new Date().toISOString().slice(0, 10));
  const [contractMessage, setContractMessage] = useState("");
  const [editingServiceId, setEditingServiceId] = useState(null);
  const [serviceName, setServiceName] = useState("");
  const [serviceDescription, setServiceDescription] = useState("");
  const [servicePrice, setServicePrice] = useState("");
  const [serviceDiscountPrice, setServiceDiscountPrice] = useState("");
  const [serviceDiscountLabel, setServiceDiscountLabel] = useState("");
  const [serviceActive, setServiceActive] = useState(true);
  const [serviceMessage, setServiceMessage] = useState("");
  const [invoice, setInvoice] = useState(null);
  const [error, setError] = useState("");
  const [authMode, setAuthMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState(localStorage.getItem("alibooks-token") || "");
  const [currentEmail, setCurrentEmail] = useState(localStorage.getItem("alibooks-email") || "");
  const [language, setLanguage] = useState(() => {
    const savedLanguage = localStorage.getItem("alibooks-language");
    return savedLanguage === "en" ? "en" : "sv";
  });
  const [customerMessage, setCustomerMessage] = useState("");
  const [activeView, setActiveView] = useState(() => {
    const savedView = localStorage.getItem("alibooks-active-view");
    return viewKeys.includes(savedView) ? savedView : "overview";
  });
  const [invoiceFilter, setInvoiceFilter] = useState(() => {
    const savedFilter = localStorage.getItem("alibooks-invoice-filter");
    return invoiceFilterKeys.includes(savedFilter) ? savedFilter : "all";
  });
  const [invoiceDateFrom, setInvoiceDateFrom] = useState(() => localStorage.getItem("alibooks-invoice-date-from") || "");
  const [invoiceDateTo, setInvoiceDateTo] = useState(() => localStorage.getItem("alibooks-invoice-date-to") || "");
  const [customerFilter, setCustomerFilter] = useState(() => {
    const savedFilter = localStorage.getItem("alibooks-customer-filter");
    return customerFilterKeys.includes(savedFilter) ? savedFilter : "active";
  });
  const [paymentOverviewFilter, setPaymentOverviewFilter] = useState(() => {
    const savedFilter = localStorage.getItem("alibooks-payment-overview-filter");
    return paymentOverviewFilterKeys.includes(savedFilter) ? savedFilter : "open";
  });
  const [paymentOverviewSearch, setPaymentOverviewSearch] = useState(() => localStorage.getItem("alibooks-payment-overview-search") || "");
  const [activityFilter, setActivityFilter] = useState(() => {
    const savedFilter = localStorage.getItem("alibooks-activity-filter");
    return activityFilterKeys.includes(savedFilter) ? savedFilter : "all";
  });
  const [activitySearch, setActivitySearch] = useState(() => localStorage.getItem("alibooks-activity-search") || "");
  const [cashflowHorizonDays, setCashflowHorizonDays] = useState(() => {
    const savedHorizon = Number(localStorage.getItem("alibooks-cashflow-horizon-days") || 30);
    return [30, 60, 90].includes(savedHorizon) ? savedHorizon : 30;
  });
  const [budgetRevenueTarget, setBudgetRevenueTarget] = useState(() => localStorage.getItem("alibooks-budget-revenue-target") || "25000");
  const [budgetExpenseLimit, setBudgetExpenseLimit] = useState(() => localStorage.getItem("alibooks-budget-expense-limit") || "12000");
  const [budgetReservePercent, setBudgetReservePercent] = useState(() => localStorage.getItem("alibooks-budget-reserve-percent") || "30");
  const [payrollEmployeeName, setPayrollEmployeeName] = useState("");
  const [payrollEmployeePersonalNumber, setPayrollEmployeePersonalNumber] = useState("");
  const [payrollPeriod, setPayrollPeriod] = useState(new Date().toISOString().slice(0, 7));
  const [payrollGrossSalary, setPayrollGrossSalary] = useState("");
  const [payrollTaxRate, setPayrollTaxRate] = useState(() => localStorage.getItem("alibooks-payroll-tax-rate") || "30");
  const [payrollEmployerRate, setPayrollEmployerRate] = useState(() => localStorage.getItem("alibooks-payroll-employer-rate") || "31.42");
  const [payrollSalaryAccount, setPayrollSalaryAccount] = useState(() => localStorage.getItem("alibooks-payroll-salary-account") || "7010");
  const [payrollMessage, setPayrollMessage] = useState("");
  const [payrollDrafts, setPayrollDrafts] = useState(() => {
    try {
      const savedDrafts = JSON.parse(localStorage.getItem("alibooks-payroll-drafts") || "[]");
      return Array.isArray(savedDrafts) ? savedDrafts : [];
    } catch {
      return [];
    }
  });
  const [monthCloseSelectedMonth, setMonthCloseSelectedMonth] = useState(() => localStorage.getItem("alibooks-month-close-selected-month") || new Date().toISOString().slice(0, 7));
  const [bookkeepingFilter, setBookkeepingFilter] = useState(() => {
    const savedFilter = localStorage.getItem("alibooks-bookkeeping-filter");
    return bookkeepingFilterKeys.includes(savedFilter) ? savedFilter : "all";
  });
  const [bookkeepingSearch, setBookkeepingSearch] = useState(() => localStorage.getItem("alibooks-bookkeeping-search") || "");
  const [bookkeepingDateFrom, setBookkeepingDateFrom] = useState(() => localStorage.getItem("alibooks-bookkeeping-date-from") || "");
  const [bookkeepingDateTo, setBookkeepingDateTo] = useState(() => localStorage.getItem("alibooks-bookkeeping-date-to") || "");
  const [expenseFilter, setExpenseFilter] = useState(() => {
    const savedFilter = localStorage.getItem("alibooks-expense-filter");
    return expenseFilterKeys.includes(savedFilter) ? savedFilter : "all";
  });
  const [expenseCategoryFilter, setExpenseCategoryFilter] = useState(() => localStorage.getItem("alibooks-expense-category-filter") || "all");
  const [expenseDateFrom, setExpenseDateFrom] = useState(() => localStorage.getItem("alibooks-expense-date-from") || "");
  const [expenseDateTo, setExpenseDateTo] = useState(() => localStorage.getItem("alibooks-expense-date-to") || "");
  const [expenseSearch, setExpenseSearch] = useState(() => localStorage.getItem("alibooks-expense-search") || "");
  const [customerSearch, setCustomerSearch] = useState(() => localStorage.getItem("alibooks-customer-search") || "");
  const [invoiceSearch, setInvoiceSearch] = useState(() => localStorage.getItem("alibooks-invoice-search") || "");
  const [paymentDates, setPaymentDates] = useState({});
  const [paymentAmounts, setPaymentAmounts] = useState({});
  const [paymentReferences, setPaymentReferences] = useState({});
  const [bankImportRows, setBankImportRows] = useState([]);
  const [bankImportFilter, setBankImportFilter] = useState("all");
  const [bankImportSearch, setBankImportSearch] = useState("");
  const [bankImportMessage, setBankImportMessage] = useState("");
  const [bankImportExpenseCategories, setBankImportExpenseCategories] = useState({});
  const [bankImportExpenseVatRates, setBankImportExpenseVatRates] = useState({});
  const [bankImportExpenseDescriptions, setBankImportExpenseDescriptions] = useState({});
  const [lastCreatedBankImportExpense, setLastCreatedBankImportExpense] = useState(null);
  const [skippedBankImportRows, setSkippedBankImportRows] = useState([]);
  const [bankImportRules, setBankImportRules] = useState(() => {
    try {
      const savedRules = JSON.parse(localStorage.getItem("alibooks-bank-import-rules") || "null");
      return Array.isArray(savedRules) && savedRules.length > 0 ? savedRules : defaultBankImportRules;
    } catch {
      return defaultBankImportRules;
    }
  });
  const [bankRuleKeyword, setBankRuleKeyword] = useState("");
  const [bankRuleCategory, setBankRuleCategory] = useState("5420");
  const [bankRuleVatRate, setBankRuleVatRate] = useState(25);
  const [bankRuleDescription, setBankRuleDescription] = useState("");
  const [bankReconciliationHistory, setBankReconciliationHistory] = useState([]);
  const [stripePayoutDate, setStripePayoutDate] = useState(new Date().toISOString().slice(0, 10));
  const [stripePayoutGrossAmount, setStripePayoutGrossAmount] = useState("");
  const [stripePayoutFeeAmount, setStripePayoutFeeAmount] = useState("");
  const [stripePayoutReference, setStripePayoutReference] = useState("");
  const [stripePayoutMessage, setStripePayoutMessage] = useState("");
  const [stripePayouts, setStripePayouts] = useState([]);
  const [stripeWebsiteSaleDate, setStripeWebsiteSaleDate] = useState(new Date().toISOString().slice(0, 10));
  const [stripeWebsiteSaleAmount, setStripeWebsiteSaleAmount] = useState("");
  const [stripeWebsiteSaleReference, setStripeWebsiteSaleReference] = useState("");
  const [stripeWebsiteSaleMessage, setStripeWebsiteSaleMessage] = useState("");
  const [copiedPaymentInfoId, setCopiedPaymentInfoId] = useState(null);
  const [copiedExpenseInfoId, setCopiedExpenseInfoId] = useState(null);
  const [copiedAiMessageId, setCopiedAiMessageId] = useState(null);
  const t = copy[language];
  const accountingLockedThroughDate = settings?.accountingLockedThroughDate || "";
  const manualVoucherDateIsLocked = Boolean(
    accountingLockedThroughDate
      && manualVoucherDate
      && manualVoucherDate <= accountingLockedThroughDate
  );
  const expenseDateIsLocked = isAccountingDateLocked(expenseDate);

  useEffect(() => {
    loadServices();
  }, []);

  useEffect(() => {
    localStorage.setItem("alibooks-language", language);
  }, [language]);

  useEffect(() => {
    localStorage.setItem("alibooks-active-view", activeView);
  }, [activeView]);

  useEffect(() => {
    localStorage.setItem("alibooks-invoice-filter", invoiceFilter);
    localStorage.setItem("alibooks-invoice-date-from", invoiceDateFrom);
    localStorage.setItem("alibooks-invoice-date-to", invoiceDateTo);
    localStorage.setItem("alibooks-invoice-search", invoiceSearch);
  }, [invoiceFilter, invoiceDateFrom, invoiceDateTo, invoiceSearch]);

  useEffect(() => {
    localStorage.setItem("alibooks-expense-filter", expenseFilter);
    localStorage.setItem("alibooks-expense-category-filter", expenseCategoryFilter);
    localStorage.setItem("alibooks-expense-date-from", expenseDateFrom);
    localStorage.setItem("alibooks-expense-date-to", expenseDateTo);
    localStorage.setItem("alibooks-expense-search", expenseSearch);
  }, [expenseFilter, expenseCategoryFilter, expenseDateFrom, expenseDateTo, expenseSearch]);

  useEffect(() => {
    localStorage.setItem("alibooks-customer-filter", customerFilter);
    localStorage.setItem("alibooks-customer-search", customerSearch);
  }, [customerFilter, customerSearch]);

  useEffect(() => {
    localStorage.setItem("alibooks-payment-overview-filter", paymentOverviewFilter);
    localStorage.setItem("alibooks-payment-overview-search", paymentOverviewSearch);
  }, [paymentOverviewFilter, paymentOverviewSearch]);

  useEffect(() => {
    localStorage.setItem("alibooks-activity-filter", activityFilter);
    localStorage.setItem("alibooks-activity-search", activitySearch);
  }, [activityFilter, activitySearch]);

  useEffect(() => {
    localStorage.setItem("alibooks-cashflow-horizon-days", String(cashflowHorizonDays));
  }, [cashflowHorizonDays]);

  useEffect(() => {
    localStorage.setItem("alibooks-budget-revenue-target", budgetRevenueTarget);
    localStorage.setItem("alibooks-budget-expense-limit", budgetExpenseLimit);
    localStorage.setItem("alibooks-budget-reserve-percent", budgetReservePercent);
  }, [budgetRevenueTarget, budgetExpenseLimit, budgetReservePercent]);

  useEffect(() => {
    localStorage.setItem("alibooks-payroll-drafts", JSON.stringify(payrollDrafts));
    localStorage.setItem("alibooks-payroll-tax-rate", payrollTaxRate);
    localStorage.setItem("alibooks-payroll-employer-rate", payrollEmployerRate);
    localStorage.setItem("alibooks-payroll-salary-account", payrollSalaryAccount);
  }, [payrollDrafts, payrollTaxRate, payrollEmployerRate, payrollSalaryAccount]);

  useEffect(() => {
    localStorage.setItem("alibooks-month-close-selected-month", monthCloseSelectedMonth);
  }, [monthCloseSelectedMonth]);

  useEffect(() => {
    localStorage.setItem("alibooks-bookkeeping-filter", bookkeepingFilter);
    localStorage.setItem("alibooks-bookkeeping-search", bookkeepingSearch);
    localStorage.setItem("alibooks-bookkeeping-date-from", bookkeepingDateFrom);
    localStorage.setItem("alibooks-bookkeeping-date-to", bookkeepingDateTo);
  }, [bookkeepingFilter, bookkeepingSearch, bookkeepingDateFrom, bookkeepingDateTo]);

  useEffect(() => {
    localStorage.setItem("alibooks-bank-import-rules", JSON.stringify(bankImportRules));
  }, [bankImportRules]);

  useEffect(() => {
    localStorage.setItem("alibooks-bokio-import-queue", JSON.stringify(bokioImportQueue));
  }, [bokioImportQueue]);

  useEffect(() => {
    localStorage.setItem("alibooks-ai-assistant-messages", JSON.stringify(aiAssistantMessages.slice(-8)));
  }, [aiAssistantMessages]);

  useEffect(() => {
    localStorage.setItem("alibooks-selected-customer-id", selectedCustomerId);
  }, [selectedCustomerId]);

  useEffect(() => {
    localStorage.setItem("alibooks-selected-service-id", selectedServiceId);
  }, [selectedServiceId]);

  useEffect(() => {
    if (Number(invoiceQuantity) > 0) {
      localStorage.setItem("alibooks-invoice-quantity", invoiceQuantity);
    }
  }, [invoiceQuantity]);

  useEffect(() => {
    localStorage.setItem("alibooks-expense-category", expenseCategory);
  }, [expenseCategory]);

  useEffect(() => {
    if (token) {
      loadAdminServices();
      loadInvoices();
      loadCustomers();
      loadJournalEntries();
      loadAccounts();
      loadVatReport();
      loadExpenses();
      loadReminders();
      loadAdvisorSummary();
      loadSettings();
      loadSystemStatus();
      loadProfitAndLoss();
      loadBalanceReport();
      loadContracts();
      loadStripePayouts();
      loadBankReconciliations();
    }
  }, [token]);

  function authHeaders(authToken = token) {
    return {
      Authorization: `Bearer ${authToken}`
    };
  }

  function isAccountingDateLocked(date) {
    return Boolean(accountingLockedThroughDate && date && date <= accountingLockedThroughDate);
  }

  function lockedAccountingMessage(date) {
    const formattedDate = formatDateOnly(accountingLockedThroughDate || date);
    return language === "sv"
      ? `Perioden ar last till och med ${formattedDate}. Valj ett senare datum.`
      : `The period is locked through ${formattedDate}. Choose a later date.`;
  }

  async function loadServices() {
    try {
      const response = await fetch(`${apiUrl}/services`);
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setServices([]);
        setError(apiErrorMessage(data, "Could not load services."));
        return;
      }
      setServices(data);
      setSelectedServiceId((current) => {
        if (data.some((service) => String(service.id) === String(current))) {
          return current;
        }

        return String(data[0]?.id || "");
      });
    } catch {
      setError("Could not load services.");
    }
  }

  async function loadAdminServices(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/products/admin`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setAdminServices([]);
        setError(apiErrorMessage(data, "Could not load service admin."));
        return;
      }
      setAdminServices(data);
    } catch {
      setError("Could not load service admin.");
    }
  }

  async function loadVatReport(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/vat-report`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      setVatReport(data);
    } catch {
      setError("Could not load VAT report.");
    }
  }

  async function loadExpenses(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/expenses`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setExpenses([]);
        setError(apiErrorMessage(data, "Could not load expenses."));
        return;
      }
      setExpenses(data);
    } catch {
      setError("Could not load expenses.");
    }
  }

  async function loadContracts(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/contracts`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setContracts([]);
        setError(apiErrorMessage(data, "Could not load contracts."));
        return;
      }
      setContracts(data);
    } catch {
      setError("Could not load contracts.");
    }
  }

  async function loadReminders(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/reminders`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      setReminders(data);
    } catch {
      setError("Could not load reminders.");
    }
  }

  async function loadAdvisorSummary(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/advisor-summary`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      setAdvisorSummary(data);
    } catch {
      setError("Could not load advisor summary.");
    }
  }

  async function loadBankReconciliations(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/bank-reconciliations`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json().catch(() => []);

      if (response.ok && Array.isArray(data)) {
        setBankReconciliationHistory(data);
      }
    } catch {
      setBankReconciliationHistory([]);
    }
  }

  async function runAutomaticInvoiceReminders() {
    setError("");
    setAutomaticReminderMessage("");
    setAutomaticReminderResult(null);

    const response = await fetch(`${apiUrl}/reminders/automatic/run`, {
      method: "POST",
      headers: authHeaders()
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      if (response.status === 404) {
        setError(language === "sv"
          ? "Automatik-endpointen hittades inte. Stoppa och starta om CloudShopApplication i IntelliJ sa nya backend-koden laddas."
          : "Automation endpoint was not found. Stop and restart CloudShopApplication in IntelliJ so the new backend code is loaded.");
        return;
      }

      setError(apiErrorMessage(data, "Could not run automatic reminders."));
      return;
    }

    const automationDisabled = settings?.automaticInvoiceRemindersEnabled === false;
    setAutomaticReminderResult(data);
    setAutomaticReminderMessage(
      automationDisabled
        ? (language === "sv" ? "Automatiken ar avstangd i Installningar." : "Automation is turned off in Settings.")
        : (language === "sv"
          ? `Automatik klar: ${data.checked} kontrollerade, ${data.sent} skickade, ${data.skipped} hoppades over.`
          : `Automation complete: ${data.checked} checked, ${data.sent} sent, ${data.skipped} skipped.`)
    );
    loadInvoices();
    loadReminders();
  }

  async function loadSettings(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/settings`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      setSettings(data);
    } catch {
      setError("Could not load settings.");
    }
  }

  async function loadSystemStatus() {
    try {
      const response = await fetch(`${apiUrl}/system/status`);
      if (!response.ok) {
        setSystemStatus({
          backend: { ok: false },
          database: { ok: false },
          email: { configured: false },
          stripe: { configured: false },
          ai: { configured: false, provider: "local" },
          error: response.status === 404
            ? (language === "sv"
              ? "Status-endpointen hittades inte. Starta om CloudShopApplication i IntelliJ."
              : "Status endpoint was not found. Restart CloudShopApplication in IntelliJ.")
            : (language === "sv" ? "Kunde inte hamta systemstatus." : "Could not load system status.")
        });
        return;
      }

      const data = await response.json();
      setSystemStatus(data);
    } catch {
      setSystemStatus({
        backend: { ok: false },
        database: { ok: false },
        email: { configured: false },
        stripe: { configured: false },
        ai: { configured: false, provider: "local" },
        error: language === "sv"
          ? "Backend svarar inte just nu."
          : "Backend is not responding right now."
      });
    }
  }

  async function loadProfitAndLoss(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/profit-and-loss`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      setProfitAndLoss(data);
    } catch {
      setError("Could not load profit and loss report.");
    }
  }

  async function loadBalanceReport(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/balance-report`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      setBalanceReport(data);
    } catch {
      setError("Could not load balance report.");
    }
  }

  async function loadJournalEntries(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/journal-entries`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setJournalEntries([]);
        setError(apiErrorMessage(data, "Could not load journal entries."));
        return;
      }
      setJournalEntries(data);
    } catch {
      setError("Could not load journal entries.");
    }
  }

  async function loadStripePayouts(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/stripe-payouts`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setStripePayouts([]);
        return;
      }
      setStripePayouts(data);
    } catch {
      setStripePayouts([]);
    }
  }

  async function loadAccounts(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/accounts`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setAccounts([]);
        setError(apiErrorMessage(data, "Could not load accounts."));
        return;
      }
      setAccounts(data);
    } catch {
      setError("Could not load accounts.");
    }
  }

  async function loadCustomers(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/customers`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setCustomers([]);
        setError(apiErrorMessage(data, "Could not load customers."));
        return;
      }
      setCustomers(data);
      setSelectedCustomerId((current) => {
        if (data.some((customer) => String(customer.id) === String(current))) {
          return current;
        }

        return String(data[0]?.id || "");
      });
    } catch {
      setError("Could not load customers.");
    }
  }

  async function loadInvoices(authToken = token) {
    try {
      const response = await fetch(`${apiUrl}/invoices`, {
        headers: authHeaders(authToken)
      });
      const data = await response.json();
      if (!response.ok || !Array.isArray(data)) {
        setInvoices([]);
        setError(apiErrorMessage(data, "Could not load invoices."));
        return;
      }
      setInvoices(data);
    } catch {
      setError("Could not load invoices.");
    }
  }

  async function createInvoiceRequest({ customerId, serviceId, quantity }) {
    setError("");
    setInvoice(null);

    const invoiceQuantityValue = Number(quantity || 1);

    if (invoiceQuantityValue <= 0) {
      setError(language === "sv" ? "Antal maste vara minst 1." : "Quantity must be at least 1.");
      return null;
    }

    const invoiceDate = new Date().toISOString().slice(0, 10);
    if (isAccountingDateLocked(invoiceDate)) {
      setError(lockedAccountingMessage(invoiceDate));
      return null;
    }

    const response = await fetch(`${apiUrl}/invoices`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        customerId: customerId || null,
        productId: serviceId,
        quantity: invoiceQuantityValue
      })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create invoice."));
      return null;
    }

    setInvoice(data);
    loadInvoices();
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
    return data;
  }

  async function handleCreateInvoice(event) {
    event.preventDefault();
    await createInvoiceRequest({
      customerId: selectedCustomerId,
      serviceId: selectedServiceId,
      quantity: invoiceQuantity
    });
  }

  async function handleCreateContract(event) {
    event.preventDefault();
    setContractMessage("");
    setError("");

    if (!contractCustomerId || !contractServiceId) {
      setContractMessage(language === "sv" ? "Valj kund och tjanst for avtalet." : "Choose customer and service for the contract.");
      return;
    }

    if (Number(contractQuantity || 1) <= 0) {
      setContractMessage(language === "sv" ? "Antal maste vara minst 1." : "Quantity must be at least 1.");
      return;
    }

    const response = await fetch(`${apiUrl}/contracts`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({
        customerId: Number(contractCustomerId),
        serviceId: Number(contractServiceId),
        quantity: Number(contractQuantity || 1),
        interval: contractInterval,
        nextInvoiceDate: contractNextDate
      })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create contract."));
      return;
    }

    setContracts((current) => [data, ...current]);
    setContractMessage(language === "sv" ? "Avtal sparades." : "Contract saved.");
  }

  async function createContractInvoice(contract) {
    setContractMessage("");
    setError("");

    const response = await fetch(`${apiUrl}/contracts/${contract.id}/invoice`, {
      method: "POST",
      headers: authHeaders()
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create contract invoice."));
      return;
    }

    setInvoice(data);
    loadContracts();
    loadInvoices();
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
    setContractMessage(language === "sv" ? "Avtalsfaktura skapades." : "Contract invoice created.");
  }

  async function createAllDueContractInvoices() {
    setContractMessage("");
    setError("");

    if (dueContracts.length === 0) {
      setContractMessage(language === "sv" ? "Inga avtal ar redo att fakturera." : "No contracts are due for invoicing.");
      return;
    }

    let createdCount = 0;

    for (const contract of dueContracts) {
      const response = await fetch(`${apiUrl}/contracts/${contract.id}/invoice`, {
        method: "POST",
        headers: authHeaders()
      });

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        setError(apiErrorMessage(data, "Could not create all due contract invoices."));
        break;
      }

      setInvoice(data);
      createdCount += 1;
    }

    loadContracts();
    loadInvoices();
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();

    if (createdCount > 0) {
      setContractMessage(language === "sv"
        ? `${createdCount} avtalsfaktura/avtalsfakturor skapades.`
        : `${createdCount} contract invoice(s) created.`);
    }
  }

  async function toggleContractActive(contract) {
    setError("");

    const response = await fetch(`${apiUrl}/contracts/${contract.id}/status`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({ active: !contract.active })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not update contract."));
      return;
    }

    setContracts((current) => current.map((item) => (item.id === contract.id ? data : item)));
  }

  async function deleteContract(contractId) {
    setError("");

    const response = await fetch(`${apiUrl}/contracts/${contractId}`, {
      method: "DELETE",
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, "Could not delete contract."));
      return;
    }

    setContracts((current) => current.filter((item) => item.id !== contractId));
  }

  async function saveSettingsChanges(updatedSettings, successMessage = t.settingsSaved) {
    setError("");
    setSettingsMessage("");

    const response = await fetch(`${apiUrl}/settings`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify(updatedSettings)
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not save settings."));
      return null;
    }

    setSettings(data);
    setSettingsMessage(successMessage);
    loadAccounts();
    loadBalanceReport();
    return data;
  }

  async function handleSaveSettings(event) {
    event.preventDefault();
    await saveSettingsChanges(settings);
  }

  async function closeSelectedAccountingPeriod() {
    setPeriodCloseMessage("");

    if (!settings) {
      setError(language === "sv" ? "Installningar ar inte laddade annu." : "Settings are not loaded yet.");
      return;
    }

    if (!vatPeriodTo) {
      setError(language === "sv" ? "Valj ett slutdatum for perioden forst." : "Choose an end date for the period first.");
      return;
    }

    if (accountingLockedThroughDate && accountingLockedThroughDate >= vatPeriodTo) {
      setPeriodCloseMessage(language === "sv"
        ? `Perioden ar redan last till ${formatDateOnly(accountingLockedThroughDate)}.`
        : `The period is already locked through ${formatDateOnly(accountingLockedThroughDate)}.`);
      return;
    }

    const confirmed = window.confirm(language === "sv"
      ? `Vill du lasa bokforingen till och med ${formatDateOnly(vatPeriodTo)}? Efter detta ska andringar i perioden goras som korrigeringar i en nyare period.`
      : `Lock bookkeeping through ${formatDateOnly(vatPeriodTo)}? After this, changes in the period should be made as corrections in a later period.`);

    if (!confirmed) return;

    const successMessage = language === "sv"
      ? `Perioden ar last till ${formatDateOnly(vatPeriodTo)}.`
      : `Period locked through ${formatDateOnly(vatPeriodTo)}.`;
    const data = await saveSettingsChanges({
      ...settings,
      accountingLockedThroughDate: vatPeriodTo
    }, successMessage);

    if (data) {
      setPeriodCloseMessage(successMessage);
    }
  }

  async function sendSettingsTestEmail() {
    setError("");
    setSettingsMessage("");

    const response = await fetch(`${apiUrl}/settings/test-email`, {
      method: "POST",
      headers: authHeaders()
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not send test email."));
      return;
    }

    setSettings(data);
    setSettingsMessage(language === "sv"
      ? `Testmail skickat till ${data.contactEmail}.`
      : `Test email sent to ${data.contactEmail}.`);
  }

  async function copyConfigValue(value) {
    try {
      await navigator.clipboard.writeText(value);
      setSettingsMessage(
        value.includes("\n")
          ? (language === "sv" ? "Konfigurationsblock kopierat." : "Configuration block copied.")
          : (language === "sv" ? "Konfigurationsrad kopierad." : "Configuration line copied.")
      );
    } catch {
      setError(language === "sv" ? "Kunde inte kopiera raden." : "Could not copy the line.");
    }
  }

  function generateJwtSecret() {
    const bytes = new Uint8Array(48);
    window.crypto.getRandomValues(bytes);
    const secret = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
    setGeneratedJwtSecret(secret);
    setSettingsMessage(language === "sv" ? "Ny JWT-hemlighet genererad." : "New JWT secret generated.");
    return secret;
  }

  function jwtSecretConfigLine() {
    return `JWT_SECRET=${generatedJwtSecret || "change_me_to_a_long_random_secret"}`;
  }

  function missingConfigLines() {
    const lines = [];

    if (!systemStatus?.security?.jwtStrong) {
      lines.push(jwtSecretConfigLine());
    }

    if (!systemStatus?.stripe?.configured) {
      lines.push("STRIPE_SECRET_KEY=sk_test_...");
    }

    if (!systemStatus?.stripe?.webhookConfigured) {
      lines.push("STRIPE_WEBHOOK_SECRET=whsec_...");
    }

    if (!systemStatus?.ai?.configured) {
      lines.push(
        "GEMINI_API_KEY=AIza...",
        "GEMINI_MODEL=gemini-3.5-flash",
        "GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta"
      );
    }

    if (!systemStatus?.email?.configured) {
      lines.push(
        "SPRING_MAIL_HOST=smtp.gmail.com",
        "SPRING_MAIL_PORT=587",
        "SPRING_MAIL_USERNAME=din-email@gmail.com",
        "SPRING_MAIL_PASSWORD=app-losenord-fran-google"
      );
    }

    return lines;
  }

  function copyMissingConfigValues() {
    const lines = missingConfigLines();

    if (lines.length === 0) {
      setSettingsMessage(language === "sv" ? "Alla konfigurationsvarden finns redan." : "All configuration values are already set.");
      return;
    }

    copyConfigValue(lines.join("\n"));
  }

  async function clearTestData() {
    setError("");
    setSettingsMessage("");

    const confirmed = window.confirm(
      language === "sv"
        ? "Rensa alla kunder, fakturor, kostnader och bokforingsrader? Detta ar bara for testdata."
        : "Clear all customers, invoices, expenses and bookkeeping rows? This is only for test data."
    );

    if (!confirmed) {
      return;
    }

    const response = await fetch(`${apiUrl}/test-data`, {
      method: "DELETE",
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, "Could not clear test data."));
      return;
    }

    setInvoices([]);
    setCustomers([]);
    setSelectedCustomerId("");
    setJournalEntries([]);
    setExpenses([]);
    setVatReport(null);
    setProfitAndLoss(null);
    setBalanceReport(null);
    setSettingsMessage(t.testDataCleared);
  }

  function clearLocalPreferences() {
    localPreferenceKeys.forEach((key) => localStorage.removeItem(key));
    setLanguage("sv");
    setActiveView("overview");
    setInvoiceFilter("all");
    setInvoiceDateFrom("");
    setInvoiceDateTo("");
    setInvoiceSearch("");
    setCustomerFilter("active");
    setCustomerSearch("");
    setPaymentOverviewFilter("open");
    setPaymentOverviewSearch("");
    setBookkeepingFilter("all");
    setBookkeepingSearch("");
    setBookkeepingDateFrom("");
    setBookkeepingDateTo("");
    setExpenseFilter("all");
    setExpenseCategoryFilter("all");
    setExpenseDateFrom("");
    setExpenseDateTo("");
    setExpenseSearch("");
    setCashflowHorizonDays(30);
    setBudgetRevenueTarget("25000");
    setBudgetExpenseLimit("12000");
    setBudgetReservePercent("30");
    setPayrollDrafts([]);
    setPayrollTaxRate("30");
    setPayrollEmployerRate("31.42");
    setPayrollSalaryAccount("7010");
    setPayrollMessage("");
    setSelectedCustomerId(customers[0]?.id ? String(customers[0].id) : "");
    setSelectedServiceId(services[0]?.id ? String(services[0].id) : "");
    setInvoiceQuantity("1");
    setExpenseCategory("5420");
    setBokioImportQueue([]);
    setBokioImportFiles({});
    setBokioImportMessage("");
    setSettingsMessage(language === "sv" ? "Lokala val har aterstallts." : "Local preferences reset.");
  }

  async function handleCreateExpense(event) {
    event.preventDefault();
    setError("");

    if (expenseDateIsLocked) {
      setError(lockedAccountingMessage(expenseDate));
      return;
    }

    const response = await fetch(`${apiUrl}/expenses`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({
        expenseDate,
        description: expenseDescription,
        netAmount: Number(expenseNetAmount),
        vatAmount: Number(expenseVatAmount),
        category: expenseCategory,
        paidFrom: "1930"
      })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create expense."));
      return;
    }

    let savedExpense = data;

    if (expenseReceiptFile) {
      const uploadedExpense = await uploadExpenseReceipt(data.id, expenseReceiptFile);
      savedExpense = uploadedExpense || data;
    }

    setExpenses((current) => [...current, savedExpense]);
    setExpenseDescription("");
    setExpenseDate(new Date().toISOString().slice(0, 10));
    setExpenseNetAmount("");
    setExpenseVatAmount("");
    setExpenseReceiptFile(null);
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  async function handleCreateManualJournalEntry(event) {
    event.preventDefault();
    setError("");

    const response = await fetch(`${apiUrl}/journal-entries/manual`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({
        voucherDate: manualVoucherDate,
        description: manualDescription,
        debitAccountNumber: manualDebitAccount,
        creditAccountNumber: manualCreditAccount,
        amount: Number(manualAmount)
      })
    });
    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create manual voucher."));
      return;
    }

    setJournalEntries((current) => [...current, ...data]);
    setManualDescription("");
    setManualVoucherDate(new Date().toISOString().slice(0, 10));
    setManualAmount("");
    loadJournalEntries();
    loadStripePayouts();
    loadVatReport();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  async function createCorrectionVoucher(voucherNumber) {
    setError("");

    const correctionDate = new Date().toISOString().slice(0, 10);
    if (isAccountingDateLocked(correctionDate)) {
      setError(lockedAccountingMessage(correctionDate));
      return;
    }

    const confirmed = window.confirm(
      language === "sv"
        ? `Skapa en rattelseverifikation som vander ${voucherNumber}?`
        : `Create a correction voucher that reverses ${voucherNumber}?`
    );

    if (!confirmed) {
      return;
    }

    const response = await fetch(`${apiUrl}/journal-entries/${encodeURIComponent(voucherNumber)}/correction`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({ voucherDate: correctionDate })
    });
    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create correction voucher."));
      return;
    }

    if (Array.isArray(data)) {
      setJournalEntries((current) => [...current, ...data]);
    }

    loadJournalEntries();
    loadVatReport();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  async function uploadExpenseReceipt(expenseId, file) {
    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch(`${apiUrl}/expenses/${expenseId}/receipt`, {
      method: "POST",
      headers: authHeaders(),
      body: formData
    });
    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not upload receipt."));
      return null;
    }

    return data;
  }

  async function openExpenseReceipt(expenseId) {
    setError("");

    const response = await fetch(`${apiUrl}/expenses/${expenseId}/receipt`, {
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, "Could not open receipt."));
      return;
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    window.open(url, "_blank", "noopener,noreferrer");
  }

  async function attachReceiptToExpense(expenseId, file) {
    if (!file) {
      return;
    }

    setError("");
    const uploadedExpense = await uploadExpenseReceipt(expenseId, file);

    if (uploadedExpense) {
      setExpenses((current) => current.map((expense) => (expense.id === expenseId ? uploadedExpense : expense)));
      loadExpenses();
    }
  }

  async function handleCreateCustomer(event) {
    event.preventDefault();
    setError("");
    setCustomerMessage("");

    const isEditingCustomer = Boolean(editingCustomerId);
    const response = await fetch(`${apiUrl}/customers${isEditingCustomer ? `/${editingCustomerId}` : ""}`, {
      method: isEditingCustomer ? "PUT" : "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        name: customerName,
        email: customerEmail,
        personalNumber: customerPersonalNumber,
        address: customerAddress,
        phone: customerPhone,
        postalCode: customerPostalCode,
        city: customerCity
      })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create customer."));
      return;
    }

    setCustomers((current) => {
      if (isEditingCustomer) {
        return current.map((customer) => (customer.id === data.id ? data : customer));
      }

      return [...current, data];
    });
    setSelectedCustomerId(String(data.id));
    setEditingCustomerId(null);
    setCustomerName("");
    setCustomerEmail("");
    setCustomerPersonalNumber("");
    setCustomerAddress("");
    setCustomerPhone("");
    setCustomerPostalCode("");
    setCustomerCity("");
    setCustomerMessage(isEditingCustomer ? (language === "sv" ? "Kunden uppdaterades." : "Customer updated.") : t.customerSaved);
  }

  function startEditCustomer(customer) {
    setEditingCustomerId(customer.id);
    setSelectedCustomerId(String(customer.id));
    setCustomerName(customer.name || "");
    setCustomerEmail(customer.email || "");
    setCustomerPersonalNumber(customer.personalNumber || "");
    setCustomerAddress(customer.address || "");
    setCustomerPhone(customer.phone || "");
    setCustomerPostalCode(customer.postalCode || "");
    setCustomerCity(customer.city || "");
    setCustomerMessage("");
    setError("");
  }

  function cancelEditCustomer() {
    setEditingCustomerId(null);
    setCustomerName("");
    setCustomerEmail("");
    setCustomerPersonalNumber("");
    setCustomerAddress("");
    setCustomerPhone("");
    setCustomerPostalCode("");
    setCustomerCity("");
  }

  async function handleSaveService(event) {
    event.preventDefault();
    setError("");
    setServiceMessage("");

    const price = Number(servicePrice || 0);
    const discountPrice = Number(serviceDiscountPrice || 0);

    if (serviceName.trim().length < 2) {
      setError(language === "sv" ? "Tjanstens namn maste innehalla minst 2 tecken." : "Service name must contain at least 2 characters.");
      return;
    }

    if (price < 0 || discountPrice < 0) {
      setError(language === "sv" ? "Pris kan inte vara negativt." : "Price cannot be negative.");
      return;
    }

    if (discountPrice > 0 && discountPrice >= price) {
      setError(language === "sv" ? "Rabattpriset maste vara lagre an ordinarie pris." : "Discount price must be lower than ordinary price.");
      return;
    }

    const isEditingService = Boolean(editingServiceId);
    const response = await fetch(`${apiUrl}/products${isEditingService ? `/${editingServiceId}` : ""}`, {
      method: isEditingService ? "PUT" : "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({
        name: serviceName,
        description: serviceDescription,
        price,
        discountPrice,
        discountLabel: serviceDiscountLabel,
        active: serviceActive
      })
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not save service."));
      return;
    }

    setServiceMessage(t.serviceSaved);
    resetServiceForm();
    loadServices();
    loadAdminServices();
  }

  function startEditService(service) {
    setEditingServiceId(service.id);
    setServiceName(service.name || "");
    setServiceDescription(service.description || "");
    setServicePrice(String(service.price || 0));
    setServiceDiscountPrice(service.discountPrice ? String(service.discountPrice) : "");
    setServiceDiscountLabel(service.discountLabel || "");
    setServiceActive(service.active !== false);
    setServiceMessage("");
    setError("");
  }

  function resetServiceForm() {
    setEditingServiceId(null);
    setServiceName("");
    setServiceDescription("");
    setServicePrice("");
    setServiceDiscountPrice("");
    setServiceDiscountLabel("");
    setServiceActive(true);
  }

  async function updateCustomerArchive(id, archived) {
    setError("");

    const response = await fetch(`${apiUrl}/customers/${id}/${archived ? "archive" : "restore"}`, {
      method: "POST",
      headers: authHeaders()
    });
    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, archived ? "Could not archive customer." : "Could not restore customer."));
      return;
    }

    setCustomers((current) => current.map((customer) => (customer.id === data.id ? data : customer)));
    setSelectedCustomerId(String(data.id));
  }

  async function deleteCustomer(id) {
    setError("");

    const customer = customers.find((item) => item.id === id);
    const confirmed = window.confirm(
      language === "sv"
        ? `Ta bort kunden ${customer?.name || ""} permanent? Detta fungerar bara om kunden saknar fakturor.`
        : `Delete customer ${customer?.name || ""} permanently? This only works if the customer has no invoices.`
    );

    if (!confirmed) {
      return;
    }

    const response = await fetch(`${apiUrl}/customers/${id}`, {
      method: "DELETE",
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(
        data,
        language === "sv"
          ? "Kunden har fakturor och ska arkiveras istallet for att tas bort."
          : "Customer has invoices and should be archived instead of deleted."
      ));
      return;
    }

    setCustomers((current) => current.filter((customerItem) => customerItem.id !== id));
    setSelectedCustomerId("");
  }

  async function updateInvoiceStatus(id, status, paymentOverride = {}) {
    setError("");
    const isPaid = status.toLowerCase() === "paid";
    const paymentDate = paymentOverride.paymentDate || paymentDates[id] || new Date().toISOString().slice(0, 10);
    const invoiceToPay = invoices.find((item) => item.id === id);
    const remainingAmount = invoiceRemainingAmount(invoiceToPay || {});
    const paidAmount = Number(paymentOverride.paidAmount || paymentAmounts[id] || remainingAmount);
    const paymentReference = paymentOverride.paymentReference || paymentReferences[id] || "";

    if (isPaid && !/^\d{4}-\d{2}-\d{2}$/.test(paymentDate)) {
      setError(language === "sv" ? "Betalningsdatum maste vara YYYY-MM-DD." : "Payment date must be YYYY-MM-DD.");
      return;
    }

    if (isPaid && isAccountingDateLocked(paymentDate)) {
      setError(lockedAccountingMessage(paymentDate));
      return;
    }

    if (isPaid && paidAmount <= 0) {
      setError(language === "sv" ? "Betalt belopp maste vara storre an 0." : "Paid amount must be greater than 0.");
      return;
    }

    if (isPaid && paidAmount > remainingAmount) {
      setError(language === "sv" ? "Betalt belopp kan inte vara storre an kvar att betala." : "Paid amount cannot be greater than remaining amount.");
      return;
    }

    const response = await fetch(`${apiUrl}/invoices/${id}/${status.toLowerCase()}`, {
      method: "POST",
      headers: {
        ...(isPaid ? { "Content-Type": "application/json" } : {}),
        Authorization: `Bearer ${token}`
      },
      body: isPaid ? JSON.stringify({ paymentDate, paidAmount, paymentReference }) : undefined
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not update invoice."));
      return;
    }

    setInvoices((current) => current.map((item) => (item.id === id ? data : item)));
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  async function createStripePayout() {
    setError("");
    setStripePayoutMessage("");

    const grossAmount = Number(stripePayoutGrossAmount);
    const feeAmount = Number(stripePayoutFeeAmount || 0);

    if (!/^\d{4}-\d{2}-\d{2}$/.test(stripePayoutDate)) {
      setError(language === "sv" ? "Utbetalningsdatum maste vara YYYY-MM-DD." : "Payout date must be YYYY-MM-DD.");
      return;
    }

    if (isAccountingDateLocked(stripePayoutDate)) {
      setError(lockedAccountingMessage(stripePayoutDate));
      return;
    }

    if (!Number.isFinite(grossAmount) || grossAmount <= 0) {
      setError(language === "sv" ? "Bruttobelopp fran Stripe maste vara storre an 0." : "Stripe gross amount must be greater than 0.");
      return;
    }

    if (!Number.isFinite(feeAmount) || feeAmount < 0) {
      setError(language === "sv" ? "Stripe-avgift kan inte vara negativ." : "Stripe fee cannot be negative.");
      return;
    }

    if (feeAmount > grossAmount) {
      setError(language === "sv" ? "Stripe-avgift kan inte vara storre an bruttobeloppet." : "Stripe fee cannot be greater than the gross amount.");
      return;
    }

    const response = await fetch(`${apiUrl}/journal-entries/stripe-payout`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        payoutDate: stripePayoutDate,
        grossAmount,
        feeAmount,
        reference: stripePayoutReference
      })
    });
    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, language === "sv" ? "Kunde inte bokfora Stripe-utbetalning." : "Could not book Stripe payout."));
      return;
    }

    setStripePayoutMessage(
      language === "sv"
        ? `Stripe-utbetalning bokford: ${grossAmount - feeAmount} SEK till bank och ${feeAmount} SEK avgift.`
        : `Stripe payout booked: ${grossAmount - feeAmount} SEK to bank and ${feeAmount} SEK fee.`
    );
    setStripePayoutGrossAmount("");
    setStripePayoutFeeAmount("");
    setStripePayoutReference("");
    loadJournalEntries();
    loadVatReport();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  async function createStripeWebsiteSale() {
    setError("");
    setStripeWebsiteSaleMessage("");

    const totalAmount = Number(stripeWebsiteSaleAmount);

    if (!/^\d{4}-\d{2}-\d{2}$/.test(stripeWebsiteSaleDate)) {
      setError(language === "sv" ? "Forsaljningsdatum maste vara YYYY-MM-DD." : "Sale date must be YYYY-MM-DD.");
      return;
    }

    if (isAccountingDateLocked(stripeWebsiteSaleDate)) {
      setError(lockedAccountingMessage(stripeWebsiteSaleDate));
      return;
    }

    if (!Number.isFinite(totalAmount) || totalAmount <= 0) {
      setError(language === "sv" ? "Stripe-forsaljning maste vara storre an 0." : "Stripe sale amount must be greater than 0.");
      return;
    }

    const response = await fetch(`${apiUrl}/journal-entries/stripe-website-sale`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        saleDate: stripeWebsiteSaleDate,
        totalAmount,
        reference: stripeWebsiteSaleReference
      })
    });
    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, language === "sv" ? "Kunde inte bokfora Stripe-forsaljning." : "Could not book Stripe website sale."));
      return;
    }

    setStripeWebsiteSaleMessage(
      language === "sv"
        ? `Stripe-forsaljning bokford: ${totalAmount} SEK.`
        : `Stripe website sale booked: ${totalAmount} SEK.`
    );
    setStripeWebsiteSaleAmount("");
    setStripeWebsiteSaleReference("");
    loadJournalEntries();
    loadVatReport();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  function handleBankCsvUpload(event) {
    const file = event.target.files?.[0];

    if (!file) {
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const rows = parseBankCsv(reader.result);
      setBankImportRows(rows);
      setBankImportExpenseCategories({});
      setBankImportExpenseVatRates({});
      setBankImportExpenseDescriptions({});
      setLastCreatedBankImportExpense(null);
      setSkippedBankImportRows([]);
      setBankImportMessage(
        rows.length > 0
          ? (language === "sv" ? `${rows.length} bankhandelser lastes in.` : `${rows.length} bank transactions imported.`)
          : (language === "sv" ? "Kunde inte hitta bankhandelser i filen." : "Could not find bank transactions in the file.")
      );
    };
    reader.onerror = () => {
      setBankImportMessage(language === "sv" ? "Kunde inte lasa bankfilen." : "Could not read the bank file.");
    };
    reader.readAsText(file, "utf-8");
    event.target.value = "";
  }

  function findBankImportMatch(row) {
    if ((row.amount || 0) <= 0) {
      return null;
    }

    const haystack = `${row.description || ""} ${row.reference || ""}`.toLowerCase();
    const amount = row.amount || 0;
    const openInvoices = invoices.filter((item) => invoiceRemainingAmount(item) > 0);

    return openInvoices.find((item) => {
      return Boolean(item.ocrNumber && haystack.includes(String(item.ocrNumber).toLowerCase()))
        || Boolean(invoiceNumber(item) && haystack.includes(invoiceNumber(item).toLowerCase()))
        || Boolean(item.customerName && haystack.includes(String(item.customerName).toLowerCase()))
        || amount === invoiceRemainingAmount(item);
    }) || null;
  }

  function bankImportPaymentDate(row) {
    return /^\d{4}-\d{2}-\d{2}$/.test(row.date || "")
      ? row.date
      : new Date().toISOString().slice(0, 10);
  }

  function suggestExpenseCategoryFromBankRow(row) {
    const matchedRule = bankImportExpenseRule(row);
    if (matchedRule) {
      return matchedRule.category || "4010";
    }

    const text = `${row.description || ""} ${row.reference || ""}`.toLowerCase();

    if (text.includes("bank") || text.includes("avgift") || text.includes("fee")) {
      return "6570";
    }

    if (text.includes("resa") || text.includes("travel") || text.includes("taxi") || text.includes("train") || text.includes("flyg")) {
      return "5800";
    }

    if (text.includes("program") || text.includes("software") || text.includes("subscription") || text.includes("abonnemang")) {
      return "5420";
    }

    if (text.includes("inventarie") || text.includes("utrustning") || text.includes("equipment")) {
      return "5410";
    }

    return "4010";
  }

  function bankImportExpenseRule(row) {
    if ((row.amount || 0) >= 0) {
      return null;
    }

    const rowText = normalizeBankImportMatchText(`${row.description || ""} ${row.reference || ""}`);
    return bankImportRules.find((rule) => {
      if (rule.enabled === false) return false;
      const keywords = normalizeBankImportMatchText(rule.keyword || "")
        .split(/\s+/)
        .map((keyword) => keyword.trim())
        .filter(Boolean);
      return keywords.some((keyword) => rowText.includes(keyword));
    }) || null;
  }

  function bankImportExpenseCategory(row) {
    return bankImportExpenseCategories[row.id] || suggestExpenseCategoryFromBankRow(row);
  }

  function defaultBankImportVatRate(category) {
    return category === "6570" ? 0 : 25;
  }

  function bankImportExpenseVatRate(row, category = bankImportExpenseCategory(row)) {
    const savedRate = bankImportExpenseVatRates[row.id];
    if (savedRate !== undefined) {
      return Number(savedRate);
    }
    const matchedRule = bankImportExpenseRule(row);
    return matchedRule ? Number(matchedRule.vatRate || 0) : defaultBankImportVatRate(category);
  }

  function bankImportExpenseDescription(row) {
    const matchedRule = bankImportExpenseRule(row);
    return (bankImportExpenseDescriptions[row.id] || matchedRule?.description || row.description || row.reference || "Bank CSV").trim();
  }

  function bankImportWorkflow(row) {
    const match = findBankImportMatch(row);

    if (match) {
      return {
        key: "invoiceReady",
        tone: "ready",
        title: language === "sv" ? "Faktura redo" : "Invoice ready",
        nextAction: language === "sv" ? `Registrera betalning for ${invoiceNumber(match)}` : `Register payment for ${invoiceNumber(match)}`
      };
    }

    if ((row.amount || 0) < 0) {
      const existingExpenseMatch = findExistingExpenseForBankRow(row);

      if (existingExpenseMatch) {
        return {
          key: "alreadyBooked",
          tone: "done",
          title: language === "sv" ? "Redan bokford" : "Already booked",
          nextAction: existingExpenseMatch.description || (language === "sv" ? "Kostnaden finns redan" : "Expense already exists")
        };
      }

      const matchedRule = bankImportExpenseRule(row);

      if (matchedRule) {
        return {
          key: "expenseReady",
          tone: "ready",
          title: language === "sv" ? "Kostnad redo" : "Expense ready",
          nextAction: language === "sv"
            ? `${matchedRule.category}, ${matchedRule.vatRate}% moms foreslagen`
            : `${matchedRule.category}, ${matchedRule.vatRate}% VAT suggested`
        };
      }

      return {
        key: "expenseReview",
        tone: "review",
        title: language === "sv" ? "Behover kontroll" : "Needs review",
        nextAction: language === "sv" ? "Valj konto och moms innan bokforing" : "Choose account and VAT before booking"
      };
    }

    return {
      key: "incomingReview",
      tone: "review",
      title: language === "sv" ? "Inbetalning utan match" : "Incoming without match",
      nextAction: language === "sv" ? "Kontrollera kund, OCR eller belopp" : "Check customer, OCR or amount"
    };
  }

  function bankImportExpenseAmounts(row, category = bankImportExpenseCategory(row), vatRate = bankImportExpenseVatRate(row, category)) {
    const total = Math.abs(row.amount || 0);

    if (vatRate <= 0) {
      return { netAmount: total, vatAmount: 0 };
    }

    const netAmount = Math.round(total / (1 + (vatRate / 100)));
    return {
      netAmount,
      vatAmount: total - netAmount
    };
  }

  function normalizeBankImportMatchText(value) {
    return String(value || "")
      .toLowerCase()
      .replaceAll("\u00e5", "a")
      .replaceAll("\u00e4", "a")
      .replaceAll("\u00f6", "o")
      .replace(/[^a-z0-9]/g, "");
  }

  function findExistingExpenseForBankRow(row) {
    if ((row.amount || 0) >= 0) {
      return null;
    }

    const rowDate = bankImportPaymentDate(row);
    const rowAmount = Math.abs(row.amount || 0);
    const rowText = normalizeBankImportMatchText(`${bankImportExpenseDescription(row)}${row.reference || ""}`);

    return expenses.find((expense) => {
      const expenseText = normalizeBankImportMatchText(expense.description);
      const sameDate = (expense.expenseDate || "") === rowDate;
      const sameAmount = (expense.totalAmount || 0) === rowAmount;
      const similarText = rowText && expenseText && (rowText.includes(expenseText) || expenseText.includes(rowText));

      return sameDate && sameAmount && similarText;
    }) || null;
  }

  async function addBankReconciliationHistory(entry) {
    const savedEntry = {
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      bookedAt: new Date().toISOString(),
      ...entry
    };

    try {
      const response = await fetch(`${apiUrl}/bank-reconciliations`, {
        method: "POST",
        headers: {
          ...authHeaders(),
          "Content-Type": "application/json"
        },
        body: JSON.stringify(entry)
      });
      const data = await response.json().catch(() => null);

      if (response.ok && data) {
        setBankReconciliationHistory((current) => [data, ...current.filter((item) => item.id !== data.id)].slice(0, 100));
        return data;
      }
    } catch {
      // Keep the UI usable even if the backend has not been restarted with the new endpoint yet.
    }

    setBankReconciliationHistory((current) => [savedEntry, ...current].slice(0, 100));
    return savedEntry;
  }

  function bankReconciliationRowBase(row) {
    return {
      bankRowId: row.id,
      date: bankImportPaymentDate(row),
      description: row.description || "",
      reference: row.reference || "",
      amount: row.amount || 0
    };
  }

  async function clearBankReconciliationHistory() {
    let clearedInDatabase = true;

    try {
      const response = await fetch(`${apiUrl}/bank-reconciliations`, {
        method: "DELETE",
        headers: authHeaders()
      });

      if (!response.ok) {
        throw new Error("Could not clear bank reconciliation history.");
      }
    } catch {
      clearedInDatabase = false;
    }

    setBankReconciliationHistory([]);
    setBankImportMessage(clearedInDatabase
      ? (language === "sv" ? "Bankavstamningshistorik rensad." : "Bank reconciliation history cleared.")
      : (language === "sv"
        ? "Historiken rensades lokalt. Starta om backend om den inte rensas i databasen."
        : "History was cleared locally. Restart the backend if it is not cleared in the database."));
  }

  function downloadBankReconciliationCsv() {
    const rows = [
      ["Datum", "Typ", "Status", "Beskrivning", "Referens", "Belopp", "Matchning", "Bokad tid"]
    ];

    bankReconciliationHistory.forEach((entry) => {
      rows.push([
        entry.date || "",
        entry.type || "",
        entry.status || "",
        entry.description || "",
        entry.reference || "",
        entry.amount || 0,
        entry.matchLabel || "",
        entry.bookedAt || ""
      ]);
    });

    downloadLocalCsv("bankavstamning.csv", rows);
  }

  function removeBankImportRow(rowId) {
    setBankImportRows((current) => current.filter((item) => item.id !== rowId));
    setBankImportExpenseCategories((current) => {
      const next = { ...current };
      delete next[rowId];
      return next;
    });
    setBankImportExpenseVatRates((current) => {
      const next = { ...current };
      delete next[rowId];
      return next;
    });
    setBankImportExpenseDescriptions((current) => {
      const next = { ...current };
      delete next[rowId];
      return next;
    });
  }

  function skipBankImportRow(row) {
    removeBankImportRow(row.id);
    setSkippedBankImportRows((current) => [row, ...current].slice(0, 8));
    addBankReconciliationHistory({
      ...bankReconciliationRowBase(row),
      type: (row.amount || 0) < 0 ? "outgoing" : "incoming",
      status: "skipped",
      matchLabel: language === "sv" ? "Hoppades over" : "Skipped"
    });
    setLastCreatedBankImportExpense(null);
    setBankImportMessage(language === "sv" ? "Bankrad hoppades over." : "Bank row skipped.");
  }

  async function restoreSkippedBankImportRow(row) {
    setBankImportRows((current) => [row, ...current]);
    setSkippedBankImportRows((current) => current.filter((item) => item.id !== row.id));
    setBankReconciliationHistory((current) => current.filter((item) => !(item.bankRowId === row.id && item.status === "skipped")));
    try {
      await fetch(`${apiUrl}/bank-reconciliations/skipped/${encodeURIComponent(row.id)}`, {
        method: "DELETE",
        headers: authHeaders()
      });
    } catch {
      // The row is restored in the UI; the persisted history will catch up after backend restart.
    }
    setBankImportMessage(language === "sv" ? "Bankrad aterstalldes." : "Bank row restored.");
  }

  function addBankImportRule(event) {
    event.preventDefault();
    const keyword = bankRuleKeyword.trim();

    if (!keyword) {
      setBankImportMessage(language === "sv" ? "Skriv minst ett nyckelord for bankregeln." : "Enter at least one keyword for the bank rule.");
      return;
    }

    setBankImportRules((current) => [
      {
        id: `rule-${Date.now()}`,
        keyword,
        category: bankRuleCategory,
        vatRate: Number(bankRuleVatRate),
        description: bankRuleDescription.trim() || keyword,
        enabled: true
      },
      ...current
    ]);
    setBankRuleKeyword("");
    setBankRuleDescription("");
    setBankImportMessage(language === "sv" ? "Bankregel skapad." : "Bank rule created.");
  }

  function toggleBankImportRule(ruleId) {
    setBankImportRules((current) => current.map((rule) => (
      rule.id === ruleId ? { ...rule, enabled: rule.enabled === false } : rule
    )));
  }

  function removeBankImportRule(ruleId) {
    setBankImportRules((current) => current.filter((rule) => rule.id !== ruleId));
  }

  function resetBankImportRules() {
    setBankImportRules(defaultBankImportRules);
    setBankImportMessage(language === "sv" ? "Standardregler aterstallda." : "Default rules restored.");
  }

  async function registerBankImportPayment(row, invoiceItem) {
    const paidAmount = Math.min(Math.abs(row.amount || 0), invoiceRemainingAmount(invoiceItem));

    await updateInvoiceStatus(invoiceItem.id, "PAID", {
      paymentDate: bankImportPaymentDate(row),
      paidAmount,
      paymentReference: row.reference || row.description || "Bank CSV"
    });

    removeBankImportRow(row.id);
    addBankReconciliationHistory({
      ...bankReconciliationRowBase(row),
      type: "invoice_payment",
      status: "booked",
      amount: paidAmount,
      matchLabel: `${invoiceNumber(invoiceItem)} - ${invoiceItem.customerName || ""}`.trim()
    });
    setBankImportMessage(language === "sv" ? "Bankbetalning registrerad." : "Bank payment registered.");
    setLastCreatedBankImportExpense(null);
  }

  async function createExpenseFromBankImport(row) {
    setError("");

    if ((row.amount || 0) >= 0) {
      setBankImportMessage(language === "sv" ? "Endast utbetalningar kan bli kostnader." : "Only outgoing payments can become expenses.");
      return;
    }

    const existingExpense = findExistingExpenseForBankRow(row);

    if (existingExpense) {
      setBankImportMessage(language === "sv" ? "Bankraden verkar redan vara bokford som kostnad." : "This bank row looks like it is already booked as an expense.");
      return;
    }

    const category = bankImportExpenseCategory(row);
    const vatRate = bankImportExpenseVatRate(row, category);
    const amounts = bankImportExpenseAmounts(row, category, vatRate);

    const response = await fetch(`${apiUrl}/expenses`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        expenseDate: bankImportPaymentDate(row),
        description: bankImportExpenseDescription(row),
        netAmount: amounts.netAmount,
        vatAmount: amounts.vatAmount,
        category,
        paidFrom: "1930"
      })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create expense from bank row."));
      return;
    }

    setExpenses((current) => [...current, data]);
    removeBankImportRow(row.id);
    addBankReconciliationHistory({
      ...bankReconciliationRowBase(row),
      type: "expense",
      status: "booked",
      matchLabel: `${data.category || category} - ${data.description || bankImportExpenseDescription(row)}`
    });
    setBankImportMessage(language === "sv" ? "Kostnad skapad fran bankrad." : "Expense created from bank row.");
    setLastCreatedBankImportExpense(data);
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  function openLastBankImportExpenseReceipts() {
    if (!lastCreatedBankImportExpense) {
      return;
    }

    setActiveView("uploaded");
    setExpenseFilter("missingReceipt");
    setExpenseCategoryFilter("all");
    setExpenseDateFrom("");
    setExpenseDateTo("");
    setExpenseSearch(lastCreatedBankImportExpense.description || "");
  }

  async function openStripeCheckout(id) {
    setError("");

    const response = await fetch(`${apiUrl}/invoices/${id}/checkout-session`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`
      }
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not start Stripe Checkout."));
      return;
    }

    window.location.href = data.url;
  }

  async function openInvoicePdf(id) {
    setError("");

    const response = await fetch(`${apiUrl}/invoices/${id}/pdf`, {
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, "Could not open invoice PDF."));
      return;
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    window.open(url, "_blank", "noopener,noreferrer");
  }

  async function saveInvoiceReminderStatus(item) {
    const response = await fetch(`${apiUrl}/invoices/${item.id}/reminder`, {
      method: "POST",
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, "Could not save reminder status."));
      return;
    }

    const updatedInvoice = await response.json();
    setInvoices((current) => current.map((invoiceItem) => (
      invoiceItem.id === updatedInvoice.id ? updatedInvoice : invoiceItem
    )));
  }

  async function copyInvoiceReminder(item) {
    setError("");
    const text = renderInvoiceReminderText(item, settings);

    try {
      await navigator.clipboard.writeText(text);
    } catch {
      window.prompt(language === "sv" ? "Kopiera paminnelsen:" : "Copy reminder:", text);
    }

    await saveInvoiceReminderStatus(item);
  }

  async function copyPaymentInfo(item) {
    setError("");
    const text = [
      `${language === "sv" ? "Faktura" : "Invoice"}: ${invoiceNumber(item)}`,
      `${language === "sv" ? "Kund" : "Customer"}: ${item.customerName || item.customer?.name || "-"}`,
      `${t.personalNumber}: ${item.customer?.personalNumber || "-"}`,
      `${language === "sv" ? "Kvar att betala" : "Remaining"}: ${invoiceRemainingAmount(item)} SEK`,
      `OCR: ${item.ocrNumber || "-"}`,
      `${language === "sv" ? "Referens" : "Reference"}: ${item.paymentReference || "-"}`,
      `PlusGiro: ${item.plusGiro || "418 76 01-2"}`,
      `${language === "sv" ? "Betalningsmottagare" : "Payment recipient"}: ${item.paymentRecipient || "Bank Norwegian"}`
    ].join("\n");

    try {
      await navigator.clipboard.writeText(text);
    } catch {
      window.prompt(language === "sv" ? "Kopiera betalinfo:" : "Copy payment info:", text);
    }

    setCopiedPaymentInfoId(item.id);
    window.setTimeout(() => {
      setCopiedPaymentInfoId((currentId) => (currentId === item.id ? null : currentId));
    }, 2500);
  }

  async function copyExpenseInfo(expense) {
    setError("");
    const text = [
      `${language === "sv" ? "Datum" : "Date"}: ${expense.expenseDate || "-"}`,
      `${t.description}: ${expense.description || "-"}`,
      `${t.category}: ${expenseCategoryLabel(expense.category)}`,
      `${t.net}: ${expense.netAmount || 0} SEK`,
      `${t.vat}: ${expense.vatAmount || 0} SEK`,
      `${t.total}: ${expense.totalAmount || 0} SEK`,
      `${t.receipt}: ${expenseHasReceipt(expense) ? (expense.receiptFileName || (language === "sv" ? "Sparat" : "Saved")) : (language === "sv" ? "Saknas" : "Missing")}`
    ].join("\n");

    try {
      await navigator.clipboard.writeText(text);
    } catch {
      window.prompt(language === "sv" ? "Kopiera kostnadsinfo:" : "Copy expense info:", text);
    }

    setCopiedExpenseInfoId(expense.id);
    window.setTimeout(() => {
      setCopiedExpenseInfoId((currentId) => (currentId === expense.id ? null : currentId));
    }, 2500);
  }

  async function copyAiAssistantMessage(message, messageId) {
    const text = message.text || "";

    try {
      await navigator.clipboard.writeText(text);
    } catch {
      window.prompt(language === "sv" ? "Kopiera AI-svar:" : "Copy AI answer:", text);
    }

    setCopiedAiMessageId(messageId);
    window.setTimeout(() => {
      setCopiedAiMessageId((currentId) => (currentId === messageId ? null : currentId));
    }, 2500);
  }

  async function openInvoiceReminderEmail(item) {
    setError("");
    const customerEmail = invoiceCustomerEmail(item);

    if (!customerEmail) {
      setError(language === "sv" ? "Kunden saknar e-postadress." : "Customer has no email address.");
      return;
    }

    const mailtoUrl = `mailto:${encodeURIComponent(customerEmail)}?subject=${encodeURIComponent(invoiceReminderSubject(item))}&body=${encodeURIComponent(renderInvoiceReminderText(item, settings))}`;
    window.location.href = mailtoUrl;

    const response = await fetch(`${apiUrl}/invoices/${item.id}/reminder-draft`, {
      method: "POST",
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, "Could not save reminder status."));
      return;
    }

    const updatedInvoice = await response.json();
    setInvoices((current) => current.map((invoiceItem) => (
      invoiceItem.id === updatedInvoice.id ? updatedInvoice : invoiceItem
    )));
  }

  async function sendInvoiceReminderEmail(item) {
    setError("");

    const response = await fetch(`${apiUrl}/invoices/${item.id}/reminder-email`, {
      method: "POST",
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, "Could not send reminder email."));
      return;
    }

    const updatedInvoice = await response.json();
    setInvoices((current) => current.map((invoiceItem) => (
      invoiceItem.id === updatedInvoice.id ? updatedInvoice : invoiceItem
    )));
  }

  async function sendInvoiceEmail(item) {
    setError("");

    const response = await fetch(`${apiUrl}/invoices/${item.id}/email`, {
      method: "POST",
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, language === "sv" ? "Kunde inte skicka fakturan via e-post." : "Could not send invoice email."));
      return;
    }

    const updatedInvoice = await response.json();
    setInvoices((current) => current.map((invoiceItem) => (
      invoiceItem.id === updatedInvoice.id ? updatedInvoice : invoiceItem
    )));
  }

  async function deleteInvoice(id) {
    setError("");

    const confirmed = window.confirm(
      language === "sv"
        ? "Ta bort fakturan och dess bokforingsrader? Anvand detta bara for test/gamla felaktiga fakturor."
        : "Delete the invoice and its bookkeeping rows? Use this only for test/old incorrect invoices."
    );

    if (!confirmed) {
      return;
    }

    const response = await fetch(`${apiUrl}/invoices/${id}`, {
      method: "DELETE",
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, "Could not delete invoice."));
      return;
    }

    setInvoices((current) => current.filter((item) => item.id !== id));
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  async function createCreditInvoice(id) {
    setError("");

    const confirmed = window.confirm(language === "sv" ? "Skapa kreditfaktura?" : "Create credit invoice?");

    if (!confirmed) {
      return;
    }

    const response = await fetch(`${apiUrl}/invoices/${id}/credit`, {
      method: "POST",
      headers: authHeaders()
    });
    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Could not create credit invoice."));
      return;
    }

    loadInvoices();
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
    setInvoiceSearch(invoiceNumber(data));
  }

  async function downloadJournalCsv() {
    setError("");

    const rows = [
      [
        "Verifikat",
        "Datum",
        "Typ",
        "Rattelse av",
        "Konto",
        "Kontonamn",
        "Beskrivning",
        "Debet",
        "Kredit"
      ]
    ];

    filteredJournalGroups.forEach((group) => {
      const voucherType = group.correctionOfVoucherNumber
        ? "Rattelse"
        : group.hasCorrection
          ? "Original med rattelse"
          : "Original";

      group.rows.forEach((entry) => {
        rows.push([
          group.voucherNumber,
          group.voucherDate || formatDateOnly(group.createdAt),
          voucherType,
          group.correctionOfVoucherNumber || "",
          entry.accountNumber || "",
          entry.accountName || "",
          entry.description || group.description || "",
          entry.debit || 0,
          entry.credit || 0
        ]);
      });
    });

    rows.push([]);
    rows.push(["Sammanfattning", "", "", "", "", "", "Verifikationer", filteredJournalGroups.length, ""]);
    rows.push(["Sammanfattning", "", "", "", "", "", "Debet", filteredJournalDebit, ""]);
    rows.push(["Sammanfattning", "", "", "", "", "", "Kredit", filteredJournalCredit, ""]);
    rows.push(["Sammanfattning", "", "", "", "", "", "Differens", filteredJournalDifference, ""]);

    rows.push([]);
    rows.push(["Kontosammanfattning", "", "", "", "Konto", "Kontonamn", "Saldo", "Debet", "Kredit"]);
    filteredJournalAccountSummary.forEach((account) => {
      rows.push([
        "Kontosammanfattning",
        "",
        "",
        "",
        account.accountNumber,
        account.accountName,
        account.debit - account.credit,
        account.debit,
        account.credit
      ]);
    });

    downloadLocalCsv("journal-entries-filtered.csv", rows);
  }

  function downloadActiveAccountLedgerCsv() {
    setError("");

    if (!bookkeepingAccountSearch) {
      setError(language === "sv" ? "Valj ett konto forst." : "Choose an account first.");
      return;
    }

    const accountName = activeBookkeepingAccount?.accountName || activeBookkeepingAccount?.name || "";
    const rows = [
      ["Konto", bookkeepingAccountSearch, accountName],
      ["Datum fran", bookkeepingDateFrom || "-", "Datum till", bookkeepingDateTo || "-"],
      [],
      ["Datum", "Verifikat", "Beskrivning", "Debet", "Kredit", "Lopande saldo"]
    ];

    if (bookkeepingDateFrom) {
      rows.push([
        bookkeepingDateFrom,
        "Ingaende",
        "Ingaende saldo fore perioden",
        0,
        0,
        activeAccountOpeningBalance
      ]);
    }

    activeAccountLedgerRows.forEach((row) => {
      rows.push([
        row.voucherDate || "",
        row.voucherNumber || "",
        row.description || "",
        row.debit || 0,
        row.credit || 0,
        row.balance || 0
      ]);
    });

    rows.push([]);
    rows.push([
      "Summa",
      "",
      "",
      activeAccountPeriodDebit,
      activeAccountPeriodCredit,
      activeAccountClosingBalance
    ]);

    downloadLocalCsv(`ledger-${bookkeepingAccountSearch}.csv`, rows);
  }

  function sieString(value) {
    return `"${String(value || "")
      .replaceAll("\\", "/")
      .replaceAll("\"", "'")
      .replace(/\r?\n/g, " ")
      .trim()}"`;
  }

  function sieDate(value) {
    const raw = String(value || "").slice(0, 10);
    if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
      return raw.replaceAll("-", "");
    }

    const parsed = new Date(value);
    if (!Number.isNaN(parsed.getTime())) {
      return dateInputString(parsed).replaceAll("-", "");
    }

    return todayInput.replaceAll("-", "");
  }

  function sieAmount(value) {
    return Number(value || 0).toFixed(2);
  }

  function downloadSieExport() {
    setError("");

    if (filteredJournalGroups.length === 0) {
      setError(language === "sv" ? "Det finns inga verifikationer att exportera." : "There are no vouchers to export.");
      return;
    }

    if (filteredJournalDifference !== 0) {
      setError(language === "sv"
        ? "SIE-export stoppad: valda verifikationer balanserar inte debet och kredit."
        : "SIE export stopped: selected vouchers do not balance debit and credit.");
      return;
    }

    const sortedGroupsForSie = [...filteredJournalGroups]
      .sort((first, second) => String(first.voucherDate || first.createdAt || "").localeCompare(String(second.voucherDate || second.createdAt || "")));
    const voucherDates = sortedGroupsForSie
      .map((group) => String(group.voucherDate || group.createdAt || "").slice(0, 10))
      .filter((date) => /^\d{4}-\d{2}-\d{2}$/.test(date))
      .sort();
    const periodStart = bookkeepingDateFrom || voucherDates[0] || `${todayInput.slice(0, 4)}-01-01`;
    const periodEnd = bookkeepingDateTo || voucherDates[voucherDates.length - 1] || `${todayInput.slice(0, 4)}-12-31`;
    const accountMap = new Map();
    const organizationNumber = settings?.organizationNumber || settings?.orgNumber || "";

    accounts.forEach((account) => {
      if (account.number) {
        accountMap.set(String(account.number), account.name || "");
      }
    });

    sortedGroupsForSie.forEach((group) => {
      group.rows.forEach((entry) => {
        if (entry.accountNumber && !accountMap.has(String(entry.accountNumber))) {
          accountMap.set(String(entry.accountNumber), entry.accountName || "");
        }
      });
    });

    const lines = [
      "#FLAGGA 0",
      `#PROGRAM ${sieString("AliBooks")} ${sieString("0.1")}`,
      "#SIETYP 4",
      "#FORMAT PC8",
      `#GEN ${sieDate(todayInput)}`,
      `#FNAMN ${sieString(settings?.companyName || "AliBooks")}`,
      `#RAR 0 ${sieDate(periodStart)} ${sieDate(periodEnd)}`,
      ""
    ];

    if (organizationNumber) {
      lines.splice(6, 0, `#ORGNR ${sieString(organizationNumber)}`);
    }

    Array.from(accountMap.entries())
      .sort(([first], [second]) => first.localeCompare(second, "sv-SE", { numeric: true }))
      .forEach(([number, name]) => {
        lines.push(`#KONTO ${number} ${sieString(name || number)}`);
      });

    lines.push("");

    sortedGroupsForSie.forEach((group) => {
      const voucherDate = sieDate(group.voucherDate || group.createdAt);
      const voucherText = group.correctionOfVoucherNumber
        ? `${group.description || group.voucherNumber || ""} (rattelse av ${group.correctionOfVoucherNumber})`
        : group.description || group.voucherNumber || "";

      lines.push(`#VER ${sieString("A")} ${sieString(group.voucherNumber || "Utan verifikat")} ${voucherDate} ${sieString(voucherText)}`);
      lines.push("{");
      group.rows.forEach((entry) => {
        const amount = (entry.debit || 0) - (entry.credit || 0);
        lines.push(`  #TRANS ${entry.accountNumber || "0000"} {} ${sieAmount(amount)} ${sieString(entry.description || group.description || "")}`);
      });
      lines.push("}");
      lines.push("");
    });

    const safePeriod = `${periodStart || "start"}_${periodEnd || "slut"}`.replaceAll("-", "");
    downloadLocalText(`alibooks-sie-${safePeriod}.se`, lines.join("\r\n"), "text/plain;charset=cp437");
  }

  async function downloadCsv(path, filename, fallbackMessage) {
    const response = await fetch(`${apiUrl}${path}`, {
      headers: authHeaders()
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      setError(apiErrorMessage(data, fallbackMessage));
      return;
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  function downloadLocalCsv(filename, rows) {
    const csv = rows.map((row) => row.map((value) => {
      const text = String(value ?? "");
      return `"${text.replaceAll("\"", "\"\"")}"`;
    }).join(";")).join("\r\n");
    const blob = new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  function downloadLocalText(filename, text, type = "text/plain;charset=utf-8") {
    const blob = new Blob([text], { type });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  function bokioImportKind(fileName = "", fileType = "") {
    const lowerName = fileName.toLowerCase();
    const lowerType = fileType.toLowerCase();

    if (lowerName.endsWith(".sie") || lowerName.endsWith(".se")) return "SIE";
    if (lowerName.endsWith(".csv") || lowerType.includes("csv")) return "CSV";
    if (lowerName.endsWith(".zip") || lowerType.includes("zip")) return "ZIP";
    if (lowerName.endsWith(".pdf") || lowerType.includes("pdf")) return "PDF";
    if (lowerType.startsWith("image/")) return language === "sv" ? "Bild" : "Image";
    return language === "sv" ? "Okand fil" : "Unknown file";
  }

  function bokioImportFileSize(bytes = 0) {
    if (!bytes) return "0 KB";
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  function parseSieAmount(value = "") {
    return Number(String(value).replace(",", ".").replace(/[^\d.-]/g, "")) || 0;
  }

  function normalizeSieDate(value = "") {
    if (!/^\d{8}$/.test(value)) return "";
    return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`;
  }

  function analyzeSieText(text = "") {
    const lines = text.split(/\r?\n/);
    const vouchers = [];
    const accounts = new Set();
    let currentVoucher = null;

    function closeVoucher() {
      if (!currentVoucher) return;
      const difference = Math.round(currentVoucher.sum * 100) / 100;
      vouchers.push({
        ...currentVoucher,
        difference,
        simpleManualImport: currentVoucher.transactionsList.length === 2
          && Math.abs(difference) <= 0.01
          && currentVoucher.transactionsList.some((line) => line.debit > 0)
          && currentVoucher.transactionsList.some((line) => line.credit > 0)
      });
      currentVoucher = null;
    }

    lines.forEach((rawLine) => {
      const line = rawLine.trim();

      if (line.startsWith("#VER ")) {
        closeVoucher();
        const dateMatch = line.match(/\s(\d{8})(?:\s|$)/);
        const quotedValues = [...line.matchAll(/"([^"]*)"/g)].map((match) => match[1]);
        currentVoucher = {
          key: `${vouchers.length + 1}-${dateMatch?.[1] || "nodate"}`,
          date: normalizeSieDate(dateMatch?.[1] || ""),
          number: quotedValues[1] || String(vouchers.length + 1),
          description: quotedValues[2] || quotedValues[quotedValues.length - 1] || "SIE-verifikat",
          transactions: 0,
          transactionsList: [],
          sum: 0
        };
        return;
      }

      if (line.startsWith("#TRANS ") || line.startsWith("#RTRANS ") || line.startsWith("#BTRANS ")) {
        const match = line.match(/^#(?:R|B)?TRANS\s+(\d+)\s+(?:\{[^}]*\}\s+)?(-?\d+(?:[.,]\d+)?)/);
        if (!match) return;

        if (!currentVoucher) {
          currentVoucher = {
            key: `${vouchers.length + 1}-nodate`,
            date: "",
            number: String(vouchers.length + 1),
            description: "SIE-verifikat",
            transactions: 0,
            transactionsList: [],
            sum: 0
          };
        }

        const amount = parseSieAmount(match[2]);
        const quotedValues = [...line.matchAll(/"([^"]*)"/g)].map((quoteMatch) => quoteMatch[1]);
        accounts.add(match[1]);
        currentVoucher.transactions += 1;
        currentVoucher.sum += amount;
        currentVoucher.transactionsList.push({
          account: match[1],
          debit: amount > 0 ? Math.round(amount) : 0,
          credit: amount < 0 ? Math.round(Math.abs(amount)) : 0,
          amount,
          description: quotedValues[quotedValues.length - 1] || ""
        });
      }
    });

    closeVoucher();

    const dates = vouchers.map((voucher) => voucher.date).filter(Boolean).sort();
    const totalTransactions = vouchers.reduce((sum, voucher) => sum + voucher.transactions, 0);
    const totalDifference = vouchers.reduce((sum, voucher) => sum + voucher.difference, 0);
    const unbalancedVouchers = vouchers.filter((voucher) => Math.abs(voucher.difference) > 0.01).length;
    const accountList = [...accounts].sort((first, second) => Number(first) - Number(second));

    return {
      kind: "SIE",
      vouchers: vouchers.length,
      transactions: totalTransactions,
      accounts: accountList.length,
      sampleAccounts: accountList.slice(0, 8).join(", "),
      unbalancedVouchers,
      totalDifference: Math.round(totalDifference * 100) / 100,
      firstDate: dates[0] || "",
      lastDate: dates[dates.length - 1] || "",
      previewVouchers: vouchers.slice(0, 25).map((voucher, index) => ({
        ...voucher,
        key: voucher.key || `${index + 1}-${voucher.date || "nodate"}`
      }))
    };
  }

  function analyzeCsvText(text = "") {
    const rows = text.split(/\r?\n/).filter((line) => line.trim());
    const separator = (rows[0]?.match(/;/g)?.length || 0) >= (rows[0]?.match(/,/g)?.length || 0) ? ";" : ",";
    const header = rows[0] ? splitCsvLine(rows[0], separator) : [];
    const amountColumn = header.find((column) => /belopp|amount|summa|total/i.test(column));
    const dateColumn = header.find((column) => /datum|date/i.test(column));

    return {
      kind: "CSV",
      rows: Math.max(rows.length - 1, 0),
      columns: header.length,
      separator,
      amountColumn: amountColumn || "",
      dateColumn: dateColumn || ""
    };
  }

  async function analyzeBokioImportFile(file, kind) {
    if (!["SIE", "CSV"].includes(kind)) return null;

    try {
      const text = await file.text();
      return kind === "SIE" ? analyzeSieText(text) : analyzeCsvText(text);
    } catch {
      return {
        kind,
        error: language === "sv" ? "Kunde inte lasa filen." : "Could not read file."
      };
    }
  }

  async function handleBokioImportFiles(event) {
    const files = Array.from(event.target.files || []);

    if (files.length === 0) return;

    const importedAt = new Date().toISOString();
    const newRows = await Promise.all(files.map(async (file, index) => {
      const kind = bokioImportKind(file.name, file.type);
      const analysis = await analyzeBokioImportFile(file, kind);

      return {
        id: `${Date.now()}-${index}-${file.name}`,
        fileName: file.name,
        fileType: file.type || "-",
        kind,
        size: file.size || 0,
        lastModified: file.lastModified ? new Date(file.lastModified).toISOString().slice(0, 10) : "",
        importedAt,
        status: "review",
        expenseDate: file.lastModified ? new Date(file.lastModified).toISOString().slice(0, 10) : new Date().toISOString().slice(0, 10),
        description: `Bokio: ${file.name.replace(/\.[^/.]+$/, "").replaceAll("_", " ")}`,
        netAmount: "",
        vatAmount: "",
        category: "5420",
        analysis,
        note: analysis?.kind === "SIE"
          ? (language === "sv" ? "SIE kontrollerad. Importera verifikat i nasta steg." : "SIE checked. Import vouchers in the next step.")
          : ""
      };
    }));

    setBokioImportFiles((current) => files.reduce((nextFiles, file, index) => ({
      ...nextFiles,
      [newRows[index].id]: file
    }), { ...current }));
    setBokioImportQueue((current) => [...newRows, ...current]);
    setBokioImportMessage(language === "sv"
      ? `${newRows.length} fil(er) lades i importkon. Granska innan du bokfor.`
      : `${newRows.length} file(s) were added to the import queue. Review before bookkeeping.`);
    event.target.value = "";
  }

  function updateBokioImportRow(rowId, patch) {
    setBokioImportQueue((current) => current.map((row) => (
      row.id === rowId ? { ...row, ...patch } : row
    )));
  }

  function removeBokioImportRow(rowId) {
    setBokioImportQueue((current) => current.filter((row) => row.id !== rowId));
    setBokioImportFiles((current) => {
      const nextFiles = { ...current };
      delete nextFiles[rowId];
      return nextFiles;
    });
  }

  function clearReviewedBokioImports() {
    const importedIds = new Set(bokioImportQueue.filter((row) => row.status === "imported").map((row) => row.id));
    setBokioImportQueue((current) => current.filter((row) => row.status !== "imported"));
    setBokioImportFiles((current) => Object.fromEntries(
      Object.entries(current).filter(([rowId]) => !importedIds.has(rowId))
    ));
    setBokioImportMessage(language === "sv"
      ? "Importerade rader doljs fran importkon."
      : "Imported rows are hidden from the import queue.");
  }

  function bokioImportCanAttachReceipt(row) {
    return ["PDF", "Bild", "Image"].includes(row.kind) && Boolean(bokioImportFiles[row.id]);
  }

  function knownAccount(accountNumber) {
    return accounts.some((account) => account.number === accountNumber);
  }

  function sieVoucherImportStatus(voucher) {
    if (!voucher?.simpleManualImport) {
      return {
        ok: false,
        reason: language === "sv" ? "Kraver flerradig SIE-import." : "Requires multi-line SIE import."
      };
    }

    const debitLine = voucher.transactionsList.find((line) => line.debit > 0);
    const creditLine = voucher.transactionsList.find((line) => line.credit > 0);

    if (!debitLine || !creditLine || debitLine.debit !== creditLine.credit) {
      return {
        ok: false,
        reason: language === "sv" ? "Debet och kredit matchar inte." : "Debit and credit do not match."
      };
    }

    if (!knownAccount(debitLine.account) || !knownAccount(creditLine.account)) {
      return {
        ok: false,
        reason: language === "sv" ? "Konto saknas i AliBooks kontoplan." : "Account is missing in AliBooks chart of accounts."
      };
    }

    if (isAccountingDateLocked(voucher.date)) {
      return {
        ok: false,
        reason: lockedAccountingMessage(voucher.date)
      };
    }

    return { ok: true, debitLine, creditLine, amount: debitLine.debit };
  }

  function updateBokioImportVoucher(rowId, voucherKey, patch) {
    setBokioImportQueue((current) => current.map((row) => {
      if (row.id !== rowId || !row.analysis?.previewVouchers) return row;

      return {
        ...row,
        analysis: {
          ...row.analysis,
          previewVouchers: row.analysis.previewVouchers.map((voucher) => (
            voucher.key === voucherKey ? { ...voucher, ...patch } : voucher
          ))
        }
      };
    }));
  }

  async function createManualVoucherFromSie(row, voucher) {
    setError("");

    const importStatus = sieVoucherImportStatus(voucher);

    if (!importStatus.ok) {
      setError(importStatus.reason);
      return;
    }

    const response = await fetch(`${apiUrl}/journal-entries/manual`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({
        voucherDate: voucher.date || new Date().toISOString().slice(0, 10),
        description: `Bokio SIE ${row.fileName}: ${voucher.description || voucher.number || ""}`.trim(),
        debitAccountNumber: importStatus.debitLine.account,
        creditAccountNumber: importStatus.creditLine.account,
        amount: importStatus.amount
      })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, language === "sv" ? "Kunde inte skapa verifikat fran SIE." : "Could not create voucher from SIE."));
      return;
    }

    const voucherNumber = data?.[0]?.voucherNumber || "";
    updateBokioImportVoucher(row.id, voucher.key, {
      importedVoucherNumber: voucherNumber || (language === "sv" ? "Importerad" : "Imported")
    });
    setBokioImportMessage(language === "sv"
      ? `SIE-verifikat importerades${voucherNumber ? ` som ${voucherNumber}` : ""}.`
      : `SIE voucher imported${voucherNumber ? ` as ${voucherNumber}` : ""}.`);
    loadJournalEntries();
    loadVatReport();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  async function createExpenseFromBokioImport(row) {
    setError("");

    const netAmount = Number(row.netAmount || 0);
    const vatAmount = Number(row.vatAmount || 0);
    const expenseDateValue = row.expenseDate || new Date().toISOString().slice(0, 10);

    if (isAccountingDateLocked(expenseDateValue)) {
      setError(lockedAccountingMessage(expenseDateValue));
      return;
    }

    if (!row.description || !row.description.trim()) {
      setError(language === "sv" ? "Beskrivning saknas pa Bokio-raden." : "Description is missing on the Bokio row.");
      return;
    }

    if (netAmount <= 0) {
      setError(language === "sv" ? "Fyll i exkl. moms innan du skapar kostnaden." : "Enter net amount before creating the expense.");
      return;
    }

    const response = await fetch(`${apiUrl}/expenses`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({
        expenseDate: expenseDateValue,
        description: row.description,
        netAmount,
        vatAmount: Math.max(vatAmount, 0),
        category: row.category || "5420",
        paidFrom: "1930"
      })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, language === "sv" ? "Kunde inte skapa kostnad fran Bokio-import." : "Could not create expense from Bokio import."));
      return;
    }

    let savedExpense = data;
    const receiptFile = bokioImportFiles[row.id];

    if (receiptFile && bokioImportCanAttachReceipt(row)) {
      const uploadedExpense = await uploadExpenseReceipt(data.id, receiptFile);
      savedExpense = uploadedExpense || data;
    }

    setExpenses((current) => [...current, savedExpense]);
    updateBokioImportRow(row.id, {
      status: "imported",
      importedExpenseId: savedExpense.id,
      note: row.note || (language === "sv" ? `Kostnad skapad: ${savedExpense.id}` : `Expense created: ${savedExpense.id}`)
    });
    setBokioImportMessage(language === "sv"
      ? `Kostnad skapad fran ${row.fileName}.`
      : `Expense created from ${row.fileName}.`);
    loadJournalEntries();
    loadVatReport();
    loadReminders();
    loadAdvisorSummary();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  function downloadBokioImportQueueCsv() {
    const rows = [
      ["Filnamn", "Typ", "Storlek", "Filens datum", "Tillagd", "Status", "Analys", "Datum", "Beskrivning", "Netto", "Moms", "Kategori", "Kostnad ID", "Anteckning"],
      ...bokioImportQueue.map((row) => [
        row.fileName,
        row.kind,
        bokioImportFileSize(row.size),
        row.lastModified || "",
        String(row.importedAt || "").slice(0, 10),
        row.status,
        row.analysis
          ? (row.analysis.error || (row.analysis.kind === "SIE"
            ? `${row.analysis.vouchers} verifikat, ${row.analysis.transactions} rader, ${row.analysis.unbalancedVouchers} obalanserade`
            : `${row.analysis.rows} rader, ${row.analysis.columns} kolumner`))
          : "",
        row.expenseDate || "",
        row.description || "",
        row.netAmount || "",
        row.vatAmount || "",
        row.category || "",
        row.importedExpenseId || "",
        row.note || ""
      ])
    ];

    downloadLocalCsv("bokio-import-checklista.csv", rows);
  }

  function downloadBokioSiePreviewCsv(row) {
    const vouchers = row.analysis?.previewVouchers || [];
    const rows = [
      ["Fil", "Verifikat", "Datum", "Beskrivning", "Konto", "Debet", "Kredit", "Radtext", "Status"],
      ...vouchers.flatMap((voucher) => voucher.transactionsList.map((line) => [
        row.fileName,
        voucher.number || voucher.key,
        voucher.date || "",
        voucher.description || "",
        line.account,
        line.debit || 0,
        line.credit || 0,
        line.description || "",
        voucher.importedVoucherNumber || ""
      ]))
    ];

    downloadLocalCsv(`sie-preview-${row.fileName.replace(/[^A-Za-z0-9._-]/g, "_")}.csv`, rows);
  }

  function downloadLocalJson(filename, data) {
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  function downloadBankImportExampleCsv() {
    const exampleInvoice = invoices.find((item) => invoiceRemainingAmount(item) > 0);
    const exampleAmount = exampleInvoice ? invoiceRemainingAmount(exampleInvoice) : 999;
    const exampleReference = exampleInvoice?.ocrNumber || exampleInvoice?.invoiceNumber || "1055065900139";
    const exampleCustomer = exampleInvoice?.customerName || "Exempel Kund";

    downloadLocalCsv("bankimport-exempel.csv", [
      ["Datum", "Beskrivning", "Referens", "Belopp"],
      [new Date().toISOString().slice(0, 10), `Inbetalning ${exampleCustomer}`, exampleReference, exampleAmount],
      [new Date().toISOString().slice(0, 10), "Bankavgift", "BANKAVGIFT", -49]
    ]);
  }

  async function downloadProfitAndLossCsv() {
    setError("");
    await downloadCsv("/profit-and-loss/export", "profit-and-loss.csv", "Could not export profit and loss report.");
  }

  async function downloadBalanceReportCsv() {
    setError("");
    await downloadCsv("/balance-report/export", "balance-report.csv", "Could not export balance report.");
  }

  async function downloadCustomersCsv() {
    setError("");
    await downloadCsv("/customers/export", "customers.csv", "Could not export customers.");
  }

  function downloadSelectedCustomerCsv() {
    if (!selectedCustomer) return;

    setError("");
    const rows = [
      ["AliBooks kundkort"],
      ["Kund", selectedCustomer.name || ""],
      ["E-post", selectedCustomer.email || ""],
      ["Personnummer", selectedCustomer.personalNumber || ""],
      ["Telefon", selectedCustomer.phone || ""],
      ["Adress", `${selectedCustomer.address || ""} ${selectedCustomer.postalCode || ""} ${selectedCustomer.city || ""}`.trim()],
      ["Status", selectedCustomer.archived ? "Arkiverad" : "Aktiv"],
      [],
      ["Sammanfattning"],
      ["Fakturor", selectedCustomerInvoices.length],
      ["Betalda", selectedCustomerPaidInvoices.length],
      ["Obetalda", selectedCustomerUnpaidInvoices.length],
      ["Fakturerat", selectedCustomerTotal],
      ["Betalt", selectedCustomerPaidTotal],
      ["Kvar att betala", selectedCustomerOutstanding],
      ["Forfallet", selectedCustomerOverdueAmount],
      [],
      ["Fakturor"],
      ["Fakturanummer", "Datum", "Forfallodatum", "Status", "Total", "Betalt", "Kvar", "OCR", "Senaste paminnelse"]
    ];

    selectedCustomerInvoices.forEach((item) => {
      const latestReminder = (item.reminders || []).slice().sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")))[0];
      rows.push([
        invoiceNumber(item),
        item.invoiceDate || String(item.createdAt || "").slice(0, 10),
        item.dueDate || "",
        statusLabel(item.status, language),
        invoiceTotalAmount(item),
        invoicePaidAmount(item),
        invoiceRemainingAmount(item),
        item.ocrNumber || "",
        latestReminder ? formatDateOnly(latestReminder.createdAt) : ""
      ]);
    });

    downloadLocalCsv(`customer-${selectedCustomer.id || "selected"}.csv`, rows);
  }

  function downloadCustomerLedgerCsv() {
    setError("");
    const rows = [
      ["Kund", "E-post", "Personnummer", "Aktiv/Arkiverad", "Fakturor", "Obetalda fakturor", "Fakturerat totalt", "Utestaende"]
    ];

    [...customers].sort((first, second) => compareCustomers(first, second, invoices)).forEach((customer) => {
      const customerInvoiceList = customerInvoices(customer, invoices);
      rows.push([
        customer.name || "",
        customer.email || "",
        customer.personalNumber || "",
        customer.archived ? "Arkiverad" : "Aktiv",
        customerInvoiceList.length,
        customerUnpaidInvoiceCount(customer, invoices),
        customerInvoiceList.reduce((sum, item) => sum + invoiceTotalAmount(item), 0),
        customerOutstandingAmount(customer, invoices)
      ]);
    });

    downloadLocalCsv("customer-ledger.csv", rows);
  }

  function downloadAgingReportCsv() {
    setError("");
    const rows = [
      ["Grupp", "Fakturanummer", "Kund", "Fakturadatum", "Forfallodatum", "Dagar till/sedan forfall", "Status", "Total", "Betalt", "Kvar att betala"]
    ];

    agingBuckets.forEach((bucket) => {
      bucket.items.forEach((item) => {
        rows.push([
          bucket.title,
          invoiceNumber(item),
          item.customerName || item.customer?.name || "",
          formatDateOnly(item.createdAt),
          formatDateOnly(item.dueDate),
          invoiceDaysUntilDue(item) ?? "",
          statusLabel(item.status, language),
          invoiceTotalAmount(item),
          invoicePaidAmount(item),
          invoiceRemainingAmount(item)
        ]);
      });
    });

    downloadLocalCsv("aging-report.csv", rows);
  }

  function downloadReceivablesReportCsv() {
    setError("");
    const rows = [
      ["Kund", "E-post", "Personnummer", "Fakturor", "Betalda", "Oppna", "Forfallna", "Fakturerat", "Betalt", "Kvar att betala", "Forfallet belopp", "Senaste faktura", "Senaste fakturadatum", "Senaste forfallodatum"]
    ];

    filteredCustomerLedgerRows.forEach((row) => {
      rows.push([
        row.name || "",
        row.email || "",
        row.personalNumber || "",
        row.invoiceCount,
        row.paidCount,
        row.openCount,
        row.overdueCount,
        row.invoicedTotal,
        row.paidTotal,
        row.outstandingTotal,
        row.overdueTotal,
        row.latestInvoiceNumber || "",
        row.latestInvoiceDate || "",
        row.latestDueDate || ""
      ]);
    });

    downloadLocalCsv("customer-receivables.csv", rows);
  }

  function downloadMonthlyReportCsv() {
    setError("");
    const rows = [
      ["Manad", "Intakter", "Kostnader", "Resultat", "Utgaende moms", "Ingaende moms", "Moms att betala", "Kassa in", "Kassa ut", "Kassaforandring", "Bokforingsrader"]
    ];

    monthlyReportRows.forEach((row) => {
      rows.push([
        formatMonthLabel(row.monthKey, language),
        row.revenue,
        row.expenses,
        row.result,
        row.outputVat,
        row.inputVat,
        row.vatToPay,
        row.cashIn,
        row.cashOut,
        row.cashChange,
        row.voucherCount
      ]);
    });

    rows.push(
      [],
      ["Totalt", monthlyReportRevenueTotal, monthlyReportExpenseTotal, monthlyReportResultTotal, "", "", monthlyReportVatToPayTotal, "", "", monthlyReportCashChangeTotal, ""]
    );

    downloadLocalCsv("monthly-report.csv", rows);
  }

  function downloadMonthlyCloseCsv() {
    setError("");
    const rows = [
      ["AliBooks manadsavslut"],
      ["Manad", formatMonthLabel(monthCloseSelectedMonth, language)],
      ["Period", `${monthlyCloseRange.from || "-"} - ${monthlyCloseRange.to || "-"}`],
      ["Status", monthlyCloseStatusText],
      ["Klarhet", `${monthlyCloseReadiness}%`],
      [],
      ["Nyckeltal", "Belopp"],
      ["Intakter", monthlyCloseRow.revenue],
      ["Kostnader", monthlyCloseRow.expenses],
      ["Resultat", monthlyCloseRow.result],
      ["Moms att betala", monthlyCloseRow.vatToPay],
      ["Kassaforandring", monthlyCloseRow.cashChange],
      ["Verifikationsrader", monthlyCloseRow.voucherCount],
      [],
      ["Kontroll", "Status", "Detalj"]
    ];

    monthlyCloseChecklist.forEach((item) => {
      rows.push([item.title, item.status, item.detail]);
    });

    downloadLocalCsv(`monthly-close-${monthCloseSelectedMonth || "month"}.csv`, rows);
  }

  function downloadCloseChecklistCsv() {
    setError("");
    const rows = [
      ["Bokslutsstatus", `${closeReadinessScore}%`],
      ["Varningar", closeChecklistWarnings],
      ["Kontroll", "Status", "Varde"]
    ];

    closeChecklistItems.forEach((item) => {
      rows.push([
        item.title,
        item.status,
        item.value
      ]);
    });

    rows.push(
      [],
      ["Resultat", profitNet],
      ["Moms vald period", vatPeriodToPay],
      ["Utestaende kundfordringar", totalOutstanding],
      ["Saknade underlag", expensesMissingReceipt.length],
      ["Obalanserade verifikat", unbalancedJournalGroups.length],
      ["Last period till", accountingLockedThroughDate || "-"]
    );

    downloadLocalCsv("close-checklist.csv", rows);
  }

  function downloadTaxPlanCsv() {
    setError("");
    const rows = [
      ["AliBooks skatteplan"],
      ["Period", archivePeriodLabel],
      ["Skapad", todayInput],
      ["OBS", "Preliminara planeringsdatum. Kontrollera alltid exakta datum hos Skatteverket."],
      [],
      ["Moment", "Status", "Datum", "Dagar kvar", "Detalj"]
    ];

    taxPlanItems.forEach((item) => {
      rows.push([
        item.title,
        item.displayStatus,
        item.deadline || "-",
        item.daysUntil === null ? "-" : item.daysUntil,
        item.detail
      ]);
    });

    rows.push(
      [],
      ["Varningar", taxPlanWarnings],
      ["Kommande inom 30 dagar", taxPlanDueSoonCount],
      ["Passerade datum", taxPlanOverdueCount]
    );

    downloadLocalCsv("tax-plan.csv", rows);
  }

  function downloadArchiveManifestCsv() {
    setError("");
    const rows = [
      ["AliBooks arkivpaket"],
      ["Period", archivePeriodLabel],
      ["Skapad", new Date().toISOString().slice(0, 10)],
      ["Bokslutsstatus", `${closeReadinessScore}%`],
      ["Varningar", closeChecklistWarnings],
      [],
      ["Del", "Status", "Antal/Belopp", "Beskrivning"]
    ];

    archivePackageItems.forEach((item) => {
      rows.push([
        item.title,
        item.status,
        item.count,
        item.description
      ]);
    });

    rows.push(
      [],
      ["Kontrollrad", "Spara fakturor, kvitton, verifikationer, grundbok/huvudbok och rapporter tillsammans."],
      ["Kontrollrad", "Bokforing och underlag ska normalt sparas i 7 ar efter rakenskapsarets slut."],
      ["Kontrollrad", "Kontrollera alltid slutliga moms- och deklarationsdatum hos Skatteverket."]
    );

    downloadLocalCsv("archive-manifest.csv", rows);
  }

  function downloadAuditTrailCsv() {
    setError("");
    const rows = [
      ["Handelselogg / Audit trail"],
      ["Filter", auditTrailFilter],
      ["Sokning", auditTrailSearch || "-"],
      ["Antal", filteredAuditTrailRows.length],
      [],
      ["Datum", "Typ", "Handelse", "Referens", "Part", "Belopp", "Status", "Skapad"]
    ];

    filteredAuditTrailRows.forEach((row) => {
      rows.push([
        row.date || "",
        row.typeLabel,
        row.title,
        row.reference || "",
        row.party || "",
        row.amount || 0,
        row.status || "",
        row.createdAt ? formatDateTime(row.createdAt) : ""
      ]);
    });

    downloadLocalCsv("audit-trail.csv", rows);
  }

  function downloadActivityCsv() {
    setError("");
    const rows = [
      ["Affarshandelser / Business events"],
      ["Filter", activityFilter],
      ["Sokning", activitySearch || "-"],
      ["Antal", filteredActivityRows.length],
      ["Intaktsflode", activityIncomeTotal],
      ["Kostnadsflode", activityExpenseTotal],
      [],
      ["Datum", "Typ", "Handelse", "Referens", "Part/Beskrivning", "Belopp", "Status", "Skapad"]
    ];

    filteredActivityRows.forEach((row) => {
      rows.push([
        row.date || "",
        row.typeLabel,
        row.title,
        row.reference || "",
        row.party || "",
        row.amount || 0,
        row.status || "",
        row.createdAt ? formatDateTime(row.createdAt) : ""
      ]);
    });

    downloadLocalCsv("affarshandelser.csv", rows);
  }

  function downloadCashflowCsv() {
    setError("");
    const rows = [
      ["Likviditetsprognos / Cashflow forecast"],
      ["Fran", todayInput],
      ["Till", cashflowEndDate],
      ["Horisont dagar", cashflowHorizonDays],
      ["Startkassa 1930", cashOpeningBalance],
      ["Vantat in", cashflowExpectedIn],
      ["Vantat ut", cashflowExpectedOut],
      ["Prognos kassa", cashflowProjectedBalance],
      ["Kostnadssnitt per manad", averageMonthlyCashOut],
      [],
      ["Prognos per period"],
      ["Dagar", "Till datum", "Fakturor in", "Stripe in", "Kostnadsreserv", "Moms ut", "Prognos kassa"]
    ];

    cashflowProjectionRows.forEach((row) => {
      rows.push([
        row.days,
        row.endDate,
        row.invoiceIn,
        row.stripeIn,
        row.reserve,
        row.vatOut,
        row.projectedBalance
      ]);
    });

    rows.push([]);
    rows.push(["Detaljer"]);
    rows.push(["Datum", "Typ", "Kalla", "Rubrik", "Detalj", "Belopp", "Status"]);

    cashflowTimelineRows.forEach((row) => {
      rows.push([
        row.date,
        row.type === "in" ? "In" : "Ut",
        row.source,
        row.title,
        row.detail,
        row.amount,
        row.status
      ]);
    });

    downloadLocalCsv("likviditetsprognos.csv", rows);
  }

  function downloadBudgetCsv() {
    setError("");
    const rows = [
      ["Budget och mal / Budget and goals"],
      ["Intaktsmal per manad", budgetMonthlyRevenueTarget],
      ["Kostnadstak per manad", budgetMonthlyExpenseLimit],
      ["Reservprocent", budgetReserveRate],
      ["Perioder", budgetRows.length],
      [],
      ["Manad", "Intakter", "Intaktsmal", "Intaktsavvikelse", "Kostnader", "Kostnadstak", "Kostnadsutrymme", "Resultat", "Reserv", "Efter reserv", "Status"]
    ];

    budgetRows.forEach((row) => {
      rows.push([
        formatMonthLabel(row.monthKey, language),
        row.revenue,
        budgetMonthlyRevenueTarget,
        row.revenueVariance,
        row.expenses,
        budgetMonthlyExpenseLimit,
        row.expenseRoom,
        row.result,
        row.reserve,
        row.afterReserve,
        row.statusLabel
      ]);
    });

    downloadLocalCsv("budget-mal.csv", rows);
  }

  function payrollCalculationFromValues(grossSalary, taxRate, employerRate) {
    const gross = Math.round(Number(grossSalary || 0));
    const tax = Math.max(0, Number(taxRate || 0));
    const employer = Math.max(0, Number(employerRate || 0));
    const withheldTax = Math.round(gross * tax / 100);
    const employerFee = Math.round(gross * employer / 100);
    const netPay = Math.max(0, gross - withheldTax);

    return {
      gross,
      withheldTax,
      employerFee,
      netPay,
      totalCost: gross + employerFee
    };
  }

  function payrollDraftCalculation(draft) {
    return payrollCalculationFromValues(draft.grossSalary, draft.taxRate, draft.employerRate);
  }

  function currentPayrollCalculation() {
    return payrollCalculationFromValues(payrollGrossSalary, payrollTaxRate, payrollEmployerRate);
  }

  function handleSavePayrollDraft(event) {
    event.preventDefault();
    setError("");

    const calculation = currentPayrollCalculation();

    if (!payrollEmployeeName.trim()) {
      setError(language === "sv" ? "Namn pa anstalld saknas." : "Employee name is missing.");
      return;
    }

    if (!payrollPeriod) {
      setError(language === "sv" ? "Loneperiod saknas." : "Payroll period is missing.");
      return;
    }

    if (calculation.gross <= 0) {
      setError(language === "sv" ? "Bruttolon maste vara storre an 0." : "Gross salary must be greater than 0.");
      return;
    }

    const draft = {
      id: Date.now(),
      employeeName: payrollEmployeeName.trim(),
      personalNumber: payrollEmployeePersonalNumber.trim(),
      period: payrollPeriod,
      grossSalary: calculation.gross,
      taxRate: Number(payrollTaxRate || 0),
      employerRate: Number(payrollEmployerRate || 0),
      salaryAccount: payrollSalaryAccount || "7010",
      status: "draft",
      createdAt: new Date().toISOString()
    };

    setPayrollDrafts((current) => [draft, ...current]);
    setPayrollEmployeeName("");
    setPayrollEmployeePersonalNumber("");
    setPayrollGrossSalary("");
    setPayrollMessage(language === "sv" ? "Loneutkast sparat." : "Payroll draft saved.");
  }

  function deletePayrollDraft(draftId) {
    setPayrollDrafts((current) => current.filter((draft) => draft.id !== draftId));
  }

  async function bookPayrollDraft(draft) {
    setError("");
    const calculation = payrollDraftCalculation(draft);
    const voucherDate = `${draft.period || new Date().toISOString().slice(0, 7)}-25`;

    if (isAccountingDateLocked(voucherDate)) {
      setError(lockedAccountingMessage(voucherDate));
      return;
    }

    const lines = [
      { accountNumber: draft.salaryAccount || "7010", debit: calculation.gross, credit: 0 },
      { accountNumber: "7510", debit: calculation.employerFee, credit: 0 },
      { accountNumber: "2710", debit: 0, credit: calculation.withheldTax },
      { accountNumber: "2731", debit: 0, credit: calculation.employerFee },
      { accountNumber: "1930", debit: 0, credit: calculation.netPay }
    ].filter((line) => line.debit > 0 || line.credit > 0);

    const response = await fetch(`${apiUrl}/journal-entries/manual-multi`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders()
      },
      body: JSON.stringify({
        voucherDate,
        description: `Lon ${draft.employeeName} ${draft.period}`,
        lines
      })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, language === "sv" ? "Kunde inte bokfora loneutkast." : "Could not book payroll draft."));
      return;
    }

    const voucherNumber = data?.[0]?.voucherNumber || "";
    setPayrollDrafts((current) => current.map((item) => (
      item.id === draft.id ? { ...item, status: "booked", voucherNumber } : item
    )));
    setPayrollMessage(language === "sv"
      ? `Lonen bokfordes${voucherNumber ? ` som ${voucherNumber}` : ""}.`
      : `Payroll was booked${voucherNumber ? ` as ${voucherNumber}` : ""}.`);
    loadJournalEntries();
    loadProfitAndLoss();
    loadBalanceReport();
  }

  function downloadPayrollCsv() {
    setError("");
    const rows = [
      ["Lon / Payroll"],
      ["Preliminar skatt %", payrollTaxRate],
      ["Arbetsgivaravgift %", payrollEmployerRate],
      [],
      ["Period", "Anstalld", "Personnummer", "Brutto", "Preliminar skatt", "Nettolon", "Arbetsgivaravgift", "Total kostnad", "Status", "Verifikat"]
    ];

    payrollDrafts.forEach((draft) => {
      const calculation = payrollDraftCalculation(draft);
      rows.push([
        draft.period,
        draft.employeeName,
        draft.personalNumber || "",
        calculation.gross,
        calculation.withheldTax,
        calculation.netPay,
        calculation.employerFee,
        calculation.totalCost,
        draft.status,
        draft.voucherNumber || ""
      ]);
    });

    downloadLocalCsv("loner.csv", rows);
  }

  function downloadDataQualityCsv() {
    setError("");
    const rows = [
      ["Datahalsa / Data quality"],
      ["Filter", dataQualityFilter],
      ["Sokning", dataQualitySearch || "-"],
      ["Antal", filteredDataQualityIssues.length],
      [],
      ["Niva", "Omrade", "Kontroll", "Detalj", "Referens"]
    ];

    filteredDataQualityIssues.forEach((issue) => {
      rows.push([
        issue.severity,
        issue.areaLabel,
        issue.title,
        issue.detail,
        issue.reference || ""
      ]);
    });

    downloadLocalCsv("data-quality.csv", rows);
  }

  function downloadDataBackupJson() {
    setError("");
    const backupDate = new Date().toISOString();
    const safeDate = backupDate.slice(0, 10);
    const sanitizedAuditTrail = auditTrailRows.map(({ action, ...row }) => row);
    const sanitizedArchiveItems = archivePackageItems.map(({ action, ...item }) => item);

    downloadLocalJson(`alibooks-backup-${safeDate}.json`, {
      app: "AliBooks",
      version: 1,
      exportedAt: backupDate,
      language,
      containsPersonalData: true,
      note: language === "sv"
        ? "Denna backup innehaller personuppgifter och ska sparas sakert."
        : "This backup contains personal data and should be stored securely.",
      period: {
        from: vatPeriodFrom || null,
        to: vatPeriodTo || null,
        label: archivePeriodLabel,
        lockedThrough: accountingLockedThroughDate || null
      },
      summary: {
        customers: customers.length,
        invoices: invoices.length,
        paidInvoices,
        unpaidInvoices,
        expenses: expenses.length,
        expensesMissingReceipt: expensesMissingReceipt.length,
        journalEntries: journalEntries.length,
        vouchers: journalGroups.length,
        unbalancedVouchers: unbalancedJournalGroups.length,
        revenueNet,
        expenseNet,
        profitNet,
        vatToPay: vatReport?.vatToPay || 0,
        selectedPeriodVatToPay: vatPeriodToPay,
        totalOutstanding,
        payrollDrafts: payrollDrafts.length,
        payrollBooked: payrollDraftTotals.booked,
        payrollTotalCost: payrollDraftTotals.totalCost,
        auditEvents: auditTrailRows.length,
        dataQualityIssues: dataQualityIssues.length,
        dataQualityCritical: dataQualityCriticalCount,
        dataQualityWarnings: dataQualityWarningCount
      },
      settings,
      customers,
      invoices,
      expenses,
      accounts,
      journalEntries,
      payroll: {
        drafts: payrollDrafts,
        taxRate: payrollTaxRate,
        employerRate: payrollEmployerRate,
        salaryAccount: payrollSalaryAccount
      },
      reports: {
        profitAndLoss,
        balanceReport,
        vatReport,
        monthlyReportRows,
        customerLedgerRows,
        agingBuckets: agingBuckets.map((bucket) => ({
          key: bucket.key,
          title: bucket.title,
          count: bucket.items.length,
          total: bucket.items.reduce((sum, item) => sum + invoiceRemainingAmount(item), 0)
        })),
        closeChecklist: closeChecklistItems.map(({ action, ...item }) => item),
        archivePackage: sanitizedArchiveItems,
        auditTrail: sanitizedAuditTrail,
        dataQuality: dataQualityIssues.map(({ action, ...issue }) => issue)
      },
      bank: {
        reconciliationHistory: bankReconciliationHistory,
        stripePayouts
      }
    });
  }

  async function validateBackupFile(event) {
    setError("");
    setSettingsMessage("");
    const file = event.target.files?.[0];

    if (!file) return;

    try {
      const parsed = JSON.parse(await file.text());
      const issues = [];

      if (parsed.app !== "AliBooks") {
        issues.push(language === "sv" ? "Filen verkar inte vara en AliBooks-backup." : "The file does not look like an AliBooks backup.");
      }

      if (!parsed.version) {
        issues.push(language === "sv" ? "Backupversion saknas." : "Backup version is missing.");
      }

      if (!parsed.exportedAt) {
        issues.push(language === "sv" ? "Exportdatum saknas." : "Export date is missing.");
      }

      if (!Array.isArray(parsed.customers)) {
        issues.push(language === "sv" ? "Kundlista saknas eller ar fel format." : "Customer list is missing or has the wrong format.");
      }

      if (!Array.isArray(parsed.invoices)) {
        issues.push(language === "sv" ? "Fakturalista saknas eller ar fel format." : "Invoice list is missing or has the wrong format.");
      }

      if (!Array.isArray(parsed.journalEntries)) {
        issues.push(language === "sv" ? "Bokforingsrader saknas eller ar fel format." : "Journal entries are missing or have the wrong format.");
      }

      const summary = parsed.summary || {};
      setBackupValidation({
        ok: issues.length === 0,
        fileName: file.name,
        exportedAt: parsed.exportedAt || "",
        version: parsed.version || "-",
        periodLabel: parsed.period?.label || "-",
        containsPersonalData: parsed.containsPersonalData !== false,
        issues,
        counts: {
          customers: Array.isArray(parsed.customers) ? parsed.customers.length : summary.customers || 0,
          invoices: Array.isArray(parsed.invoices) ? parsed.invoices.length : summary.invoices || 0,
          expenses: Array.isArray(parsed.expenses) ? parsed.expenses.length : summary.expenses || 0,
          journalEntries: Array.isArray(parsed.journalEntries) ? parsed.journalEntries.length : summary.journalEntries || 0,
          auditEvents: Array.isArray(parsed.reports?.auditTrail) ? parsed.reports.auditTrail.length : summary.auditEvents || 0
        }
      });

      setSettingsMessage(issues.length === 0
        ? (language === "sv" ? "Backupfilen ser giltig ut. Ingen data har importerats." : "Backup file looks valid. No data has been imported.")
        : (language === "sv" ? "Backupfilen kunde lasas men har varningar." : "Backup file could be read but has warnings."));
    } catch {
      setBackupValidation({
        ok: false,
        fileName: file.name,
        exportedAt: "",
        version: "-",
        periodLabel: "-",
        containsPersonalData: true,
        issues: [language === "sv" ? "Filen kunde inte lasas som JSON." : "The file could not be read as JSON."],
        counts: {
          customers: 0,
          invoices: 0,
          expenses: 0,
          journalEntries: 0,
          auditEvents: 0
        }
      });
    } finally {
      event.target.value = "";
    }
  }

  function downloadVatReportCsv() {
    setError("");
    const report = vatReport || { outputVat: 0, inputVat: 0, vatToPay: 0 };
    const rows = [
      ["Rad", "Konto", "Belopp"],
      [t.outputVat, "2611", report.outputVat || 0],
      [t.inputVat, "2641", report.inputVat || 0],
      [t.vatToPay, "", report.vatToPay || 0]
    ];

    downloadLocalCsv("vat-report.csv", rows);
  }

  function downloadVatReconciliationCsv() {
    setError("");
    const rows = [
      ["Momsperiod fran", vatPeriodFrom || "-", "Momsperiod till", vatPeriodTo || "-"],
      ["Status", vatPeriodStatusText],
      ["Intakter exkl. moms", vatPeriodRevenue],
      ["Kostnader exkl. moms", vatPeriodExpenses],
      ["Utgaende moms 2611", vatPeriodOutputVat],
      ["Ingaende moms 2641", vatPeriodInputVat],
      ["Moms att betala/fa tillbaka", vatPeriodToPay],
      ["Verifikat", vatPeriodVoucherNumbers.size],
      [],
      ["Datum", "Verifikat", "Konto", "Kontonamn", "Beskrivning", "Debet", "Kredit"]
    ];

    vatPeriodEntries
      .filter((entry) => entry.accountNumber === "2611" || entry.accountNumber === "2641")
      .sort((first, second) => String(first.voucherDate || "").localeCompare(String(second.voucherDate || "")))
      .forEach((entry) => {
        rows.push([
          entry.voucherDate || "",
          entry.voucherNumber || "",
          entry.accountNumber || "",
          entry.accountName || "",
          entry.description || "",
          entry.debit || 0,
          entry.credit || 0
        ]);
      });

    downloadLocalCsv("vat-reconciliation.csv", rows);
  }

  function downloadExpensesCsv() {
    setError("");
    const rows = [
      ["Filter", expenseFilter, "Kategori", expenseCategoryFilter === "all" ? t.all : expenseCategoryLabel(expenseCategoryFilter)],
      ["Fran datum", expenseDateFrom || "-", "Till datum", expenseDateTo || "-", "Sokning", expenseSearch || "-"],
      ["Sparade underlag", filteredExpensesWithReceipt.length, "Sparat belopp", filteredReceiptTotal],
      ["Saknar kvitto", filteredExpensesMissingReceipt.length, "Saknar kvitto belopp", filteredMissingReceiptTotal],
      [],
      ["Datum", "Beskrivning", "Kategori", "Netto", "Moms", "Totalt", "Kvitto sparat", "Filnamn"]
    ];

    filteredExpenses.forEach((expense) => {
      rows.push([
        expense.expenseDate || "",
        expense.description || "",
        expenseCategoryLabel(expense.category),
        expense.netAmount || 0,
        expense.vatAmount || 0,
        expense.totalAmount || 0,
        expenseHasReceipt(expense) ? (language === "sv" ? "Ja" : "Yes") : (language === "sv" ? "Nej" : "No"),
        expense.receiptFileName || ""
      ]);
    });

    downloadLocalCsv("expenses.csv", rows);
  }

  function downloadInvoicesCsv() {
    setError("");
    const rows = [
      ["Statusfilter", invoiceFilter, "Fran datum", invoiceDateFrom || "-", "Till datum", invoiceDateTo || "-", "Sokning", invoiceSearch || "-"],
      [],
      ["Fakturanummer", "Kund", "Fakturadatum", "Forfallodatum", "Status", "Tjanst", "Netto", "Moms", "Total", "Betalt", "Kvar att betala", "OCR"]
    ];

    filteredInvoices.forEach((item) => {
      rows.push([
        invoiceNumber(item),
        item.customerName || item.customer?.name || "",
        item.invoiceDate || String(item.createdAt || "").slice(0, 10),
        item.dueDate || "",
        statusLabel(item.status, language),
        item.product?.name || "",
        invoiceNetAmount(item),
        invoiceVatAmount(item),
        invoiceTotalAmount(item),
        invoicePaidAmount(item),
        invoiceRemainingAmount(item),
        item.ocrNumber || ""
      ]);
    });

    downloadLocalCsv("invoices.csv", rows);
  }

  function downloadPaymentOverviewCsv() {
    setError("");
    const rows = [
      ["Betalningsfilter", paymentOverviewFilter, "Sokning", paymentOverviewSearch || "-", "Antal fakturor", paymentOverviewInvoices.length, "Kvar att betala", paymentOverviewOutstanding],
      [],
      ["Fakturanummer", "Kund", "Personnummer", "Status", "Forfallostatus", "Fakturadatum", "Forfallodatum", "Total", "Betalt", "Kvar att betala", "Senaste paminnelse", "OCR", "Referens"]
    ];

    paymentOverviewInvoices.forEach((item) => {
      const dueStatus = invoiceDueStatus(item, language);
      rows.push([
        invoiceNumber(item),
        item.customerName || item.customer?.name || "",
        item.customer?.personalNumber || "",
        statusLabel(item.status, language),
        dueStatus.label,
        item.invoiceDate || String(item.createdAt || "").slice(0, 10),
        item.dueDate || "",
        invoiceTotalAmount(item),
        invoicePaidAmount(item),
        invoiceRemainingAmount(item),
        item.reminderSentDate || "",
        item.ocrNumber || "",
        item.paymentReference || ""
      ]);
    });

    downloadLocalCsv("payment-overview.csv", rows);
  }

  function downloadStripePayoutsCsv() {
    setError("");
    const sortedPayouts = stripePayouts
      .slice()
      .sort((a, b) => String(b.payoutDate || "").localeCompare(String(a.payoutDate || "")));
    const sortedWebsiteSales = stripeWebsiteSaleEntries
      .slice()
      .sort((a, b) => String(b.voucherDate || "").localeCompare(String(a.voucherDate || "")));
    const rows = [
      ["Stripe-forsaljning hemsida", stripeWebsiteSaleTotal, "Saldo 1580", stripeReceivableBalance, "Brutto utbetalt", stripePayoutGrossTotal, "Stripe-avgifter", stripePayoutFeeTotal, "Netto till bank", stripePayoutNetTotal],
      [],
      ["Stripe-forsaljningar fran hemsidan"],
      ["Datum", "Beskrivning", "Verifikat", "Belopp"],
    ];

    sortedWebsiteSales.forEach((entry) => {
      rows.push([
        entry.voucherDate || "",
        entry.description || "",
        entry.voucherNumber || "",
        entry.debit || 0
      ]);
    });

    rows.push(
      [],
      ["Stripe-utbetalningar"],
      ["Datum", "Referens", "Verifikat", "Brutto", "Stripe-avgift", "Netto till bank", "Skapad"]
    );

    sortedPayouts.forEach((payout) => {
      rows.push([
        payout.payoutDate || "",
        payout.reference || "",
        payout.voucherNumber || "",
        payout.grossAmount || 0,
        payout.feeAmount || 0,
        payout.netAmount || 0,
        payout.createdAt ? formatDateTime(payout.createdAt) : ""
      ]);
    });

    downloadLocalCsv("stripe-payouts.csv", rows);
  }

  async function handleAuth(event) {
    event.preventDefault();
    setError("");
    setInvoice(null);

    const response = await fetch(`${apiUrl}/auth/${authMode}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ email, password })
    });

    const data = await response.json();

    if (!response.ok) {
      setError(apiErrorMessage(data, "Authentication failed."));
      return;
    }

    localStorage.setItem("alibooks-token", data.token);
    localStorage.setItem("alibooks-email", data.email);
    setToken(data.token);
    setCurrentEmail(data.email);
    setEmail("");
    setPassword("");
    loadInvoices(data.token);
    loadCustomers(data.token);
    loadJournalEntries(data.token);
    loadAccounts(data.token);
    loadVatReport(data.token);
    loadExpenses(data.token);
    loadReminders(data.token);
    loadAdvisorSummary(data.token);
    loadSettings(data.token);
    loadProfitAndLoss(data.token);
    loadBalanceReport(data.token);
    loadStripePayouts(data.token);
  }

  function handleLogout() {
    localStorage.removeItem("alibooks-token");
    localStorage.removeItem("alibooks-email");
    setToken("");
    setCurrentEmail("");
    setInvoices([]);
    setCustomers([]);
    setJournalEntries([]);
    setAccounts([]);
    setExpenses([]);
    setContracts([]);
    setReminders([]);
    setAdvisorSummary(null);
    setSettings(null);
    setProfitAndLoss(null);
    setBalanceReport(null);
    setVatReport(null);
    setStripePayouts([]);
  }

  function focusAuthForm(mode) {
    setAuthMode(mode);
    window.requestAnimationFrame(() => {
      authFormRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      authFormRef.current?.querySelector("input")?.focus();
    });
  }

  const unpaidInvoices = invoices.filter((item) => item.status !== "PAID");
  const partiallyPaidInvoices = invoices.filter((item) => item.status === "PARTIALLY_PAID");
  const paidInvoices = invoices.filter((item) => item.status === "PAID");
  const overdueInvoices = invoices.filter(invoiceIsOverdue);
  const dueSoonInvoices = invoices.filter((item) => invoiceShouldSendReminder(item, settings?.invoiceReminderDaysBeforeDue || 5) && !invoiceIsOverdue(item));
  const totalOutstanding = invoices.reduce((sum, item) => sum + invoiceRemainingAmount(item), 0);
  const totalPaid = invoices.reduce((sum, item) => sum + invoicePaidAmount(item), 0);
  const paymentOverviewInvoices = invoices.filter((item) => invoiceRemainingAmount(item) > 0).filter((item) => {
    if (paymentOverviewFilter === "overdue") return invoiceIsOverdue(item);
    if (paymentOverviewFilter === "dueSoon") return invoiceShouldSendReminder(item, settings?.invoiceReminderDaysBeforeDue || 5) && !invoiceIsOverdue(item);
    if (paymentOverviewFilter === "partiallyPaid") return item.status === "PARTIALLY_PAID";
    if (paymentOverviewFilter === "sent") return item.status === "SENT";
    return true;
  }).filter((item) => {
    const query = paymentOverviewSearch.toLowerCase().trim();
    if (!query) return true;
    return [
      invoiceNumber(item),
      item.customerName,
      item.customer?.name,
      item.customer?.personalNumber,
      item.status,
      item.ocrNumber,
      item.paymentReference
    ].some((value) => String(value || "").toLowerCase().includes(query));
  }).sort(compareInvoices);
  const paymentOverviewOutstanding = paymentOverviewInvoices.reduce((sum, item) => sum + invoiceRemainingAmount(item), 0);
  const activeContracts = contracts.filter((contract) => contract.active);
  const dueContracts = activeContracts.filter((contract) => contract.nextInvoiceDate <= new Date().toISOString().slice(0, 10));
  const bankImportMatchedCount = bankImportRows.filter((row) => findBankImportMatch(row)).length;
  const bankImportRuleMatchedCount = bankImportRows.filter((row) => !findBankImportMatch(row) && Boolean(bankImportExpenseRule(row))).length;
  const bankImportInvoiceReadyCount = bankImportRows.filter((row) => bankImportWorkflow(row).key === "invoiceReady").length;
  const bankImportExpenseReadyCount = bankImportRows.filter((row) => bankImportWorkflow(row).key === "expenseReady").length;
  const bankImportNeedsReviewCount = bankImportRows.filter((row) => ["expenseReview", "incomingReview"].includes(bankImportWorkflow(row).key)).length;
  const bankImportAlreadyBookedCount = bankImportRows.filter((row) => bankImportWorkflow(row).key === "alreadyBooked").length;
  const bankImportTotalAmount = bankImportRows.reduce((sum, row) => sum + (row.amount || 0), 0);
  const bankImportIncomingAmount = bankImportRows.filter((row) => (row.amount || 0) > 0).reduce((sum, row) => sum + (row.amount || 0), 0);
  const bankImportOutgoingAmount = bankImportRows.filter((row) => (row.amount || 0) < 0).reduce((sum, row) => sum + Math.abs(row.amount || 0), 0);
  const stripeReceivableEntries = journalEntries.filter((entry) => entry.accountNumber === "1580");
  const stripeWebsiteSaleEntries = stripeReceivableEntries.filter((entry) =>
    (entry.debit || 0) > 0 && String(entry.description || "").toLowerCase().includes("stripe website sale")
  );
  const stripeWebsiteSaleTotal = stripeWebsiteSaleEntries.reduce((sum, entry) => sum + (entry.debit || 0), 0);
  const stripeReceivableBalance = stripeReceivableEntries.reduce((sum, entry) => sum + (entry.debit || 0) - (entry.credit || 0), 0);
  const stripePayoutGrossTotal = stripePayouts.reduce((sum, payout) => sum + (payout.grossAmount || 0), 0);
  const stripePayoutFeeTotal = stripePayouts.reduce((sum, payout) => sum + (payout.feeAmount || 0), 0);
  const stripePayoutNetTotal = stripePayouts.reduce((sum, payout) => sum + (payout.netAmount || 0), 0);
  const bankReconciliationBookedRows = bankReconciliationHistory.filter((entry) => entry.status === "booked");
  const bankReconciliationSkippedRows = bankReconciliationHistory.filter((entry) => entry.status === "skipped");
  const bankReconciliationIncomingAmount = bankReconciliationBookedRows
    .filter((entry) => (entry.amount || 0) > 0)
    .reduce((sum, entry) => sum + (entry.amount || 0), 0);
  const bankReconciliationOutgoingAmount = bankReconciliationBookedRows
    .filter((entry) => (entry.amount || 0) < 0)
    .reduce((sum, entry) => sum + Math.abs(entry.amount || 0), 0);
  const bankReconciliationOpenAmount = bankImportRows.reduce((sum, row) => sum + (row.amount || 0), 0);
  const filteredBankImportRows = bankImportRows.filter((row) => {
    const match = findBankImportMatch(row);
    const hasMatch = Boolean(match);
    const workflow = bankImportWorkflow(row);

    if (bankImportFilter === "matched") return hasMatch;
    if (bankImportFilter === "unmatched") return !hasMatch;
    if (bankImportFilter === "ready") return ["invoiceReady", "expenseReady"].includes(workflow.key);
    if (bankImportFilter === "review") return ["expenseReview", "incomingReview"].includes(workflow.key);
    if (bankImportFilter === "expenses") return (row.amount || 0) < 0;
    if (bankImportFilter === "booked") return workflow.key === "alreadyBooked";
    return true;
  }).filter((row) => {
    const query = bankImportSearch.toLowerCase().trim();
    const match = findBankImportMatch(row);
    const workflow = bankImportWorkflow(row);

    if (!query) return true;

    return [
      row.date,
      row.description,
      row.reference,
      row.amount,
      match ? invoiceNumber(match) : "",
      match?.customerName,
      match?.ocrNumber,
      workflow.title,
      workflow.nextAction
    ].some((value) => String(value || "").toLowerCase().includes(query));
  });
  const agingInvoices = unpaidInvoices.filter((item) => invoiceRemainingAmount(item) > 0);
  const agingBuckets = [
    {
      key: "no-due-date",
      title: language === "sv" ? "Utan forfallodatum" : "No due date",
      items: agingInvoices.filter((item) => invoiceDaysUntilDue(item) === null).sort(compareInvoices)
    },
    {
      key: "not-due",
      title: language === "sv" ? "Ej forfallen" : "Not overdue",
      items: agingInvoices.filter((item) => {
        const days = invoiceDaysUntilDue(item);
        return days !== null && days >= 0;
      }).sort(compareInvoices)
    },
    {
      key: "overdue-1-7",
      title: language === "sv" ? "Forfallen 1-7 dagar" : "Overdue 1-7 days",
      items: agingInvoices.filter((item) => {
        const days = invoiceDaysUntilDue(item);
        return days !== null && days < 0 && Math.abs(days) <= 7;
      }).sort(compareInvoices)
    },
    {
      key: "overdue-8-30",
      title: language === "sv" ? "Forfallen 8-30 dagar" : "Overdue 8-30 days",
      items: agingInvoices.filter((item) => {
        const days = invoiceDaysUntilDue(item);
        return days !== null && days < 0 && Math.abs(days) > 7 && Math.abs(days) <= 30;
      }).sort(compareInvoices)
    },
    {
      key: "overdue-30",
      title: language === "sv" ? "Forfallen over 30 dagar" : "Overdue more than 30 days",
      items: agingInvoices.filter((item) => {
        const days = invoiceDaysUntilDue(item);
        return days !== null && days < -30;
      }).sort(compareInvoices)
    }
  ];
  const customerLedgerMap = new Map();

  customers.forEach((customer) => {
    customerLedgerMap.set(`customer-${customer.id}`, {
      customerId: customer.id,
      name: customer.name || "",
      email: customer.email || "",
      personalNumber: customer.personalNumber || "",
      invoiceCount: 0,
      openCount: 0,
      overdueCount: 0,
      paidCount: 0,
      invoicedTotal: 0,
      paidTotal: 0,
      outstandingTotal: 0,
      overdueTotal: 0,
      latestInvoiceDate: "",
      latestDueDate: "",
      latestInvoiceNumber: "",
      invoices: []
    });
  });

  invoices.forEach((item) => {
    const customerId = item.customer?.id || item.customerId || "";
    const customerKey = customerId ? `customer-${customerId}` : `invoice-customer-${normalizeNameValue(item.customerName || item.customer?.name || "unknown")}`;
    const existing = customerLedgerMap.get(customerKey) || {
      customerId,
      name: item.customerName || item.customer?.name || "",
      email: item.customer?.email || "",
      personalNumber: item.customer?.personalNumber || "",
      invoiceCount: 0,
      openCount: 0,
      overdueCount: 0,
      paidCount: 0,
      invoicedTotal: 0,
      paidTotal: 0,
      outstandingTotal: 0,
      overdueTotal: 0,
      latestInvoiceDate: "",
      latestDueDate: "",
      latestInvoiceNumber: "",
      invoices: []
    };
    const invoiceDateValue = item.invoiceDate || String(item.createdAt || "").slice(0, 10);
    const remainingAmount = invoiceRemainingAmount(item);

    existing.name = existing.name || item.customerName || item.customer?.name || "";
    existing.email = existing.email || item.customer?.email || "";
    existing.personalNumber = existing.personalNumber || item.customer?.personalNumber || "";
    existing.invoiceCount += 1;
    existing.openCount += remainingAmount > 0 ? 1 : 0;
    existing.overdueCount += invoiceIsOverdue(item) ? 1 : 0;
    existing.paidCount += item.status === "PAID" ? 1 : 0;
    existing.invoicedTotal += invoiceTotalAmount(item);
    existing.paidTotal += invoicePaidAmount(item);
    existing.outstandingTotal += remainingAmount;
    existing.overdueTotal += invoiceIsOverdue(item) ? remainingAmount : 0;
    existing.invoices.push(item);

    if (!existing.latestInvoiceDate || invoiceDateValue > existing.latestInvoiceDate) {
      existing.latestInvoiceDate = invoiceDateValue;
      existing.latestDueDate = item.dueDate || "";
      existing.latestInvoiceNumber = invoiceNumber(item);
    }

    customerLedgerMap.set(customerKey, existing);
  });

  const customerLedgerRows = Array.from(customerLedgerMap.values())
    .filter((row) => row.invoiceCount > 0 || row.outstandingTotal > 0)
    .sort((a, b) => b.outstandingTotal - a.outstandingTotal || a.name.localeCompare(b.name));
  const filteredCustomerLedgerRows = customerLedgerRows.filter((row) => {
    const query = customerLedgerSearch.toLowerCase().trim();
    if (!query) return true;

    return [
      row.name,
      row.email,
      row.personalNumber,
      row.latestInvoiceNumber,
      row.invoices.map((item) => invoiceNumber(item)).join(" ")
    ].some((value) => String(value || "").toLowerCase().includes(query));
  });
  const customerLedgerOpenCustomers = customerLedgerRows.filter((row) => row.outstandingTotal > 0).length;
  const customerLedgerOverdueCustomers = customerLedgerRows.filter((row) => row.overdueTotal > 0).length;
  const customerLedgerOutstandingTotal = customerLedgerRows.reduce((sum, row) => sum + row.outstandingTotal, 0);
  const customerLedgerOverdueTotal = customerLedgerRows.reduce((sum, row) => sum + row.overdueTotal, 0);
  const monthlyReportMap = new Map();
  const ensureMonthlyReportRow = (monthKey) => {
    const safeMonthKey = /^\d{4}-\d{2}$/.test(monthKey || "") ? monthKey : new Date().toISOString().slice(0, 7);
    if (!monthlyReportMap.has(safeMonthKey)) {
      monthlyReportMap.set(safeMonthKey, {
        monthKey: safeMonthKey,
        revenue: 0,
        expenses: 0,
        result: 0,
        outputVat: 0,
        inputVat: 0,
        vatToPay: 0,
        cashIn: 0,
        cashOut: 0,
        cashChange: 0,
        voucherCount: 0
      });
    }
    return monthlyReportMap.get(safeMonthKey);
  };

  journalEntries.forEach((entry) => {
    const monthKey = String(entry.voucherDate || entry.createdAt || "").slice(0, 7);
    const row = ensureMonthlyReportRow(monthKey);
    const accountNumber = String(entry.accountNumber || "");
    const debit = entry.debit || 0;
    const credit = entry.credit || 0;

    if (accountNumber.startsWith("3")) {
      row.revenue += credit - debit;
    }

    if (["4", "5", "6", "7"].some((prefix) => accountNumber.startsWith(prefix))) {
      row.expenses += debit - credit;
    }

    if (accountNumber === "2611") {
      row.outputVat += credit - debit;
    }

    if (accountNumber === "2641") {
      row.inputVat += debit - credit;
    }

    if (accountNumber === "1930") {
      row.cashIn += debit;
      row.cashOut += credit;
    }

    row.voucherCount += 1;
  });

  const monthlyReportRows = Array.from(monthlyReportMap.values())
    .map((row) => ({
      ...row,
      result: row.revenue - row.expenses,
      vatToPay: row.outputVat - row.inputVat,
      cashChange: row.cashIn - row.cashOut
    }))
    .sort((a, b) => b.monthKey.localeCompare(a.monthKey));
  const monthlyReportRevenueTotal = monthlyReportRows.reduce((sum, row) => sum + row.revenue, 0);
  const monthlyReportExpenseTotal = monthlyReportRows.reduce((sum, row) => sum + row.expenses, 0);
  const monthlyReportResultTotal = monthlyReportRows.reduce((sum, row) => sum + row.result, 0);
  const monthlyReportVatToPayTotal = monthlyReportRows.reduce((sum, row) => sum + row.vatToPay, 0);
  const monthlyReportCashChangeTotal = monthlyReportRows.reduce((sum, row) => sum + row.cashChange, 0);
  const vatPeriodEntries = journalEntries.filter((entry) => {
    const voucherDate = String(entry.voucherDate || entry.createdAt || "").slice(0, 10);
    if (!voucherDate) return false;
    if (vatPeriodFrom && voucherDate < vatPeriodFrom) return false;
    if (vatPeriodTo && voucherDate > vatPeriodTo) return false;
    return true;
  });
  const vatPeriodOutputVat = vatPeriodEntries
    .filter((entry) => entry.accountNumber === "2611")
    .reduce((sum, entry) => sum + (entry.credit || 0) - (entry.debit || 0), 0);
  const vatPeriodInputVat = vatPeriodEntries
    .filter((entry) => entry.accountNumber === "2641")
    .reduce((sum, entry) => sum + (entry.debit || 0) - (entry.credit || 0), 0);
  const vatPeriodToPay = vatPeriodOutputVat - vatPeriodInputVat;
  const vatPeriodRevenue = vatPeriodEntries
    .filter((entry) => String(entry.accountNumber || "").startsWith("3"))
    .reduce((sum, entry) => sum + (entry.credit || 0) - (entry.debit || 0), 0);
  const vatPeriodExpenses = vatPeriodEntries
    .filter((entry) => ["4", "5", "6", "7"].some((prefix) => String(entry.accountNumber || "").startsWith(prefix)))
    .reduce((sum, entry) => sum + (entry.debit || 0) - (entry.credit || 0), 0);
  const vatPeriodVoucherNumbers = new Set(vatPeriodEntries.map((entry) => entry.voucherNumber).filter(Boolean));
  const vatPeriodHasData = vatPeriodEntries.length > 0;
  const vatPeriodStatusText = !vatPeriodHasData
    ? (language === "sv" ? "Ingen bokforing i vald period." : "No bookkeeping in the selected period.")
    : vatPeriodToPay > 0
      ? (language === "sv" ? "Moms att betala" : "VAT to pay")
      : vatPeriodToPay < 0
        ? (language === "sv" ? "Moms att fa tillbaka" : "VAT to reclaim")
        : (language === "sv" ? "Momsperioden balanserar till 0 SEK" : "The VAT period balances to 0 SEK");
  const revenueNet = invoices.reduce((sum, item) => sum + invoiceNetAmount(item), 0);
  const revenueTotal = invoices.reduce((sum, item) => sum + invoiceTotalAmount(item), 0);
  const expenseNet = expenses.reduce((sum, item) => sum + (item.netAmount || 0), 0);
  const expenseTotal = expenses.reduce((sum, item) => sum + (item.totalAmount || 0), 0);
  const profitNet = revenueNet - expenseNet;
  const todayInput = dateInputString(new Date());
  const cashflowEndDate = addDaysInputString(todayInput, cashflowHorizonDays);
  const cashAccountBalanceFromJournal = journalEntries
    .filter((entry) => entry.accountNumber === "1930")
    .reduce((sum, entry) => sum + (entry.debit || 0) - (entry.credit || 0), 0);
  const cashAccountBalanceFromReport = balanceReport?.assets?.find((line) => line.accountNumber === "1930")?.amount;
  const cashOpeningBalance = Number.isFinite(cashAccountBalanceFromReport)
    ? cashAccountBalanceFromReport
    : cashAccountBalanceFromJournal;
  const openInvoiceCashflowRows = invoices
    .filter((item) => invoiceRemainingAmount(item) > 0)
    .map((item) => {
      const plannedDate = item.dueDate || item.invoiceDate || String(item.createdAt || "").slice(0, 10) || todayInput;
      return {
        id: `cash-invoice-${item.id}`,
        type: "in",
        source: language === "sv" ? "Faktura" : "Invoice",
        date: plannedDate < todayInput ? todayInput : plannedDate,
        title: invoiceNumber(item),
        detail: item.customerName || item.customer?.name || "-",
        amount: invoiceRemainingAmount(item),
        status: invoiceIsOverdue(item)
          ? (language === "sv" ? "Forfallen" : "Overdue")
          : invoiceDueStatus(item, language).label,
        action: () => {
          setActiveView("invoices");
          setInvoiceSearch(invoiceNumber(item));
        }
      };
    });
  const cashflowInvoicesInHorizon = openInvoiceCashflowRows.filter((row) => row.date <= cashflowEndDate);
  const cashflowInvoiceInTotal = cashflowInvoicesInHorizon.reduce((sum, row) => sum + (row.amount || 0), 0);
  const cashflowStripeInTotal = Math.max(stripeReceivableBalance, 0);
  const recentCashOutRows = monthlyReportRows.filter((row) => (row.cashOut || 0) > 0).slice(0, 3);
  const averageMonthlyCashOut = recentCashOutRows.length === 0
    ? 0
    : Math.round(recentCashOutRows.reduce((sum, row) => sum + (row.cashOut || 0), 0) / recentCashOutRows.length);
  const cashflowExpenseReserve = Math.round(averageMonthlyCashOut * (cashflowHorizonDays / 30));
  const cashflowVatOutTotal = Math.max(vatPeriodToPay, 0);
  const cashflowExpectedIn = cashflowInvoiceInTotal + cashflowStripeInTotal;
  const cashflowExpectedOut = cashflowExpenseReserve + cashflowVatOutTotal;
  const cashflowProjectedBalance = cashOpeningBalance + cashflowExpectedIn - cashflowExpectedOut;
  const cashflowRunwayMonths = averageMonthlyCashOut > 0
    ? Math.max(0, Math.round((cashflowProjectedBalance / averageMonthlyCashOut) * 10) / 10)
    : null;
  const cashflowProjectionRows = [30, 60, 90].map((days) => {
    const endDate = addDaysInputString(todayInput, days);
    const invoiceIn = openInvoiceCashflowRows
      .filter((row) => row.date <= endDate)
      .reduce((sum, row) => sum + (row.amount || 0), 0);
    const reserve = Math.round(averageMonthlyCashOut * (days / 30));
    const vatOut = cashflowVatOutTotal > 0 ? cashflowVatOutTotal : 0;

    return {
      days,
      endDate,
      invoiceIn,
      stripeIn: cashflowStripeInTotal,
      reserve,
      vatOut,
      projectedBalance: cashOpeningBalance + invoiceIn + cashflowStripeInTotal - reserve - vatOut
    };
  });
  const cashflowTimelineRows = [
    ...cashflowInvoicesInHorizon,
    ...(cashflowStripeInTotal > 0 ? [{
      id: "cash-stripe-receivable",
      type: "in",
      source: "Stripe",
      date: todayInput,
      title: language === "sv" ? "Stripe-fordran" : "Stripe receivable",
      detail: language === "sv" ? "Belopp kvar pa konto 1580" : "Remaining balance on account 1580",
      amount: cashflowStripeInTotal,
      status: language === "sv" ? "Att stamma av" : "To reconcile",
      action: () => setActiveView("payments")
    }] : []),
    ...(cashflowVatOutTotal > 0 ? [{
      id: "cash-vat",
      type: "out",
      source: language === "sv" ? "Moms" : "VAT",
      date: vatPeriodTo && vatPeriodTo > todayInput ? vatPeriodTo : todayInput,
      title: language === "sv" ? "Preliminar moms att betala" : "Preliminary VAT to pay",
      detail: `${vatPeriodFrom || "-"} - ${vatPeriodTo || "-"}`,
      amount: cashflowVatOutTotal,
      status: language === "sv" ? "Kontrollera datum" : "Verify date",
      action: () => setActiveView("vat")
    }] : []),
    ...(cashflowExpenseReserve > 0 ? [{
      id: "cash-expense-reserve",
      type: "out",
      source: language === "sv" ? "Reserv" : "Reserve",
      date: cashflowEndDate,
      title: language === "sv" ? "Kostnadsreserv" : "Expense reserve",
      detail: language === "sv"
        ? `Baserad pa snitt av senaste ${recentCashOutRows.length} manad(er)`
        : `Based on the average of the latest ${recentCashOutRows.length} month(s)`,
      amount: cashflowExpenseReserve,
      status: language === "sv" ? "Prognos" : "Forecast",
      action: () => setActiveView("reports")
    }] : [])
  ].sort((first, second) => String(first.date || "").localeCompare(String(second.date || "")));
  const budgetMonthlyRevenueTarget = Math.max(0, Number(budgetRevenueTarget) || 0);
  const budgetMonthlyExpenseLimit = Math.max(0, Number(budgetExpenseLimit) || 0);
  const budgetReserveRate = Math.min(80, Math.max(0, Number(budgetReservePercent) || 0));
  const budgetSourceRows = monthlyReportRows.length > 0
    ? monthlyReportRows.slice(0, 12)
    : [{
      monthKey: todayInput.slice(0, 7),
      revenue: revenueNet,
      expenses: expenseNet,
      result: profitNet,
      outputVat: vatReport?.outputVat || 0,
      inputVat: vatReport?.inputVat || 0,
      vatToPay: vatReport?.vatToPay || 0,
      cashIn: totalPaid,
      cashOut: expenseTotal,
      cashChange: totalPaid - expenseTotal,
      voucherCount: journalEntries.length
    }];
  const budgetRows = budgetSourceRows.map((row) => {
    const revenueVariance = row.revenue - budgetMonthlyRevenueTarget;
    const expenseRoom = budgetMonthlyExpenseLimit - row.expenses;
    const reserve = Math.round(Math.max(row.result, 0) * (budgetReserveRate / 100));
    const afterReserve = row.result - reserve;
    const onTrack = revenueVariance >= 0 && expenseRoom >= 0 && afterReserve >= 0;

    return {
      ...row,
      revenueVariance,
      expenseRoom,
      reserve,
      afterReserve,
      status: onTrack ? "ok" : afterReserve < 0 ? "risk" : "watch",
      statusLabel: onTrack
        ? (language === "sv" ? "Pa plan" : "On track")
        : afterReserve < 0
          ? (language === "sv" ? "Risk" : "Risk")
          : (language === "sv" ? "Bevaka" : "Watch")
    };
  });
  const budgetCurrentRow = budgetRows[0] || null;
  const budgetRevenueProgress = budgetMonthlyRevenueTarget > 0 && budgetCurrentRow
    ? Math.round((budgetCurrentRow.revenue / budgetMonthlyRevenueTarget) * 100)
    : 0;
  const budgetExpenseUsage = budgetMonthlyExpenseLimit > 0 && budgetCurrentRow
    ? Math.round((budgetCurrentRow.expenses / budgetMonthlyExpenseLimit) * 100)
    : 0;
  const budgetReserveTotal = budgetRows.reduce((sum, row) => sum + row.reserve, 0);
  const budgetAfterReserveTotal = budgetRows.reduce((sum, row) => sum + row.afterReserve, 0);
  const budgetRiskMonths = budgetRows.filter((row) => row.status === "risk").length;
  const budgetWatchMonths = budgetRows.filter((row) => row.status === "watch").length;
  const payrollCurrentCalculation = currentPayrollCalculation();
  const payrollDraftTotals = payrollDrafts.reduce((totals, draft) => {
    const calculation = payrollDraftCalculation(draft);
    totals.gross += calculation.gross;
    totals.tax += calculation.withheldTax;
    totals.net += calculation.netPay;
    totals.employerFees += calculation.employerFee;
    totals.totalCost += calculation.totalCost;
    if (draft.status === "booked") totals.booked += 1;
    return totals;
  }, { gross: 0, tax: 0, net: 0, employerFees: 0, totalCost: 0, booked: 0 });

  function createAiAssistantAnswer(questionText) {
    const normalizedQuestion = normalizeNameValue(questionText);
    const openInvoiceCount = invoices.filter((item) => invoiceRemainingAmount(item) > 0).length;
    const answerIntro = language === "sv" ? "AliBooks-assistenten:" : "AliBooks assistant:";
    const createAnswer = (text, targetView = "") => ({ text, targetView });

    if (!normalizedQuestion.trim()) {
      return createAnswer(language === "sv"
        ? `${answerIntro} Skriv en fraga, till exempel "hur ska jag bokfora en faktura?" eller "hur laddar jag upp underlag?".`
        : `${answerIntro} Write a question, for example "how do I bookkeep an invoice?" or "how do I upload receipts?".`);
    }

    if (normalizedQuestion.includes("faktura") || normalizedQuestion.includes("invoice")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For fakturor: skapa eller valj kund, valj tjanst, skapa faktura och skicka PDF/e-post. Nar fakturan skapas bokfors den automatiskt som 1510 debet, 3041 kredit och 2611 kredit. Du har just nu ${openInvoiceCount} oppna fakturor och ${totalOutstanding} SEK kvar att fa betalt.`
        : `${answerIntro} For invoices: create or choose a customer, choose a service, create the invoice and send PDF/email. When an invoice is created it is booked automatically as 1510 debit, 3041 credit and 2611 credit. You currently have ${openInvoiceCount} open invoices and ${totalOutstanding} SEK outstanding.`, "invoices");
    }

    if (normalizedQuestion.includes("kund") || normalizedQuestion.includes("customer") || normalizedQuestion.includes("personnummer")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For kunder: ga till Kunder. Kundkortet visar kontaktuppgifter, fakturor, betalt/obetalt, forfallet belopp, senaste aktivitet och nasta rekommenderade steg. Du har ${customers.length} kunder, ${activeCustomers.length} aktiva och ${customersWithOutstanding.length} med utestaende saldo.`
        : `${answerIntro} For customers: go to Customers. The customer card shows contact details, invoices, paid/unpaid amounts, overdue balance, recent activity and the next recommended step. You have ${customers.length} customers, ${activeCustomers.length} active and ${customersWithOutstanding.length} with outstanding balance.`, "customers");
    }

    if (normalizedQuestion.includes("avtal") || normalizedQuestion.includes("aterkommande") || normalizedQuestion.includes("contract") || normalizedQuestion.includes("recurring")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For avtalsfakturering: ga till Avtal, valj kund, tjanst, intervall och nasta fakturadatum. Nar avtalet ar redo klickar du Skapa faktura, sa skapas en vanlig faktura med automatisk bokforing och nasta datum flyttas fram. Du har ${activeContracts.length} aktiva avtal och ${dueContracts.length} redo att fakturera.`
        : `${answerIntro} For contract invoicing: go to Contracts, choose customer, service, interval and next invoice date. When the contract is due, click Create invoice. A normal invoice is created with automatic bookkeeping, and the next date moves forward. You have ${activeContracts.length} active contracts and ${dueContracts.length} due.`, "contracts");
    }

    if (normalizedQuestion.includes("bokfor") || normalizedQuestion.includes("verifikat") || normalizedQuestion.includes("konto") || normalizedQuestion.includes("journal")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For bokforing: ga till Bokforing. Dar ser du verifikat, debet/kredit, huvudbok per konto, rattelser och manadsoversikt. Grundregeln ar att varje verifikat ska balansera: total debet ska vara samma som total kredit. Fakturor, betalningar, kostnader och kreditfakturor skapar redan bokforingsrader automatiskt.`
        : `${answerIntro} For bookkeeping: go to Bookkeeping. There you can see vouchers, debit/credit, account ledger, corrections and monthly overview. The main rule is that every voucher must balance: total debit must equal total credit. Invoices, payments, expenses and credit invoices already create bookkeeping rows automatically.`, "bookkeeping");
    }

    if (normalizedQuestion.includes("underlag") || normalizedQuestion.includes("kvitto") || normalizedQuestion.includes("receipt") || normalizedQuestion.includes("attachment")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For underlag: ga till Underlag eller Kostnader. Ladda upp kvitto/faktura som fil, fyll datum, beskrivning, totalbelopp, moms och kategori. Underlaget sparas kopplat till kostnaden, och appen skapar bokforing automatiskt for kostnaden. Spara alltid kvitton och fakturor, normalt minst 7 ar.`
        : `${answerIntro} For receipts: go to Uploaded or Expenses. Upload the receipt/invoice file, enter date, description, total, VAT and category. The file is linked to the expense, and the app creates bookkeeping automatically for the expense. Keep receipts and invoices, normally for at least 7 years.`, "uploaded");
    }

    if (normalizedQuestion.includes("sie") || normalizedQuestion.includes("bokforingsfil") || normalizedQuestion.includes("importfil")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For SIE-export: ga till Rapporter eller Bokforing och klicka Exportera SIE. Exporten anvander samma urval som Bokforing: period, filter och sokning. Valda verifikationer maste balansera, annars stoppar AliBooks exporten. Just nu ar status: ${sieExportStatusText}, period ${sieExportPeriodLabel}, ${filteredJournalGroups.length} verifikationer och differens ${filteredJournalDifference} SEK.`
        : `${answerIntro} For SIE export: go to Reports or Bookkeeping and click Export SIE. The export uses the same selection as Bookkeeping: period, filter and search. Selected vouchers must balance, otherwise AliBooks stops the export. Current status: ${sieExportStatusText}, period ${sieExportPeriodLabel}, ${filteredJournalGroups.length} vouchers and difference ${filteredJournalDifference} SEK.`, "reports");
    }

    if (normalizedQuestion.includes("rapport") || normalizedQuestion.includes("resultat") || normalizedQuestion.includes("balans") || normalizedQuestion.includes("export")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For rapporter: ga till Rapporter och borja med boksluts- och kontrollcentret. Manadsavslut visar vald manads nyckeltal, varningar och kontrollpunkter innan du exporterar. Skatteplanen visar preliminara steg for moms, skattekonto, deklaration och arkivering. Datahalsa visar dubbletter, saknade kunduppgifter, saknade underlag och obalanserade verifikat. Under Arkivpaket kan du samla export och ladda ner en komplett JSON-backup. Vald manad ar ${formatMonthLabel(monthCloseSelectedMonth, language)} med ${monthlyCloseWarnings} varningar.`
        : `${answerIntro} For reports: go to Reports and start with the closing and control center. Monthly close shows the selected month's metrics, warnings and checks before export. The Tax plan shows preliminary steps for VAT, tax account, declaration and archiving. Data quality shows duplicates, missing customer details, missing receipts and unbalanced vouchers. Under Archive package you can gather exports and download a complete JSON backup. The selected month is ${formatMonthLabel(monthCloseSelectedMonth, language)} with ${monthlyCloseWarnings} warnings.`, "reports");
    }

    if (normalizedQuestion.includes("handelse") || normalizedQuestion.includes("hant") || normalizedQuestion.includes("timeline") || normalizedQuestion.includes("event")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For handelser: ga till Handelser. Dar ser du en samlad tidslinje for fakturor, betalningar, bankimport, Stripe, kostnader, paminnelser och verifikat. Du kan filtrera pa typ, soka pa fakturanummer, kund, bankreferens eller verifikat och exportera listan som CSV. Just nu finns ${auditTrailRows.length} handelser och ${activityOpenBankRows} oppna bankrader.`
        : `${answerIntro} For events: go to Events. There you can see a combined timeline for invoices, payments, bank import, Stripe, expenses, reminders and vouchers. You can filter by type, search by invoice number, customer, bank reference or voucher and export the list as CSV. There are currently ${auditTrailRows.length} events and ${activityOpenBankRows} open bank rows.`, "activities");
    }

    if (normalizedQuestion.includes("likvid") || normalizedQuestion.includes("kassa") || normalizedQuestion.includes("cash") || normalizedQuestion.includes("runway") || normalizedQuestion.includes("prognos")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For likviditet: ga till Likviditet. Dar ser du startkassa pa 1930, vantade fakturainbetalningar, Stripe-fordran, moms att betala, kostnadsreserv och prognos for 30/60/90 dagar. Just nu visar vald horisont ${cashflowHorizonDays} dagar, prognos ${cashflowProjectedBalance} SEK och kostnadssnitt ${averageMonthlyCashOut} SEK per manad.`
        : `${answerIntro} For cashflow: go to Cashflow. There you see opening cash on account 1930, expected invoice payments, Stripe receivable, VAT to pay, expense reserve and 30/60/90 day forecasts. The selected horizon is ${cashflowHorizonDays} days, forecast ${cashflowProjectedBalance} SEK and average spending ${averageMonthlyCashOut} SEK per month.`, "cashflow");
    }

    if (normalizedQuestion.includes("budget") || normalizedQuestion.includes("mal") || normalizedQuestion.includes("mål") || normalizedQuestion.includes("target")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For budget: ga till Budget & mal. Dar satter du intaktsmal, kostnadstak och reservprocent. AliBooks jamfor varje manad mot planen och visar om du ligger pa plan, behover bevaka eller har risk. Den senaste perioden visar ${budgetCurrentRow?.revenue || 0} SEK i intakter, ${budgetCurrentRow?.expenses || 0} SEK i kostnader och ${budgetCurrentRow?.afterReserve || 0} SEK efter reserv.`
        : `${answerIntro} For budget: go to Budget & goals. Set revenue target, expense limit and reserve percentage. AliBooks compares each month against plan and shows whether you are on track, should watch, or have risk. The latest period shows ${budgetCurrentRow?.revenue || 0} SEK revenue, ${budgetCurrentRow?.expenses || 0} SEK expenses and ${budgetCurrentRow?.afterReserve || 0} SEK after reserve.`, "budget");
    }

    if (normalizedQuestion.includes("lon") || normalizedQuestion.includes("lÃ¶n") || normalizedQuestion.includes("payroll") || normalizedQuestion.includes("salary")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For lon: ga till Lon. Dar kan du skapa loneutkast med bruttolon, preliminar skatt och arbetsgivaravgift. Nar utkastet bokfors skapas ett flerradigt verifikat for lon, personalskatt, nettoutbetalning och arbetsgivaravgift. Kontrollera alltid exakta skatter innan riktig utbetalning.`
        : `${answerIntro} For payroll: go to Payroll. Create a payroll draft with gross salary, preliminary tax and employer contribution. When booked, AliBooks creates a multi-line voucher for salary, tax withholding, net pay and employer contribution. Always verify exact taxes before real payment.`, "payroll");
    }

    if (normalizedQuestion.includes("moms") || normalizedQuestion.includes("vat")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For moms: ga till Momsrapport. Dar finns total momsrapport och momsavstamning for vald period. I Rapporter finns aven Skatteplanen med preliminara kontrollpunkter for moms, skattekonto, deklaration och arkivering. Vald period visar ${vatPeriodToPay} SEK och skatteplanen har ${taxPlanWarnings} varningar. Kontrollera alltid period och exakta datum hos Skatteverket innan du deklarerar.`
        : `${answerIntro} For VAT: go to VAT report. There you have the total VAT report and VAT reconciliation for the selected period. Reports also has the Tax plan with preliminary checkpoints for VAT, tax account, declaration and archiving. The selected period shows ${vatPeriodToPay} SEK and the tax plan has ${taxPlanWarnings} warnings. Always verify period and exact dates with the tax authority before filing.`, "vat");
    }

    if (normalizedQuestion.includes("bank") || normalizedQuestion.includes("csv") || normalizedQuestion.includes("betal") || normalizedQuestion.includes("payment")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For bank och betalningar: ga till Betalningar. Du kan registrera betalning manuellt, delbetala, anvanda Stripe eller importera bank-CSV. Bankimporten kan matcha mot OCR, fakturanummer, kundnamn eller belopp och sedan registrera betalningen. Automatiska bankregler kan dessutom foresla kostnadskonto, moms och bokforingstext for utbetalningar. Importpanelen har ${bankImportRows.length} importerade rader just nu.`
        : `${answerIntro} For bank and payments: go to Payments. You can register payment manually, partial-pay, use Stripe or import bank CSV. The bank import can match by OCR, invoice number, customer name or amount and then register the payment. Automatic bank rules can also suggest expense account, VAT and bookkeeping description for outgoing rows. The import panel currently has ${bankImportRows.length} imported rows.`, "payments");
    }

    if (normalizedQuestion.includes("installning") || normalizedQuestion.includes("smtp") || normalizedQuestion.includes("stripe") || normalizedQuestion.includes("settings")) {
      return createAnswer(language === "sv"
        ? `${answerIntro} For installningar: ga till Installningar. Dar styr du foretagstyp, e-postmallar, SMTP-test, automatiska paminnelser, betalningsinformation, lasning av bokforingsperiod och JSON-sakerhetskopia. Du kan ocksa kontrollera en backupfil utan att importera data. Stripe- och SMTP-hemligheter laggs sakrast i IntelliJ Run Configuration eller miljo variabler, inte synligt i koden.`
        : `${answerIntro} For settings: go to Settings. There you control company type, email templates, SMTP test, automatic reminders, payment information, accounting period lock and JSON data backup. You can also verify a backup file without importing data. Stripe and SMTP secrets should be stored in IntelliJ Run Configuration or environment variables, not visibly in code.`, "settings");
    }

    return createAnswer(language === "sv"
      ? `${answerIntro} Jag kan hjalpa med fakturor, bokforing, moms, rapporter, underlag, bankimport, betalningar och installningar. Skriv garna mer konkret, till exempel "hur bokfor jag en kostnad?" eller "hur exporterar jag rapporter?".`
      : `${answerIntro} I can help with invoices, bookkeeping, VAT, reports, receipts, bank import, payments and settings. Try asking more specifically, for example "how do I bookkeep an expense?" or "how do I export reports?".`);
  }

  function createAiAssistantContext() {
    return JSON.stringify({
      activeView,
      openInvoices: invoices.filter((item) => invoiceRemainingAmount(item) > 0).length,
      totalOutstanding,
      vatToPay: vatReport?.vatToPay || 0,
      profitNet,
      monthlyReportMonths: monthlyReportRows.length,
      monthlyReportCashChange: monthlyReportCashChangeTotal,
      monthCloseSelectedMonth,
      monthCloseReadiness: monthlyCloseReadiness,
      monthCloseWarnings: monthlyCloseWarnings,
      monthCloseResult: monthlyCloseRow.result,
      monthCloseVatToPay: monthlyCloseRow.vatToPay,
      vatPeriodFrom,
      vatPeriodTo,
      vatPeriodToPay,
      expenses: expenses.length,
      customers: customers.length,
      selectedCustomer: selectedCustomer?.name || "",
      selectedCustomerOutstanding,
      selectedCustomerOverdueAmount,
      contracts: contracts.length,
      events: auditTrailRows.length,
      openBankRows: activityOpenBankRows,
      cashflowForecast: cashflowProjectedBalance,
      cashflowHorizonDays,
      averageMonthlyCashOut,
      budgetRevenueTarget: budgetMonthlyRevenueTarget,
      budgetExpenseLimit: budgetMonthlyExpenseLimit,
      budgetAfterReserve: budgetCurrentRow?.afterReserve || 0,
      payrollDrafts: payrollDrafts.length,
      payrollBooked: payrollDraftTotals.booked,
      payrollTotalCost: payrollDraftTotals.totalCost,
      sieExportStatus: sieExportStatusText,
      sieExportPeriod: sieExportPeriodLabel,
      sieExportVouchers: filteredJournalGroups.length,
      sieExportDifference: filteredJournalDifference,
      bankImportRows: bankImportRows.length,
      bankImportRules: bankImportRules.length,
      bankImportRuleMatches: bankImportRuleMatchedCount,
      bankImportInvoiceReady: bankImportInvoiceReadyCount,
      bankImportExpenseReady: bankImportExpenseReadyCount,
      bankImportNeedsReview: bankImportNeedsReviewCount,
      bankImportAlreadyBooked: bankImportAlreadyBookedCount,
      taxPlanWarnings,
      taxPlanDueSoon: taxPlanDueSoonCount,
      taxPlanOverdue: taxPlanOverdueCount,
      taxPlanVatDeadline,
      taxPlanDeclarationDeadline
    });
  }

  async function askAiAssistant(questionOverride) {
    const questionText = questionOverride || aiAssistantQuestion;
    const fallbackAnswer = createAiAssistantAnswer(questionText);

    setAiAssistantMessages((current) => [
      ...current,
      { role: "user", text: questionText || (language === "sv" ? "Tom fraga" : "Empty question") }
    ].slice(-8));
    setAiAssistantQuestion("");
    setAiAssistantOpen(true);
    setAiAssistantLoading(true);

    try {
      const response = await fetch(`${apiUrl}/ai/assistant`, {
        method: "POST",
        headers: {
          ...authHeaders(),
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          question: questionText,
          language,
          context: createAiAssistantContext()
        })
      });

      if (!response.ok) {
        throw new Error("AI assistant request failed.");
      }

      const data = await response.json();
      setAiAssistantMessages((current) => [
        ...current,
        {
          role: "assistant",
          text: data.answer || fallbackAnswer.text,
          targetView: data.targetView || fallbackAnswer.targetView,
          provider: data.provider || "local"
        }
      ].slice(-8));
    } catch (error) {
      setAiAssistantMessages((current) => [
        ...current,
        { role: "assistant", text: fallbackAnswer.text, targetView: fallbackAnswer.targetView, provider: "local" }
      ].slice(-8));
    } finally {
      setAiAssistantLoading(false);
    }
  }

  function aiTargetLabel(targetView) {
    const labels = {
      invoices: t.invoices,
      bookkeeping: t.bookkeeping,
      uploaded: t.uploaded,
      activities: t.activities,
      cashflow: t.cashflow,
      budget: t.budget,
      payroll: t.payroll,
      reports: t.reports,
      vat: t.vatReport,
      payments: t.payments,
      settings: t.settings
    };

    return labels[targetView] || "";
  }

  function aiProviderLabel(provider) {
    if (provider === "gemini") {
      return language === "sv" ? "Gemini AI" : "Gemini AI";
    }

    if (provider === "huggingface") {
      return language === "sv" ? "Hugging Face AI" : "Hugging Face AI";
    }

    return language === "sv" ? "Lokal fallback" : "Local fallback";
  }

  function aiContextQuestions() {
    const fallback = language === "sv"
      ? ["Hur bokfor jag?", "Rapporter?", "Underlag?"]
      : ["How do I bookkeep?", "Reports?", "Receipts?"];
    const questionsByView = {
      overview: language === "sv"
        ? ["Vad ska jag kontrollera idag?", "Vad har hant i foretaget?", "Vad betyder moms att betala?"]
        : ["What should I check today?", "What happened in the business?", "What does VAT to pay mean?"],
      customers: language === "sv"
        ? ["Hur skapar jag en kund?", "Varfor arkivera kund?", "Hur exporterar jag kunder?"]
        : ["How do I create a customer?", "Why archive a customer?", "How do I export customers?"],
      invoices: language === "sv"
        ? ["Hur skapar jag faktura?", "Hur skickar jag PDF?", "Hur skapar jag kreditfaktura?"]
        : ["How do I create an invoice?", "How do I send PDF?", "How do I create a credit invoice?"],
      payments: language === "sv"
        ? ["Hur registrerar jag betalning?", "Hur funkar bankimport?", "Hur gor jag delbetalning?"]
        : ["How do I register payment?", "How does bank import work?", "How do I make partial payment?"],
      activities: language === "sv"
        ? ["Vad visar Handelser?", "Hur hittar jag en bankrad?", "Hur exporterar jag handelser?"]
        : ["What does Events show?", "How do I find a bank row?", "How do I export events?"],
      cashflow: language === "sv"
        ? ["Hur ser kassan ut?", "Klarar jag momsbetalningen?", "Vad betyder kostnadsreserv?"]
        : ["How does cash look?", "Can I cover the VAT payment?", "What does expense reserve mean?"],
      budget: language === "sv"
        ? ["Ligger jag pa budget?", "Vad betyder reserv?", "Hur justerar jag mina mal?"]
        : ["Am I on budget?", "What does reserve mean?", "How do I adjust my goals?"],
      payroll: language === "sv"
        ? ["Hur skapar jag loneutkast?", "Hur bokfors lon?", "Vad betyder arbetsgivaravgift?"]
        : ["How do I create payroll draft?", "How is payroll booked?", "What is employer contribution?"],
      uploaded: language === "sv"
        ? ["Hur laddar jag upp underlag?", "Vilka kvitton maste sparas?", "Hur kopplas underlag till kostnad?"]
        : ["How do I upload receipts?", "Which receipts must be kept?", "How are receipts linked to expenses?"],
      bookkeeping: language === "sv"
        ? ["Hur laser jag verifikat?", "Vad betyder debet och kredit?", "Hur skapar jag rattelse?"]
        : ["How do I read vouchers?", "What do debit and credit mean?", "How do I create a correction?"],
      accounts: language === "sv"
        ? ["Vad ar kontoplan?", "Vilket konto ska jag anvanda?", "Hur laser jag huvudbok?"]
        : ["What is a chart of accounts?", "Which account should I use?", "How do I read the ledger?"],
      vat: language === "sv"
        ? ["Hur fungerar momsrapport?", "Vad ar utgaende moms?", "Vad ar ingaende moms?"]
        : ["How does VAT report work?", "What is output VAT?", "What is input VAT?"],
      reports: language === "sv"
        ? ["Hur gor jag manadsavslut?", "Hur laser jag resultatrapport?", "Hur exporterar jag rapport?"]
        : ["How do I do monthly close?", "How do I read profit and loss?", "How do I export a report?"],
      settings: language === "sv"
        ? ["Hur staller jag in SMTP?", "Hur staller jag in Stripe?", "Vad betyder periodlasning?"]
        : ["How do I set up SMTP?", "How do I set up Stripe?", "What is period locking?"]
    };

    return questionsByView[activeView] || fallback;
  }

  const expensesWithReceipt = expenses.filter((expense) => expenseHasReceipt(expense));
  const expensesMissingReceipt = expenses.filter((expense) => !expenseHasReceipt(expense));
  const expenseCategories = [...new Set(expenses.map((expense) => expense.category).filter(Boolean))]
    .sort((first, second) => expenseCategoryLabel(first).localeCompare(expenseCategoryLabel(second), "sv"));
  const filteredExpenses = expenses.filter((expense) => {
    if (expenseFilter === "withReceipt" && !expenseHasReceipt(expense)) return false;
    if (expenseFilter === "missingReceipt" && expenseHasReceipt(expense)) return false;
    if (expenseCategoryFilter !== "all" && expense.category !== expenseCategoryFilter) return false;
    if (expenseDateFrom && (!expense.expenseDate || expense.expenseDate < expenseDateFrom)) return false;
    if (expenseDateTo && (!expense.expenseDate || expense.expenseDate > expenseDateTo)) return false;
    const query = expenseSearch.toLowerCase().trim();
    if (query) {
      return [
        expense.description,
        expenseCategoryLabel(expense.category),
        expense.expenseDate,
        expense.receiptFileName,
        expense.totalAmount,
        expense.netAmount,
        expense.vatAmount
      ].some((value) => String(value || "").toLowerCase().includes(query));
    }
    return true;
  }).sort((first, second) => new Date(second.expenseDate || 0) - new Date(first.expenseDate || 0));
  const filteredExpenseNet = filteredExpenses.reduce((sum, item) => sum + (item.netAmount || 0), 0);
  const filteredExpenseTotal = filteredExpenses.reduce((sum, item) => sum + (item.totalAmount || 0), 0);
  const filteredExpenseVat = filteredExpenses.reduce((sum, item) => sum + (item.vatAmount || 0), 0);
  const filteredExpensesWithReceipt = filteredExpenses.filter((expense) => expenseHasReceipt(expense));
  const filteredExpensesMissingReceipt = filteredExpenses.filter((expense) => !expenseHasReceipt(expense));
  const filteredReceiptTotal = filteredExpensesWithReceipt.reduce((sum, item) => sum + (item.totalAmount || 0), 0);
  const filteredMissingReceiptTotal = filteredExpensesMissingReceipt.reduce((sum, item) => sum + (item.totalAmount || 0), 0);
  const bokioImportReviewCount = bokioImportQueue.filter((row) => row.status === "review").length;
  const bokioImportReadyCount = bokioImportQueue.filter((row) => row.status === "ready").length;
  const bokioImportImportedCount = bokioImportQueue.filter((row) => row.status === "imported").length;
  const bokioImportAnalyzedCount = bokioImportQueue.filter((row) => row.analysis && !row.analysis.error).length;
  const expenseCategorySummary = Object.values(filteredExpenses.reduce((groups, expense) => {
    const key = expense.category || "unknown";

    if (!groups[key]) {
      groups[key] = {
        category: key,
        label: expenseCategoryLabel(key),
        count: 0,
        net: 0,
        vat: 0,
        total: 0
      };
    }

    groups[key].count += 1;
    groups[key].net += expense.netAmount || 0;
    groups[key].vat += expense.vatAmount || 0;
    groups[key].total += expense.totalAmount || 0;

    return groups;
  }, {})).sort((first, second) => second.total - first.total);
  const filteredInvoices = invoices.filter((item) => {
    if (invoiceFilter === "draft") return (item.status || "DRAFT") === "DRAFT";
    if (invoiceFilter === "sent") return item.status === "SENT";
    if (invoiceFilter === "paid") return item.status === "PAID";
    if (invoiceFilter === "unpaid") return item.status !== "PAID";
    if (invoiceFilter === "overdue") return invoiceIsOverdue(item);
    if (invoiceFilter === "dueSoon") return invoiceShouldSendReminder(item, settings?.invoiceReminderDaysBeforeDue || 5) && !invoiceIsOverdue(item);
    return true;
  }).filter((item) => {
    const invoiceDate = item.invoiceDate || String(item.createdAt || "").slice(0, 10);
    if (invoiceDateFrom && (!invoiceDate || invoiceDate < invoiceDateFrom)) return false;
    if (invoiceDateTo && (!invoiceDate || invoiceDate > invoiceDateTo)) return false;
    return true;
  }).filter((item) => {
    const query = invoiceSearch.toLowerCase().trim();

    if (!query) return true;

    return [
      invoiceNumber(item),
      item.customerName,
      item.status,
      item.product?.name,
      item.ocrNumber
    ].some((value) => String(value || "").toLowerCase().includes(query));
  }).sort(compareInvoices);
  const filteredInvoiceTotal = filteredInvoices.reduce((sum, item) => sum + invoiceTotalAmount(item), 0);
  const filteredInvoicePaid = filteredInvoices.reduce((sum, item) => sum + invoicePaidAmount(item), 0);
  const filteredInvoiceOutstanding = filteredInvoices.reduce((sum, item) => sum + invoiceRemainingAmount(item), 0);
  const filteredInvoiceVat = filteredInvoices.reduce((sum, item) => sum + invoiceVatAmount(item), 0);
  const filteredCustomers = customers.filter((customer) => {
    if (customerFilter === "active" && customer.archived) return false;
    if (customerFilter === "archived" && !customer.archived) return false;
    if (customerFilter === "outstanding" && customerOutstandingAmount(customer, invoices) <= 0) return false;

    const query = customerSearch.toLowerCase().trim();

    if (!query) return true;

    return [
      customer.name,
      customer.email,
      customer.personalNumber,
      customer.phone,
      customer.city
    ].some((value) => String(value || "").toLowerCase().includes(query));
  }).sort((first, second) => compareCustomers(first, second, invoices));
  const selectedCustomer = customers.find((customer) => String(customer.id) === String(selectedCustomerId));
  const selectedCustomerInvoices = selectedCustomer ? customerInvoices(selectedCustomer, invoices) : [];
  const selectedCustomerPaidInvoices = selectedCustomerInvoices.filter((item) => item.status === "PAID");
  const selectedCustomerUnpaidInvoices = selectedCustomerInvoices.filter((item) => item.status !== "PAID");
  const selectedCustomerTotal = selectedCustomerInvoices.reduce((sum, item) => sum + invoiceTotalAmount(item), 0);
  const selectedCustomerOutstanding = selectedCustomer ? customerOutstandingAmount(selectedCustomer, invoices) : 0;
  const selectedCustomerPaidTotal = selectedCustomerInvoices.reduce((sum, item) => sum + invoicePaidAmount(item), 0);
  const selectedCustomerOverdueInvoices = selectedCustomerInvoices.filter((item) => invoiceIsOverdue(item));
  const selectedCustomerOverdueAmount = selectedCustomerOverdueInvoices.reduce((sum, item) => sum + invoiceRemainingAmount(item), 0);
  const selectedCustomerLatestInvoice = selectedCustomerInvoices
    .slice()
    .sort((first, second) => String(second.invoiceDate || second.createdAt || "").localeCompare(String(first.invoiceDate || first.createdAt || "")))[0] || null;
  const selectedCustomerLastPayment = selectedCustomerInvoices
    .flatMap((item) => (item.payments || []).map((payment) => ({
      ...payment,
      invoiceNumber: invoiceNumber(item),
      customerName: item.customerName || item.customer?.name || ""
    })))
    .sort((first, second) => String(second.paymentDate || second.createdAt || "").localeCompare(String(first.paymentDate || first.createdAt || "")))[0] || null;
  const selectedCustomerRecentActivity = selectedCustomerInvoices
    .flatMap((item) => [
      {
        id: `invoice-${item.id}`,
        date: item.invoiceDate || String(item.createdAt || "").slice(0, 10),
        title: language === "sv" ? "Faktura skapad" : "Invoice created",
        detail: `${invoiceNumber(item)} - ${invoiceTotalAmount(item)} SEK`,
        action: () => {
          setActiveView("invoices");
          setInvoiceSearch(invoiceNumber(item));
        }
      },
      ...(item.payments || []).map((payment) => ({
        id: `payment-${payment.id}`,
        date: payment.paymentDate || String(payment.createdAt || "").slice(0, 10),
        title: language === "sv" ? "Betalning registrerad" : "Payment registered",
        detail: `${invoiceNumber(item)} - ${payment.amount || 0} SEK`,
        action: () => {
          setActiveView("payments");
          setPaymentOverviewSearch(invoiceNumber(item));
        }
      })),
      ...(item.reminders || []).map((reminder) => ({
        id: `reminder-${reminder.id}`,
        date: String(reminder.createdAt || "").slice(0, 10),
        title: invoiceHistoryLabel(reminder, language),
        detail: `${invoiceNumber(item)} - ${reminder.recipientEmail || ""}`,
        action: () => {
          setActiveView("invoices");
          setInvoiceSearch(invoiceNumber(item));
        }
      }))
    ])
    .filter((activity) => activity.date)
    .sort((first, second) => String(second.date || "").localeCompare(String(first.date || "")))
    .slice(0, 6);
  const selectedCustomerNextAction = !selectedCustomer
    ? null
    : selectedCustomerOverdueInvoices.length > 0
      ? {
        status: "warning",
        title: language === "sv" ? "Forfallen betalning" : "Overdue payment",
        detail: `${selectedCustomerOverdueInvoices.length} ${language === "sv" ? "fakturor" : "invoices"} - ${selectedCustomerOverdueAmount} SEK`,
        label: language === "sv" ? "Oppna betalningar" : "Open payments",
        action: () => {
          setActiveView("payments");
          setPaymentOverviewFilter("overdue");
          setPaymentOverviewSearch(selectedCustomer.name || "");
        }
      }
      : selectedCustomerOutstanding > 0
        ? {
          status: "info",
          title: language === "sv" ? "Obetalda fakturor" : "Unpaid invoices",
          detail: `${selectedCustomerOutstanding} SEK ${language === "sv" ? "kvar att betala" : "remaining"}`,
          label: language === "sv" ? "Visa betalningar" : "View payments",
          action: () => {
            setActiveView("payments");
            setPaymentOverviewFilter("open");
            setPaymentOverviewSearch(selectedCustomer.name || "");
          }
        }
        : {
          status: "ok",
          title: language === "sv" ? "Kunden ar avstamd" : "Customer reconciled",
          detail: language === "sv" ? "Inga obetalda fakturor just nu." : "No unpaid invoices right now.",
          label: language === "sv" ? "Skapa faktura" : "Create invoice",
          action: () => {
            setActiveView("invoices");
            setSelectedCustomerId(String(selectedCustomer.id));
          }
        };
  const activeCustomers = customers.filter((customer) => !customer.archived);
  const archivedCustomers = customers.filter((customer) => customer.archived);
  const customersWithOutstanding = customers.filter((customer) => customerOutstandingAmount(customer, invoices) > 0);
  const selectedService = services.find((service) => String(service.id) === String(selectedServiceId));
  const invoicePreview = invoicePreviewFor(selectedService, invoiceQuantity);
  const reminderPreviewInvoice = invoices.find((item) => invoiceRemainingAmount(item) > 0) || demoInvoiceForReminderPreview(settings);
  const sortedJournalEntries = [...journalEntries].sort((first, second) => {
    return new Date(second.voucherDate || second.createdAt || 0) - new Date(first.voucherDate || first.createdAt || 0);
  });
  const correctedVoucherNumbers = new Set(
    journalEntries
      .map((entry) => entry.correctionOfVoucherNumber)
      .filter(Boolean)
  );
  const journalGroups = Object.values(sortedJournalEntries.reduce((groups, entry) => {
    const key = entry.voucherNumber || "Utan verifikat";

    if (!groups[key]) {
      groups[key] = {
        voucherNumber: key,
        createdAt: entry.createdAt,
        voucherDate: entry.voucherDate,
        description: entry.description,
        correctionOfVoucherNumber: entry.correctionOfVoucherNumber || "",
        hasCorrection: correctedVoucherNumbers.has(key),
        rows: [],
        debit: 0,
        credit: 0
      };
    }

    groups[key].rows.push(entry);
    if (entry.correctionOfVoucherNumber) {
      groups[key].correctionOfVoucherNumber = entry.correctionOfVoucherNumber;
    }
    groups[key].hasCorrection = correctedVoucherNumbers.has(key);
    groups[key].debit += entry.debit || 0;
    groups[key].credit += entry.credit || 0;

    if (new Date(entry.voucherDate || entry.createdAt || 0) > new Date(groups[key].voucherDate || groups[key].createdAt || 0)) {
      groups[key].createdAt = entry.createdAt;
      groups[key].voucherDate = entry.voucherDate;
      groups[key].description = entry.description;
    }

    return groups;
  }, {})).sort((first, second) => new Date(second.voucherDate || second.createdAt || 0) - new Date(first.voucherDate || first.createdAt || 0));
  const filteredJournalGroups = journalGroups.filter((group) => {
    if (bookkeepingFilter === "original") return !group.correctionOfVoucherNumber;
    if (bookkeepingFilter === "corrections") return Boolean(group.correctionOfVoucherNumber);
    if (bookkeepingFilter === "corrected") return group.hasCorrection;
    return true;
  }).filter((group) => {
    const voucherDate = group.voucherDate || String(group.createdAt || "").slice(0, 10);
    if (bookkeepingDateFrom && (!voucherDate || voucherDate < bookkeepingDateFrom)) return false;
    if (bookkeepingDateTo && (!voucherDate || voucherDate > bookkeepingDateTo)) return false;
    return true;
  }).filter((group) => {
    const query = bookkeepingSearch.toLowerCase().trim();

    if (!query) return true;

    return [
      group.voucherNumber,
      group.description,
      group.voucherDate,
      group.createdAt,
      group.debit,
      group.credit,
      group.correctionOfVoucherNumber,
      group.hasCorrection ? "har rattelse has correction" : "",
      group.correctionOfVoucherNumber ? "rattelse correction" : "original"
    ].some((value) => String(value || "").toLowerCase().includes(query))
      || group.rows.some((entry) => [
        entry.accountNumber,
        entry.accountName,
        entry.description,
        entry.debit,
        entry.credit,
        entry.voucherDate,
        entry.correctionOfVoucherNumber
      ].some((value) => String(value || "").toLowerCase().includes(query)));
  });
  const filteredJournalDebit = filteredJournalGroups.reduce((sum, group) => sum + group.debit, 0);
  const filteredJournalCredit = filteredJournalGroups.reduce((sum, group) => sum + group.credit, 0);
  const filteredJournalDifference = filteredJournalDebit - filteredJournalCredit;
  const monthlyCloseRange = monthRangeFromKey(monthCloseSelectedMonth);
  const monthlyCloseRow = monthlyReportRows.find((row) => row.monthKey === monthCloseSelectedMonth) || {
    monthKey: monthCloseSelectedMonth,
    revenue: 0,
    expenses: 0,
    result: 0,
    outputVat: 0,
    inputVat: 0,
    vatToPay: 0,
    cashIn: 0,
    cashOut: 0,
    cashChange: 0,
    voucherCount: 0
  };
  const monthlyCloseInvoices = invoices.filter((item) => {
    const invoiceDate = item.invoiceDate || String(item.createdAt || "").slice(0, 10);
    return invoiceDate >= monthlyCloseRange.from && invoiceDate <= monthlyCloseRange.to;
  });
  const monthlyCloseOpenInvoices = monthlyCloseInvoices.filter((item) => invoiceRemainingAmount(item) > 0);
  const monthlyCloseOverdueInvoices = monthlyCloseOpenInvoices.filter((item) => invoiceIsOverdue(item));
  const monthlyCloseExpenses = expenses.filter((expense) => {
    const expenseDateValue = expense.expenseDate || String(expense.createdAt || "").slice(0, 10);
    return expenseDateValue >= monthlyCloseRange.from && expenseDateValue <= monthlyCloseRange.to;
  });
  const monthlyCloseMissingReceipts = monthlyCloseExpenses.filter((expense) => !expenseHasReceipt(expense));
  const monthlyCloseJournalGroups = journalGroups.filter((group) => {
    const voucherDate = group.voucherDate || String(group.createdAt || "").slice(0, 10);
    return voucherDate >= monthlyCloseRange.from && voucherDate <= monthlyCloseRange.to;
  });
  const monthlyCloseUnbalancedGroups = monthlyCloseJournalGroups.filter((group) => (group.debit || 0) !== (group.credit || 0));
  const monthlyCloseBankRows = bankImportRows.filter((row) => {
    const rowDate = bankImportPaymentDate(row);
    return rowDate >= monthlyCloseRange.from && rowDate <= monthlyCloseRange.to;
  });
  const monthlyCloseBankNeedsReview = monthlyCloseBankRows.filter((row) => ["expenseReview", "incomingReview"].includes(bankImportWorkflow(row).key));
  const monthlyCloseBudgetRow = budgetRows.find((row) => row.monthKey === monthCloseSelectedMonth);
  const monthlyCloseChecklist = [
    {
      key: "bookkeeping",
      status: monthlyCloseUnbalancedGroups.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Bokforing balanserar" : "Bookkeeping balances",
      detail: monthlyCloseUnbalancedGroups.length === 0
        ? `${monthlyCloseJournalGroups.length} ${language === "sv" ? "verifikat i manaden" : "vouchers in month"}`
        : `${monthlyCloseUnbalancedGroups.length} ${language === "sv" ? "obalanserade verifikat" : "unbalanced vouchers"}`,
      actionLabel: language === "sv" ? "Oppna bokforing" : "Open bookkeeping",
      action: () => {
        setActiveView("bookkeeping");
        setBookkeepingDateFrom(monthlyCloseRange.from);
        setBookkeepingDateTo(monthlyCloseRange.to);
        setBookkeepingSearch(monthlyCloseUnbalancedGroups[0]?.voucherNumber || "");
      }
    },
    {
      key: "receipts",
      status: monthlyCloseMissingReceipts.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Underlag finns" : "Receipts exist",
      detail: monthlyCloseMissingReceipts.length === 0
        ? `${monthlyCloseExpenses.length} ${language === "sv" ? "kostnader kontrollerade" : "expenses checked"}`
        : `${monthlyCloseMissingReceipts.length} ${language === "sv" ? "kostnader saknar underlag" : "expenses missing receipts"}`,
      actionLabel: language === "sv" ? "Oppna underlag" : "Open receipts",
      action: () => {
        setActiveView("uploaded");
        setExpenseFilter("missingReceipt");
        setExpenseDateFrom(monthlyCloseRange.from);
        setExpenseDateTo(monthlyCloseRange.to);
      }
    },
    {
      key: "invoices",
      status: monthlyCloseOpenInvoices.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Fakturor avstamda" : "Invoices reconciled",
      detail: monthlyCloseOpenInvoices.length === 0
        ? `${monthlyCloseInvoices.length} ${language === "sv" ? "fakturor i manaden" : "invoices in month"}`
        : `${monthlyCloseOpenInvoices.length} ${language === "sv" ? "oppna, varav" : "open, including"} ${monthlyCloseOverdueInvoices.length} ${language === "sv" ? "forfallna" : "overdue"}`,
      actionLabel: language === "sv" ? "Oppna betalningar" : "Open payments",
      action: () => {
        setActiveView("payments");
        setPaymentOverviewFilter("open");
        setPaymentOverviewSearch("");
      }
    },
    {
      key: "bank",
      status: monthlyCloseBankNeedsReview.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Bankimport kontrollerad" : "Bank import reviewed",
      detail: monthlyCloseBankNeedsReview.length === 0
        ? `${monthlyCloseBankRows.length} ${language === "sv" ? "bankrader i manaden" : "bank rows in month"}`
        : `${monthlyCloseBankNeedsReview.length} ${language === "sv" ? "bankrader behover kontroll" : "bank rows need review"}`,
      actionLabel: language === "sv" ? "Oppna bankimport" : "Open bank import",
      action: () => {
        setActiveView("payments");
        setBankImportFilter(monthlyCloseBankNeedsReview.length > 0 ? "review" : "all");
      }
    },
    {
      key: "vat",
      status: monthlyCloseRow.voucherCount > 0 ? "ok" : "info",
      title: language === "sv" ? "Moms beraknad" : "VAT calculated",
      detail: `${monthlyCloseRow.vatToPay} SEK ${language === "sv" ? "preliminar moms" : "preliminary VAT"}`,
      actionLabel: language === "sv" ? "Oppna moms" : "Open VAT",
      action: () => {
        setActiveView("vat");
        setVatPeriodFrom(monthlyCloseRange.from);
        setVatPeriodTo(monthlyCloseRange.to);
      }
    },
    {
      key: "budget",
      status: !monthlyCloseBudgetRow || monthlyCloseBudgetRow.status === "ok" ? "ok" : monthlyCloseBudgetRow.status === "risk" ? "warning" : "info",
      title: language === "sv" ? "Budget jamford" : "Budget compared",
      detail: monthlyCloseBudgetRow
        ? `${monthlyCloseBudgetRow.statusLabel}: ${monthlyCloseBudgetRow.afterReserve} SEK ${language === "sv" ? "efter reserv" : "after reserve"}`
        : (language === "sv" ? "Ingen budgetrad for manaden" : "No budget row for month"),
      actionLabel: language === "sv" ? "Oppna budget" : "Open budget",
      action: () => setActiveView("budget")
    }
  ];
  const monthlyCloseWarnings = monthlyCloseChecklist.filter((item) => item.status === "warning").length;
  const monthlyCloseReadiness = Math.round(((monthlyCloseChecklist.length - monthlyCloseWarnings) / monthlyCloseChecklist.length) * 100);
  const monthlyCloseStatusText = monthlyCloseWarnings === 0
    ? (language === "sv" ? "Manaden ser redo ut" : "Month looks ready")
    : `${monthlyCloseWarnings} ${language === "sv" ? "saker att kontrollera" : "items to check"}`;
  const filteredJournalMonthlySummary = Object.values(filteredJournalGroups.reduce((summary, group) => {
    const voucherDate = group.voucherDate || String(group.createdAt || "").slice(0, 10);
    const monthKey = voucherDate ? voucherDate.slice(0, 7) : "unknown";

    if (!summary[monthKey]) {
      summary[monthKey] = {
        monthKey,
        voucherCount: 0,
        debit: 0,
        credit: 0
      };
    }

    summary[monthKey].voucherCount += 1;
    summary[monthKey].debit += group.debit || 0;
    summary[monthKey].credit += group.credit || 0;

    return summary;
  }, {})).sort((first, second) => String(second.monthKey).localeCompare(String(first.monthKey)));
  const filteredJournalAccountSummary = Object.values(filteredJournalGroups.reduce((summary, group) => {
    group.rows.forEach((entry) => {
      const key = entry.accountNumber || "unknown";

      if (!summary[key]) {
        summary[key] = {
          accountNumber: entry.accountNumber || "-",
          accountName: entry.accountName || "-",
          debit: 0,
          credit: 0
        };
      }

      summary[key].debit += entry.debit || 0;
      summary[key].credit += entry.credit || 0;
    });

    return summary;
  }, {})).sort((first, second) => String(first.accountNumber).localeCompare(String(second.accountNumber)));
  const sieExportAccountCount = new Set(filteredJournalGroups.flatMap((group) => group.rows.map((entry) => entry.accountNumber).filter(Boolean))).size;
  const sieExportVoucherDates = filteredJournalGroups
    .map((group) => String(group.voucherDate || group.createdAt || "").slice(0, 10))
    .filter((date) => /^\d{4}-\d{2}-\d{2}$/.test(date))
    .sort();
  const sieExportPeriodLabel = `${bookkeepingDateFrom || sieExportVoucherDates[0] || "-"} - ${bookkeepingDateTo || sieExportVoucherDates[sieExportVoucherDates.length - 1] || "-"}`;
  const sieExportCanDownload = filteredJournalGroups.length > 0 && filteredJournalDifference === 0;
  const sieExportStatusText = filteredJournalGroups.length === 0
    ? (language === "sv" ? "Inga verifikationer i urvalet" : "No vouchers in selection")
    : filteredJournalDifference === 0
      ? (language === "sv" ? "Redo for SIE-export" : "Ready for SIE export")
      : (language === "sv" ? "Obalanserat urval" : "Unbalanced selection");
  const bookkeepingAccountSearch = /^\d{4}$/.test(bookkeepingSearch.trim()) ? bookkeepingSearch.trim() : "";
  const activeBookkeepingAccount = bookkeepingAccountSearch
    ? filteredJournalAccountSummary.find((account) => account.accountNumber === bookkeepingAccountSearch)
      || accounts.find((account) => account.number === bookkeepingAccountSearch)
    : null;
  const bookkeepingVoucherSearch = /^[A-Z]{1,4}-\d+$/i.test(bookkeepingSearch.trim())
    ? bookkeepingSearch.trim().toUpperCase()
    : "";
  const activeBookkeepingVoucher = bookkeepingVoucherSearch
    ? filteredJournalGroups.find((group) => group.voucherNumber === bookkeepingVoucherSearch)
      || journalGroups.find((group) => group.voucherNumber === bookkeepingVoucherSearch)
    : null;
  const activeBookkeepingVoucherDifference = activeBookkeepingVoucher
    ? activeBookkeepingVoucher.debit - activeBookkeepingVoucher.credit
    : 0;
  const unbalancedJournalGroups = journalGroups.filter((group) => (group.debit || 0) !== (group.credit || 0));
  const closeChecklistItems = [
    {
      key: "balanced-vouchers",
      status: unbalancedJournalGroups.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Verifikat balanserar" : "Vouchers are balanced",
      value: unbalancedJournalGroups.length === 0
        ? (language === "sv" ? "Alla verifikat balanserar" : "All vouchers balance")
        : `${unbalancedJournalGroups.length} ${language === "sv" ? "obalanserade verifikat" : "unbalanced vouchers"}`,
      action: () => {
        setActiveView("bookkeeping");
        setBookkeepingSearch(unbalancedJournalGroups[0]?.voucherNumber || "");
      }
    },
    {
      key: "missing-receipts",
      status: expensesMissingReceipt.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Underlag sparade" : "Receipts saved",
      value: expensesMissingReceipt.length === 0
        ? (language === "sv" ? "Alla kostnader har underlag" : "All expenses have receipts")
        : `${expensesMissingReceipt.length} ${language === "sv" ? "kostnader saknar underlag" : "expenses missing receipts"}`,
      action: () => {
        setActiveView("uploaded");
        setExpenseFilter("missingReceipt");
      }
    },
    {
      key: "open-invoices",
      status: totalOutstanding === 0 ? "ok" : "warning",
      title: language === "sv" ? "Kundfordringar" : "Customer receivables",
      value: totalOutstanding === 0
        ? (language === "sv" ? "Inga obetalda fakturor" : "No unpaid invoices")
        : `${totalOutstanding} SEK ${language === "sv" ? "kvar att fa betalt" : "outstanding"}`,
      action: () => {
        setActiveView("payments");
        setPaymentOverviewFilter("open");
      }
    },
    {
      key: "overdue-invoices",
      status: overdueInvoices.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Forfallna fakturor" : "Overdue invoices",
      value: overdueInvoices.length === 0
        ? (language === "sv" ? "Inga forfallna fakturor" : "No overdue invoices")
        : `${overdueInvoices.length} ${language === "sv" ? "forfallna fakturor" : "overdue invoices"}`,
      action: () => {
        setActiveView("payments");
        setPaymentOverviewFilter("overdue");
      }
    },
    {
      key: "vat",
      status: vatPeriodHasData ? "info" : "warning",
      title: language === "sv" ? "Momsavstamning" : "VAT reconciliation",
      value: vatPeriodHasData
        ? `${vatPeriodToPay} SEK`
        : (language === "sv" ? "Ingen momsdata i vald period" : "No VAT data in selected period"),
      action: () => setActiveView("vat")
    },
    {
      key: "period-lock",
      status: accountingLockedThroughDate ? "ok" : "info",
      title: language === "sv" ? "Periodlasning" : "Period lock",
      value: accountingLockedThroughDate
        ? `${language === "sv" ? "Last till" : "Locked through"} ${formatDateOnly(accountingLockedThroughDate)}`
        : (language === "sv" ? "Ingen period ar last" : "No period is locked"),
      action: () => setActiveView("settings")
    }
  ];
  const closeChecklistWarnings = closeChecklistItems.filter((item) => item.status === "warning").length;
  const closeReadinessScore = closeChecklistItems.length === 0
    ? 0
    : Math.round(((closeChecklistItems.length - closeChecklistWarnings) / closeChecklistItems.length) * 100);
  const archivePeriodLabel = `${vatPeriodFrom || "-"} - ${vatPeriodTo || "-"}`;
  const archivePackageItems = [
    {
      key: "manifest",
      status: "info",
      title: language === "sv" ? "Arkivmanifest" : "Archive manifest",
      count: archivePeriodLabel,
      description: language === "sv"
        ? "Sammanfattning av vilka delar som ska sparas for perioden."
        : "Summary of which parts should be stored for the period.",
      actionLabel: language === "sv" ? "Exportera manifest" : "Export manifest",
      action: downloadArchiveManifestCsv
    },
    {
      key: "close-checklist",
      status: closeChecklistWarnings === 0 ? "ok" : "warning",
      title: language === "sv" ? "Kontrollista" : "Checklist",
      count: `${closeReadinessScore}%`,
      description: language === "sv"
        ? "Visar om bokforing, underlag, kundfordringar, moms och periodlasning ser redo ut."
        : "Shows whether bookkeeping, receipts, receivables, VAT and period lock look ready.",
      actionLabel: t.exportCsv,
      action: downloadCloseChecklistCsv
    },
    {
      key: "data-quality",
      status: "info",
      title: language === "sv" ? "Datahalsa" : "Data quality",
      count: "CSV",
      description: language === "sv"
        ? "Kontrollerar dubbletter, saknade uppgifter, underlag, forfallna fakturor och obalanserade verifikat."
        : "Checks duplicates, missing details, receipts, overdue invoices and unbalanced vouchers.",
      actionLabel: t.exportCsv,
      action: downloadDataQualityCsv
    },
    {
      key: "monthly-close",
      status: monthlyCloseWarnings === 0 ? "ok" : "warning",
      title: language === "sv" ? "Manadsavslut" : "Monthly close",
      count: `${monthlyCloseReadiness}%`,
      description: language === "sv"
        ? "Exporterar vald manads kontrollpunkter, nyckeltal och status for snabb avstamning."
        : "Exports the selected month's checks, metrics and status for quick reconciliation.",
      actionLabel: t.exportCsv,
      action: downloadMonthlyCloseCsv
    },
    {
      key: "journal",
      status: unbalancedJournalGroups.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Verifikationer och huvudbok" : "Vouchers and ledger",
      count: `${journalGroups.length} ${language === "sv" ? "verifikat" : "vouchers"}`,
      description: language === "sv"
        ? "Exportera bokforingsrader och anvand huvudbok per konto i Bokforing."
        : "Export bookkeeping rows and use account ledger in Bookkeeping.",
      actionLabel: t.exportCsv,
      action: downloadJournalCsv
    },
    {
      key: "invoices",
      status: filteredInvoices.length > 0 ? "ok" : "info",
      title: language === "sv" ? "Fakturor" : "Invoices",
      count: `${invoices.length} ${language === "sv" ? "fakturor" : "invoices"}`,
      description: language === "sv"
        ? "Spara fakturalista och ladda ner PDF pa viktiga fakturor."
        : "Save invoice list and download PDF for important invoices.",
      actionLabel: t.exportCsv,
      action: downloadInvoicesCsv
    },
    {
      key: "receipts",
      status: expensesMissingReceipt.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Underlag och kostnader" : "Receipts and expenses",
      count: `${filteredExpensesWithReceipt.length}/${expenses.length}`,
      description: language === "sv"
        ? "Kontrollera att kvitton och fakturaunderlag finns sparade for kostnader."
        : "Check that receipts and invoice documents are saved for expenses.",
      actionLabel: language === "sv" ? "Oppna underlag" : "Open receipts",
      action: () => setActiveView("uploaded")
    },
    {
      key: "vat",
      status: vatPeriodHasData ? "ok" : "warning",
      title: language === "sv" ? "Momsrapport" : "VAT report",
      count: `${vatPeriodToPay} SEK`,
      description: language === "sv"
        ? "Spara momsavstamning for vald period och jamfor med Skatteverket innan deklaration."
        : "Save VAT reconciliation for the selected period and compare with the tax authority before filing.",
      actionLabel: t.exportCsv,
      action: downloadVatReconciliationCsv
    },
    {
      key: "tax-plan",
      status: "info",
      title: language === "sv" ? "Skatteplan" : "Tax plan",
      count: "CSV",
      description: language === "sv"
        ? "Planeringslista for moms, skattekonto, deklaration och arkivering. Datum ska kontrolleras hos Skatteverket."
        : "Planning list for VAT, tax account, declaration and archiving. Dates must be verified with the tax authority.",
      actionLabel: t.exportCsv,
      action: downloadTaxPlanCsv
    },
    {
      key: "reports",
      status: profitAndLoss && balanceReport ? "ok" : "info",
      title: language === "sv" ? "Resultat och balans" : "Profit and balance",
      count: `${profitNet} SEK`,
      description: language === "sv"
        ? "Exportera resultatrapport, balansrapport och manadsrapport."
        : "Export profit and loss, balance report and monthly report.",
      actionLabel: language === "sv" ? "Exportera resultat" : "Export profit",
      action: downloadProfitAndLossCsv
    },
    {
      key: "payments",
      status: totalOutstanding === 0 ? "ok" : "warning",
      title: language === "sv" ? "Betalningar och bank" : "Payments and bank",
      count: `${totalOutstanding} SEK ${language === "sv" ? "kvar" : "outstanding"}`,
      description: language === "sv"
        ? "Spara betalningsoversikt, bankimport och bankavstamningshistorik."
        : "Save payment overview, bank import and reconciliation history.",
      actionLabel: t.exportCsv,
      action: downloadPaymentOverviewCsv
    },
    {
      key: "data-backup",
      status: "info",
      title: language === "sv" ? "Sakerhetskopia" : "Data backup",
      count: "JSON",
      description: language === "sv"
        ? "Exporterar en komplett maskinlasbar backup av data, rapportstatus och revisionsspar."
        : "Exports a complete machine-readable backup of data, report status and audit trail.",
      actionLabel: language === "sv" ? "Ladda ner backup" : "Download backup",
      action: downloadDataBackupJson
    }
  ];
  const archivePackageWarnings = archivePackageItems.filter((item) => item.status === "warning").length;
  const archivePackageReadyCount = archivePackageItems.filter((item) => item.status === "ok").length;
  const taxPlanBaseYear = Number((vatPeriodTo || todayInput).slice(0, 4)) || new Date().getFullYear();
  const taxPlanVatDeadline = vatPeriodTo ? addMonthsInputString(vatPeriodTo, 2, 12) : "";
  const taxPlanDeclarationDeadline = `${taxPlanBaseYear + 1}-05-04`;
  const taxPlanItems = [
    {
      key: "bookkeeping",
      status: unbalancedJournalGroups.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Bokfor perioden" : "Book the period",
      deadline: vatPeriodTo || todayInput,
      detail: unbalancedJournalGroups.length === 0
        ? (language === "sv" ? "Alla verifikat balanserar." : "All vouchers balance.")
        : `${unbalancedJournalGroups.length} ${language === "sv" ? "verifikat behover kontroll" : "vouchers need review"}`,
      actionLabel: language === "sv" ? "Oppna bokforing" : "Open bookkeeping",
      action: () => setActiveView("bookkeeping")
    },
    {
      key: "receipts",
      status: expensesMissingReceipt.length === 0 ? "ok" : "warning",
      title: language === "sv" ? "Sakra underlag" : "Secure receipts",
      deadline: vatPeriodTo || todayInput,
      detail: expensesMissingReceipt.length === 0
        ? (language === "sv" ? "Alla kostnader har underlag." : "All expenses have receipts.")
        : `${expensesMissingReceipt.length} ${language === "sv" ? "kostnader saknar underlag" : "expenses missing receipts"}`,
      actionLabel: language === "sv" ? "Oppna underlag" : "Open receipts",
      action: () => {
        setActiveView("uploaded");
        setExpenseFilter("missingReceipt");
      }
    },
    {
      key: "vat-return",
      status: vatPeriodHasData ? "ok" : "warning",
      title: language === "sv" ? "Momsdeklaration" : "VAT return",
      deadline: taxPlanVatDeadline,
      detail: vatPeriodHasData
        ? `${language === "sv" ? "Preliminar moms" : "Preliminary VAT"}: ${vatPeriodToPay} SEK`
        : (language === "sv" ? "Ingen momsdata i vald period." : "No VAT data in selected period."),
      actionLabel: language === "sv" ? "Oppna moms" : "Open VAT",
      action: () => setActiveView("vat")
    },
    {
      key: "tax-account",
      status: vatPeriodToPay > 0 ? "warning" : "ok",
      title: language === "sv" ? "Planera skattekonto" : "Plan tax account",
      deadline: taxPlanVatDeadline,
      detail: vatPeriodToPay > 0
        ? `${language === "sv" ? "Planera betalning" : "Plan payment"}: ${vatPeriodToPay} SEK`
        : (language === "sv" ? "Ingen preliminar moms att betala i vald period." : "No preliminary VAT to pay in selected period."),
      actionLabel: language === "sv" ? "Oppna likviditet" : "Open cashflow",
      action: () => setActiveView("cashflow")
    },
    {
      key: "income-declaration",
      status: "info",
      title: language === "sv" ? "Inkomstdeklaration" : "Income declaration",
      deadline: taxPlanDeclarationDeadline,
      detail: language === "sv"
        ? "Preliminar planeringsrad. Kontrollera exakt datum och bilagor hos Skatteverket."
        : "Preliminary planning row. Verify exact date and attachments with the tax authority.",
      actionLabel: language === "sv" ? "Oppna rapporter" : "Open reports",
      action: () => setActiveView("reports")
    },
    {
      key: "archive",
      status: archivePackageWarnings === 0 ? "ok" : "warning",
      title: language === "sv" ? "Arkivera perioden" : "Archive the period",
      deadline: vatPeriodTo ? addDaysInputString(vatPeriodTo, 7) : todayInput,
      detail: archivePackageWarnings === 0
        ? (language === "sv" ? "Arkivpaketet ser redo ut." : "Archive package looks ready.")
        : `${archivePackageWarnings} ${language === "sv" ? "arkivvarningar kvar" : "archive warnings remaining"}`,
      actionLabel: language === "sv" ? "Oppna arkivpaket" : "Open archive package",
      action: () => setActiveView("reports")
    }
  ].map((item) => {
    const daysUntil = daysUntilInputDate(item.deadline, todayInput);
    const timeStatus = daysUntil === null
      ? "none"
      : daysUntil < 0
        ? "overdue"
        : daysUntil <= 30
          ? "soon"
          : "later";
    const displayStatus = item.status === "warning" && timeStatus === "overdue" ? "critical" : item.status;

    return {
      ...item,
      daysUntil,
      timeStatus,
      displayStatus
    };
  });
  const taxPlanWarnings = taxPlanItems.filter((item) => item.displayStatus === "warning" || item.displayStatus === "critical").length;
  const taxPlanDueSoonCount = taxPlanItems.filter((item) => item.timeStatus === "soon").length;
  const taxPlanOverdueCount = taxPlanItems.filter((item) => item.timeStatus === "overdue").length;
  const selectedPeriodIsLocked = Boolean(
    accountingLockedThroughDate
      && vatPeriodTo
      && accountingLockedThroughDate >= vatPeriodTo
  );
  const selectedPeriodCanBeLocked = Boolean(settings && vatPeriodTo && !selectedPeriodIsLocked);
  const auditTrailRows = [
    ...invoices.map((item) => ({
      id: `invoice-${item.id}`,
      type: "invoice",
      typeLabel: language === "sv" ? "Faktura" : "Invoice",
      date: item.invoiceDate || String(item.createdAt || "").slice(0, 10),
      createdAt: item.createdAt,
      title: language === "sv" ? "Faktura skapad" : "Invoice created",
      reference: invoiceNumber(item),
      party: item.customerName || item.customer?.name || "",
      amount: invoiceTotalAmount(item),
      status: statusLabel(item.status, language),
      action: () => {
        setActiveView("invoices");
        setInvoiceSearch(invoiceNumber(item));
      }
    })),
    ...invoices.flatMap((item) => (item.payments || []).map((payment) => ({
      id: `payment-${payment.id}`,
      type: "payment",
      typeLabel: language === "sv" ? "Betalning" : "Payment",
      date: payment.paymentDate || String(payment.createdAt || "").slice(0, 10),
      createdAt: payment.createdAt,
      title: language === "sv" ? "Betalning registrerad" : "Payment registered",
      reference: invoiceNumber(item),
      party: item.customerName || item.customer?.name || "",
      amount: payment.amount || 0,
      status: payment.reference || item.paymentReference || "-",
      action: () => {
        setActiveView("payments");
        setPaymentOverviewSearch(invoiceNumber(item));
      }
    }))),
    ...invoices.flatMap((item) => (item.reminders || []).map((reminder) => ({
      id: `reminder-${reminder.id}`,
      type: "reminder",
      typeLabel: language === "sv" ? "Paminnelse" : "Reminder",
      date: String(reminder.createdAt || "").slice(0, 10),
      createdAt: reminder.createdAt,
      title: invoiceHistoryLabel(reminder, language),
      reference: invoiceNumber(item),
      party: reminder.recipientEmail || item.customerName || item.customer?.name || "",
      amount: invoiceRemainingAmount(item),
      status: reminder.status || "",
      action: () => {
        setActiveView("invoices");
        setInvoiceSearch(invoiceNumber(item));
      }
    }))),
    ...expenses.map((expense) => ({
      id: `expense-${expense.id}`,
      type: "expense",
      typeLabel: language === "sv" ? "Kostnad" : "Expense",
      date: expense.expenseDate || String(expense.createdAt || "").slice(0, 10),
      createdAt: expense.createdAt,
      title: expense.description || expenseCategoryLabel(expense.category),
      reference: expenseCategoryLabel(expense.category),
      party: expense.paidFrom || "",
      amount: expense.totalAmount || 0,
      status: expenseHasReceipt(expense)
        ? (language === "sv" ? "Underlag sparat" : "Receipt saved")
        : (language === "sv" ? "Underlag saknas" : "Receipt missing"),
      action: () => {
        setActiveView("uploaded");
        setExpenseSearch(expense.description || expense.receiptFileName || "");
      }
    })),
    ...journalGroups.map((group) => ({
      id: `voucher-${group.voucherNumber}`,
      type: "voucher",
      typeLabel: language === "sv" ? "Verifikat" : "Voucher",
      date: group.voucherDate || String(group.createdAt || "").slice(0, 10),
      createdAt: group.createdAt,
      title: group.description || (language === "sv" ? "Bokforingspost" : "Bookkeeping entry"),
      reference: group.voucherNumber,
      party: group.correctionOfVoucherNumber
        ? `${language === "sv" ? "Rattelse av" : "Correction of"} ${group.correctionOfVoucherNumber}`
        : "",
      amount: Math.max(group.debit || 0, group.credit || 0),
      status: (group.debit || 0) === (group.credit || 0)
        ? (language === "sv" ? "Balanserar" : "Balanced")
        : (language === "sv" ? "Obalanserad" : "Unbalanced"),
      action: () => {
        setActiveView("bookkeeping");
        setBookkeepingSearch(group.voucherNumber);
      }
    })),
    ...bankImportRows.map((row) => ({
      id: `bank-open-${row.id}`,
      type: "bank",
      typeLabel: language === "sv" ? "Bankrad" : "Bank row",
      date: row.date || "",
      createdAt: row.date || "",
      title: language === "sv" ? "Importerad bankrad" : "Imported bank row",
      reference: row.reference || "",
      party: row.description || "",
      amount: row.amount || 0,
      status: findBankImportMatch(row)
        ? (language === "sv" ? "Matchningsforslag" : "Match suggestion")
        : (language === "sv" ? "Ohanterad" : "Open"),
      action: () => {
        setActiveView("payments");
        setBankImportSearch(row.reference || row.description || "");
      }
    })),
    ...bankReconciliationHistory.map((entry) => ({
      id: `bank-reconciliation-${entry.id}`,
      type: "bank",
      typeLabel: language === "sv" ? "Bankavstamning" : "Bank reconciliation",
      date: entry.date || String(entry.bookedAt || "").slice(0, 10),
      createdAt: entry.bookedAt,
      title: entry.status === "skipped"
        ? (language === "sv" ? "Bankrad hoppades over" : "Bank row skipped")
        : (language === "sv" ? "Bankrad bokford" : "Bank row booked"),
      reference: entry.reference || "",
      party: entry.description || "",
      amount: entry.amount || 0,
      status: entry.matchLabel || entry.status || "",
      action: () => {
        setActiveView("payments");
        setBankImportSearch(entry.reference || entry.description || "");
      }
    })),
    ...stripeWebsiteSaleEntries.map((entry) => ({
      id: `stripe-sale-${entry.id}`,
      type: "stripe",
      typeLabel: language === "sv" ? "Stripe" : "Stripe",
      date: entry.voucherDate || String(entry.createdAt || "").slice(0, 10),
      createdAt: entry.createdAt,
      title: language === "sv" ? "Stripe-forsaljning fran hemsida" : "Stripe website sale",
      reference: entry.voucherNumber || "",
      party: entry.description || "Stripe",
      amount: entry.debit || 0,
      status: language === "sv" ? "Bokford" : "Booked",
      action: () => {
        setActiveView("bookkeeping");
        setBookkeepingSearch(entry.voucherNumber || "1580");
      }
    })),
    ...stripePayouts.map((payout) => ({
      id: `stripe-payout-${payout.id}`,
      type: "stripe",
      typeLabel: language === "sv" ? "Stripe" : "Stripe",
      date: payout.payoutDate || String(payout.createdAt || "").slice(0, 10),
      createdAt: payout.createdAt,
      title: language === "sv" ? "Stripe-utbetalning till bank" : "Stripe payout to bank",
      reference: payout.reference || payout.voucherNumber || "",
      party: "Stripe",
      amount: payout.netAmount || payout.grossAmount || 0,
      status: `${language === "sv" ? "Avgift" : "Fee"} ${payout.feeAmount || 0} SEK`,
      action: () => {
        setActiveView("payments");
        setBankImportSearch(payout.reference || "");
      }
    })),
    ...(accountingLockedThroughDate ? [{
      id: "period-lock",
      type: "period",
      typeLabel: language === "sv" ? "Period" : "Period",
      date: accountingLockedThroughDate,
      createdAt: accountingLockedThroughDate,
      title: language === "sv" ? "Bokforingsperiod last" : "Bookkeeping period locked",
      reference: accountingLockedThroughDate,
      party: settings?.companyName || "AliBooks",
      amount: 0,
      status: language === "sv" ? "Last" : "Locked",
      action: () => setActiveView("settings")
    }] : [])
  ].sort((first, second) => {
    const firstDate = new Date(first.date || first.createdAt || 0);
    const secondDate = new Date(second.date || second.createdAt || 0);
    if (secondDate.getTime() !== firstDate.getTime()) {
      return secondDate - firstDate;
    }

    return String(second.createdAt || "").localeCompare(String(first.createdAt || ""));
  });
  const filteredAuditTrailRows = auditTrailRows.filter((row) => {
    if (auditTrailFilter !== "all" && row.type !== auditTrailFilter) return false;

    const query = auditTrailSearch.toLowerCase().trim();
    if (!query) return true;

    return [
      row.date,
      row.typeLabel,
      row.title,
      row.reference,
      row.party,
      row.amount,
      row.status,
      row.createdAt
    ].some((value) => String(value || "").toLowerCase().includes(query));
  });
  const auditTrailTypeCounts = auditTrailRows.reduce((counts, row) => {
    counts[row.type] = (counts[row.type] || 0) + 1;
    return counts;
  }, {});
  const filteredActivityRows = auditTrailRows.filter((row) => {
    if (activityFilter !== "all" && row.type !== activityFilter) return false;

    const query = activitySearch.toLowerCase().trim();
    if (!query) return true;

    return [
      row.date,
      row.typeLabel,
      row.title,
      row.reference,
      row.party,
      row.amount,
      row.status,
      row.createdAt
    ].some((value) => String(value || "").toLowerCase().includes(query));
  });
  const activityIncomeTotal = filteredActivityRows
    .filter((row) => ["invoice", "payment", "stripe"].includes(row.type) && (row.amount || 0) > 0)
    .reduce((sum, row) => sum + (row.amount || 0), 0);
  const activityExpenseTotal = filteredActivityRows
    .filter((row) => row.type === "expense" || (row.type === "bank" && (row.amount || 0) < 0))
    .reduce((sum, row) => sum + Math.abs(row.amount || 0), 0);
  const activityOpenBankRows = bankImportRows.length;
  const activityLatestDate = filteredActivityRows[0]?.date || "";
  const duplicateCustomerEmails = Object.entries(customers.reduce((groups, customer) => {
    const key = String(customer.email || "").trim().toLowerCase();
    if (!key) return groups;
    groups[key] = [...(groups[key] || []), customer];
    return groups;
  }, {})).filter(([, group]) => group.length > 1);
  const duplicateCustomerPersonalNumbers = Object.entries(customers.reduce((groups, customer) => {
    const key = String(customer.personalNumber || "").trim();
    if (!key) return groups;
    groups[key] = [...(groups[key] || []), customer];
    return groups;
  }, {})).filter(([, group]) => group.length > 1);
  const dataQualityIssues = [
    ...customers.filter((customer) => !customer.email).map((customer) => ({
      id: `customer-email-${customer.id}`,
      severity: "warning",
      area: "customers",
      areaLabel: language === "sv" ? "Kunder" : "Customers",
      title: language === "sv" ? "Kund saknar e-post" : "Customer missing email",
      detail: customer.name || "-",
      reference: customer.personalNumber || "",
      action: () => {
        setActiveView("customers");
        setCustomerSearch(customer.name || "");
      }
    })),
    ...customers.filter((customer) => !customer.personalNumber).map((customer) => ({
      id: `customer-personal-number-${customer.id}`,
      severity: "warning",
      area: "customers",
      areaLabel: language === "sv" ? "Kunder" : "Customers",
      title: language === "sv" ? "Kund saknar personnummer" : "Customer missing personal number",
      detail: customer.name || "-",
      reference: customer.email || "",
      action: () => {
        setActiveView("customers");
        setCustomerSearch(customer.name || customer.email || "");
      }
    })),
    ...customers.filter((customer) => !customer.address || !customer.postalCode || !customer.city).map((customer) => ({
      id: `customer-address-${customer.id}`,
      severity: "info",
      area: "customers",
      areaLabel: language === "sv" ? "Kunder" : "Customers",
      title: language === "sv" ? "Kundadress ar inte komplett" : "Customer address is incomplete",
      detail: customer.name || "-",
      reference: [customer.address, customer.postalCode, customer.city].filter(Boolean).join(", "),
      action: () => {
        setActiveView("customers");
        setCustomerSearch(customer.name || "");
      }
    })),
    ...duplicateCustomerEmails.map(([email, group]) => ({
      id: `duplicate-email-${email}`,
      severity: "warning",
      area: "customers",
      areaLabel: language === "sv" ? "Kunder" : "Customers",
      title: language === "sv" ? "Dubblett e-post" : "Duplicate email",
      detail: group.map((customer) => customer.name).filter(Boolean).join(", "),
      reference: email,
      action: () => {
        setActiveView("customers");
        setCustomerSearch(email);
      }
    })),
    ...duplicateCustomerPersonalNumbers.map(([personalNumber, group]) => ({
      id: `duplicate-personal-number-${personalNumber}`,
      severity: "warning",
      area: "customers",
      areaLabel: language === "sv" ? "Kunder" : "Customers",
      title: language === "sv" ? "Dubblett personnummer" : "Duplicate personal number",
      detail: group.map((customer) => customer.name).filter(Boolean).join(", "),
      reference: personalNumber,
      action: () => {
        setActiveView("customers");
        setCustomerSearch(personalNumber);
      }
    })),
    ...invoices.filter((item) => !(item.customer?.email || item.customerEmail)).map((item) => ({
      id: `invoice-email-${item.id}`,
      severity: "warning",
      area: "invoices",
      areaLabel: language === "sv" ? "Fakturor" : "Invoices",
      title: language === "sv" ? "Faktura saknar kundens e-post" : "Invoice missing customer email",
      detail: item.customerName || item.customer?.name || "-",
      reference: invoiceNumber(item),
      action: () => {
        setActiveView("invoices");
        setInvoiceSearch(invoiceNumber(item));
      }
    })),
    ...overdueInvoices.map((item) => ({
      id: `invoice-overdue-${item.id}`,
      severity: "warning",
      area: "payments",
      areaLabel: language === "sv" ? "Betalningar" : "Payments",
      title: language === "sv" ? "Faktura ar forfallen" : "Invoice is overdue",
      detail: `${invoiceRemainingAmount(item)} SEK ${language === "sv" ? "kvar att betala" : "outstanding"}`,
      reference: invoiceNumber(item),
      action: () => {
        setActiveView("payments");
        setPaymentOverviewSearch(invoiceNumber(item));
      }
    })),
    ...expensesMissingReceipt.map((expense) => ({
      id: `expense-receipt-${expense.id}`,
      severity: "warning",
      area: "receipts",
      areaLabel: language === "sv" ? "Underlag" : "Receipts",
      title: language === "sv" ? "Kostnad saknar underlag" : "Expense missing receipt",
      detail: expense.description || expenseCategoryLabel(expense.category),
      reference: `${expense.expenseDate || "-"} - ${expense.totalAmount || 0} SEK`,
      action: () => {
        setActiveView("uploaded");
        setExpenseSearch(expense.description || "");
      }
    })),
    ...unbalancedJournalGroups.map((group) => ({
      id: `voucher-unbalanced-${group.voucherNumber}`,
      severity: "critical",
      area: "bookkeeping",
      areaLabel: language === "sv" ? "Bokforing" : "Bookkeeping",
      title: language === "sv" ? "Verifikat balanserar inte" : "Voucher is unbalanced",
      detail: `${language === "sv" ? "Debet" : "Debit"} ${group.debit || 0} / ${language === "sv" ? "Kredit" : "Credit"} ${group.credit || 0}`,
      reference: group.voucherNumber,
      action: () => {
        setActiveView("bookkeeping");
        setBookkeepingSearch(group.voucherNumber);
      }
    })),
    ...(!settings?.contactEmail ? [{
      id: "settings-contact-email",
      severity: "info",
      area: "settings",
      areaLabel: language === "sv" ? "Installningar" : "Settings",
      title: language === "sv" ? "Kontakt-e-post saknas" : "Contact email missing",
      detail: language === "sv" ? "Behovs pa fakturor och mejlmallar." : "Used on invoices and email templates.",
      reference: "",
      action: () => setActiveView("settings")
    }] : []),
    ...(!settings?.plusGiro ? [{
      id: "settings-plusgiro",
      severity: "info",
      area: "settings",
      areaLabel: language === "sv" ? "Installningar" : "Settings",
      title: language === "sv" ? "PlusGiro saknas" : "PlusGiro missing",
      detail: language === "sv" ? "Betalningsinfo blir svagare pa fakturan." : "Payment information is weaker on the invoice.",
      reference: "",
      action: () => setActiveView("settings")
    }] : []),
    ...(!backupValidation ? [{
      id: "backup-not-verified",
      severity: "info",
      area: "backup",
      areaLabel: language === "sv" ? "Backup" : "Backup",
      title: language === "sv" ? "Ingen backup verifierad" : "No backup verified",
      detail: language === "sv" ? "Ladda ner och kontrollera en backup i Installningar." : "Download and verify a backup in Settings.",
      reference: "",
      action: () => setActiveView("settings")
    }] : [])
  ];
  const filteredDataQualityIssues = dataQualityIssues.filter((issue) => {
    if (dataQualityFilter !== "all" && issue.severity !== dataQualityFilter && issue.area !== dataQualityFilter) return false;

    const query = dataQualitySearch.toLowerCase().trim();
    if (!query) return true;

    return [
      issue.severity,
      issue.areaLabel,
      issue.title,
      issue.detail,
      issue.reference
    ].some((value) => String(value || "").toLowerCase().includes(query));
  });
  const dataQualityCriticalCount = dataQualityIssues.filter((issue) => issue.severity === "critical").length;
  const dataQualityWarningCount = dataQualityIssues.filter((issue) => issue.severity === "warning").length;
  const dataQualityInfoCount = dataQualityIssues.filter((issue) => issue.severity === "info").length;
  const dataQualityScore = dataQualityIssues.length === 0
    ? 100
    : Math.max(0, Math.round(100 - (dataQualityCriticalCount * 25) - (dataQualityWarningCount * 10) - (dataQualityInfoCount * 3)));
  const topCriticalDataQualityIssue = dataQualityIssues.find((issue) => issue.severity === "critical");
  const overdueOutstanding = overdueInvoices.reduce((sum, item) => sum + invoiceRemainingAmount(item), 0);
  const actionCenterItems = [
    dataQualityCriticalCount > 0 && {
      key: "critical-data-quality",
      severity: "critical",
      title: language === "sv" ? "Kritiska kontroller" : "Critical checks",
      detail: topCriticalDataQualityIssue
        ? `${topCriticalDataQualityIssue.title}: ${topCriticalDataQualityIssue.reference || topCriticalDataQualityIssue.detail}`
        : (language === "sv" ? "Kontrollera datahalsa innan du stanger perioden." : "Review data quality before closing the period."),
      meta: `${dataQualityCriticalCount} ${language === "sv" ? "kritiska" : "critical"}`,
      actionLabel: language === "sv" ? "Oppna datahalsa" : "Open data quality",
      action: () => {
        setActiveView("reports");
        setDataQualityFilter("critical");
        setDataQualitySearch("");
      }
    },
    overdueInvoices.length > 0 && {
      key: "overdue-invoices",
      severity: "warning",
      title: language === "sv" ? "Forfallna fakturor" : "Overdue invoices",
      detail: `${overdueInvoices.length} ${language === "sv" ? "fakturor behover foljas upp." : "invoices need follow-up."}`,
      meta: `${overdueOutstanding} SEK`,
      actionLabel: language === "sv" ? "Oppna betalningar" : "Open payments",
      action: () => {
        setActiveView("payments");
        setPaymentOverviewFilter("overdue");
        setPaymentOverviewSearch("");
      }
    },
    expensesMissingReceipt.length > 0 && {
      key: "missing-receipts",
      severity: "warning",
      title: language === "sv" ? "Kostnader saknar underlag" : "Expenses missing receipts",
      detail: language === "sv"
        ? "Ladda upp kvitton eller fakturaunderlag sa bokforingen blir komplett."
        : "Upload receipts or invoice documents so bookkeeping is complete.",
      meta: `${expensesMissingReceipt.length} ${language === "sv" ? "saknas" : "missing"}`,
      actionLabel: language === "sv" ? "Oppna underlag" : "Open receipts",
      action: () => {
        setActiveView("uploaded");
        setExpenseFilter("missingReceipt");
        setExpenseSearch("");
      }
    },
    totalOutstanding > 0 && overdueInvoices.length === 0 && {
      key: "open-receivables",
      severity: "info",
      title: language === "sv" ? "Obetalda kundfordringar" : "Open receivables",
      detail: language === "sv"
        ? "Det finns fakturor kvar att fa betalt, men inga ar forfallna just nu."
        : "Some invoices are still outstanding, but none are overdue right now.",
      meta: `${totalOutstanding} SEK`,
      actionLabel: language === "sv" ? "Visa betalningar" : "View payments",
      action: () => {
        setActiveView("payments");
        setPaymentOverviewFilter("open");
        setPaymentOverviewSearch("");
      }
    },
    vatPeriodToPay > 0 && {
      key: "vat-to-pay",
      severity: "info",
      title: language === "sv" ? "Moms att bevaka" : "VAT to monitor",
      detail: language === "sv"
        ? "Momsrapporten visar belopp att betala for vald period."
        : "The VAT report shows an amount payable for the selected period.",
      meta: `${vatPeriodToPay} SEK`,
      actionLabel: language === "sv" ? "Oppna momsrapport" : "Open VAT report",
      action: () => setActiveView("vat")
    },
    dataQualityWarningCount > 0 && dataQualityCriticalCount === 0 && {
      key: "warning-data-quality",
      severity: "warning",
      title: language === "sv" ? "Datahalsa har varningar" : "Data quality has warnings",
      detail: language === "sv"
        ? "Det finns saker att fixa, men inget kritiskt blockerar just nu."
        : "There are items to fix, but nothing critical is blocking right now.",
      meta: `${dataQualityWarningCount} ${language === "sv" ? "varningar" : "warnings"}`,
      actionLabel: language === "sv" ? "Granska varningar" : "Review warnings",
      action: () => {
        setActiveView("reports");
        setDataQualityFilter("warning");
        setDataQualitySearch("");
      }
    },
    !backupValidation && {
      key: "backup-verification",
      severity: "info",
      title: language === "sv" ? "Verifiera backup" : "Verify backup",
      detail: language === "sv"
        ? "Ladda ner en JSON-backup och kontrollera att filen gar att lasa."
        : "Download a JSON backup and check that the file can be read.",
      meta: language === "sv" ? "Inte verifierad" : "Not verified",
      actionLabel: language === "sv" ? "Oppna installningar" : "Open settings",
      action: () => setActiveView("settings")
    },
    selectedPeriodCanBeLocked && {
      key: "period-close",
      severity: "info",
      title: language === "sv" ? "Period kan lasas" : "Period can be locked",
      detail: language === "sv"
        ? `Efter kontroll kan perioden lasas till ${formatDateOnly(vatPeriodTo)}.`
        : `After review, the period can be locked through ${formatDateOnly(vatPeriodTo)}.`,
      meta: archivePeriodLabel,
      actionLabel: language === "sv" ? "Oppna rapporter" : "Open reports",
      action: () => setActiveView("reports")
    }
  ].filter(Boolean);
  const actionCenterCriticalCount = actionCenterItems.filter((item) => item.severity === "critical").length;
  const actionCenterWarningCount = actionCenterItems.filter((item) => item.severity === "warning").length;
  const actionCenterInfoCount = actionCenterItems.filter((item) => item.severity === "info").length;
  const matchesBookkeepingTypeFilter = (group) => {
    if (bookkeepingFilter === "original") return !group.correctionOfVoucherNumber;
    if (bookkeepingFilter === "corrections") return Boolean(group.correctionOfVoucherNumber);
    if (bookkeepingFilter === "corrected") return group.hasCorrection;
    return true;
  };
  const activeAccountOpeningBalance = bookkeepingAccountSearch && bookkeepingDateFrom
    ? journalGroups
      .filter(matchesBookkeepingTypeFilter)
      .filter((group) => {
        const voucherDate = group.voucherDate || String(group.createdAt || "").slice(0, 10);
        return voucherDate && voucherDate < bookkeepingDateFrom;
      })
      .flatMap((group) => group.rows)
      .filter((entry) => entry.accountNumber === bookkeepingAccountSearch)
      .reduce((sum, entry) => sum + (entry.debit || 0) - (entry.credit || 0), 0)
    : 0;
  let activeAccountRunningBalance = activeAccountOpeningBalance;
  const activeAccountLedgerRows = bookkeepingAccountSearch
    ? filteredJournalGroups
      .flatMap((group) => group.rows
        .filter((entry) => entry.accountNumber === bookkeepingAccountSearch)
        .map((entry) => ({
          voucherNumber: group.voucherNumber,
          voucherDate: group.voucherDate || String(group.createdAt || "").slice(0, 10),
          description: entry.description || group.description || "",
          debit: entry.debit || 0,
          credit: entry.credit || 0
        })))
      .sort((first, second) => {
        const firstDate = new Date(first.voucherDate || 0);
        const secondDate = new Date(second.voucherDate || 0);
        if (firstDate.getTime() !== secondDate.getTime()) {
          return firstDate - secondDate;
        }

        return String(first.voucherNumber).localeCompare(String(second.voucherNumber));
      })
      .map((row) => {
        activeAccountRunningBalance += row.debit - row.credit;
        return { ...row, balance: activeAccountRunningBalance };
      })
    : [];
  const activeAccountPeriodDebit = activeAccountLedgerRows.reduce((sum, row) => sum + row.debit, 0);
  const activeAccountPeriodCredit = activeAccountLedgerRows.reduce((sum, row) => sum + row.credit, 0);
  const activeAccountPeriodChange = activeAccountPeriodDebit - activeAccountPeriodCredit;
  const activeAccountClosingBalance = activeAccountLedgerRows.at(-1)?.balance ?? activeAccountOpeningBalance;
  const customerValidationErrors = [];
  const normalizedCustomerName = customerName.trim();
  const customerNameParts = normalizeNameValue(normalizedCustomerName).trim().split(/\s+/).filter(Boolean);
  const emailLocalPart = customerEmail.includes("@") ? normalizeNameValue(customerEmail.split("@")[0]) : "";
  const emailMatchesName = customerNameParts.some((part) => part.length >= 3 && emailLocalPart.includes(part));

  if (normalizedCustomerName && (normalizedCustomerName.length < 5 || !normalizedCustomerName.includes(" "))) {
    customerValidationErrors.push(language === "sv" ? "Namn maste vara fornamn och efternamn." : "Name must include first and last name.");
  }

  if (normalizedCustomerName && !/^[A-Za-zÅÄÖåäö\s'-]+$/.test(normalizedCustomerName)) {
    customerValidationErrors.push(language === "sv" ? "Namn far bara innehalla bokstaver." : "Name may only contain letters.");
  }

  if (customerEmail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(customerEmail)) {
    customerValidationErrors.push(language === "sv" ? "E-post ar inte giltig." : "Email is not valid.");
  }

  if (customerEmail && normalizedCustomerName && !emailMatchesName) {
    customerValidationErrors.push("E-post ska matcha namnet, t.ex. ali.wafa@gmail.com.");
  }

  if (customerPersonalNumber && !/^\d{8}-\d{4}$/.test(customerPersonalNumber)) {
    customerValidationErrors.push("Personnummer maste vara YYYYMMDD-XXXX.");
  }

  if (customerAddress && (customerAddress.trim().length < 5 || !/.*\d.*/.test(customerAddress))) {
    customerValidationErrors.push(language === "sv" ? "Adress maste innehalla gata och nummer." : "Address must include street and number.");
  }

  if (customerPostalCode && !/^\d{3}\s?\d{2}$/.test(customerPostalCode)) {
    customerValidationErrors.push("Postnummer maste vara 123 45.");
  }

  if (customerCity && !/^[A-Za-zÅÄÖåäö\s'-]{2,}$/.test(customerCity)) {
    customerValidationErrors.push(language === "sv" ? "Stad maste innehalla minst tva bokstaver." : "City must contain at least two letters.");
  }

  if (customerPhone && !/^[+\d][\d\s-]{6,}$/.test(customerPhone)) {
    customerValidationErrors.push(language === "sv" ? "Telefonnummer ar inte giltigt." : "Phone number is not valid.");
  }

  const requiredCustomerFieldsFilled = Boolean(
    normalizedCustomerName &&
      customerEmail &&
      customerPersonalNumber &&
      customerAddress &&
      customerPostalCode &&
      customerCity
  );
  const canSaveCustomer = token && requiredCustomerFieldsFilled && customerValidationErrors.length === 0;

  function navButton(view, label) {
    return (
      <button
        type="button"
        className={activeView === view ? "nav-button active" : "nav-button"}
        onClick={() => setActiveView(view)}
      >
        {label}
      </button>
    );
  }

  function filterButton(filter, label) {
    return (
      <button
        type="button"
        className={invoiceFilter === filter ? "filter-button active" : "filter-button"}
        onClick={() => setInvoiceFilter(filter)}
      >
        {label}
      </button>
    );
  }

  function renderSystemStatusItem(label, ok, detail) {
    return (
      <div className={ok ? "system-status-item system-status-ok" : "system-status-item system-status-warning"}>
        <div>
          <strong>{label}</strong>
          {detail && <span>{detail}</span>}
        </div>
        <span className="system-status-pill">
          {ok ? "OK" : (language === "sv" ? "Kontrollera" : "Check")}
        </span>
      </div>
    );
  }

  function viewTitle() {
    if (activeView === "customers") return t.customers;
    if (activeView === "invoices") return t.invoices;
    if (activeView === "contracts") return t.contracts;
    if (activeView === "services") return t.services;
    if (activeView === "activities") return t.activities;
    if (activeView === "cashflow") return t.cashflow;
    if (activeView === "budget") return t.budget;
    if (activeView === "payroll") return t.payroll;
    if (activeView === "payments") return t.payments;
    if (activeView === "uploaded") return t.uploaded;
    if (activeView === "bookkeeping") return t.bookkeeping;
    if (activeView === "accounts") return t.chartOfAccounts;
    if (activeView === "vat") return t.vatReport;
    if (activeView === "reports") return t.reports;
    if (activeView === "settings") return t.settings;
    return t.overview;
  }

  function activityFilterLabel(type) {
    if (type === "all") return language === "sv" ? "Alla" : "All";
    if (type === "invoice") return language === "sv" ? "Fakturor" : "Invoices";
    if (type === "payment") return language === "sv" ? "Betalningar" : "Payments";
    if (type === "expense") return language === "sv" ? "Kostnader" : "Expenses";
    if (type === "voucher") return language === "sv" ? "Verifikat" : "Vouchers";
    if (type === "reminder") return language === "sv" ? "Paminnelser" : "Reminders";
    if (type === "bank") return language === "sv" ? "Bank" : "Bank";
    if (type === "stripe") return language === "sv" ? "Stripe" : "Stripe";
    return language === "sv" ? "Period" : "Period";
  }

  const reactBitsMenuItems = [
    {
      text: language === "sv" ? "Fakturor" : "Invoices",
      image: "https://picsum.photos/600/400?random=31",
      onClick: (event) => {
        event.preventDefault();
        setActiveView("invoices");
      }
    },
    {
      text: language === "sv" ? "Bankimport" : "Bank import",
      image: "https://picsum.photos/600/400?random=32",
      onClick: (event) => {
        event.preventDefault();
        setActiveView("bank");
      }
    },
    {
      text: language === "sv" ? "Budget & mal" : "Budget goals",
      image: "https://picsum.photos/600/400?random=33",
      onClick: (event) => {
        event.preventDefault();
        setActiveView("budget");
      }
    },
    {
      text: language === "sv" ? "Rapporter" : "Reports",
      image: "https://picsum.photos/600/400?random=34",
      onClick: (event) => {
        event.preventDefault();
        setActiveView("reports");
      }
    }
  ];

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">AliBooks</div>
        <button className="create-button" type="button">{t.createNew}</button>
        <nav className="nav">
          {navButton("overview", t.overview)}
          {navButton("customers", t.customers)}
          {navButton("invoices", t.invoices)}
          {navButton("contracts", t.contracts)}
          {navButton("services", t.services)}
          {navButton("activities", t.activities)}
          {navButton("cashflow", t.cashflow)}
          {navButton("budget", t.budget)}
          {navButton("payroll", t.payroll)}
          {navButton("payments", t.payments)}
          {navButton("uploaded", t.uploaded)}
          {navButton("bookkeeping", t.bookkeeping)}
          {navButton("accounts", t.chartOfAccounts)}
          {navButton("vat", t.vatReport)}
          {navButton("reports", t.reports)}
          {navButton("settings", t.settings)}
        </nav>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">{t.dashboard}</p>
            <h1>{viewTitle()}</h1>
          </div>
          <div className="account-box">
            {token ? (
              <>
                <select
                  className="language-select"
                  value={language}
                  onChange={(event) => setLanguage(event.target.value)}
                >
                  <option value="sv">Svenska</option>
                  <option value="en">English</option>
                </select>
                <p className="signed-in">{t.signedIn} {currentEmail}</p>
                <button type="button" className="secondary-button" onClick={handleLogout}>{t.logout}</button>
              </>
            ) : (
              <form ref={authFormRef} onSubmit={handleAuth} className="auth-form">
                <label className="auth-language-label">
                  {language === "sv" ? "Sprak" : "Language"}
                  <select
                    className="language-select"
                    value={language}
                    onChange={(event) => setLanguage(event.target.value)}
                  >
                    <option value="sv">Svenska</option>
                    <option value="en">English</option>
                  </select>
                </label>
                <div className="tabs">
                  <button
                    type="button"
                    className={authMode === "login" ? "active" : ""}
                    onClick={() => setAuthMode("login")}
                  >
                    {t.login}
                  </button>
                  <button
                    type="button"
                    className={authMode === "register" ? "active" : ""}
                    onClick={() => setAuthMode("register")}
                  >
                    {t.register}
                  </button>
                </div>

                <label>
                  {t.email}
                  <input
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="you@example.com"
                  />
                </label>

                <label>
                  {t.password}
                  <input
                    type="password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="At least 6 characters"
                  />
                </label>

                <button type="submit">
                  {authMode === "login" ? t.login : t.register}
                </button>
              </form>
            )}
          </div>
        </header>

        {error && <p className="message error">{error}</p>}

        {!token && activeView === "overview" && (
          <>
            <HeroErrorBoundary language={language} resetKey={language}>
              <Suspense fallback={<div className="liquid-metal-hero-loading">{language === "sv" ? "Laddar startsida..." : "Loading start page..."}</div>}>
                <LiquidMetalHero
                  badge={language === "sv" ? "AliBooks for Muscle&Focus" : "AliBooks for Muscle&Focus"}
                  title={language === "sv" ? "Fakturering, bokforing och betalningar i ett flode" : "Invoices, bookkeeping and payments in one flow"}
                  subtitle={language === "sv"
                    ? "Skapa fakturor, folj betalningar, importera bankrader och hall koll pa underlag utan att tappa helheten."
                    : "Create invoices, track payments, import bank rows and keep receipts organized without losing the full picture."}
                  primaryCtaLabel={language === "sv" ? "Registrera konto" : "Create account"}
                  secondaryCtaLabel={language === "sv" ? "Logga in" : "Log in"}
                  onPrimaryCtaClick={() => focusAuthForm("register")}
                  onSecondaryCtaClick={() => focusAuthForm("login")}
                  features={language === "sv"
                    ? ["Smart fakturering", "Bankimport CSV", "AI-assistent"]
                    : ["Smart invoicing", "CSV bank import", "AI assistant"]}
                />
              </Suspense>
            </HeroErrorBoundary>

            <section className="landing-feature-panel">
              <article>
                <strong>{language === "sv" ? "Fakturering" : "Invoicing"}</strong>
                <span>{language === "sv" ? "Kunder, tjanster, PDF, Stripe och e-postpaminnelser." : "Customers, services, PDF, Stripe and email reminders."}</span>
              </article>
              <article>
                <strong>{language === "sv" ? "Bokforing" : "Bookkeeping"}</strong>
                <span>{language === "sv" ? "Automatiska verifikat for fakturor, betalningar och kostnader." : "Automatic vouchers for invoices, payments and expenses."}</span>
              </article>
              <article>
                <strong>{language === "sv" ? "Bankimport" : "Bank import"}</strong>
                <span>{language === "sv" ? "CSV-matchning, kostnadsforslag och skydd mot dubbletter." : "CSV matching, expense suggestions and duplicate protection."}</span>
              </article>
              <article>
                <strong>{language === "sv" ? "Rapporter" : "Reports"}</strong>
                <span>{language === "sv" ? "Momsrapport, resultatrapport, balansrapport och export." : "VAT report, profit and loss, balance report and export."}</span>
              </article>
            </section>
          </>
        )}

        {token && activeView === "overview" && <section className="stats">
          <article>
            <span>{language === "sv" ? "Intakter exkl. moms" : "Revenue net"}</span>
            <strong>{revenueNet} SEK</strong>
          </article>
          <article>
            <span>{language === "sv" ? "Kostnader exkl. moms" : "Expenses net"}</span>
            <strong>{expenseNet} SEK</strong>
          </article>
          <article>
            <span>{language === "sv" ? "Resultat" : "Profit"}</span>
            <strong>{profitNet} SEK</strong>
          </article>
          <article>
            <span>{language === "sv" ? "Moms att betala" : "VAT to pay"}</span>
            <strong>{vatReport?.vatToPay || 0} SEK</strong>
          </article>
          <article>
            <span>{language === "sv" ? "Obetalda fakturor" : "Unpaid invoices"}</span>
            <strong>{unpaidInvoices.length}</strong>
          </article>
          <article>
            <span>{language === "sv" ? "Avtal redo" : "Contracts due"}</span>
            <strong>{dueContracts.length}</strong>
          </article>
          <article>
            <span>{language === "sv" ? "Totalt inkl. moms" : "Total incl. VAT"}</span>
            <strong>{revenueTotal - expenseTotal} SEK</strong>
          </article>
        </section>}

        {token && activeView === "overview" && (
          <SafeRenderBoundary
            label="React Bits overview panel failed"
            resetKey={`${language}-${activeView}`}
            fallback={
              <section className="orders-section react-bits-fallback-panel">
                {language === "sv"
                  ? "Den visuella snabbpanelen kunde inte laddas, men AliBooks fungerar fortfarande."
                  : "The visual quick panel could not load, but AliBooks is still available."}
              </section>
            }
          >
            <section className="orders-section react-bits-showcase">
              <div className="react-bits-ether">
                <Suspense fallback={null}>
                  <LiquidEther
                    colors={["#155ee8", "#f2a900", "#62d6a3"]}
                    mouseForce={18}
                    cursorSize={130}
                    resolution={0.55}
                    autoSpeed={0.35}
                    autoIntensity={1.7}
                    autoResumeDelay={1200}
                  />
                </Suspense>
              </div>

              <div className="react-bits-copy">
                <p className="eyebrow">{language === "sv" ? "Interaktivt kontrollrum" : "Interactive control room"}</p>
                <h2>{language === "sv" ? "Hoppa snabbt mellan de viktigaste delarna" : "Jump quickly between the most important areas"}</h2>
                <p>
                  {language === "sv"
                    ? "Den har panelen anvander React Bits-kansla for att gora AliBooks mer levande, men varje val leder fortfarande till en riktig arbetsvy."
                    : "This panel brings a React Bits feel into AliBooks, while every choice still opens a real work view."}
                </p>
              </div>

              <div className="react-bits-menu-frame">
                <Suspense fallback={<div className="react-bits-loading">{language === "sv" ? "Laddar meny..." : "Loading menu..."}</div>}>
                  <FlowingMenu
                    items={reactBitsMenuItems}
                    speed={18}
                    textColor="#ffffff"
                    bgColor="#111827"
                    marqueeBgColor="#f2a900"
                    marqueeTextColor="#111827"
                    borderColor="rgba(255, 255, 255, 0.22)"
                  />
                </Suspense>
              </div>
            </section>
          </SafeRenderBoundary>
        )}

        {token && activeView === "overview" && (
          <section className="orders-section action-center-section">
            <div className="section-heading">
              <div>
                <h2>{language === "sv" ? "Atgardscenter" : "Action center"}</h2>
                <p className="automation-note">
                  {language === "sv"
                    ? "AliBooks samlar det viktigaste som behover kontrolleras innan du fakturerar, bokfor eller stanger perioden."
                    : "AliBooks gathers the most important items to review before invoicing, bookkeeping or closing the period."}
                </p>
              </div>
              <div className="action-center-score">
                <span>{language === "sv" ? "Datahalsa" : "Data health"}</span>
                <strong>{dataQualityScore}%</strong>
              </div>
            </div>

            <div className="action-center-summary">
              <article className="critical">
                <span>{language === "sv" ? "Kritiska" : "Critical"}</span>
                <strong>{actionCenterCriticalCount}</strong>
              </article>
              <article className="warning">
                <span>{language === "sv" ? "Varningar" : "Warnings"}</span>
                <strong>{actionCenterWarningCount}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Bevaka" : "Monitor"}</span>
                <strong>{actionCenterInfoCount}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Totalt" : "Total"}</span>
                <strong>{actionCenterItems.length}</strong>
              </article>
            </div>

            {actionCenterItems.length === 0 ? (
              <p className="empty-state">
                {language === "sv"
                  ? "Allt ser lugnt ut just nu. Inga akuta atgarder finns i oversikten."
                  : "Everything looks calm right now. No urgent actions are listed on the overview."}
              </p>
            ) : (
              <div className="action-center-list">
                {actionCenterItems.map((item) => (
                  <button type="button" className={`action-center-row ${item.severity}`} key={item.key} onClick={item.action}>
                    <span className="action-center-priority">
                      {item.severity === "critical"
                        ? (language === "sv" ? "Kritisk" : "Critical")
                        : item.severity === "warning"
                          ? (language === "sv" ? "Varning" : "Warning")
                          : (language === "sv" ? "Bevaka" : "Monitor")}
                    </span>
                    <span className="action-center-main">
                      <strong>{item.title}</strong>
                      <small>{item.detail}</small>
                    </span>
                    <span className="action-center-meta">
                      <strong>{item.meta}</strong>
                      <small>{item.actionLabel}</small>
                    </span>
                  </button>
                ))}
              </div>
            )}
          </section>
        )}

        {token && activeView === "overview" && dueContracts.length > 0 && (
          <section className="orders-section">
            <div className="section-heading">
              <div>
                <h2>{language === "sv" ? "Avtal att fakturera" : "Contracts to invoice"}</h2>
                <p className="automation-note">
                  {language === "sv"
                    ? "Dessa avtal har natt sitt nasta fakturadatum."
                    : "These contracts have reached their next invoice date."}
                </p>
              </div>
              <div className="button-row">
                <button
                  type="button"
                  className="primary-small-button"
                  disabled={!token || isAccountingDateLocked(new Date().toISOString().slice(0, 10))}
                  onClick={createAllDueContractInvoices}
                >
                  {language === "sv" ? "Skapa alla redo fakturor" : "Create all due invoices"}
                </button>
                <button type="button" className="secondary-button" onClick={() => setActiveView("contracts")}>
                  {language === "sv" ? "Ga till Avtal" : "Go to Contracts"}
                </button>
              </div>
            </div>
            <div className="mini-list">
              {dueContracts.map((contract) => (
                <button
                  type="button"
                  className="mini-list-row"
                  key={contract.id}
                  onClick={() => setActiveView("contracts")}
                >
                  <span className="mini-list-main">
                    <strong>{contract.customerName || "-"}</strong>
                    <span>{contract.serviceName || "-"} | {language === "sv" ? "Nasta faktura" : "Next invoice"}: {contract.nextInvoiceDate}</span>
                  </span>
                  <span className="mini-list-amount">
                    <strong>{language === "sv" ? "Redo" : "Due"}</strong>
                    <span>{language === "sv" ? "Intervall" : "Interval"}: {contract.interval}</span>
                  </span>
                </button>
              ))}
            </div>
          </section>
        )}

        {token && activeView === "overview" && <section className="orders-section ai-assistant-section">
          <div className="section-heading">
            <div>
              <h2>{language === "sv" ? "AI-assistent" : "AI assistant"}</h2>
              <p className="automation-note">
                {language === "sv"
                  ? "Fraga om fakturor, bokforing, moms, rapporter, underlag, bankimport eller installningar."
                  : "Ask about invoices, bookkeeping, VAT, reports, receipts, bank import or settings."}
              </p>
            </div>
          </div>

          <div className="ai-quick-questions">
            {[
              language === "sv" ? "Hur ska jag bokfora en faktura?" : "How do I bookkeep an invoice?",
              language === "sv" ? "Hur laddar jag upp underlag?" : "How do I upload receipts?",
              language === "sv" ? "Hur ser jag rapporter?" : "How do I view reports?",
              language === "sv" ? "Hur funkar bankimport?" : "How does bank import work?",
              language === "sv" ? "Hur funkar avtal?" : "How do contracts work?"
            ].map((question) => (
              <button type="button" className="secondary-button" key={question} onClick={() => askAiAssistant(question)}>
                {question}
              </button>
            ))}
          </div>

          <div className="ai-assistant-chat">
            {aiAssistantMessages.length === 0 ? (
              <p className="empty-state">
                {language === "sv"
                  ? "Stall en fraga sa svarar assistenten steg for steg."
                  : "Ask a question and the assistant will answer step by step."}
              </p>
            ) : aiAssistantMessages.map((message, index) => (
              <article className={`ai-message ai-message-${message.role}`} key={`${message.role}-${index}`}>
                <strong>{message.role === "user" ? (language === "sv" ? "Du" : "You") : "AliBooks"}</strong>
                {message.role === "assistant" && (
                  <span className={`ai-provider-badge ai-provider-${message.provider || "local"}`}>
                    {aiProviderLabel(message.provider)}
                  </span>
                )}
                <p>{message.text}</p>
                {message.role === "assistant" && (
                  <div className="ai-message-actions">
                    <button
                      type="button"
                      className="ai-message-action"
                      onClick={() => copyAiAssistantMessage(message, `overview-${index}`)}
                    >
                      {copiedAiMessageId === `overview-${index}`
                        ? (language === "sv" ? "Kopierad" : "Copied")
                        : (language === "sv" ? "Kopiera" : "Copy")}
                    </button>
                    {message.targetView && (
                      <button
                        type="button"
                        className="ai-message-action"
                        onClick={() => setActiveView(message.targetView)}
                      >
                        {language === "sv" ? `Ga till ${aiTargetLabel(message.targetView)}` : `Go to ${aiTargetLabel(message.targetView)}`}
                      </button>
                    )}
                  </div>
                )}
              </article>
            ))}
          </div>

          <form
            className="ai-assistant-form"
            onSubmit={(event) => {
              event.preventDefault();
              askAiAssistant();
            }}
          >
            <textarea
              value={aiAssistantQuestion}
              onChange={(event) => setAiAssistantQuestion(event.target.value)}
              placeholder={language === "sv" ? "Skriv din fraga har..." : "Write your question here..."}
              rows="3"
            />
            <div className="button-row">
              <button type="submit" className="primary-button">
                {language === "sv" ? "Fraga assistenten" : "Ask assistant"}
              </button>
              {aiAssistantMessages.length > 0 && (
                <button type="button" className="secondary-button" onClick={() => setAiAssistantMessages([])}>
                  {language === "sv" ? "Rensa chatten" : "Clear chat"}
                </button>
              )}
            </div>
          </form>
        </section>}

        {token && activeView === "overview" && <section className="orders-section">
          <div className="section-heading">
            <h2>{t.reminders}</h2>
            <div className="button-row">
              <button type="button" className="secondary-button" onClick={() => loadReminders()}>
                {t.refresh}
              </button>
              <button type="button" className="primary-small-button" onClick={runAutomaticInvoiceReminders}>
                {language === "sv" ? "Kor automatiken nu" : "Run automation now"}
              </button>
            </div>
          </div>

          <p className="automation-note">
            {settings?.automaticInvoiceRemindersEnabled === false
              ? (language === "sv"
                ? "Automatiska fakturapaminnelser ar avstangda i Installningar."
                : "Automatic invoice reminders are turned off in Settings.")
              : (language === "sv"
                ? `Automatiken kor varje dag kl. 09:00 och skickar en e-postpaminnelse exakt ${settings?.invoiceReminderDaysBeforeDue || 5} dagar fore forfallodatum.`
                : `Automation runs every day at 09:00 and sends an email reminder exactly ${settings?.invoiceReminderDaysBeforeDue || 5} days before the due date.`)}
          </p>
          {automaticReminderMessage && <p className="message success">{automaticReminderMessage}</p>}
          {automaticReminderResult && (
            <div className="automation-result">
              <article>
                <span>{language === "sv" ? "Senast kord" : "Last run"}</span>
                <strong>{formatDateTime(automaticReminderResult.ranAt)}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Status" : "Status"}</span>
                <strong>{automaticReminderResult.enabled ? (language === "sv" ? "Pa" : "On") : (language === "sv" ? "Av" : "Off")}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Regel" : "Rule"}</span>
                <strong>{automaticReminderResult.daysBeforeDue} {language === "sv" ? "dagar innan" : "days before"}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Skickade" : "Sent"}</span>
                <strong>{automaticReminderResult.sent}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Hoppades over" : "Skipped"}</span>
                <strong>{automaticReminderResult.skipped}</strong>
              </article>
            </div>
          )}

          {advisorSummary && (
            <article className="advisor-card">
              <strong>{advisorSummary.title}</strong>
              <p>{advisorSummary.message}</p>
            </article>
          )}

          {reminders.length === 0 ? (
            <p className="empty-state">{t.customerRequiredLogin}</p>
          ) : (
            <div className="reminders">
              {reminders.map((reminder) => (
                <article className={`reminder reminder-${reminder.severity}`} key={`${reminder.type}-${reminder.title}`}>
                  <strong>{reminder.title}</strong>
                  <p>{reminder.message}</p>
                </article>
              ))}
            </div>
          )}
        </section>}

        <div
          className="grid"
          style={{ gridTemplateColumns: activeView === "customers" ? "minmax(320px, 720px)" : undefined }}
        >
          <section style={{ display: activeView === "customers" ? undefined : "none" }}>
            <div className="section-heading">
              <h2>{t.customers}</h2>
              <div className="button-row">
                <button type="button" className="secondary-button" onClick={downloadCustomersCsv}>
                  {t.exportCsv}
                </button>
                <button type="button" className="secondary-button" onClick={downloadCustomerLedgerCsv}>
                  {language === "sv" ? "Exportera reskontra" : "Export ledger"}
                </button>
              </div>
            </div>
            <form onSubmit={handleCreateCustomer} className="form">
              <label>
                {t.name}
                <input
                  value={customerName}
                  onChange={(event) => setCustomerName(event.target.value)}
                  placeholder="Ali Wafa"
                />
              </label>

              <label>
                {t.email}
                <input
                  type="email"
                  value={customerEmail}
                  onChange={(event) => setCustomerEmail(event.target.value)}
                  placeholder="ali.wafa@gmail.com"
                />
              </label>

              <label>
                {t.personalNumber}
                <input
                  value={customerPersonalNumber}
                  onChange={(event) => setCustomerPersonalNumber(event.target.value)}
                  placeholder="YYYYMMDD-XXXX"
                />
              </label>

              <label>
                {t.address}
                <input
                  value={customerAddress}
                  onChange={(event) => setCustomerAddress(event.target.value)}
                  placeholder="Byvagen 56"
                />
              </label>

              <div className="form-row">
                <label>
                  {t.postalCode}
                  <input
                    value={customerPostalCode}
                    onChange={(event) => setCustomerPostalCode(event.target.value)}
                    placeholder="123 45"
                  />
                </label>

                <label>
                  {t.city}
                  <input
                    value={customerCity}
                    onChange={(event) => setCustomerCity(event.target.value)}
                    placeholder="Stockholm"
                  />
                </label>
              </div>

              <label>
                {t.phone}
                <input
                  value={customerPhone}
                  onChange={(event) => setCustomerPhone(event.target.value)}
                  placeholder="+46..."
                />
              </label>

              {customerValidationErrors.length > 0 && (
                <div className="validation-list">
                  {customerValidationErrors.map((validationError) => (
                    <p key={validationError}>{validationError}</p>
                  ))}
                </div>
              )}

              <div className="button-row">
                <button type="submit" disabled={!canSaveCustomer}>
                  {editingCustomerId ? (language === "sv" ? "Spara andringar" : "Save changes") : t.saveCustomer}
                </button>
                {editingCustomerId && (
                  <button type="button" className="secondary-button" onClick={cancelEditCustomer}>
                    {language === "sv" ? "Avbryt" : "Cancel"}
                  </button>
                )}
              </div>
            </form>

            {customerMessage && <p className="message success">{customerMessage}</p>}

            <div className="invoice-summary-cards customer-summary-cards">
              <button type="button" className="invoice-summary-card" onClick={() => setCustomerFilter("active")}>
                <span>{language === "sv" ? "Aktiva kunder" : "Active customers"}</span>
                <strong>{activeCustomers.length}</strong>
              </button>
              <button type="button" className="invoice-summary-card danger-summary" onClick={() => setCustomerFilter("outstanding")}>
                <span>{language === "sv" ? "Kunder med skuld" : "Customers with balance"}</span>
                <strong>{customersWithOutstanding.length}</strong>
              </button>
              <button type="button" className="invoice-summary-card warning-summary" onClick={() => setCustomerFilter("outstanding")}>
                <span>{language === "sv" ? "Utestaende totalt" : "Total outstanding"}</span>
                <strong>{totalOutstanding} SEK</strong>
              </button>
              <button type="button" className="invoice-summary-card" onClick={() => setCustomerFilter("archived")}>
                <span>{language === "sv" ? "Arkiverade" : "Archived"}</span>
                <strong>{archivedCustomers.length}</strong>
              </button>
            </div>

            <div className="filter-bar">
              <button
                type="button"
                className={customerFilter === "active" ? "filter-button active" : "filter-button"}
                onClick={() => setCustomerFilter("active")}
              >
                {language === "sv" ? "Aktiva" : "Active"}
              </button>
              <button
                type="button"
                className={customerFilter === "archived" ? "filter-button active" : "filter-button"}
                onClick={() => setCustomerFilter("archived")}
              >
                {language === "sv" ? "Arkiverade" : "Archived"}
              </button>
              <button
                type="button"
                className={customerFilter === "all" ? "filter-button active" : "filter-button"}
                onClick={() => setCustomerFilter("all")}
              >
                {t.all}
              </button>
              <button
                type="button"
                className={customerFilter === "outstanding" ? "filter-button active" : "filter-button"}
                onClick={() => setCustomerFilter("outstanding")}
              >
                {language === "sv" ? "Med skuld" : "With balance"}
              </button>
              <button
                type="button"
                className="filter-button"
                onClick={() => {
                  setCustomerFilter("active");
                  setCustomerSearch("");
                }}
              >
                {language === "sv" ? "Rensa filter" : "Clear filters"}
              </button>
            </div>

            <div className="search-box">
              <label>
              {t.searchCustomers}
                <div className="search-actions">
                  <input
                    value={customerSearch}
                    onChange={(event) => setCustomerSearch(event.target.value)}
                    placeholder={language === "sv" ? "Namn, e-post, stad..." : "Name, email, city..."}
                  />
                  {customerSearch && (
                    <button type="button" className="secondary-button" onClick={() => setCustomerSearch("")}>
                      {language === "sv" ? "Rensa" : "Clear"}
                    </button>
                  )}
                </div>
              </label>
            </div>

            <div className="customer-list">
              {filteredCustomers.map((customer) => (
                <button
                  type="button"
                  className={selectedCustomerId === String(customer.id) ? "customer selected-product" : "customer"}
                  key={customer.id}
                  onClick={() => setSelectedCustomerId(String(customer.id))}
                >
                  <strong>{customer.name}</strong>
                  {customer.archived && <span className="status">{language === "sv" ? "Arkiverad" : "Archived"}</span>}
                  <span>{customer.email || t.noEmail}</span>
                  <span>{customer.personalNumber || t.noPersonalNumber}</span>
                  <span>{customer.address || ""} {customer.postalCode || ""} {customer.city || ""}</span>
                  <span className={customerOutstandingAmount(customer, invoices) > 0 ? "customer-balance outstanding" : "customer-balance"}>
                    {language === "sv" ? "Utestaende" : "Outstanding"}: {customerOutstandingAmount(customer, invoices)} SEK
                    {" | "}
                    {language === "sv" ? "Obetalda" : "Unpaid"}: {customerUnpaidInvoiceCount(customer, invoices)}
                  </span>
                </button>
              ))}
            </div>

            {selectedCustomer && (
              <article className="customer-detail">
                <div className="section-heading">
                  <h2>{selectedCustomer.name}</h2>
                  <div className="button-row">
                    <span className="status">{selectedCustomerInvoices.length} {t.invoices}</span>
                    <button type="button" className="secondary-button" onClick={downloadSelectedCustomerCsv}>
                      {language === "sv" ? "Exportera kundkort" : "Export customer card"}
                    </button>
                    <button type="button" className="secondary-button" onClick={() => startEditCustomer(selectedCustomer)}>
                      {language === "sv" ? "Redigera" : "Edit"}
                    </button>
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => updateCustomerArchive(selectedCustomer.id, !selectedCustomer.archived)}
                    >
                      {selectedCustomer.archived
                        ? (language === "sv" ? "Aterstall" : "Restore")
                        : (language === "sv" ? "Arkivera" : "Archive")}
                    </button>
                    <button
                      type="button"
                      className="danger-button"
                      onClick={() => deleteCustomer(selectedCustomer.id)}
                    >
                      {language === "sv" ? "Ta bort permanent" : "Delete permanently"}
                    </button>
                  </div>
                </div>

                <div className="customer-360-hero">
                  <div className="detail-grid">
                    <p><strong>{t.email}</strong><span>{selectedCustomer.email || "-"}</span></p>
                    <p><strong>{t.personalNumber}</strong><span>{selectedCustomer.personalNumber || "-"}</span></p>
                    <p><strong>{t.address}</strong><span>{selectedCustomer.address || "-"}</span></p>
                    <p><strong>{t.postalCode}</strong><span>{selectedCustomer.postalCode || "-"}</span></p>
                    <p><strong>{t.city}</strong><span>{selectedCustomer.city || "-"}</span></p>
                    <p><strong>{t.phone}</strong><span>{selectedCustomer.phone || "-"}</span></p>
                  </div>
                  {selectedCustomerNextAction && (
                    <article className={`customer-next-action ${selectedCustomerNextAction.status}`}>
                      <span>{language === "sv" ? "Nasta steg" : "Next step"}</span>
                      <strong>{selectedCustomerNextAction.title}</strong>
                      <p>{selectedCustomerNextAction.detail}</p>
                      <button type="button" className="primary-small-button" onClick={selectedCustomerNextAction.action}>
                        {selectedCustomerNextAction.label}
                      </button>
                    </article>
                  )}
                </div>

                <div className="stats compact-stats customer-stats">
                  <article>
                    <span>{t.invoices}</span>
                    <strong>{selectedCustomerInvoices.length}</strong>
                  </article>
                  <article>
                    <span>{t.paid}</span>
                    <strong>{selectedCustomerPaidInvoices.length}</strong>
                  </article>
                  <article>
                    <span>{t.unpaid}</span>
                    <strong>{selectedCustomerUnpaidInvoices.length}</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Utestaende" : "Outstanding"}</span>
                    <strong>{selectedCustomerOutstanding} SEK</strong>
                  </article>
                  <article>
                    <span>{t.total}</span>
                    <strong>{selectedCustomerTotal} SEK</strong>
                  </article>
                  <article className={selectedCustomerOverdueAmount > 0 ? "unbalanced-summary" : "balanced-summary"}>
                    <span>{language === "sv" ? "Forfallet" : "Overdue"}</span>
                    <strong>{selectedCustomerOverdueAmount} SEK</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Betalt totalt" : "Paid total"}</span>
                    <strong>{selectedCustomerPaidTotal} SEK</strong>
                  </article>
                </div>

                <div className="customer-insight-grid">
                  <article>
                    <span>{language === "sv" ? "Senaste faktura" : "Latest invoice"}</span>
                    <strong>{selectedCustomerLatestInvoice ? invoiceNumber(selectedCustomerLatestInvoice) : "-"}</strong>
                    <small>
                      {selectedCustomerLatestInvoice
                        ? `${formatDateOnly(selectedCustomerLatestInvoice.invoiceDate || selectedCustomerLatestInvoice.createdAt)} - ${invoiceTotalAmount(selectedCustomerLatestInvoice)} SEK`
                        : (language === "sv" ? "Ingen faktura annu" : "No invoice yet")}
                    </small>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Senaste betalning" : "Latest payment"}</span>
                    <strong>{selectedCustomerLastPayment ? `${selectedCustomerLastPayment.amount || 0} SEK` : "-"}</strong>
                    <small>
                      {selectedCustomerLastPayment
                        ? `${formatDateOnly(selectedCustomerLastPayment.paymentDate || selectedCustomerLastPayment.createdAt)} - ${selectedCustomerLastPayment.invoiceNumber}`
                        : (language === "sv" ? "Ingen betalning annu" : "No payment yet")}
                    </small>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Betalningsgrad" : "Payment rate"}</span>
                    <strong>{selectedCustomerTotal > 0 ? Math.round((selectedCustomerPaidTotal / selectedCustomerTotal) * 100) : 0}%</strong>
                    <small>{selectedCustomerPaidTotal} / {selectedCustomerTotal} SEK</small>
                  </article>
                </div>

                {selectedCustomerRecentActivity.length > 0 && (
                  <div className="customer-activity-panel">
                    <div className="section-heading">
                      <h3>{language === "sv" ? "Senaste aktivitet" : "Recent activity"}</h3>
                    </div>
                    <div className="customer-activity-list">
                      {selectedCustomerRecentActivity.map((activity) => (
                        <button type="button" className="customer-activity-row" key={activity.id} onClick={activity.action}>
                          <span>{formatDateOnly(activity.date)}</span>
                          <strong>{activity.title}</strong>
                          <small>{activity.detail}</small>
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {selectedCustomerInvoices.length > 0 && (
                  <div className="mini-list">
                    {selectedCustomerInvoices.map((item) => {
                      const dueStatus = invoiceDueStatus(item, language);

                      return (
                        <button
                          type="button"
                          className="mini-list-row"
                          key={item.id}
                          onClick={() => {
                            setActiveView("invoices");
                            setInvoiceSearch(invoiceNumber(item));
                          }}
                        >
                          <span className="mini-list-main">
                            <strong>{invoiceNumber(item)}</strong>
                            <span>
                              {language === "sv" ? "Datum" : "Date"}: {formatDateOnly(item.createdAt)}
                              {" | "}
                              {language === "sv" ? "Forfallodatum" : "Due date"}: {formatDateOnly(item.dueDate)}
                            </span>
                          </span>
                          <span className="mini-list-amount">
                            <strong>{invoiceRemainingAmount(item)} SEK</strong>
                            <span>{language === "sv" ? "kvar av" : "left of"} {invoiceTotalAmount(item)} SEK</span>
                          </span>
                          <span className={`due-status ${dueStatus.className}`}>{dueStatus.label}</span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </article>
            )}
          </section>

          <section style={{ display: activeView === "invoices" ? undefined : "none" }}>
            <h2>{t.services}</h2>
            {services.length === 0 && <p className="empty-state">{t.noServices}</p>}
            <div className="products">
              {services.map((service) => (
                <button
                  type="button"
                  className={selectedServiceId === String(service.id) ? "product selected-product" : "product"}
                  key={service.id}
                  disabled={!serviceHasInvoicePrice(service)}
                  onClick={() => setSelectedServiceId(String(service.id))}
                >
                  <h3>{service.name}</h3>
                  <p>{service.description}</p>
                  <strong>{servicePriceLabel(service, language)}</strong>
                </button>
              ))}
            </div>
          </section>

          <section style={{ display: activeView === "invoices" ? undefined : "none" }}>
            <h2>{t.createInvoice}</h2>
            <form onSubmit={handleCreateInvoice} className="form">
              <label>
                {t.customers}
                <select
                  value={selectedCustomerId}
                  onChange={(event) => setSelectedCustomerId(event.target.value)}
                >
                  <option value="">{t.chooseCustomer}</option>
                  {activeCustomers.map((customer) => (
                    <option key={customer.id} value={customer.id}>
                      {customer.name}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                {t.services}
                <select
                  value={selectedServiceId}
                  onChange={(event) => setSelectedServiceId(event.target.value)}
                >
                  {services.map((service) => (
                    <option key={service.id} value={service.id} disabled={!serviceHasInvoicePrice(service)}>
                      {service.name} - {servicePriceLabel(service, language)}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                {t.quantity}
                <input
                  type="number"
                  min="1"
                  value={invoiceQuantity}
                  onChange={(event) => setInvoiceQuantity(event.target.value)}
                />
              </label>

              {invoicePreview && (
                <div className="invoice-preview">
                  <strong>{language === "sv" ? "Forhandsgranskning" : "Preview"}</strong>
                  <p>
                    <span>{selectedService.name}</span>
                    <span>{invoicePreview.quantity} x {servicePriceLabel(selectedService, language)}</span>
                  </p>
                  {invoicePreview.discountAmount > 0 && (
                    <>
                      <p>
                        <span>{language === "sv" ? "Ordinarie pris" : "Regular price"}</span>
                        <span>{invoicePreview.ordinaryPrice} SEK</span>
                      </p>
                      <p className="discount-line">
                        <span>
                          {language === "sv" ? "Rabatt" : "Discount"}
                          {invoicePreview.discountLabel ? ` (${invoicePreview.discountLabel})` : ""}
                        </span>
                        <span>-{invoicePreview.discountAmount} SEK</span>
                      </p>
                    </>
                  )}
                  <p>
                    <span>{t.net}</span>
                    <span>{invoicePreview.netAmount} SEK</span>
                  </p>
                  <p>
                    <span>{t.vat} 25%</span>
                    <span>{invoicePreview.vatAmount} SEK</span>
                  </p>
                  <p className="invoice-preview-total">
                    <span>{t.total}</span>
                    <span>{invoicePreview.totalAmount} SEK</span>
                  </p>
                </div>
              )}

              {isAccountingDateLocked(new Date().toISOString().slice(0, 10)) && (
                <p className="message warning">{lockedAccountingMessage(new Date().toISOString().slice(0, 10))}</p>
              )}

              <button
                type="submit"
                disabled={!token || !selectedCustomerId || !selectedServiceId || isAccountingDateLocked(new Date().toISOString().slice(0, 10))}
              >
                {t.createInvoice}
              </button>
            </form>

            {invoice && (
              <p className="message success">
                {language === "sv" ? "Faktura" : "Invoice"} #{invoice.id} {language === "sv" ? "skapad for" : "created for"} {invoice.customerName}.
              </p>
            )}
          </section>
        </div>

        {activeView === "contracts" && (
          <section className="orders-section">
            <div className="section-heading">
              <div>
                <h2>{language === "sv" ? "Avtalsfakturering" : "Contract invoicing"}</h2>
                <p className="muted-line">
                  {language === "sv"
                    ? "Skapa enkla aterkommande fakturaplaner och fakturera nar planen ar redo."
                  : "Create simple recurring invoice plans and invoice when the plan is due."}
                </p>
              </div>
              <div className="button-row">
                <span className="status">{dueContracts.length} {language === "sv" ? "redo" : "due"}</span>
                <button
                  type="button"
                  className="primary-small-button"
                  disabled={!token || dueContracts.length === 0 || isAccountingDateLocked(new Date().toISOString().slice(0, 10))}
                  onClick={createAllDueContractInvoices}
                >
                  {language === "sv" ? "Skapa alla redo fakturor" : "Create all due invoices"}
                </button>
              </div>
            </div>

            <form onSubmit={handleCreateContract} className="form contract-form">
              <label>
                {t.customers}
                <select value={contractCustomerId} onChange={(event) => setContractCustomerId(event.target.value)}>
                  <option value="">{t.chooseCustomer}</option>
                  {activeCustomers.map((customer) => (
                    <option key={customer.id} value={customer.id}>{customer.name}</option>
                  ))}
                </select>
              </label>
              <label>
                {t.services}
                <select value={contractServiceId} onChange={(event) => setContractServiceId(event.target.value)}>
                  <option value="">{t.chooseService}</option>
                  {services.map((service) => (
                    <option key={service.id} value={service.id} disabled={!serviceHasInvoicePrice(service)}>
                      {service.name} - {servicePriceLabel(service, language)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                {t.quantity}
                <input
                  type="number"
                  min="1"
                  value={contractQuantity}
                  onChange={(event) => setContractQuantity(event.target.value)}
                />
              </label>
              <label>
                {language === "sv" ? "Intervall" : "Interval"}
                <select value={contractInterval} onChange={(event) => setContractInterval(event.target.value)}>
                  <option value="monthly">{language === "sv" ? "Manad" : "Monthly"}</option>
                  <option value="quarterly">{language === "sv" ? "Kvartal" : "Quarterly"}</option>
                  <option value="yearly">{language === "sv" ? "Ar" : "Yearly"}</option>
                </select>
              </label>
              <label>
                {language === "sv" ? "Nasta fakturadatum" : "Next invoice date"}
                <input
                  type="date"
                  value={contractNextDate}
                  onChange={(event) => setContractNextDate(event.target.value)}
                />
              </label>
              <button type="submit" disabled={!token || !contractCustomerId || !contractServiceId}>
                {language === "sv" ? "Spara avtal" : "Save contract"}
              </button>
            </form>

            {contractMessage && <p className="message success">{contractMessage}</p>}

            <div className="contract-list">
              {contracts.length === 0 ? (
                <p className="empty-state">{language === "sv" ? "Inga avtal annu." : "No contracts yet."}</p>
              ) : contracts.map((contract) => {
                const isDue = contract.active && contract.nextInvoiceDate <= new Date().toISOString().slice(0, 10);

                return (
                  <article className={isDue ? "contract-card due" : "contract-card"} key={contract.id}>
                    <div>
                      <h3>{contract.customerName || "-"}</h3>
                      <span>{contract.serviceName || "-"}</span>
                      <span>{t.quantity}: {contract.quantity}</span>
                      <span>{language === "sv" ? "Intervall" : "Interval"}: {contract.interval}</span>
                      {contract.lastInvoiceNumber && <span>{language === "sv" ? "Senaste faktura" : "Last invoice"}: {contract.lastInvoiceNumber}</span>}
                    </div>
                    <div>
                      <strong>{language === "sv" ? "Nasta faktura" : "Next invoice"}: {contract.nextInvoiceDate}</strong>
                      <span className={`due-status ${isDue ? "due-status-soon" : "due-status-neutral"}`}>
                        {contract.active
                          ? (isDue ? (language === "sv" ? "Redo att fakturera" : "Ready to invoice") : (language === "sv" ? "Aktiv" : "Active"))
                          : (language === "sv" ? "Pausad" : "Paused")}
                      </span>
                      <button
                        type="button"
                        className="primary-small-button"
                        disabled={!token || !contract.active || isAccountingDateLocked(new Date().toISOString().slice(0, 10))}
                        onClick={() => createContractInvoice(contract)}
                      >
                        {language === "sv" ? "Skapa faktura" : "Create invoice"}
                      </button>
                      <button type="button" className="secondary-button" onClick={() => toggleContractActive(contract)}>
                        {contract.active ? (language === "sv" ? "Pausa" : "Pause") : (language === "sv" ? "Aktivera" : "Activate")}
                      </button>
                      <button type="button" className="danger-button" onClick={() => deleteContract(contract.id)}>
                        {language === "sv" ? "Ta bort" : "Delete"}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          </section>
        )}

        {activeView === "services" && (
          <section className="orders-section">
            <div className="section-heading">
              <h2>{t.serviceAdmin}</h2>
              <button type="button" className="secondary-button" onClick={() => {
                loadServices();
                loadAdminServices();
              }}>
                {t.refresh}
              </button>
            </div>

            <form onSubmit={handleSaveService} className="form service-form">
              <label>
                {t.serviceName}
                <input
                  value={serviceName}
                  onChange={(event) => setServiceName(event.target.value)}
                  placeholder="PT online Fokus 12"
                />
              </label>

              <label>
                {t.serviceDescription}
                <textarea
                  rows="4"
                  value={serviceDescription}
                  onChange={(event) => setServiceDescription(event.target.value)}
                  placeholder={language === "sv" ? "Kort beskrivning av tjansten" : "Short service description"}
                />
              </label>

              <div className="form-row">
                <label>
                  {t.ordinaryPrice}
                  <input
                    type="number"
                    min="0"
                    value={servicePrice}
                    onChange={(event) => setServicePrice(event.target.value)}
                    placeholder="2469"
                  />
                </label>

                <label>
                  {t.discountPrice}
                  <input
                    type="number"
                    min="0"
                    value={serviceDiscountPrice}
                    onChange={(event) => setServiceDiscountPrice(event.target.value)}
                    placeholder="2215"
                  />
                </label>
              </div>

              <label>
                {t.discountLabel}
                <input
                  value={serviceDiscountLabel}
                  onChange={(event) => setServiceDiscountLabel(event.target.value)}
                  placeholder={language === "sv" ? "Sommarkampanj" : "Summer campaign"}
                />
              </label>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={serviceActive}
                  onChange={(event) => setServiceActive(event.target.checked)}
                />
                {t.activeService}
              </label>

              <div className="button-row">
                <button type="submit" disabled={!token}>
                  {editingServiceId ? t.saveService : t.newService}
                </button>
                {editingServiceId && (
                  <button type="button" className="secondary-button" onClick={resetServiceForm}>
                    {language === "sv" ? "Avbryt" : "Cancel"}
                  </button>
                )}
              </div>
            </form>

            {serviceMessage && <p className="message success">{serviceMessage}</p>}

            <div className="service-admin-list">
              {adminServices.length === 0 ? (
                <p className="empty-state">{t.noServices}</p>
              ) : (
                adminServices.map((service) => (
                  <article className={service.active === false ? "service-admin-row inactive-service" : "service-admin-row"} key={service.id}>
                    <div>
                      <h3>{service.name}</h3>
                      <p>{service.description}</p>
                      {service.discountLabel && <span className="discount-badge">{service.discountLabel}</span>}
                      {service.active === false && <span className="status">{t.inactive}</span>}
                    </div>
                    <div className="service-price-box">
                      <strong>{servicePriceLabel(service, language)}</strong>
                      <span>{t.ordinaryPrice}: {service.price || 0} SEK</span>
                    </div>
                    <button type="button" className="secondary-button" onClick={() => startEditService(service)}>
                      {t.edit}
                    </button>
                  </article>
                ))
              )}
            </div>
          </section>
        )}

        {activeView === "activities" && (
          <section className="orders-section activity-section">
            <div className="section-heading">
              <div>
                <h2>{t.activities}</h2>
                <p className="automation-note">
                  {language === "sv"
                    ? "Samlad tidslinje for fakturor, betalningar, bankimport, Stripe, kostnader, paminnelser och verifikat."
                    : "A combined timeline for invoices, payments, bank import, Stripe, expenses, reminders and vouchers."}
                </p>
              </div>
              <div className="button-row">
                <span className="status">{filteredActivityRows.length} {language === "sv" ? "handelser" : "events"}</span>
                <button type="button" className="secondary-button" onClick={downloadActivityCsv}>
                  {t.exportCsv}
                </button>
              </div>
            </div>

            <div className="activity-summary-grid">
              <article>
                <span>{language === "sv" ? "Alla handelser" : "All events"}</span>
                <strong>{auditTrailRows.length}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Bank och Stripe" : "Bank and Stripe"}</span>
                <strong>{(auditTrailTypeCounts.bank || 0) + (auditTrailTypeCounts.stripe || 0)}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Oppna bankrader" : "Open bank rows"}</span>
                <strong>{activityOpenBankRows}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Belopp in i filter" : "Amount in filter"}</span>
                <strong>{activityIncomeTotal} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Belopp ut i filter" : "Amount out in filter"}</span>
                <strong>{activityExpenseTotal} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Senaste datum" : "Latest date"}</span>
                <strong>{activityLatestDate ? formatDateOnly(activityLatestDate) : "-"}</strong>
              </article>
            </div>

            <div className="invoice-filter-panel activity-filter-panel">
              <label>
                {language === "sv" ? "Sok handelser" : "Search events"}
                <div className="search-actions">
                  <input
                    value={activitySearch}
                    onChange={(event) => setActivitySearch(event.target.value)}
                    placeholder={language === "sv" ? "Fakturanummer, kund, bankreferens, verifikat..." : "Invoice number, customer, bank reference, voucher..."}
                  />
                  {activitySearch && (
                    <button type="button" className="secondary-button" onClick={() => setActivitySearch("")}>
                      {language === "sv" ? "Rensa" : "Clear"}
                    </button>
                  )}
                </div>
              </label>
              <div className="payment-filter-row">
                {activityFilterKeys.map((type) => (
                  <button
                    type="button"
                    className={activityFilter === type ? "filter-button active" : "filter-button"}
                    key={type}
                    onClick={() => setActivityFilter(type)}
                  >
                    {activityFilterLabel(type)}
                  </button>
                ))}
              </div>
            </div>

            {filteredActivityRows.length === 0 ? (
              <p className="empty-state">{language === "sv" ? "Inga handelser matchar filtret." : "No events match the filter."}</p>
            ) : (
              <div className="activity-timeline">
                {filteredActivityRows.slice(0, 100).map((row) => (
                  <button type="button" className={`activity-row ${row.type}`} key={row.id} onClick={row.action}>
                    <span className="activity-date">
                      <strong>{formatDateOnly(row.date) || "-"}</strong>
                      <small>{row.typeLabel}</small>
                    </span>
                    <span className="activity-main">
                      <strong>{row.title}</strong>
                      <small>{row.reference || "-"}</small>
                    </span>
                    <span className="activity-party">{row.party || "-"}</span>
                    <strong className={Number(row.amount || 0) < 0 ? "unbalanced-text" : ""}>
                      {row.amount ? `${row.amount} SEK` : "-"}
                    </strong>
                    <span className="status">{row.status || "-"}</span>
                  </button>
                ))}
              </div>
            )}
          </section>
        )}

        {activeView === "cashflow" && (
          <section className="orders-section cashflow-section">
            <div className="section-heading">
              <div>
                <h2>{t.cashflow}</h2>
                <p className="automation-note">
                  {language === "sv"
                    ? "Prognos for kassan baserad pa 1930 Foretagskonto, obetalda fakturor, Stripe-fordran, moms och historisk kostnadstakt."
                    : "Cash forecast based on account 1930 bank balance, unpaid invoices, Stripe receivable, VAT and historical spending pace."}
                </p>
              </div>
              <div className="button-row">
                <span className={cashflowProjectedBalance >= 0 ? "status success-status" : "status warning-status"}>
                  {language === "sv" ? "Prognos" : "Forecast"} {cashflowProjectedBalance} SEK
                </span>
                <button type="button" className="secondary-button" onClick={downloadCashflowCsv}>
                  {t.exportCsv}
                </button>
              </div>
            </div>

            <div className="cashflow-hero">
              <article className={cashflowProjectedBalance >= 0 ? "cashflow-main-card positive" : "cashflow-main-card negative"}>
                <span>{language === "sv" ? "Beraknad kassa efter period" : "Projected cash after period"}</span>
                <strong>{cashflowProjectedBalance} SEK</strong>
                <p>
                  {language === "sv"
                    ? `Period: ${todayInput} till ${cashflowEndDate}. Det har ar en arbetsprognos, inte ett bankbesked.`
                    : `Period: ${todayInput} to ${cashflowEndDate}. This is a working forecast, not a bank statement.`}
                </p>
              </article>
              <article className="cashflow-advice-card">
                <span>{language === "sv" ? "Kassalage" : "Cash position"}</span>
                <strong>
                  {cashflowProjectedBalance < 0
                    ? (language === "sv" ? "Risk for minus" : "Risk of negative cash")
                    : cashflowRunwayMonths === null
                      ? (language === "sv" ? "Ingen kostnadstakt annu" : "No spending pace yet")
                      : `${cashflowRunwayMonths} ${language === "sv" ? "manader runway" : "months runway"}`}
                </strong>
                <p>
                  {cashflowProjectedBalance < 0
                    ? (language === "sv"
                      ? "Prioritera forfallna fakturor, kontrollera Stripe-utbetalningar och planera momsbetalningen."
                      : "Prioritize overdue invoices, check Stripe payouts and plan the VAT payment.")
                    : (language === "sv"
                      ? "Kassan ser positiv ut i vald horisont. Kontrollera anda momsdatum och stora kommande kostnader."
                      : "Cash looks positive in the selected horizon. Still verify VAT dates and large upcoming costs.")}
                </p>
              </article>
            </div>

            <div className="payment-filter-row cashflow-horizon-row">
              {[30, 60, 90].map((days) => (
                <button
                  type="button"
                  className={cashflowHorizonDays === days ? "filter-button active" : "filter-button"}
                  key={days}
                  onClick={() => setCashflowHorizonDays(days)}
                >
                  {days} {language === "sv" ? "dagar" : "days"}
                </button>
              ))}
            </div>

            <div className="cashflow-summary-grid">
              <article>
                <span>{language === "sv" ? "Startkassa 1930" : "Opening cash 1930"}</span>
                <strong>{cashOpeningBalance} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Fakturor in" : "Invoices in"}</span>
                <strong>{cashflowInvoiceInTotal} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Stripe-fordran" : "Stripe receivable"}</span>
                <strong>{cashflowStripeInTotal} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Moms ut" : "VAT out"}</span>
                <strong>{cashflowVatOutTotal} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Kostnadsreserv" : "Expense reserve"}</span>
                <strong>{cashflowExpenseReserve} SEK</strong>
              </article>
            </div>

            <div className="cashflow-projection-grid">
              {cashflowProjectionRows.map((row) => (
                <article className={row.projectedBalance >= 0 ? "cashflow-projection-card positive" : "cashflow-projection-card negative"} key={row.days}>
                  <header>
                    <span>{row.days} {language === "sv" ? "dagar" : "days"}</span>
                    <strong>{row.projectedBalance} SEK</strong>
                  </header>
                  <p><span>{language === "sv" ? "Till" : "Until"}</span><strong>{formatDateOnly(row.endDate)}</strong></p>
                  <p><span>{language === "sv" ? "In" : "In"}</span><strong>{row.invoiceIn + row.stripeIn} SEK</strong></p>
                  <p><span>{language === "sv" ? "Ut" : "Out"}</span><strong>{row.reserve + row.vatOut} SEK</strong></p>
                </article>
              ))}
            </div>

            <div className="section-heading report-subheading">
              <div>
                <h2>{language === "sv" ? "Pengar in och ut" : "Cash in and out"}</h2>
                <p className="automation-note">
                  {language === "sv"
                    ? "Klicka pa en rad for att oppna faktura, betalningar, momsrapport eller rapporter."
                    : "Click a row to open the invoice, payments, VAT report or reports."}
                </p>
              </div>
            </div>

            {cashflowTimelineRows.length === 0 ? (
              <p className="empty-state">{language === "sv" ? "Ingen likviditetsdata finns annu." : "No cashflow data yet."}</p>
            ) : (
              <div className="cashflow-timeline">
                {cashflowTimelineRows.map((row) => (
                  <button type="button" className={`cashflow-row ${row.type}`} key={row.id} onClick={row.action}>
                    <span>
                      <strong>{formatDateOnly(row.date)}</strong>
                      <small>{row.source}</small>
                    </span>
                    <span>
                      <strong>{row.title}</strong>
                      <small>{row.detail}</small>
                    </span>
                    <strong>{row.type === "out" ? "-" : "+"}{row.amount} SEK</strong>
                    <span className="status">{row.status}</span>
                  </button>
                ))}
              </div>
            )}
          </section>
        )}

        {activeView === "budget" && (
          <section className="orders-section budget-section">
            <div className="section-heading">
              <div>
                <h2>{t.budget}</h2>
                <p className="automation-note">
                  {language === "sv"
                    ? "Satt manadsmal och jamfor mot verkliga intakter, kostnader, resultat och reserv."
                    : "Set monthly goals and compare against actual revenue, expenses, result and reserve."}
                </p>
              </div>
              <div className="button-row">
                <span className={budgetRiskMonths === 0 ? "status success-status" : "status warning-status"}>
                  {budgetRiskMonths} {language === "sv" ? "riskmanader" : "risk months"}
                </span>
                <button type="button" className="secondary-button" onClick={downloadBudgetCsv}>
                  {t.exportCsv}
                </button>
              </div>
            </div>

            <div className="budget-control-panel">
              <label>
                {language === "sv" ? "Intaktsmal per manad" : "Monthly revenue target"}
                <input
                  type="number"
                  min="0"
                  value={budgetRevenueTarget}
                  onChange={(event) => setBudgetRevenueTarget(event.target.value)}
                />
              </label>
              <label>
                {language === "sv" ? "Kostnadstak per manad" : "Monthly expense limit"}
                <input
                  type="number"
                  min="0"
                  value={budgetExpenseLimit}
                  onChange={(event) => setBudgetExpenseLimit(event.target.value)}
                />
              </label>
              <label>
                {language === "sv" ? "Reserv for skatt/moms %" : "Tax/VAT reserve %"}
                <input
                  type="number"
                  min="0"
                  max="80"
                  value={budgetReservePercent}
                  onChange={(event) => setBudgetReservePercent(event.target.value)}
                />
              </label>
            </div>

            <div className="budget-hero">
              <article className={budgetCurrentRow?.status === "ok" ? "budget-main-card ok" : budgetCurrentRow?.status === "risk" ? "budget-main-card risk" : "budget-main-card watch"}>
                <span>{language === "sv" ? "Senaste period" : "Latest period"}</span>
                <strong>{budgetCurrentRow ? budgetCurrentRow.statusLabel : "-"}</strong>
                <p>
                  {budgetCurrentRow
                    ? `${formatMonthLabel(budgetCurrentRow.monthKey, language)}: ${budgetCurrentRow.afterReserve} SEK ${language === "sv" ? "efter reserv" : "after reserve"}`
                    : (language === "sv" ? "Ingen manadsdata annu." : "No monthly data yet.")}
                </p>
              </article>
              <article>
                <span>{language === "sv" ? "Intaktsmal uppnatt" : "Revenue target reached"}</span>
                <strong>{budgetRevenueProgress}%</strong>
                <p>{budgetCurrentRow?.revenue || 0} / {budgetMonthlyRevenueTarget} SEK</p>
              </article>
              <article>
                <span>{language === "sv" ? "Kostnadstak anvant" : "Expense limit used"}</span>
                <strong>{budgetExpenseUsage}%</strong>
                <p>{budgetCurrentRow?.expenses || 0} / {budgetMonthlyExpenseLimit} SEK</p>
              </article>
              <article>
                <span>{language === "sv" ? "Reserv totalt" : "Total reserve"}</span>
                <strong>{budgetReserveTotal} SEK</strong>
                <p>{budgetReserveRate}% {language === "sv" ? "pa positivt resultat" : "of positive result"}</p>
              </article>
              <article className={budgetAfterReserveTotal >= 0 ? "ok" : "risk"}>
                <span>{language === "sv" ? "Efter reserv" : "After reserve"}</span>
                <strong>{budgetAfterReserveTotal} SEK</strong>
                <p>{budgetRows.length} {language === "sv" ? "perioder" : "periods"}</p>
              </article>
            </div>

            <div className="budget-status-strip">
              <span>{budgetRows.filter((row) => row.status === "ok").length} {language === "sv" ? "pa plan" : "on track"}</span>
              <span>{budgetWatchMonths} {language === "sv" ? "att bevaka" : "to watch"}</span>
              <span>{budgetRiskMonths} {language === "sv" ? "risk" : "risk"}</span>
            </div>

            <div className="budget-table">
              <div className="budget-row budget-header">
                <span>{language === "sv" ? "Manad" : "Month"}</span>
                <span>{language === "sv" ? "Intakter" : "Revenue"}</span>
                <span>{language === "sv" ? "Kostnader" : "Expenses"}</span>
                <span>{language === "sv" ? "Resultat" : "Result"}</span>
                <span>{language === "sv" ? "Reserv" : "Reserve"}</span>
                <span>{language === "sv" ? "Efter reserv" : "After reserve"}</span>
                <span>Status</span>
              </div>
              {budgetRows.map((row) => {
                const monthRange = monthRangeFromKey(row.monthKey);

                return (
                  <button
                    type="button"
                    className={`budget-row ${row.status}`}
                    key={row.monthKey}
                    onClick={() => {
                      setActiveView("bookkeeping");
                      setBookkeepingDateFrom(monthRange.from);
                      setBookkeepingDateTo(monthRange.to);
                      setBookkeepingSearch("");
                    }}
                  >
                    <strong>{formatMonthLabel(row.monthKey, language)}</strong>
                    <span>{row.revenue} SEK <small>{row.revenueVariance >= 0 ? "+" : ""}{row.revenueVariance}</small></span>
                    <span>{row.expenses} SEK <small>{row.expenseRoom >= 0 ? "+" : ""}{row.expenseRoom}</small></span>
                    <strong className={row.result >= 0 ? "balanced-text" : "unbalanced-text"}>{row.result} SEK</strong>
                    <span>{row.reserve} SEK</span>
                    <strong className={row.afterReserve >= 0 ? "balanced-text" : "unbalanced-text"}>{row.afterReserve} SEK</strong>
                    <span className="status">{row.statusLabel}</span>
                  </button>
                );
              })}
            </div>
          </section>
        )}

        {activeView === "payroll" && (
          <section className="orders-section payroll-section">
            <div className="section-heading">
              <div>
                <h2>{t.payroll}</h2>
                <p className="automation-note">
                  {language === "sv"
                    ? "Skapa loneutkast, rakna nettolon och bokfor lonen som ett flerradigt verifikat. Kontrollera alltid skattetabell och arbetsgivaravgifter innan riktig utbetalning."
                    : "Create payroll drafts, calculate net pay and book payroll as a multi-line voucher. Always verify tax table and employer contributions before real payment."}
                </p>
              </div>
              <div className="button-row">
                <span className={payrollDraftTotals.booked === payrollDrafts.length && payrollDrafts.length > 0 ? "status success-status" : "status warning-status"}>
                  {payrollDraftTotals.booked}/{payrollDrafts.length} {language === "sv" ? "bokforda" : "booked"}
                </span>
                <button type="button" className="secondary-button" onClick={downloadPayrollCsv} disabled={payrollDrafts.length === 0}>
                  {t.exportCsv}
                </button>
              </div>
            </div>

            <div className="payroll-warning-panel">
              <strong>{language === "sv" ? "Viktigt innan riktig lon" : "Important before real payroll"}</strong>
              <p>
                {language === "sv"
                  ? "Detta ar ett arbetsverktyg. Preliminar skatt och arbetsgivaravgift ar redigerbara och maste kontrolleras mot aktuell skattetabell, avtal och Skatteverkets uppgifter."
                  : "This is a working tool. Preliminary tax and employer contribution are editable and must be checked against the current tax table, agreement and tax authority information."}
              </p>
            </div>

            <div className="payroll-summary-grid">
              <article>
                <span>{language === "sv" ? "Brutto totalt" : "Total gross"}</span>
                <strong>{payrollDraftTotals.gross} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Preliminar skatt" : "Preliminary tax"}</span>
                <strong>{payrollDraftTotals.tax} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Nettolon" : "Net pay"}</span>
                <strong>{payrollDraftTotals.net} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Arbetsgivaravgift" : "Employer contribution"}</span>
                <strong>{payrollDraftTotals.employerFees} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Total lonekostnad" : "Total payroll cost"}</span>
                <strong>{payrollDraftTotals.totalCost} SEK</strong>
              </article>
            </div>

            <form className="form payroll-form" onSubmit={handleSavePayrollDraft}>
              <label>
                {language === "sv" ? "Anstalld" : "Employee"}
                <input
                  value={payrollEmployeeName}
                  onChange={(event) => setPayrollEmployeeName(event.target.value)}
                  placeholder={language === "sv" ? "Namn pa anstalld" : "Employee name"}
                />
              </label>
              <label>
                {t.personalNumber}
                <input
                  value={payrollEmployeePersonalNumber}
                  onChange={(event) => setPayrollEmployeePersonalNumber(event.target.value)}
                  placeholder="YYYYMMDD-XXXX"
                />
              </label>
              <label>
                {language === "sv" ? "Period" : "Period"}
                <input type="month" value={payrollPeriod} onChange={(event) => setPayrollPeriod(event.target.value)} />
              </label>
              <label>
                {language === "sv" ? "Bruttolon" : "Gross salary"}
                <input
                  type="number"
                  min="0"
                  value={payrollGrossSalary}
                  onChange={(event) => setPayrollGrossSalary(event.target.value)}
                  placeholder="30000"
                />
              </label>
              <label>
                {language === "sv" ? "Preliminar skatt %" : "Preliminary tax %"}
                <input
                  type="number"
                  min="0"
                  max="80"
                  step="0.01"
                  value={payrollTaxRate}
                  onChange={(event) => setPayrollTaxRate(event.target.value)}
                />
              </label>
              <label>
                {language === "sv" ? "Arbetsgivaravgift %" : "Employer contribution %"}
                <input
                  type="number"
                  min="0"
                  max="80"
                  step="0.01"
                  value={payrollEmployerRate}
                  onChange={(event) => setPayrollEmployerRate(event.target.value)}
                />
              </label>
              <label>
                {language === "sv" ? "Lonekonto" : "Salary account"}
                <select value={payrollSalaryAccount} onChange={(event) => setPayrollSalaryAccount(event.target.value)}>
                  <option value="7010">7010 Lon till anstallda</option>
                  <option value="7210">7210 Lon till tjansteman</option>
                </select>
              </label>
              <div className="payroll-preview-card">
                <span>{language === "sv" ? "Forhandsrakning" : "Preview"}</span>
                <strong>{payrollCurrentCalculation.netPay} SEK {language === "sv" ? "netto" : "net"}</strong>
                <p>{language === "sv" ? "Skatt" : "Tax"}: {payrollCurrentCalculation.withheldTax} SEK</p>
                <p>{language === "sv" ? "Arbetsgivaravgift" : "Employer contribution"}: {payrollCurrentCalculation.employerFee} SEK</p>
                <p>{language === "sv" ? "Total kostnad" : "Total cost"}: {payrollCurrentCalculation.totalCost} SEK</p>
              </div>
              <button type="submit" disabled={!token}>{language === "sv" ? "Spara loneutkast" : "Save payroll draft"}</button>
            </form>

            {payrollMessage && <p className="message success">{payrollMessage}</p>}

            <div className="payroll-booking-panel">
              <h3>{language === "sv" ? "Bokforingsforslag" : "Bookkeeping proposal"}</h3>
              <div className="payroll-booking-grid">
                <span>{payrollSalaryAccount}</span><span>Debet</span><strong>{payrollCurrentCalculation.gross} SEK</strong>
                <span>7510</span><span>Debet</span><strong>{payrollCurrentCalculation.employerFee} SEK</strong>
                <span>2710</span><span>Kredit</span><strong>{payrollCurrentCalculation.withheldTax} SEK</strong>
                <span>2731</span><span>Kredit</span><strong>{payrollCurrentCalculation.employerFee} SEK</strong>
                <span>1930</span><span>Kredit</span><strong>{payrollCurrentCalculation.netPay} SEK</strong>
              </div>
            </div>

            {payrollDrafts.length === 0 ? (
              <p className="empty-state">{language === "sv" ? "Inga loneutkast annu." : "No payroll drafts yet."}</p>
            ) : (
              <div className="payroll-draft-list">
                {payrollDrafts.map((draft) => {
                  const calculation = payrollDraftCalculation(draft);

                  return (
                    <article className={draft.status === "booked" ? "payroll-draft-card booked" : "payroll-draft-card"} key={draft.id}>
                      <div>
                        <strong>{draft.employeeName}</strong>
                        <span>{draft.period} - {draft.personalNumber || "-"}</span>
                        <span>{language === "sv" ? "Brutto" : "Gross"}: {calculation.gross} SEK</span>
                        <span>{language === "sv" ? "Netto" : "Net"}: {calculation.netPay} SEK</span>
                        <span>{language === "sv" ? "Total kostnad" : "Total cost"}: {calculation.totalCost} SEK</span>
                        {draft.voucherNumber && <span>{t.voucher}: {draft.voucherNumber}</span>}
                      </div>
                      <div className="payroll-card-actions">
                        <span className={draft.status === "booked" ? "status success-status" : "status warning-status"}>
                          {draft.status === "booked" ? (language === "sv" ? "Bokford" : "Booked") : (language === "sv" ? "Utkast" : "Draft")}
                        </span>
                        <button
                          type="button"
                          className="primary-small-button"
                          onClick={() => bookPayrollDraft(draft)}
                          disabled={!token || draft.status === "booked"}
                        >
                          {language === "sv" ? "Bokfor lon" : "Book payroll"}
                        </button>
                        <button type="button" className="danger-button soft" onClick={() => deletePayrollDraft(draft.id)}>
                          {language === "sv" ? "Ta bort" : "Remove"}
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </section>
        )}

        {activeView === "payments" && (
          <section className="orders-section">
            <div className="section-heading">
              <h2>{t.payments}</h2>
              <button type="button" className="secondary-button" onClick={loadInvoices}>
                {t.refresh}
              </button>
            </div>

            <div className="stats compact-stats">
              <article>
                <span>{t.unpaid}</span>
                <strong>{unpaidInvoices.length}</strong>
              </article>
              <article>
                <span>{t.paid}</span>
                <strong>{paidInvoices.length}</strong>
              </article>
            </div>
          </section>
        )}

        {(activeView === "invoices" || activeView === "payments") && <section className="orders-section">
          <div className="section-heading">
            <h2>{t.invoices}</h2>
            <div className="button-row">
              <button type="button" className="secondary-button" onClick={loadInvoices}>
                {t.refresh}
              </button>
              <button type="button" className="secondary-button" onClick={downloadInvoicesCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="invoice-summary-cards">
            <button type="button" className="invoice-summary-card danger-summary" onClick={() => setInvoiceFilter("overdue")}>
              <span>{t.overdue}</span>
              <strong>{overdueInvoices.length}</strong>
            </button>
            <button type="button" className="invoice-summary-card warning-summary" onClick={() => setInvoiceFilter("dueSoon")}>
              <span>{t.dueSoon}</span>
              <strong>{dueSoonInvoices.length}</strong>
            </button>
            <button type="button" className="invoice-summary-card" onClick={() => setInvoiceFilter("unpaid")}>
              <span>{t.unpaid}</span>
              <strong>{unpaidInvoices.length}</strong>
            </button>
            <button type="button" className="invoice-summary-card" onClick={() => setInvoiceFilter("unpaid")}>
              <span>{language === "sv" ? "Kvar att betala" : "Outstanding"}</span>
              <strong>{totalOutstanding} SEK</strong>
            </button>
          </div>

          <div className="filter-bar">
            {filterButton("all", t.all)}
            {filterButton("draft", t.draft)}
            {filterButton("sent", t.sent)}
            {filterButton("paid", t.paid)}
            {filterButton("unpaid", t.unpaid)}
            {filterButton("overdue", t.overdue)}
            {filterButton("dueSoon", t.dueSoon)}
          </div>

          <div className="invoice-filter-panel">
            <label>
              {language === "sv" ? "Fakturadatum fran" : "Invoice date from"}
              <input
                type="date"
                value={invoiceDateFrom}
                onChange={(event) => setInvoiceDateFrom(event.target.value)}
              />
            </label>

            <label>
              {language === "sv" ? "Fakturadatum till" : "Invoice date to"}
              <input
                type="date"
                value={invoiceDateTo}
                onChange={(event) => setInvoiceDateTo(event.target.value)}
              />
            </label>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentMonthRange();
                setInvoiceDateFrom(range.from);
                setInvoiceDateTo(range.to);
              }}
            >
              {language === "sv" ? "Denna manad" : "This month"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentQuarterRange();
                setInvoiceDateFrom(range.from);
                setInvoiceDateTo(range.to);
              }}
            >
              {language === "sv" ? "Detta kvartal" : "This quarter"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentYearRange();
                setInvoiceDateFrom(range.from);
                setInvoiceDateTo(range.to);
              }}
            >
              {language === "sv" ? "I ar" : "This year"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                setInvoiceFilter("all");
                setInvoiceDateFrom("");
                setInvoiceDateTo("");
                setInvoiceSearch("");
              }}
            >
              {language === "sv" ? "Rensa filter" : "Clear filters"}
            </button>
          </div>

          <div className="search-box">
            <label>
              {t.searchInvoices}
              <div className="search-actions">
                <input
                  value={invoiceSearch}
                  onChange={(event) => setInvoiceSearch(event.target.value)}
                  placeholder={language === "sv" ? "Fakturanummer, kund, status..." : "Invoice number, customer, status..."}
                />
                {invoiceSearch && (
                  <button type="button" className="secondary-button" onClick={() => setInvoiceSearch("")}>
                    {language === "sv" ? "Rensa" : "Clear"}
                  </button>
                )}
              </div>
            </label>
          </div>

          <div className="expense-summary-grid invoice-selection-summary">
            <article>
              <span>{language === "sv" ? "Fakturor i urval" : "Invoices in selection"}</span>
              <strong>{filteredInvoices.length}</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Fakturerat totalt" : "Invoiced total"}</span>
              <strong>{filteredInvoiceTotal} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Betalt" : "Paid"}</span>
              <strong>{filteredInvoicePaid} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Moms i urval" : "VAT in selection"}</span>
              <strong>{filteredInvoiceVat} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Kvar att betala" : "Outstanding"}</span>
              <strong>{filteredInvoiceOutstanding} SEK</strong>
            </article>
          </div>

          {filteredInvoices.length === 0 ? (
            <p className="empty-state">{t.noInvoices}</p>
          ) : (
            <div className="orders">
              {filteredInvoices.map((item) => (
                <article className="order-card" key={item.id}>
                  <div>
                    <h3>{invoiceNumber(item)}</h3>
                    <p>{item.customerName}</p>
                    <p>{t.date}: {item.invoiceDate || new Date().toISOString().slice(0, 10)}</p>
                    <p>{t.dueDate}: {item.dueDate || "-"}</p>
                    <p>{t.paymentTerms}: {item.paymentTermsDays || 30} {t.days}</p>
                    {item.ftaxApproved !== false && <p>{t.approvedForFtax}</p>}
                    {item.customer && (
                      <div className="invoice-customer">
                        <span>{t.email}: {item.customer.email || "-"}</span>
                        <span>{t.personalNumber}: {item.customer.personalNumber || "-"}</span>
                        <span>{t.address}: {item.customer.address || "-"}</span>
                        <span>{t.postalCode}: {item.customer.postalCode || "-"}</span>
                        <span>{t.city}: {item.customer.city || "-"}</span>
                        <span>Tel: {item.customer.phone || "-"}</span>
                      </div>
                    )}
                    <span className={`status status-${(item.status || "DRAFT").toLowerCase()}`}>
                      {statusLabel(item.status, language)}
                    </span>
                    <span className={`due-status ${invoiceDueStatus(item, language).className}`}>
                      {invoiceDueStatus(item, language).label}
                    </span>
                  </div>
                  <div>
                    <strong>{item.product?.name || t.serviceMissing}</strong>
                    <span>{t.quantity}: {item.quantity || 1}</span>
                    {invoiceDiscountAmount(item) > 0 && (
                      <>
                        <span>{language === "sv" ? "Ordinarie pris" : "Regular price"}: {invoiceOrdinaryPrice(item)} SEK</span>
                        <span className="discount-line">
                          {language === "sv" ? "Rabatt" : "Discount"}
                          {item.discountLabel ? ` (${item.discountLabel})` : ""}: -{invoiceDiscountAmount(item)} SEK
                        </span>
                      </>
                    )}
                    <span>{t.net}: {invoiceNetAmount(item)} SEK</span>
                    <span>{t.vat} 25%: {invoiceVatAmount(item)} SEK</span>
                    <span>{t.total}: {invoiceTotalAmount(item)} SEK</span>
                    <div className="payment-info">
                      <strong>{t.payment}</strong>
                      <span>PlusGiro: {item.plusGiro || "418 76 01-2"}</span>
                      <span>OCR: {item.ocrNumber || "1055065900139"}</span>
                      <span>{t.paymentRecipient}: {item.paymentRecipient || "Bank Norwegian"}</span>
                      {(item.status === "PAID" || item.status === "PARTIALLY_PAID") && (
                        <>
                          <span>{language === "sv" ? "Betaldatum" : "Payment date"}: {item.paidDate || "-"}</span>
                          <span>{language === "sv" ? "Betalt belopp" : "Paid amount"}: {invoicePaidAmount(item)} SEK</span>
                          <span>{language === "sv" ? "Kvar att betala" : "Remaining"}: {invoiceRemainingAmount(item)} SEK</span>
                          <span>{language === "sv" ? "Referens" : "Reference"}: {item.paymentReference || "-"}</span>
                        </>
                      )}
                      {item.payments?.length > 0 && (
                        <div className="payment-history">
                          <strong>{language === "sv" ? "Betalningshistorik" : "Payment history"}</strong>
                          {item.payments.map((payment) => (
                            <span key={payment.id}>
                              {payment.paymentDate || "-"} - {payment.amount} SEK - {payment.reference || "-"}
                            </span>
                          ))}
                        </div>
                      )}
                      <div className="invoice-history">
                        <strong>{language === "sv" ? "Fakturahistorik" : "Invoice history"}</strong>
                        <span>
                          {formatDateTime(item.createdAt)} - {language === "sv" ? "Faktura skapad" : "Invoice created"} - {item.customerName || "-"}
                        </span>
                        {[...(item.reminders || [])]
                          .sort((first, second) => new Date(second.createdAt || 0) - new Date(first.createdAt || 0))
                          .map((reminder) => (
                            <span key={reminder.id}>
                              {formatDateTime(reminder.createdAt)} - {invoiceHistoryLabel(reminder, language)} - {reminder.status} - {reminder.recipientEmail || "-"}
                            </span>
                          ))}
                      </div>
                    </div>
                    <div className="invoice-actions">
                      <button
                        type="button"
                        className="secondary-button"
                        disabled={!token || item.status === "SENT" || item.status === "PAID"}
                        onClick={() => updateInvoiceStatus(item.id, "sent")}
                      >
                        {t.markSent}
                      </button>
                      <label className="inline-date">
                        {language === "sv" ? "Betaldatum" : "Payment date"}
                        <input
                          type="date"
                          value={paymentDates[item.id] || new Date().toISOString().slice(0, 10)}
                          onChange={(event) => setPaymentDates({ ...paymentDates, [item.id]: event.target.value })}
                          disabled={!token || item.status === "PAID"}
                        />
                      </label>
                      {isAccountingDateLocked(paymentDates[item.id] || new Date().toISOString().slice(0, 10)) && (
                        <p className="message warning">{lockedAccountingMessage(paymentDates[item.id] || new Date().toISOString().slice(0, 10))}</p>
                      )}
                      <label className="inline-date">
                        {language === "sv" ? "Belopp" : "Amount"}
                        <input
                          type="number"
                          value={paymentAmounts[item.id] || invoiceRemainingAmount(item)}
                          onChange={(event) => setPaymentAmounts({ ...paymentAmounts, [item.id]: event.target.value })}
                          disabled={!token || item.status === "PAID"}
                        />
                      </label>
                      <label className="inline-date">
                        {language === "sv" ? "Referens" : "Reference"}
                        <input
                          value={paymentReferences[item.id] || ""}
                          onChange={(event) => setPaymentReferences({ ...paymentReferences, [item.id]: event.target.value })}
                          disabled={!token || item.status === "PAID"}
                          placeholder={language === "sv" ? "Bank/Swish/Stripe" : "Bank/Swish/Stripe"}
                        />
                      </label>
                      <p className="payment-hint">
                        {language === "sv"
                          ? "Belopp = det kunden betalade. Referens = Swish, Bank, Stripe eller transaktions-ID."
                          : "Amount = what the customer paid. Reference = Swish, Bank, Stripe or transaction ID."}
                      </p>
                      {invoiceShouldSendReminder(item, settings?.invoiceReminderDaysBeforeDue || 5) && (
                        <div className="invoice-reminder-panel">
                          <strong>{language === "sv" ? "Fakturapaminnelse" : "Invoice reminder"}</strong>
                          <span>{invoiceReminderRuleText(item, language)}</span>
                          <span>{t.email}: {invoiceCustomerEmail(item) || "-"}</span>
                          <span className="next-reminder-line">{nextAutomaticReminderText(item, settings, language)}</span>
                          {item.reminderSentDate && (
                            <span className="reminder-copy-status">
                              {language === "sv" ? "Paminnelse sparad" : "Reminder saved"}: {item.reminderSentDate}
                            </span>
                          )}
                          <div className="payment-reminder-actions">
                            <button type="button" className="primary-small-button" disabled={!token} onClick={() => sendInvoiceReminderEmail(item)}>
                              {language === "sv" ? "Skicka e-post" : "Send email"}
                            </button>
                            <button type="button" className="secondary-button" disabled={!token} onClick={() => openInvoiceReminderEmail(item)}>
                              {language === "sv" ? "Oppna e-post" : "Open email"}
                            </button>
                            <button type="button" className="secondary-button" disabled={!token} onClick={() => copyInvoiceReminder(item)}>
                              {language === "sv" ? "Kopiera text" : "Copy text"}
                            </button>
                          </div>
                        </div>
                      )}
                      <button
                        type="button"
                        className="secondary-button"
                        disabled={!token || item.status === "PAID" || isAccountingDateLocked(paymentDates[item.id] || new Date().toISOString().slice(0, 10))}
                        onClick={() => updateInvoiceStatus(item.id, "paid")}
                      >
                        {t.markPaid}
                      </button>
                      <button
                        type="button"
                        className="secondary-button"
                        disabled={!token || item.status === "PAID" || isAccountingDateLocked(new Date().toISOString().slice(0, 10))}
                        onClick={() => openStripeCheckout(item.id)}
                      >
                        Stripe
                      </button>
                      <button
                        type="button"
                        className="secondary-button"
                        disabled={!token}
                        onClick={() => openInvoicePdf(item.id)}
                      >
                        PDF
                      </button>
                      <button
                        type="button"
                        className="primary-small-button"
                        disabled={!token || !invoiceCustomerEmail(item)}
                        onClick={() => sendInvoiceEmail(item)}
                      >
                        {language === "sv" ? "Skicka faktura" : "Send invoice"}
                      </button>
                      <button
                        type="button"
                        className="danger-button"
                        disabled={!token}
                        onClick={() => deleteInvoice(item.id)}
                      >
                        {language === "sv" ? "Ta bort faktura" : "Delete invoice"}
                      </button>
                      {(item.status === "SENT" || item.status === "PAID") && !item.creditInvoice && (
                        <button
                          type="button"
                          className="secondary-button"
                          disabled={!token}
                          onClick={() => createCreditInvoice(item.id)}
                        >
                          {language === "sv" ? "Kreditera" : "Credit"}
                        </button>
                      )}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>}

        {activeView === "payments" && <section className="orders-section">
          <div className="section-heading">
            <h2>{t.payments}</h2>
            <button type="button" className="secondary-button" onClick={loadInvoices}>
              {t.refresh}
            </button>
          </div>

          <div className="stats compact-stats">
            <article>
              <span>{t.unpaid}</span>
              <strong>{unpaidInvoices.length}</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Delbetalda" : "Partially paid"}</span>
              <strong>{partiallyPaidInvoices.length}</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Forfallna" : "Overdue"}</span>
              <strong>{overdueInvoices.length}</strong>
            </article>
            <article>
              <span>{t.paid}</span>
              <strong>{paidInvoices.length}</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Kvar att betala" : "Outstanding"}</span>
              <strong>{totalOutstanding} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Betalt" : "Paid amount"}</span>
              <strong>{totalPaid} SEK</strong>
            </article>
          </div>

          <div className="payment-filter-row">
            <div className="filter-bar">
              <button
                type="button"
                className={paymentOverviewFilter === "open" ? "filter-button active" : "filter-button"}
                onClick={() => setPaymentOverviewFilter("open")}
              >
                {language === "sv" ? "Alla oppna" : "All open"}
              </button>
              <button
                type="button"
                className={paymentOverviewFilter === "overdue" ? "filter-button active" : "filter-button"}
                onClick={() => setPaymentOverviewFilter("overdue")}
              >
                {language === "sv" ? "Forfallna" : "Overdue"}
              </button>
              <button
                type="button"
                className={paymentOverviewFilter === "dueSoon" ? "filter-button active" : "filter-button"}
                onClick={() => setPaymentOverviewFilter("dueSoon")}
              >
                {language === "sv" ? "Forfaller snart" : "Due soon"}
              </button>
              <button
                type="button"
                className={paymentOverviewFilter === "partiallyPaid" ? "filter-button active" : "filter-button"}
                onClick={() => setPaymentOverviewFilter("partiallyPaid")}
              >
                {language === "sv" ? "Delbetalda" : "Partially paid"}
              </button>
              <button
                type="button"
                className={paymentOverviewFilter === "sent" ? "filter-button active" : "filter-button"}
                onClick={() => setPaymentOverviewFilter("sent")}
              >
                {t.sent}
              </button>
            </div>
            <div className="button-row">
              <span className="status">
                {paymentOverviewInvoices.length} {language === "sv" ? "fakturor" : "invoices"} - {paymentOverviewOutstanding} SEK
              </span>
              <button type="button" className="secondary-button" onClick={downloadPaymentOverviewCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="search-box">
            <label>
              {language === "sv" ? "Sok betalningar" : "Search payments"}
              <div className="search-actions">
                <input
                  value={paymentOverviewSearch}
                  onChange={(event) => setPaymentOverviewSearch(event.target.value)}
                  placeholder={language === "sv" ? "Fakturanummer, kund, personnummer, OCR..." : "Invoice number, customer, personal number, OCR..."}
                />
                {paymentOverviewSearch && (
                  <button type="button" className="secondary-button" onClick={() => setPaymentOverviewSearch("")}>
                    {language === "sv" ? "Rensa" : "Clear"}
                  </button>
                )}
              </div>
            </label>
          </div>

          <div className="bank-import-panel">
            <div className="section-heading compact-heading">
              <div>
                <h3>{language === "sv" ? "Stripe-utbetalning till bank" : "Stripe payout to bank"}</h3>
                <p>
                  {language === "sv"
                    ? "Nar Stripe betalar ut pengar till ditt bankkonto bokfor du bruttobeloppet, Stripe-avgiften och referensen fran Stripe."
                    : "When Stripe pays money to your bank account, book the gross amount, Stripe fee and Stripe reference."}
                </p>
              </div>
              <button type="button" className="secondary-button" onClick={downloadStripePayoutsCsv} disabled={stripePayouts.length === 0}>
                {t.exportCsv}
              </button>
            </div>
            <div className="expense-summary-grid bank-import-summary-grid">
              <article>
                <span>{language === "sv" ? "Stripe-forsaljning hemsida" : "Website Stripe sales"}</span>
                <strong>{stripeWebsiteSaleTotal} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Saldo 1580" : "Account 1580 balance"}</span>
                <strong>{stripeReceivableBalance} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Stripe brutto utbetalt" : "Stripe gross paid out"}</span>
                <strong>{stripePayoutGrossTotal} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Stripe-avgifter" : "Stripe fees"}</span>
                <strong>{stripePayoutFeeTotal} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Netto till bank" : "Net to bank"}</span>
                <strong>{stripePayoutNetTotal} SEK</strong>
              </article>
            </div>
            <p className={stripeReceivableBalance < 0 ? "warning-text" : "status"}>
              {stripeReceivableBalance > 0
                ? (language === "sv"
                  ? `${stripeReceivableBalance} SEK ligger kvar som fordran hos Stripe och ska stämmas av mot kommande utbetalningar.`
                  : `${stripeReceivableBalance} SEK remains as a Stripe receivable and should be reconciled against upcoming payouts.`)
                : stripeReceivableBalance < 0
                  ? (language === "sv"
                    ? "Konto 1580 ar negativt. Kontrollera om en Stripe-utbetalning bokforts utan motsvarande Stripe-forsaljning."
                    : "Account 1580 is negative. Check if a Stripe payout was booked without a matching Stripe sale.")
                  : (language === "sv"
                    ? "Konto 1580 ar avstamt just nu."
                    : "Account 1580 is reconciled right now.")}
            </p>
            <div className="stripe-manual-sale-panel">
              <div>
                <strong>{language === "sv" ? "Manuell Stripe-forsaljning fran hemsidan" : "Manual website Stripe sale"}</strong>
                <p>
                  {language === "sv"
                    ? "Anvand detta som fallback om webhooken inte ar kopplad annu eller om du vill testa bokforingsflodet."
                    : "Use this as a fallback if the webhook is not connected yet, or to test the bookkeeping flow."}
                </p>
              </div>
              <div className="form-grid">
                <label>
                  {language === "sv" ? "Forsaljningsdatum" : "Sale date"}
                  <input
                    type="date"
                    value={stripeWebsiteSaleDate}
                    onChange={(event) => setStripeWebsiteSaleDate(event.target.value)}
                  />
                </label>
                <label>
                  {language === "sv" ? "Belopp inkl. moms" : "Amount incl. VAT"}
                  <input
                    type="number"
                    min="0"
                    value={stripeWebsiteSaleAmount}
                    onChange={(event) => setStripeWebsiteSaleAmount(event.target.value)}
                    placeholder="999"
                  />
                </label>
                <label>
                  {language === "sv" ? "Stripe-referens" : "Stripe reference"}
                  <input
                    type="text"
                    value={stripeWebsiteSaleReference}
                    onChange={(event) => setStripeWebsiteSaleReference(event.target.value)}
                    placeholder={language === "sv" ? "pi_ eller cs_" : "pi_ or cs_"}
                  />
                </label>
              </div>
              <div className="button-row">
                <span className="status">
                  {language === "sv"
                    ? "Bokfor: 1580 debet, 3041 kredit, 2611 kredit"
                    : "Books: 1580 debit, 3041 credit, 2611 credit"}
                </span>
                <button type="button" className="primary-small-button" onClick={createStripeWebsiteSale}>
                  {language === "sv" ? "Bokfor Stripe-forsaljning" : "Book Stripe sale"}
                </button>
              </div>
              {stripeWebsiteSaleMessage && <strong className="status">{stripeWebsiteSaleMessage}</strong>}
            </div>
            <div className="form-grid">
              <label>
                {language === "sv" ? "Utbetalningsdatum" : "Payout date"}
                <input
                  type="date"
                  value={stripePayoutDate}
                  onChange={(event) => setStripePayoutDate(event.target.value)}
                />
              </label>
              <label>
                {language === "sv" ? "Bruttobelopp fran Stripe" : "Gross amount from Stripe"}
                <input
                  type="number"
                  min="0"
                  value={stripePayoutGrossAmount}
                  onChange={(event) => setStripePayoutGrossAmount(event.target.value)}
                  placeholder="1000"
                />
              </label>
              <label>
                {language === "sv" ? "Stripe-avgift" : "Stripe fee"}
                <input
                  type="number"
                  min="0"
                  value={stripePayoutFeeAmount}
                  onChange={(event) => setStripePayoutFeeAmount(event.target.value)}
                  placeholder="29"
                />
              </label>
              <label>
                {language === "sv" ? "Referens" : "Reference"}
                <input
                  type="text"
                  value={stripePayoutReference}
                  onChange={(event) => setStripePayoutReference(event.target.value)}
                  placeholder={language === "sv" ? "Stripe payout ID" : "Stripe payout ID"}
                />
              </label>
            </div>
            <div className="button-row">
              <span className="status">
                {language === "sv"
                  ? `Netto till bank: ${Math.max(Number(stripePayoutGrossAmount || 0) - Number(stripePayoutFeeAmount || 0), 0)} SEK`
                  : `Net to bank: ${Math.max(Number(stripePayoutGrossAmount || 0) - Number(stripePayoutFeeAmount || 0), 0)} SEK`}
              </span>
              <button type="button" className="primary-small-button" onClick={createStripePayout}>
                {language === "sv" ? "Bokfor Stripe-utbetalning" : "Book Stripe payout"}
              </button>
            </div>
            {stripePayoutMessage && <strong className="status">{stripePayoutMessage}</strong>}
            {stripeWebsiteSaleEntries.length > 0 && (
              <div className="stripe-payout-history stripe-sale-history">
                <strong>{language === "sv" ? "Senaste Stripe-forsaljningar fran hemsidan" : "Recent website Stripe sales"}</strong>
                {stripeWebsiteSaleEntries
                  .slice()
                  .sort((a, b) => String(b.voucherDate || "").localeCompare(String(a.voucherDate || "")))
                  .slice(0, 5)
                  .map((entry) => (
                    <article key={entry.id}>
                      <div>
                        <strong>{entry.voucherDate || "-"}</strong>
                        <span>{entry.description || "-"}</span>
                      </div>
                      <div>
                        <span>{language === "sv" ? "Verifikat" : "Voucher"}: {entry.voucherNumber || "-"}</span>
                        <strong>{language === "sv" ? "Belopp" : "Amount"}: {entry.debit || 0} SEK</strong>
                      </div>
                    </article>
                  ))}
              </div>
            )}
            {stripePayouts.length > 0 && (
              <div className="stripe-payout-history">
                <strong>{language === "sv" ? "Senaste Stripe-utbetalningar" : "Recent Stripe payouts"}</strong>
                {stripePayouts
                  .slice()
                  .sort((a, b) => String(b.payoutDate || "").localeCompare(String(a.payoutDate || "")))
                  .slice(0, 5)
                  .map((payout) => (
                    <article key={payout.id}>
                      <div>
                        <strong>{payout.payoutDate || "-"}</strong>
                        <span>{payout.reference || "-"}</span>
                      </div>
                      <div>
                        <span>{language === "sv" ? "Verifikat" : "Voucher"}: {payout.voucherNumber || "-"}</span>
                        <span>{language === "sv" ? "Brutto" : "Gross"}: {payout.grossAmount} SEK</span>
                        <span>{language === "sv" ? "Avgift" : "Fee"}: {payout.feeAmount} SEK</span>
                        <strong>{language === "sv" ? "Netto" : "Net"}: {payout.netAmount} SEK</strong>
                      </div>
                    </article>
                  ))}
              </div>
            )}
          </div>

          <div className="bank-import-panel">
            <div>
              <h3>{language === "sv" ? "Bankimport CSV" : "Bank import CSV"}</h3>
              <p>
                {language === "sv"
                  ? "Ladda upp en CSV-fil fran banken for att hitta betalningar som matchar fakturor. Filen sparas inte annu."
                  : "Upload a CSV file from your bank to find payments that match invoices. The file is not saved yet."}
              </p>
            </div>
            <div className="button-row bank-import-actions">
              <label className="file-upload-button">
                {language === "sv" ? "Valj bankfil" : "Choose bank file"}
                <input type="file" accept=".csv,text/csv" onChange={handleBankCsvUpload} />
              </label>
              <button type="button" className="secondary-button" onClick={downloadBankImportExampleCsv}>
                {language === "sv" ? "Ladda ner exempel" : "Download example"}
              </button>
              {bankImportRows.length > 0 && (
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => {
                    setBankImportRows([]);
                    setBankImportFilter("all");
                    setBankImportSearch("");
                    setBankImportMessage("");
                    setBankImportExpenseCategories({});
                    setBankImportExpenseVatRates({});
                    setBankImportExpenseDescriptions({});
                    setLastCreatedBankImportExpense(null);
                    setSkippedBankImportRows([]);
                  }}
                >
                  {language === "sv" ? "Rensa import" : "Clear import"}
                </button>
              )}
            </div>
            <div className="bank-rules-panel">
              <div className="section-heading bank-rules-heading">
                <div>
                  <h4>{language === "sv" ? "Automatiska bankregler" : "Automatic bank rules"}</h4>
                  <p className="automation-note">
                    {language === "sv"
                      ? "Reglerna laser texten i bankraden och foreslar kostnadskonto, moms och bokforingstext."
                      : "Rules read the bank row text and suggest expense account, VAT and bookkeeping text."}
                  </p>
                </div>
                <div className="button-row">
                  <span className="status">
                    {bankImportRules.filter((rule) => rule.enabled !== false).length}/{bankImportRules.length} {language === "sv" ? "aktiva" : "active"}
                  </span>
                  <button type="button" className="secondary-button" onClick={resetBankImportRules}>
                    {language === "sv" ? "Aterstall regler" : "Reset rules"}
                  </button>
                </div>
              </div>
              <form className="bank-rule-form" onSubmit={addBankImportRule}>
                <label>
                  {language === "sv" ? "Nyckelord" : "Keywords"}
                  <input
                    type="text"
                    value={bankRuleKeyword}
                    onChange={(event) => setBankRuleKeyword(event.target.value)}
                    placeholder={language === "sv" ? "stripe gym bankavgift" : "stripe gym bank fee"}
                  />
                </label>
                <label>
                  {language === "sv" ? "Konto" : "Account"}
                  <select value={bankRuleCategory} onChange={(event) => setBankRuleCategory(event.target.value)}>
                    <option value="5420">5420 Programvaror</option>
                    <option value="5410">5410 Forbrukningsinventarier</option>
                    <option value="5800">5800 Resekostnader</option>
                    <option value="6570">6570 Bankkostnader</option>
                    <option value="4010">4010 Inkop</option>
                  </select>
                </label>
                <label>
                  {language === "sv" ? "Moms" : "VAT"}
                  <select value={bankRuleVatRate} onChange={(event) => setBankRuleVatRate(Number(event.target.value))}>
                    <option value={25}>25%</option>
                    <option value={12}>12%</option>
                    <option value={6}>6%</option>
                    <option value={0}>0%</option>
                  </select>
                </label>
                <label>
                  {language === "sv" ? "Bokforingstext" : "Bookkeeping text"}
                  <input
                    type="text"
                    value={bankRuleDescription}
                    onChange={(event) => setBankRuleDescription(event.target.value)}
                    placeholder={language === "sv" ? "T.ex. Stripe-avgift" : "E.g. Stripe fee"}
                  />
                </label>
                <button type="submit" className="primary-small-button">
                  {language === "sv" ? "Lagg till regel" : "Add rule"}
                </button>
              </form>
              <div className="bank-rule-list">
                {bankImportRules.map((rule) => (
                  <article className={rule.enabled === false ? "bank-rule-card disabled" : "bank-rule-card"} key={rule.id}>
                    <div>
                      <strong>{rule.keyword}</strong>
                      <span>{rule.description || "-"}</span>
                    </div>
                    <div>
                      <span>{rule.category} - {expenseCategoryLabel(rule.category)}</span>
                      <span>{rule.vatRate}% {language === "sv" ? "moms" : "VAT"}</span>
                    </div>
                    <div className="button-row">
                      <button type="button" className="secondary-button" onClick={() => toggleBankImportRule(rule.id)}>
                        {rule.enabled === false
                          ? (language === "sv" ? "Aktivera" : "Enable")
                          : (language === "sv" ? "Stang av" : "Disable")}
                      </button>
                      <button type="button" className="danger-button" onClick={() => removeBankImportRule(rule.id)}>
                        {language === "sv" ? "Ta bort" : "Delete"}
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>
            {bankImportMessage && <strong className="status">{bankImportMessage}</strong>}
            {skippedBankImportRows.length > 0 && (
              <div className="bank-import-skipped-panel">
                <strong>
                  {skippedBankImportRows.length} {language === "sv" ? "overhoppad rad/overhoppade rader" : "skipped row(s)"}
                </strong>
                <div>
                  {skippedBankImportRows.map((row) => (
                    <button
                      type="button"
                      className="secondary-button"
                      key={row.id}
                      onClick={() => restoreSkippedBankImportRow(row)}
                    >
                      {language === "sv" ? "Aterstall" : "Restore"} {row.date || "-"} {row.amount} SEK
                    </button>
                  ))}
                </div>
              </div>
            )}
            {lastCreatedBankImportExpense && (
              <div className="bank-import-follow-up">
                <span>
                  {language === "sv"
                    ? "Kostnaden saknar fortfarande kvitto/underlag."
                    : "The expense still needs a receipt/document."}
                </span>
                <button type="button" className="secondary-button" onClick={openLastBankImportExpenseReceipts}>
                  {language === "sv" ? "Oppna underlag" : "Open receipts"}
                </button>
              </div>
            )}
            {bankImportRows.length > 0 && (
              <>
                <div className="expense-summary-grid bank-import-summary-grid">
                  <article>
                    <span>{language === "sv" ? "Importerade rader" : "Imported rows"}</span>
                    <strong>{bankImportRows.length}</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Matchade fakturor" : "Matched invoices"}</span>
                    <strong>{bankImportMatchedCount}</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Regeltraffar" : "Rule matches"}</span>
                    <strong>{bankImportRuleMatchedCount}</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Utan matchning" : "Unmatched"}</span>
                    <strong>{bankImportRows.length - bankImportMatchedCount}</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Inbetalningar" : "Incoming"}</span>
                    <strong>{bankImportIncomingAmount} SEK</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Utbetalningar" : "Outgoing"}</span>
                    <strong>{bankImportOutgoingAmount} SEK</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Netto bankfil" : "Bank file net"}</span>
                    <strong>{bankImportTotalAmount} SEK</strong>
                  </article>
                </div>
                <div className="bank-workflow-grid">
                  <button type="button" className="bank-workflow-card ready" onClick={() => setBankImportFilter("ready")}>
                    <span>{language === "sv" ? "Redo att boka" : "Ready to book"}</span>
                    <strong>{bankImportInvoiceReadyCount + bankImportExpenseReadyCount}</strong>
                    <small>
                      {language === "sv"
                        ? `${bankImportInvoiceReadyCount} faktura, ${bankImportExpenseReadyCount} kostnad`
                        : `${bankImportInvoiceReadyCount} invoice, ${bankImportExpenseReadyCount} expense`}
                    </small>
                  </button>
                  <button type="button" className="bank-workflow-card review" onClick={() => setBankImportFilter("review")}>
                    <span>{language === "sv" ? "Behover kontroll" : "Needs review"}</span>
                    <strong>{bankImportNeedsReviewCount}</strong>
                    <small>{language === "sv" ? "Saknar saker matchning eller regel" : "Missing safe match or rule"}</small>
                  </button>
                  <button type="button" className="bank-workflow-card done" onClick={() => setBankImportFilter("booked")}>
                    <span>{language === "sv" ? "Redan bokfort" : "Already booked"}</span>
                    <strong>{bankImportAlreadyBookedCount}</strong>
                    <small>{language === "sv" ? "Liknar befintlig kostnad" : "Looks like an existing expense"}</small>
                  </button>
                  <button type="button" className="bank-workflow-card neutral" onClick={() => setBankImportFilter("expenses")}>
                    <span>{language === "sv" ? "Utbetalningar" : "Outgoing rows"}</span>
                    <strong>{bankImportRows.filter((row) => (row.amount || 0) < 0).length}</strong>
                    <small>{language === "sv" ? "Kan bli kostnader" : "Can become expenses"}</small>
                  </button>
                </div>
                <div className="filter-bar bank-import-filter-bar">
                  <button
                    type="button"
                    className={bankImportFilter === "all" ? "filter-button active" : "filter-button"}
                    onClick={() => setBankImportFilter("all")}
                  >
                    {language === "sv" ? "Alla" : "All"}
                  </button>
                  <button
                    type="button"
                    className={bankImportFilter === "matched" ? "filter-button active" : "filter-button"}
                    onClick={() => setBankImportFilter("matched")}
                  >
                    {language === "sv" ? "Matchade" : "Matched"}
                  </button>
                  <button
                    type="button"
                    className={bankImportFilter === "unmatched" ? "filter-button active" : "filter-button"}
                    onClick={() => setBankImportFilter("unmatched")}
                  >
                    {language === "sv" ? "Utan matchning" : "Unmatched"}
                  </button>
                  <button
                    type="button"
                    className={bankImportFilter === "ready" ? "filter-button active" : "filter-button"}
                    onClick={() => setBankImportFilter("ready")}
                  >
                    {language === "sv" ? "Redo" : "Ready"}
                  </button>
                  <button
                    type="button"
                    className={bankImportFilter === "review" ? "filter-button active" : "filter-button"}
                    onClick={() => setBankImportFilter("review")}
                  >
                    {language === "sv" ? "Kontroll" : "Review"}
                  </button>
                  <button
                    type="button"
                    className={bankImportFilter === "expenses" ? "filter-button active" : "filter-button"}
                    onClick={() => setBankImportFilter("expenses")}
                  >
                    {language === "sv" ? "Kostnader" : "Expenses"}
                  </button>
                  <button
                    type="button"
                    className={bankImportFilter === "booked" ? "filter-button active" : "filter-button"}
                    onClick={() => setBankImportFilter("booked")}
                  >
                    {language === "sv" ? "Redan bokfort" : "Already booked"}
                  </button>
                  <span className="status">
                    {filteredBankImportRows.length} {language === "sv" ? "visas" : "shown"}
                  </span>
                </div>
                <AnimatedGlowingSearchBar
                  label={language === "sv" ? "Sok i bankimport" : "Search bank import"}
                  value={bankImportSearch}
                  onChange={(event) => setBankImportSearch(event.target.value)}
                  placeholder={language === "sv" ? "Datum, text, referens, OCR, belopp..." : "Date, text, reference, OCR, amount..."}
                  onClear={() => setBankImportSearch("")}
                  clearLabel={language === "sv" ? "Rensa" : "Clear"}
                  name="bank-import-search"
                />
                <div className="bank-import-list">
                  {filteredBankImportRows.map((row) => {
                    const match = findBankImportMatch(row);
                    const selectedExpenseCategory = bankImportExpenseCategory(row);
                    const selectedVatRate = bankImportExpenseVatRate(row, selectedExpenseCategory);
                    const suggestedExpenseAmounts = bankImportExpenseAmounts(row, selectedExpenseCategory, selectedVatRate);
                    const existingExpenseMatch = findExistingExpenseForBankRow(row);
                    const matchedExpenseRule = bankImportExpenseRule(row);
                    const workflow = bankImportWorkflow(row);

                    return (
                      <article className={`bank-import-row ${match ? "matched" : ""} ${workflow.tone}`} key={row.id}>
                        <div>
                          <strong>{row.date || "-"}</strong>
                          <span className={`bank-workflow-pill ${workflow.tone}`}>{workflow.title}</span>
                          <span>{row.description || "-"}</span>
                          <span>{language === "sv" ? "Referens" : "Reference"}: {row.reference || "-"}</span>
                          <span>{workflow.nextAction}</span>
                        </div>
                        <div>
                          <strong>{row.amount} SEK</strong>
                          {match ? (
                            <>
                              <span>{language === "sv" ? "Matchar" : "Matches"}: {invoiceNumber(match)}</span>
                              <span>{match.customerName}</span>
                              <button
                                type="button"
                                className="secondary-button"
                                onClick={() => {
                                  setPaymentOverviewSearch(invoiceNumber(match));
                                  setPaymentDates((current) => ({ ...current, [match.id]: bankImportPaymentDate(row) }));
                                  setPaymentAmounts((current) => ({ ...current, [match.id]: Math.abs(row.amount || 0) }));
                                  setPaymentReferences((current) => ({ ...current, [match.id]: row.reference || row.description || "Bank CSV" }));
                                }}
                              >
                                {language === "sv" ? "Anvand forslag" : "Use suggestion"}
                              </button>
                              <button
                                type="button"
                                className="primary-small-button"
                                onClick={() => registerBankImportPayment(row, match)}
                              >
                                {language === "sv" ? "Registrera betalning" : "Register payment"}
                              </button>
                              <button
                                type="button"
                                className="secondary-button"
                                onClick={() => skipBankImportRow(row)}
                              >
                                {language === "sv" ? "Hoppa over rad" : "Skip row"}
                              </button>
                            </>
                          ) : (
                            <>
                              <span className="warning-text">{language === "sv" ? "Ingen fakturamatchning" : "No invoice match"}</span>
                              {(row.amount || 0) < 0 && (
                                <>
                                  {matchedExpenseRule && (
                                    <span className="status success-status">
                                      {language === "sv" ? "Regel" : "Rule"}: {matchedExpenseRule.keyword} - {matchedExpenseRule.category}, {matchedExpenseRule.vatRate}% {language === "sv" ? "moms" : "VAT"}
                                    </span>
                                  )}
                                  <label className="bank-import-category-select">
                                    <span>{language === "sv" ? "Kostnadskategori" : "Expense category"}</span>
                                    <select
                                      value={selectedExpenseCategory}
                                      onChange={(event) => {
                                        setBankImportExpenseCategories((current) => ({
                                          ...current,
                                          [row.id]: event.target.value
                                        }));
                                        setBankImportExpenseVatRates((current) => ({
                                          ...current,
                                          [row.id]: defaultBankImportVatRate(event.target.value)
                                        }));
                                      }}
                                    >
                                      <option value="5420">5420 Programvaror</option>
                                      <option value="5410">5410 Forbrukningsinventarier</option>
                                      <option value="5800">5800 Resekostnader</option>
                                      <option value="6570">6570 Bankkostnader</option>
                                      <option value="4010">4010 Inkop</option>
                                    </select>
                                  </label>
                                  <label className="bank-import-category-select">
                                    <span>{language === "sv" ? "Moms" : "VAT"}</span>
                                    <select
                                      value={selectedVatRate}
                                      onChange={(event) => {
                                        setBankImportExpenseVatRates((current) => ({
                                          ...current,
                                          [row.id]: Number(event.target.value)
                                        }));
                                      }}
                                    >
                                      <option value={25}>25%</option>
                                      <option value={12}>12%</option>
                                      <option value={6}>6%</option>
                                      <option value={0}>0%</option>
                                    </select>
                                  </label>
                                  <label className="bank-import-description-input">
                                    <span>{language === "sv" ? "Beskrivning i bokforingen" : "Bookkeeping description"}</span>
                                    <input
                                      type="text"
                                      value={bankImportExpenseDescription(row)}
                                      onChange={(event) => {
                                        setBankImportExpenseDescriptions((current) => ({
                                          ...current,
                                          [row.id]: event.target.value
                                        }));
                                      }}
                                    />
                                  </label>
                                  <span>{language === "sv" ? "Netto/moms" : "Net/VAT"}: {suggestedExpenseAmounts.netAmount} / {suggestedExpenseAmounts.vatAmount} SEK</span>
                                  {existingExpenseMatch && (
                                    <span className="warning-text">
                                      {language === "sv"
                                        ? `Verkar redan bokford: ${existingExpenseMatch.description}`
                                        : `Looks already booked: ${existingExpenseMatch.description}`}
                                    </span>
                                  )}
                                  <button
                                    type="button"
                                    className="primary-small-button"
                                    disabled={Boolean(existingExpenseMatch)}
                                    onClick={() => createExpenseFromBankImport(row)}
                                  >
                                    {existingExpenseMatch
                                      ? (language === "sv" ? "Redan bokford" : "Already booked")
                                      : (language === "sv" ? "Skapa kostnad" : "Create expense")}
                                  </button>
                                </>
                              )}
                              <button
                                type="button"
                                className="secondary-button"
                                onClick={() => skipBankImportRow(row)}
                              >
                                {language === "sv" ? "Hoppa over rad" : "Skip row"}
                              </button>
                            </>
                          )}
                        </div>
                      </article>
                    );
                  })}
                </div>
              </>
            )}
          </div>

          <div className="bank-reconciliation-panel">
            <div className="section-heading">
              <div>
                <h3>{language === "sv" ? "Bankavstamning" : "Bank reconciliation"}</h3>
                <p className="automation-note">
                  {language === "sv"
                    ? "Historik over bankrader som har bokats som fakturabetalning, kostnad eller hoppats over."
                    : "History of bank rows booked as invoice payments, expenses or skipped."}
                </p>
              </div>
              <div className="button-row">
                <button type="button" className="secondary-button" onClick={downloadBankReconciliationCsv} disabled={bankReconciliationHistory.length === 0}>
                  {language === "sv" ? "Exportera avstamning" : "Export reconciliation"}
                </button>
                <button type="button" className="secondary-button" onClick={clearBankReconciliationHistory} disabled={bankReconciliationHistory.length === 0}>
                  {language === "sv" ? "Rensa historik" : "Clear history"}
                </button>
              </div>
            </div>

            <div className="expense-summary-grid bank-reconciliation-summary-grid">
              <article>
                <span>{language === "sv" ? "Bokade bankrader" : "Booked rows"}</span>
                <strong>{bankReconciliationBookedRows.length}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Overhoppade" : "Skipped"}</span>
                <strong>{bankReconciliationSkippedRows.length}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Bokade inbetalningar" : "Booked incoming"}</span>
                <strong>{bankReconciliationIncomingAmount} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Bokade utbetalningar" : "Booked outgoing"}</span>
                <strong>{bankReconciliationOutgoingAmount} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Kvar i import" : "Still in import"}</span>
                <strong>{bankReconciliationOpenAmount} SEK</strong>
              </article>
            </div>

            {bankReconciliationHistory.length === 0 ? (
              <p className="empty-state">
                {language === "sv"
                  ? "Ingen bankavstamningshistorik annu. Importera en bankfil och behandla rader for att fylla listan."
                  : "No bank reconciliation history yet. Import a bank file and process rows to fill the list."}
              </p>
            ) : (
              <div className="bank-reconciliation-list">
                {bankReconciliationHistory.slice(0, 12).map((entry) => (
                  <article className={`bank-reconciliation-row bank-reconciliation-${entry.status}`} key={entry.id}>
                    <div>
                      <strong>{entry.date || "-"}</strong>
                      <span>{entry.description || "-"}</span>
                      <span>{language === "sv" ? "Referens" : "Reference"}: {entry.reference || "-"}</span>
                    </div>
                    <div>
                      <strong>{entry.amount || 0} SEK</strong>
                      <span>{entry.matchLabel || "-"}</span>
                      <span className="status">{entry.status === "booked" ? (language === "sv" ? "Bokad" : "Booked") : (language === "sv" ? "Overhoppad" : "Skipped")}</span>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>

          <div className="payment-overview-list">
            {paymentOverviewInvoices.length === 0 ? (
              <p className="empty-state">{language === "sv" ? "Inga fakturor i detta urval." : "No invoices in this selection."}</p>
            ) : paymentOverviewInvoices.map((item) => {
              const dueStatus = invoiceDueStatus(item, language);
              return (
                <article className={invoiceIsOverdue(item) ? "payment-overview-row overdue" : "payment-overview-row"} key={item.id}>
                  <div>
                    <strong>{invoiceNumber(item)}</strong>
                    <span>{item.customerName}</span>
                    <span>{t.personalNumber}: {item.customer?.personalNumber || "-"}</span>
                    <span>{statusLabel(item.status, language)}</span>
                    <span>{t.dueDate}: {item.dueDate || "-"}</span>
                    <strong className={`due-status ${dueStatus.className}`}>{dueStatus.label}</strong>
                    {invoiceShouldSendReminder(item, settings?.invoiceReminderDaysBeforeDue || 5) && !invoiceIsOverdue(item) && (
                      <strong className="due-soon-label">{invoiceReminderRuleText(item, language)}</strong>
                    )}
                    {invoiceIsOverdue(item) && (
                      <strong className="overdue-label">{language === "sv" ? "Forfallen" : "Overdue"}</strong>
                    )}
                  </div>
                  <div>
                    <span>{language === "sv" ? "Totalt" : "Total"}: {invoiceTotalAmount(item)} SEK</span>
                    <span>{language === "sv" ? "Betalt" : "Paid"}: {invoicePaidAmount(item)} SEK</span>
                    <strong>{language === "sv" ? "Kvar" : "Remaining"}: {invoiceRemainingAmount(item)} SEK</strong>
                    <span>{language === "sv" ? "OCR" : "OCR"}: {item.ocrNumber || "-"}</span>
                    <span>{language === "sv" ? "Referens" : "Reference"}: {item.paymentReference || "-"}</span>
                    <button type="button" className="secondary-button" onClick={() => copyPaymentInfo(item)}>
                      {copiedPaymentInfoId === item.id
                        ? (language === "sv" ? "Kopierad" : "Copied")
                        : (language === "sv" ? "Kopiera betalinfo" : "Copy payment info")}
                    </button>
                    {item.reminderSentDate && (
                      <span className="reminder-copy-status">
                        {language === "sv" ? "Paminnelse sparad" : "Reminder saved"}: {item.reminderSentDate}
                      </span>
                    )}
                    {invoiceShouldSendReminder(item, settings?.invoiceReminderDaysBeforeDue || 5) && (
                      <div className="payment-reminder-actions">
                        <button type="button" className="primary-small-button" onClick={() => sendInvoiceReminderEmail(item)}>
                          {language === "sv" ? "Skicka e-post" : "Send email"}
                        </button>
                        <button type="button" className="secondary-button" onClick={() => openInvoiceReminderEmail(item)}>
                          {language === "sv" ? "Oppna e-post" : "Open email"}
                        </button>
                        <button type="button" className="secondary-button" onClick={() => copyInvoiceReminder(item)}>
                          {language === "sv" ? "Kopiera paminnelse" : "Copy reminder"}
                        </button>
                      </div>
                    )}
                  </div>
                </article>
              );
            })}
          </div>

          <div className="section-heading">
            <h2>{t.expenses}</h2>
            <div className="button-row">
              <button type="button" className="secondary-button" onClick={() => loadExpenses()}>
                {t.refresh}
              </button>
              <button type="button" className="secondary-button" onClick={downloadExpensesCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="expense-summary-grid">
            <article>
              <span>{language === "sv" ? "Kostnader totalt" : "Total expenses"}</span>
              <strong>{filteredExpenseTotal} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Netto" : "Net"}</span>
              <strong>{filteredExpenseNet} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Moms" : "VAT"}</span>
              <strong>{filteredExpenseVat} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Saknar kvitto" : "Missing receipts"}</span>
              <strong>{filteredExpensesMissingReceipt.length}</strong>
            </article>
          </div>

          <div className="expense-filter-panel">
            <div className="filter-bar">
              <button
                type="button"
                className={expenseFilter === "all" ? "filter-button active" : "filter-button"}
                onClick={() => setExpenseFilter("all")}
              >
                {t.all}
              </button>
              <button
                type="button"
                className={expenseFilter === "withReceipt" ? "filter-button active" : "filter-button"}
                onClick={() => setExpenseFilter("withReceipt")}
              >
                {language === "sv" ? "Med kvitto" : "With receipt"}
              </button>
              <button
                type="button"
                className={expenseFilter === "missingReceipt" ? "filter-button active" : "filter-button"}
                onClick={() => setExpenseFilter("missingReceipt")}
              >
                {language === "sv" ? "Saknar kvitto" : "Missing receipt"}
              </button>
            </div>

            <label>
              {t.category}
              <select value={expenseCategoryFilter} onChange={(event) => setExpenseCategoryFilter(event.target.value)}>
                <option value="all">{t.all}</option>
                {expenseCategories.map((category) => (
                  <option value={category} key={category}>{expenseCategoryLabel(category)}</option>
                ))}
              </select>
            </label>

            <label>
              {language === "sv" ? "Fran datum" : "From date"}
              <input
                type="date"
                value={expenseDateFrom}
                onChange={(event) => setExpenseDateFrom(event.target.value)}
              />
            </label>

            <label>
              {language === "sv" ? "Till datum" : "To date"}
              <input
                type="date"
                value={expenseDateTo}
                onChange={(event) => setExpenseDateTo(event.target.value)}
              />
            </label>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentMonthRange();
                setExpenseDateFrom(range.from);
                setExpenseDateTo(range.to);
              }}
            >
              {language === "sv" ? "Denna manad" : "This month"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentQuarterRange();
                setExpenseDateFrom(range.from);
                setExpenseDateTo(range.to);
              }}
            >
              {language === "sv" ? "Detta kvartal" : "This quarter"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentYearRange();
                setExpenseDateFrom(range.from);
                setExpenseDateTo(range.to);
              }}
            >
              {language === "sv" ? "I ar" : "This year"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                setExpenseFilter("all");
                setExpenseCategoryFilter("all");
                setExpenseDateFrom("");
                setExpenseDateTo("");
                setExpenseSearch("");
              }}
            >
              {language === "sv" ? "Rensa filter" : "Clear filters"}
            </button>
          </div>

          <div className="search-box">
            <label>
              {language === "sv" ? "Sok kostnader" : "Search expenses"}
              <div className="search-actions">
                <input
                  value={expenseSearch}
                  onChange={(event) => setExpenseSearch(event.target.value)}
                  placeholder={language === "sv" ? "Beskrivning, kategori, kvitto..." : "Description, category, receipt..."}
                />
                {expenseSearch && (
                  <button type="button" className="secondary-button" onClick={() => setExpenseSearch("")}>
                    {language === "sv" ? "Rensa" : "Clear"}
                  </button>
                )}
              </div>
            </label>
          </div>

          {expenseCategorySummary.length > 0 && (
            <div className="expense-category-list">
              {expenseCategorySummary.map((group) => (
                <article className="expense-category-row" key={group.category}>
                  <div>
                    <strong>{group.label}</strong>
                    <span>{group.count} {language === "sv" ? "kostnad/kostnader" : "expense(s)"}</span>
                  </div>
                  <div>
                    <span>{t.net}: {group.net} SEK</span>
                    <span>{t.vat}: {group.vat} SEK</span>
                    <strong>{t.total}: {group.total} SEK</strong>
                  </div>
                </article>
              ))}
            </div>
          )}

          <form onSubmit={handleCreateExpense} className="form expense-form">
            <label>
              {t.date}
              <input
                type="date"
                value={expenseDate}
                onChange={(event) => setExpenseDate(event.target.value)}
              />
            </label>
            {expenseDateIsLocked && (
              <p className="message warning">{lockedAccountingMessage(expenseDate)}</p>
            )}

            <label>
              {t.description}
              <input
                value={expenseDescription}
                onChange={(event) => setExpenseDescription(event.target.value)}
                placeholder="Software subscription"
              />
            </label>

            <div className="form-row">
              <label>
                {t.net}
                <input
                  type="number"
                  value={expenseNetAmount}
                  onChange={(event) => setExpenseNetAmount(event.target.value)}
                  placeholder="1000"
                />
              </label>

              <label>
                {t.vat}
                <input
                  type="number"
                  value={expenseVatAmount}
                  onChange={(event) => setExpenseVatAmount(event.target.value)}
                  placeholder="250"
                />
              </label>
            </div>

            <label>
              {t.category}
              <select value={expenseCategory} onChange={(event) => setExpenseCategory(event.target.value)}>
                <option value="5420">5420 Programvaror</option>
                <option value="5410">5410 Forbrukningsinventarier</option>
                <option value="5800">5800 Resekostnader</option>
                <option value="6570">6570 Bankkostnader</option>
                <option value="4010">4010 Inkop</option>
              </select>
            </label>

            <label>
              {t.receipt}
              <input
                type="file"
                accept="application/pdf,image/*"
                onChange={(event) => setExpenseReceiptFile(event.target.files?.[0] || null)}
              />
            </label>

            <button type="submit" disabled={!token || expenseDateIsLocked}>{t.saveExpense}</button>
          </form>

          {filteredExpenses.length === 0 ? (
            <p className="empty-state">{t.noExpenses}</p>
          ) : (
            <div className="orders">
              {filteredExpenses.map((expense) => (
                <article className="order-card" key={expense.id}>
                  <div>
                    <h3>{expense.description}</h3>
                    <p>{expense.expenseDate}</p>
                    <p>{t.category}: {expenseCategoryLabel(expense.category)}</p>
                    <span className={`due-status ${expenseHasReceipt(expense) ? "due-status-ok" : "due-status-soon"}`}>
                      {expenseHasReceipt(expense)
                        ? (language === "sv" ? "Kvitto sparat" : "Receipt saved")
                        : (language === "sv" ? "Saknar kvitto" : "Missing receipt")}
                    </span>
                    {expense.receiptFileName && (
                      <p>{language === "sv" ? "Fil" : "File"}: {expense.receiptFileName}</p>
                    )}
                  </div>
                  <div>
                    <span>{t.net}: {expense.netAmount} SEK</span>
                    <span>{t.vat}: {expense.vatAmount} SEK</span>
                    <strong>{t.total}: {expense.totalAmount} SEK</strong>
                    {expenseHasReceipt(expense) && (
                      <button type="button" className="secondary-button" onClick={() => openExpenseReceipt(expense.id)}>
                        {t.openReceipt}
                      </button>
                    )}
                    {!expenseHasReceipt(expense) && (
                      <label className="file-button">
                        {t.attachReceipt}
                        <input
                          type="file"
                          accept="application/pdf,image/*"
                          onChange={(event) => attachReceiptToExpense(expense.id, event.target.files?.[0] || null)}
                        />
                      </label>
                    )}
                    <button type="button" className="secondary-button" onClick={() => copyExpenseInfo(expense)}>
                      {copiedExpenseInfoId === expense.id
                        ? (language === "sv" ? "Kopierad" : "Copied")
                        : (language === "sv" ? "Kopiera info" : "Copy info")}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>}

        {activeView === "uploaded" && <section className="orders-section">
          <div className="section-heading">
            <h2>{t.uploaded}</h2>
            <div className="button-row">
              <button type="button" className="secondary-button" onClick={() => loadExpenses()}>
                {t.refresh}
              </button>
              <button type="button" className="secondary-button" onClick={downloadExpensesCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="bokio-import-panel">
            <div className="bokio-import-intro">
              <div>
                <span>{language === "sv" ? "Flytta fran Bokio" : "Move from Bokio"}</span>
                <h3>{language === "sv" ? "Importera gamla underlag" : "Import old documents"}</h3>
                <p>
                  {language === "sv"
                    ? "Lagg PDF, bilder, CSV, SIE eller ZIP fran Bokio i en granskningsko. Fyll belopp och moms, skapa sedan en riktig kostnad nar raden ar kontrollerad."
                    : "Add PDF, images, CSV, SIE or ZIP files from Bokio to a review queue. Enter amount and VAT, then create a real expense when the row is checked."}
                </p>
              </div>
              <div className="bokio-import-actions">
                <label className="file-button primary-file-button">
                  {language === "sv" ? "Valj filer" : "Choose files"}
                  <input
                    type="file"
                    multiple
                    accept=".pdf,.jpg,.jpeg,.png,.csv,.sie,.se,.zip,application/pdf,image/*,text/csv"
                    onChange={handleBokioImportFiles}
                  />
                </label>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={downloadBokioImportQueueCsv}
                  disabled={bokioImportQueue.length === 0}
                >
                  {language === "sv" ? "Exportera checklista" : "Export checklist"}
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={clearReviewedBokioImports}
                  disabled={bokioImportImportedCount === 0}
                >
                  {language === "sv" ? "Dolj importerade" : "Hide imported"}
                </button>
              </div>
            </div>

            <div className="bokio-import-summary">
              <article>
                <span>{language === "sv" ? "I kon" : "In queue"}</span>
                <strong>{bokioImportQueue.length}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Att granska" : "To review"}</span>
                <strong>{bokioImportReviewCount}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Redo" : "Ready"}</span>
                <strong>{bokioImportReadyCount}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Importerade" : "Imported"}</span>
                <strong>{bokioImportImportedCount}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Analyserade filer" : "Analyzed files"}</span>
                <strong>{bokioImportAnalyzedCount}</strong>
              </article>
            </div>

            {bokioImportMessage && <p className="message success">{bokioImportMessage}</p>}

            {bokioImportQueue.length === 0 ? (
              <p className="empty-state">
                {language === "sv"
                  ? "Ingen Bokio-import startad annu. Exportera eller ladda ner dina underlag fran Bokio och valj filerna har."
                  : "No Bokio import has been started yet. Export or download your documents from Bokio and choose the files here."}
              </p>
            ) : (
              <div className="bokio-import-list">
                {bokioImportQueue.map((row) => (
                  <div className={`bokio-import-row ${row.status}`} key={row.id}>
                    <div className="bokio-import-file-cell">
                      <strong>{row.fileName}</strong>
                      <span>
                        {row.kind} - {bokioImportFileSize(row.size)}
                        {row.lastModified ? ` - ${language === "sv" ? "fildatum" : "file date"} ${row.lastModified}` : ""}
                      </span>
                      <span>
                        {bokioImportFiles[row.id]
                          ? (language === "sv" ? "Filen finns redo for kvitto-uppladdning." : "File is ready for receipt upload.")
                          : (language === "sv" ? "Filen maste valjas igen om den ska laddas upp som kvitto." : "Choose the file again if it should be uploaded as receipt.")}
                      </span>
                      {row.importedExpenseId && (
                        <span>{language === "sv" ? "Kostnad ID" : "Expense ID"}: {row.importedExpenseId}</span>
                      )}
                      {row.analysis?.error && (
                        <span className="bokio-analysis-warning">{row.analysis.error}</span>
                      )}
                      {row.analysis?.kind === "SIE" && !row.analysis.error && (
                        <div className={row.analysis.unbalancedVouchers > 0 ? "bokio-analysis warning" : "bokio-analysis ok"}>
                          <span>
                            SIE: {row.analysis.vouchers} {language === "sv" ? "verifikat" : "vouchers"},
                            {" "}{row.analysis.transactions} {language === "sv" ? "rader" : "rows"},
                            {" "}{row.analysis.accounts} {language === "sv" ? "konton" : "accounts"}
                          </span>
                          <span>
                            {language === "sv" ? "Period" : "Period"}: {row.analysis.firstDate || "-"} - {row.analysis.lastDate || "-"}
                          </span>
                          <span>
                            {row.analysis.unbalancedVouchers === 0
                              ? (language === "sv" ? "Alla verifikat verkar balansera." : "All vouchers appear balanced.")
                              : `${row.analysis.unbalancedVouchers} ${language === "sv" ? "verifikat balanserar inte" : "vouchers are unbalanced"}`}
                          </span>
                          {row.analysis.sampleAccounts && <span>{language === "sv" ? "Konton" : "Accounts"}: {row.analysis.sampleAccounts}</span>}
                          <button type="button" className="secondary-button compact-button" onClick={() => downloadBokioSiePreviewCsv(row)}>
                            {language === "sv" ? "Exportera SIE-forhandsvisning" : "Export SIE preview"}
                          </button>
                          {row.analysis.previewVouchers?.length > 0 && (
                            <div className="sie-preview-list">
                              {row.analysis.previewVouchers.slice(0, 6).map((voucher) => {
                                const importStatus = sieVoucherImportStatus(voucher);

                                return (
                                  <div className="sie-preview-voucher" key={voucher.key}>
                                    <div>
                                      <strong>{voucher.number || voucher.key} - {voucher.date || "-"}</strong>
                                      <span>{voucher.description || "SIE-verifikat"}</span>
                                      <span>
                                        {voucher.transactionsList.map((line) => (
                                          `${line.account} D${line.debit || 0}/K${line.credit || 0}`
                                        )).join(" | ")}
                                      </span>
                                      {!importStatus.ok && <span className="muted-line">{importStatus.reason}</span>}
                                      {voucher.importedVoucherNumber && (
                                        <span className="success-line">
                                          {language === "sv" ? "Importerad som" : "Imported as"} {voucher.importedVoucherNumber}
                                        </span>
                                      )}
                                    </div>
                                    <button
                                      type="button"
                                      className="primary-small-button"
                                      onClick={() => createManualVoucherFromSie(row, voucher)}
                                      disabled={!token || !importStatus.ok || Boolean(voucher.importedVoucherNumber)}
                                    >
                                      {language === "sv" ? "Importera verifikat" : "Import voucher"}
                                    </button>
                                  </div>
                                );
                              })}
                            </div>
                          )}
                        </div>
                      )}
                      {row.analysis?.kind === "CSV" && !row.analysis.error && (
                        <div className="bokio-analysis ok">
                          <span>
                            CSV: {row.analysis.rows} {language === "sv" ? "rader" : "rows"},
                            {" "}{row.analysis.columns} {language === "sv" ? "kolumner" : "columns"}
                          </span>
                          <span>
                            {language === "sv" ? "Separator" : "Separator"}: {row.analysis.separator}
                            {row.analysis.dateColumn ? ` - ${language === "sv" ? "datumkolumn" : "date column"}: ${row.analysis.dateColumn}` : ""}
                          </span>
                          {row.analysis.amountColumn && (
                            <span>{language === "sv" ? "Beloppskolumn" : "Amount column"}: {row.analysis.amountColumn}</span>
                          )}
                        </div>
                      )}
                    </div>
                    <label>
                      {language === "sv" ? "Status" : "Status"}
                      <select
                        value={row.status}
                        onChange={(event) => updateBokioImportRow(row.id, { status: event.target.value })}
                      >
                        <option value="review">{language === "sv" ? "Granska" : "Review"}</option>
                        <option value="ready">{language === "sv" ? "Redo att importera" : "Ready to import"}</option>
                        <option value="imported">{language === "sv" ? "Importerad" : "Imported"}</option>
                      </select>
                    </label>
                    <label>
                      {t.date}
                      <input
                        type="date"
                        value={row.expenseDate || ""}
                        onChange={(event) => updateBokioImportRow(row.id, { expenseDate: event.target.value })}
                      />
                    </label>
                    <label className="wide">
                      {t.description}
                      <input
                        value={row.description || ""}
                        onChange={(event) => updateBokioImportRow(row.id, { description: event.target.value })}
                        placeholder={language === "sv" ? "Beskrivning for kostnaden" : "Expense description"}
                      />
                    </label>
                    <label>
                      {t.net}
                      <input
                        type="number"
                        min="0"
                        value={row.netAmount || ""}
                        onChange={(event) => updateBokioImportRow(row.id, { netAmount: event.target.value })}
                        placeholder="1000"
                      />
                    </label>
                    <label>
                      {t.vat}
                      <input
                        type="number"
                        min="0"
                        value={row.vatAmount || ""}
                        onChange={(event) => updateBokioImportRow(row.id, { vatAmount: event.target.value })}
                        placeholder="250"
                      />
                    </label>
                    <label>
                      {t.category}
                      <select
                        value={row.category || "5420"}
                        onChange={(event) => updateBokioImportRow(row.id, { category: event.target.value })}
                      >
                        <option value="5420">5420 Programvaror</option>
                        <option value="5410">5410 Forbrukningsinventarier</option>
                        <option value="5800">5800 Resekostnader</option>
                        <option value="6570">6570 Bankkostnader</option>
                        <option value="4010">4010 Inkop</option>
                      </select>
                    </label>
                    <label>
                      {language === "sv" ? "Anteckning" : "Note"}
                      <input
                        value={row.note || ""}
                        onChange={(event) => updateBokioImportRow(row.id, { note: event.target.value })}
                        placeholder={language === "sv" ? "Ex: koppla till inkop, faktura eller ar 2025" : "Ex: link to expense, invoice or year 2025"}
                      />
                    </label>
                    <div className="bokio-import-row-actions">
                      <button
                        type="button"
                        className="primary-small-button"
                        onClick={() => createExpenseFromBokioImport(row)}
                        disabled={!token || row.status === "imported"}
                      >
                        {language === "sv" ? "Skapa kostnad" : "Create expense"}
                      </button>
                      <button type="button" className="danger-button soft" onClick={() => removeBokioImportRow(row.id)}>
                        {language === "sv" ? "Ta bort" : "Remove"}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="stats compact-stats">
            <article>
              <span>{t.receiptsSaved}</span>
              <strong>{filteredExpensesWithReceipt.length}</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Sparat belopp" : "Saved amount"}</span>
              <strong>{filteredReceiptTotal} SEK</strong>
            </article>
            <article>
              <span>{t.receiptsMissing}</span>
              <strong>{filteredExpensesMissingReceipt.length}</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Saknar kvitto belopp" : "Missing receipt amount"}</span>
              <strong>{filteredMissingReceiptTotal} SEK</strong>
            </article>
          </div>

          <div className="expense-filter-panel">
            <div className="filter-bar">
              <button
                type="button"
                className={expenseFilter === "all" ? "filter-button active" : "filter-button"}
                onClick={() => setExpenseFilter("all")}
              >
                {t.all}
              </button>
              <button
                type="button"
                className={expenseFilter === "withReceipt" ? "filter-button active" : "filter-button"}
                onClick={() => setExpenseFilter("withReceipt")}
              >
                {language === "sv" ? "Med kvitto" : "With receipt"}
              </button>
              <button
                type="button"
                className={expenseFilter === "missingReceipt" ? "filter-button active" : "filter-button"}
                onClick={() => setExpenseFilter("missingReceipt")}
              >
                {language === "sv" ? "Saknar kvitto" : "Missing receipt"}
              </button>
            </div>

            <label>
              {t.category}
              <select value={expenseCategoryFilter} onChange={(event) => setExpenseCategoryFilter(event.target.value)}>
                <option value="all">{t.all}</option>
                {expenseCategories.map((category) => (
                  <option value={category} key={category}>{expenseCategoryLabel(category)}</option>
                ))}
              </select>
            </label>

            <label>
              {language === "sv" ? "Fran datum" : "From date"}
              <input
                type="date"
                value={expenseDateFrom}
                onChange={(event) => setExpenseDateFrom(event.target.value)}
              />
            </label>

            <label>
              {language === "sv" ? "Till datum" : "To date"}
              <input
                type="date"
                value={expenseDateTo}
                onChange={(event) => setExpenseDateTo(event.target.value)}
              />
            </label>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentMonthRange();
                setExpenseDateFrom(range.from);
                setExpenseDateTo(range.to);
              }}
            >
              {language === "sv" ? "Denna manad" : "This month"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentQuarterRange();
                setExpenseDateFrom(range.from);
                setExpenseDateTo(range.to);
              }}
            >
              {language === "sv" ? "Detta kvartal" : "This quarter"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                const range = currentYearRange();
                setExpenseDateFrom(range.from);
                setExpenseDateTo(range.to);
              }}
            >
              {language === "sv" ? "I ar" : "This year"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                setExpenseFilter("all");
                setExpenseCategoryFilter("all");
                setExpenseDateFrom("");
                setExpenseDateTo("");
                setExpenseSearch("");
              }}
            >
              {language === "sv" ? "Rensa filter" : "Clear filters"}
            </button>
          </div>

          <div className="search-box">
            <label>
              {language === "sv" ? "Sok underlag" : "Search receipts"}
              <div className="search-actions">
                <input
                  value={expenseSearch}
                  onChange={(event) => setExpenseSearch(event.target.value)}
                  placeholder={language === "sv" ? "Beskrivning, kategori, kvitto..." : "Description, category, receipt..."}
                />
                {expenseSearch && (
                  <button type="button" className="secondary-button" onClick={() => setExpenseSearch("")}>
                    {language === "sv" ? "Rensa" : "Clear"}
                  </button>
                )}
              </div>
            </label>
          </div>

          <div className="receipt-sections">
            <article className="receipt-panel">
              <h3>{t.receiptsSaved}</h3>
              {filteredExpensesWithReceipt.length === 0 ? (
                <p className="empty-state">{t.noReceipts}</p>
              ) : (
                <div className="receipt-list">
                  {filteredExpensesWithReceipt.map((expense) => (
                    <div className="receipt-row" key={expense.id}>
                      <div>
                        <strong>{expense.description}</strong>
                        <span>{expense.expenseDate} - {expense.totalAmount} SEK</span>
                        <span>{t.net}: {expense.netAmount || 0} SEK - {t.vat}: {expense.vatAmount || 0} SEK</span>
                        <span>{t.category}: {expenseCategoryLabel(expense.category)}</span>
                        <span>{expense.receiptFileName || t.receipt}</span>
                      </div>
                      <div className="receipt-row-actions">
                        <button type="button" className="secondary-button" onClick={() => openExpenseReceipt(expense.id)}>
                          {t.openReceipt}
                        </button>
                        <button type="button" className="secondary-button" onClick={() => copyExpenseInfo(expense)}>
                          {copiedExpenseInfoId === expense.id
                            ? (language === "sv" ? "Kopierad" : "Copied")
                            : (language === "sv" ? "Kopiera info" : "Copy info")}
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </article>

            <article className="receipt-panel">
              <h3>{t.receiptsMissing}</h3>
              {filteredExpensesMissingReceipt.length === 0 ? (
                <p className="empty-state">{t.noMissingReceipts}</p>
              ) : (
                <div className="receipt-list">
                  {filteredExpensesMissingReceipt.map((expense) => (
                    <div className="receipt-row missing" key={expense.id}>
                      <div>
                        <strong>{expense.description}</strong>
                        <span>{expense.expenseDate} - {expense.totalAmount} SEK</span>
                        <span>{t.net}: {expense.netAmount || 0} SEK - {t.vat}: {expense.vatAmount || 0} SEK</span>
                        <span>{t.category}: {expenseCategoryLabel(expense.category)}</span>
                      </div>
                      <div className="receipt-row-actions">
                        <label className="file-button">
                          {t.attachReceipt}
                          <input
                            type="file"
                            accept="application/pdf,image/*"
                            onChange={(event) => attachReceiptToExpense(expense.id, event.target.files?.[0] || null)}
                          />
                        </label>
                        <button type="button" className="secondary-button" onClick={() => copyExpenseInfo(expense)}>
                          {copiedExpenseInfoId === expense.id
                            ? (language === "sv" ? "Kopierad" : "Copied")
                            : (language === "sv" ? "Kopiera info" : "Copy info")}
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </article>
          </div>
        </section>}

        {activeView === "vat" && <section className="orders-section">
          <div className="section-heading">
            <h2>{t.vatReport}</h2>
            <div className="button-row">
              <button type="button" className="secondary-button" onClick={loadVatReport}>
                {t.refresh}
              </button>
              <button type="button" className="secondary-button" onClick={downloadVatReportCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="vat-report">
            <article>
              <span>{t.outputVat} 2611</span>
              <strong>{vatReport?.outputVat || 0} SEK</strong>
            </article>
            <article>
              <span>{t.inputVat} 2641</span>
              <strong>{vatReport?.inputVat || 0} SEK</strong>
            </article>
            <article>
              <span>{t.vatToPay}</span>
              <strong>{vatReport?.vatToPay || 0} SEK</strong>
            </article>
          </div>

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Momsavstamning" : "VAT reconciliation"}</h2>
            <div className="button-row">
              <span className="status">{vatPeriodVoucherNumbers.size} {language === "sv" ? "verifikat i perioden" : "vouchers in period"}</span>
              <button type="button" className="secondary-button" onClick={downloadVatReconciliationCsv} disabled={vatPeriodEntries.length === 0}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="invoice-filter-panel vat-period-panel">
            <label>
              {language === "sv" ? "Fran datum" : "From date"}
              <input type="date" value={vatPeriodFrom} onChange={(event) => setVatPeriodFrom(event.target.value)} />
            </label>
            <label>
              {language === "sv" ? "Till datum" : "To date"}
              <input type="date" value={vatPeriodTo} onChange={(event) => setVatPeriodTo(event.target.value)} />
            </label>
            <button
              type="button"
              onClick={() => {
                const range = currentMonthRange();
                setVatPeriodFrom(range.from);
                setVatPeriodTo(range.to);
              }}
            >
              {language === "sv" ? "Denna manad" : "This month"}
            </button>
            <button
              type="button"
              onClick={() => {
                const range = currentQuarterRange();
                setVatPeriodFrom(range.from);
                setVatPeriodTo(range.to);
              }}
            >
              {language === "sv" ? "Detta kvartal" : "This quarter"}
            </button>
            <button
              type="button"
              onClick={() => {
                const range = currentYearRange();
                setVatPeriodFrom(range.from);
                setVatPeriodTo(range.to);
              }}
            >
              {language === "sv" ? "I ar" : "This year"}
            </button>
          </div>

          <div className="expense-summary-grid vat-reconciliation-summary-grid">
            <article>
              <span>{language === "sv" ? "Intakter exkl. moms" : "Revenue excl. VAT"}</span>
              <strong>{vatPeriodRevenue} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Kostnader exkl. moms" : "Expenses excl. VAT"}</span>
              <strong>{vatPeriodExpenses} SEK</strong>
            </article>
            <article>
              <span>{t.outputVat} 2611</span>
              <strong>{vatPeriodOutputVat} SEK</strong>
            </article>
            <article>
              <span>{t.inputVat} 2641</span>
              <strong>{vatPeriodInputVat} SEK</strong>
            </article>
            <article className={vatPeriodToPay >= 0 ? "balanced-summary" : "unbalanced-summary"}>
              <span>{language === "sv" ? "Periodens moms" : "Period VAT"}</span>
              <strong>{vatPeriodToPay} SEK</strong>
            </article>
          </div>

          <div className="vat-reconciliation-panel">
            <article className={vatPeriodToPay > 0 ? "vat-status-card pay" : vatPeriodToPay < 0 ? "vat-status-card reclaim" : "vat-status-card neutral"}>
              <span>{language === "sv" ? "Status" : "Status"}</span>
              <strong>{vatPeriodStatusText}</strong>
              <p>
                {vatPeriodToPay > 0
                  ? (language === "sv"
                    ? "Beloppet ar preliminart moms att betala for vald period. Kontrollera alltid perioden mot Skatteverket innan deklaration."
                    : "This is the preliminary VAT to pay for the selected period. Always verify the period with the tax authority before filing.")
                  : vatPeriodToPay < 0
                    ? (language === "sv"
                      ? "Negativt belopp betyder preliminar moms att fa tillbaka. Kontrollera underlag och period innan deklaration."
                      : "A negative amount means preliminary VAT to reclaim. Check receipts and period before filing.")
                    : (language === "sv"
                      ? "Ingen moms att betala eller fa tillbaka enligt valda bokforingsrader."
                      : "No VAT to pay or reclaim according to the selected bookkeeping rows.")}
              </p>
            </article>

            <article className="vat-checklist-card">
              <strong>{language === "sv" ? "Kontroll innan momsdeklaration" : "Checks before VAT filing"}</strong>
              <ul>
                <li>{language === "sv" ? "Alla fakturor i perioden ar skapade och bokforda." : "All invoices in the period are created and booked."}</li>
                <li>{language === "sv" ? "Alla kostnader har ratt datum, moms och underlag." : "All expenses have correct date, VAT and receipt."}</li>
                <li>{language === "sv" ? "Bank/Stripe ar avstamt mot betalningar." : "Bank/Stripe is reconciled against payments."}</li>
                <li>{language === "sv" ? "Kontrollera exakta deklarationsdatum hos Skatteverket." : "Verify exact filing dates with the tax authority."}</li>
              </ul>
            </article>
          </div>

          <div className="button-row vat-drilldown-actions">
            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                setActiveView("bookkeeping");
                setBookkeepingSearch("2611");
                setBookkeepingDateFrom(vatPeriodFrom);
                setBookkeepingDateTo(vatPeriodTo);
              }}
            >
              {language === "sv" ? "Visa 2611 i bokforing" : "Show 2611 in bookkeeping"}
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                setActiveView("bookkeeping");
                setBookkeepingSearch("2641");
                setBookkeepingDateFrom(vatPeriodFrom);
                setBookkeepingDateTo(vatPeriodTo);
              }}
            >
              {language === "sv" ? "Visa 2641 i bokforing" : "Show 2641 in bookkeeping"}
            </button>
          </div>
        </section>}

        {activeView === "bookkeeping" && <section className="orders-section">
          <div className="section-heading">
            <h2>{t.bookkeeping}</h2>
            <div className="button-row">
              <button type="button" className="secondary-button" onClick={loadJournalEntries}>
                {t.refresh}
              </button>
              <button type="button" className="secondary-button" onClick={downloadJournalCsv}>
                {t.exportCsv}
              </button>
              <button type="button" className="secondary-button" onClick={downloadSieExport} disabled={!sieExportCanDownload}>
                {language === "sv" ? "Exportera SIE" : "Export SIE"}
              </button>
            </div>
          </div>

          <form onSubmit={handleCreateManualJournalEntry} className="form manual-voucher-form">
            <h3>{t.manualVoucher}</h3>
            <p className="settings-hint">
              {language === "sv"
                ? "Bokforda verifikationer ska bevaras som historik. Om nagot blir fel, skapa en ny korrigeringsverifikation i stallet for att andra eller radera gamla rader."
                : "Posted journal entries should be preserved as history. If something is wrong, create a new correction voucher instead of changing or deleting old rows."}
            </p>
            <label>
              {t.date}
              <input
                type="date"
                value={manualVoucherDate}
                onChange={(event) => setManualVoucherDate(event.target.value)}
              />
            </label>
            {manualVoucherDateIsLocked && (
              <p className="message warning">
                {language === "sv"
                  ? `Denna period ar last till och med ${formatDateOnly(accountingLockedThroughDate)}. Valj ett senare datum eller skapa en korrigering i en olast period.`
                  : `This period is locked through ${formatDateOnly(accountingLockedThroughDate)}. Choose a later date or create a correction in an unlocked period.`}
              </p>
            )}

            <label>
              {t.description}
              <input
                value={manualDescription}
                onChange={(event) => setManualDescription(event.target.value)}
                placeholder={language === "sv" ? "Exempel: korrigering" : "Example: adjustment"}
              />
            </label>

            <div className="form-row">
              <label>
                {t.debitAccount}
                <select value={manualDebitAccount} onChange={(event) => setManualDebitAccount(event.target.value)}>
                  <option value="">{language === "sv" ? "Valj konto" : "Choose account"}</option>
                  {accounts.map((account) => (
                    <option key={account.number} value={account.number}>
                      {account.number} {account.name}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                {t.creditAccount}
                <select value={manualCreditAccount} onChange={(event) => setManualCreditAccount(event.target.value)}>
                  <option value="">{language === "sv" ? "Valj konto" : "Choose account"}</option>
                  {accounts.map((account) => (
                    <option key={account.number} value={account.number}>
                      {account.number} {account.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <label>
              {t.amount}
              <input
                type="number"
                value={manualAmount}
                onChange={(event) => setManualAmount(event.target.value)}
                placeholder="1000"
              />
            </label>

            <button type="submit" disabled={!token || manualVoucherDateIsLocked || !manualDescription || !manualDebitAccount || !manualCreditAccount || !manualAmount}>
              {t.saveVoucher}
            </button>
          </form>

          {journalEntries.length === 0 ? (
            <p className="empty-state">{t.noJournalEntries}</p>
          ) : (
            <>
            <div className="filter-bar bookkeeping-filter-bar">
              <button
                type="button"
                className={bookkeepingFilter === "all" ? "filter-button active" : "filter-button"}
                onClick={() => setBookkeepingFilter("all")}
              >
                {t.all}
              </button>
              <button
                type="button"
                className={bookkeepingFilter === "original" ? "filter-button active" : "filter-button"}
                onClick={() => setBookkeepingFilter("original")}
              >
                {language === "sv" ? "Original" : "Original"}
              </button>
              <button
                type="button"
                className={bookkeepingFilter === "corrections" ? "filter-button active" : "filter-button"}
                onClick={() => setBookkeepingFilter("corrections")}
              >
                {language === "sv" ? "Rattelser" : "Corrections"}
              </button>
              <button
                type="button"
                className={bookkeepingFilter === "corrected" ? "filter-button active" : "filter-button"}
                onClick={() => setBookkeepingFilter("corrected")}
              >
                {language === "sv" ? "Har rattelse" : "Has correction"}
              </button>
            </div>

            <div className="search-box bookkeeping-search-box">
              <label>
                {language === "sv" ? "Sok i bokforing" : "Search bookkeeping"}
                <div className="search-actions">
                  <input
                    value={bookkeepingSearch}
                    onChange={(event) => setBookkeepingSearch(event.target.value)}
                    placeholder={language === "sv" ? "Verifikat, konto, beskrivning, belopp..." : "Voucher, account, description, amount..."}
                  />
                  {bookkeepingSearch && (
                    <button type="button" className="secondary-button" onClick={() => setBookkeepingSearch("")}>
                      {language === "sv" ? "Rensa" : "Clear"}
                    </button>
                  )}
                </div>
              </label>
            </div>
            {bookkeepingAccountSearch && (
              <div className="account-drilldown-banner">
                <span>
                  {language === "sv" ? "Visar huvudbok for konto" : "Showing ledger for account"}{" "}
                  <strong>{bookkeepingAccountSearch}</strong>
                  {activeBookkeepingAccount?.accountName || activeBookkeepingAccount?.name
                    ? ` - ${activeBookkeepingAccount.accountName || activeBookkeepingAccount.name}`
                    : ""}
                </span>
                <div className="account-drilldown-actions">
                  <button type="button" className="secondary-button" onClick={downloadActiveAccountLedgerCsv}>
                    {language === "sv" ? "Exportera huvudbok" : "Export ledger"}
                  </button>
                  <button type="button" className="secondary-button" onClick={() => setBookkeepingSearch("")}>
                    {language === "sv" ? "Visa alla konton" : "Show all accounts"}
                  </button>
                </div>
              </div>
            )}
            {bookkeepingVoucherSearch && (
              <div className="account-drilldown-banner voucher-drilldown-banner">
                <span>
                  {language === "sv" ? "Visar verifikat" : "Showing voucher"}{" "}
                  <strong>{bookkeepingVoucherSearch}</strong>
                  {activeBookkeepingVoucher?.description ? ` - ${activeBookkeepingVoucher.description}` : ""}
                </span>
                <div className="account-drilldown-actions">
                  <button type="button" className="secondary-button" onClick={() => setBookkeepingSearch("")}>
                    {language === "sv" ? "Visa alla verifikat" : "Show all vouchers"}
                  </button>
                </div>
              </div>
            )}
            {activeBookkeepingVoucher && (
              <div className="voucher-detail-panel">
                <div className="expense-summary-grid voucher-detail-summary-grid">
                  <article>
                    <span>{language === "sv" ? "Verifikationsdatum" : "Voucher date"}</span>
                    <strong>{activeBookkeepingVoucher.voucherDate || formatDateTime(activeBookkeepingVoucher.createdAt)}</strong>
                  </article>
                  <article>
                    <span>{t.debit}</span>
                    <strong>{activeBookkeepingVoucher.debit} SEK</strong>
                  </article>
                  <article>
                    <span>{t.credit}</span>
                    <strong>{activeBookkeepingVoucher.credit} SEK</strong>
                  </article>
                  <article className={activeBookkeepingVoucherDifference === 0 ? "balanced-summary" : "unbalanced-summary"}>
                    <span>{language === "sv" ? "Status" : "Status"}</span>
                    <strong>
                      {activeBookkeepingVoucherDifference === 0
                        ? (language === "sv" ? "Balanserar" : "Balanced")
                        : `${language === "sv" ? "Differens" : "Difference"} ${activeBookkeepingVoucherDifference} SEK`}
                    </strong>
                  </article>
                </div>
                <div className="journal-table voucher-detail-table">
                  <div className="journal-header">
                    <span>{t.account}</span>
                    <span>{t.description}</span>
                    <span>{t.debit}</span>
                    <span>{t.credit}</span>
                  </div>
                  {activeBookkeepingVoucher.rows.map((entry) => (
                    <div className="journal-row" key={`voucher-detail-${entry.id}`}>
                      <span>{entry.accountNumber} {entry.accountName}</span>
                      <span>{entry.description}</span>
                      <span>{entry.debit} SEK</span>
                      <span>{entry.credit} SEK</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
            {bookkeepingAccountSearch && activeAccountLedgerRows.length > 0 && (
              <>
                <div className="expense-summary-grid active-account-summary-grid">
                  <article>
                    <span>{language === "sv" ? "Ingaende saldo" : "Opening balance"}</span>
                    <strong>{activeAccountOpeningBalance} SEK</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Period debet" : "Period debit"}</span>
                    <strong>{activeAccountPeriodDebit} SEK</strong>
                  </article>
                  <article>
                    <span>{language === "sv" ? "Period kredit" : "Period credit"}</span>
                    <strong>{activeAccountPeriodCredit} SEK</strong>
                  </article>
                  <article className={activeAccountPeriodChange === 0 ? "balanced-summary" : ""}>
                    <span>{language === "sv" ? "Utgaende saldo" : "Closing balance"}</span>
                    <strong>{activeAccountClosingBalance} SEK</strong>
                  </article>
                </div>
                <div className="account-table active-account-ledger">
                  <div className="account-row active-account-ledger-header">
                    <span>{t.date}</span>
                    <span>{language === "sv" ? "Verifikat" : "Voucher"}</span>
                    <span>{t.description}</span>
                    <span>{t.debit}</span>
                    <span>{t.credit}</span>
                    <span>{language === "sv" ? "Lopande saldo" : "Running balance"}</span>
                  </div>
                  {bookkeepingDateFrom && (
                    <div className="account-row active-account-ledger-row opening-balance-row">
                      <span>{bookkeepingDateFrom}</span>
                      <strong>{language === "sv" ? "Ingaende" : "Opening"}</strong>
                      <span>{language === "sv" ? "Ingaende saldo fore perioden" : "Opening balance before period"}</span>
                      <span>0 SEK</span>
                      <span>0 SEK</span>
                      <strong>{activeAccountOpeningBalance} SEK</strong>
                    </div>
                  )}
                  {activeAccountLedgerRows.map((row, index) => (
                    <div className="account-row active-account-ledger-row" key={`${row.voucherNumber}-${index}`}>
                      <span>{row.voucherDate || "-"}</span>
                      <button
                        type="button"
                        className="account-drilldown-button"
                        onClick={() => setBookkeepingSearch(row.voucherNumber)}
                        title={language === "sv" ? "Visa hela verifikatet" : "Show full voucher"}
                      >
                        {row.voucherNumber}
                      </button>
                      <span>{row.description}</span>
                      <span>{row.debit} SEK</span>
                      <span>{row.credit} SEK</span>
                      <strong>{row.balance} SEK</strong>
                    </div>
                  ))}
                </div>
              </>
            )}

            <div className="invoice-filter-panel bookkeeping-date-panel">
              <label>
                {language === "sv" ? "Verifikationsdatum fran" : "Voucher date from"}
                <input
                  type="date"
                  value={bookkeepingDateFrom}
                  onChange={(event) => setBookkeepingDateFrom(event.target.value)}
                />
              </label>

              <label>
                {language === "sv" ? "Verifikationsdatum till" : "Voucher date to"}
                <input
                  type="date"
                  value={bookkeepingDateTo}
                  onChange={(event) => setBookkeepingDateTo(event.target.value)}
                />
              </label>

              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  const range = currentMonthRange();
                  setBookkeepingDateFrom(range.from);
                  setBookkeepingDateTo(range.to);
                }}
              >
                {language === "sv" ? "Denna manad" : "This month"}
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  const range = currentQuarterRange();
                  setBookkeepingDateFrom(range.from);
                  setBookkeepingDateTo(range.to);
                }}
              >
                {language === "sv" ? "Detta kvartal" : "This quarter"}
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  const range = currentYearRange();
                  setBookkeepingDateFrom(range.from);
                  setBookkeepingDateTo(range.to);
                }}
              >
                {language === "sv" ? "I ar" : "This year"}
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  setBookkeepingFilter("all");
                  setBookkeepingDateFrom("");
                  setBookkeepingDateTo("");
                  setBookkeepingSearch("");
                }}
              >
                {language === "sv" ? "Rensa filter" : "Clear filters"}
              </button>
            </div>

            <div className="expense-summary-grid bookkeeping-summary-grid">
              <article>
                <span>{language === "sv" ? "Verifikationer i urval" : "Vouchers in selection"}</span>
                <strong>{filteredJournalGroups.length}</strong>
              </article>
              <article>
                <span>{t.debit}</span>
                <strong>{filteredJournalDebit} SEK</strong>
              </article>
              <article>
                <span>{t.credit}</span>
                <strong>{filteredJournalCredit} SEK</strong>
              </article>
              <article className={filteredJournalDifference === 0 ? "balanced-summary" : "unbalanced-summary"}>
                <span>{language === "sv" ? "Differens" : "Difference"}</span>
                <strong>{filteredJournalDifference} SEK</strong>
              </article>
            </div>

            {filteredJournalMonthlySummary.length > 0 && (
              <div className="account-table bookkeeping-monthly-summary">
                <div className="account-row bookkeeping-monthly-header">
                  <span>{language === "sv" ? "Manad" : "Month"}</span>
                  <span>{language === "sv" ? "Verifikationer" : "Vouchers"}</span>
                  <span>{t.debit}</span>
                  <span>{t.credit}</span>
                  <span>{language === "sv" ? "Differens" : "Difference"}</span>
                </div>
                {filteredJournalMonthlySummary.map((month) => {
                  const difference = month.debit - month.credit;

                  return (
                    <div className="account-row bookkeeping-monthly-row" key={month.monthKey}>
                      <button
                        type="button"
                        className="account-drilldown-button"
                        onClick={() => {
                          const range = monthRangeFromKey(month.monthKey);
                          setBookkeepingDateFrom(range.from);
                          setBookkeepingDateTo(range.to);
                          setBookkeepingSearch("");
                        }}
                        title={language === "sv" ? "Filtrera bokforing till denna manad" : "Filter bookkeeping to this month"}
                      >
                        {formatMonthLabel(month.monthKey, language)}
                      </button>
                      <span>{month.voucherCount}</span>
                      <span>{month.debit} SEK</span>
                      <span>{month.credit} SEK</span>
                      <strong className={difference === 0 ? "balanced-text" : "unbalanced-text"}>{difference} SEK</strong>
                    </div>
                  );
                })}
              </div>
            )}

            {filteredJournalAccountSummary.length > 0 && (
              <div className="account-table bookkeeping-account-summary">
                <div className="account-row bookkeeping-account-header">
                  <span>{t.account}</span>
                  <span>{t.accountName}</span>
                  <span>{t.debit}</span>
                  <span>{t.credit}</span>
                  <span>{language === "sv" ? "Saldo" : "Balance"}</span>
                </div>
                {filteredJournalAccountSummary.map((account) => (
                  <div className="account-row bookkeeping-account-row" key={account.accountNumber}>
                    <button
                      type="button"
                      className="account-drilldown-button"
                      onClick={() => setBookkeepingSearch(account.accountNumber)}
                      title={language === "sv" ? "Visa verifikationer for kontot" : "Show vouchers for this account"}
                    >
                      {account.accountNumber}
                    </button>
                    <span>{account.accountName}</span>
                    <span>{account.debit} SEK</span>
                    <span>{account.credit} SEK</span>
                    <strong>{account.debit - account.credit} SEK</strong>
                  </div>
                ))}
              </div>
            )}

            {filteredJournalGroups.length === 0 ? (
              <p className="empty-state">
                {language === "sv" ? "Inga verifikationer matchar filtret." : "No vouchers match the filter."}
              </p>
            ) : (
            <div className="journal-groups">
              {filteredJournalGroups.map((group) => (
                <article className="journal-group" key={group.voucherNumber}>
                  <header className="journal-group-header">
                    <div>
                      <div className="journal-title-row">
                        <h3>{group.voucherNumber}</h3>
                        <span className="locked-badge">{language === "sv" ? "Last historik" : "Locked history"}</span>
                        {group.correctionOfVoucherNumber ? (
                          <span className="correction-badge">
                            {language === "sv"
                              ? `Rattelse av ${group.correctionOfVoucherNumber}`
                              : `Correction of ${group.correctionOfVoucherNumber}`}
                          </span>
                        ) : group.hasCorrection ? (
                          <span className="corrected-badge">
                            {language === "sv" ? "Har rattelse" : "Has correction"}
                          </span>
                        ) : (
                          <span className="original-badge">
                            {language === "sv" ? "Original" : "Original"}
                          </span>
                        )}
                      </div>
                      <p>{group.description}</p>
                      <span>{group.voucherDate || formatDateTime(group.createdAt)}</span>
                    </div>
                    <div className="journal-header-actions">
                      <div className="journal-totals">
                        <span>{t.debit}: {group.debit} SEK</span>
                        <span>{t.credit}: {group.credit} SEK</span>
                      </div>
                      <button
                        type="button"
                        className="secondary-button"
                        disabled={!token || group.voucherNumber === "Utan verifikat" || isAccountingDateLocked(new Date().toISOString().slice(0, 10))}
                        onClick={() => createCorrectionVoucher(group.voucherNumber)}
                      >
                        {language === "sv" ? "Skapa rattelse" : "Create correction"}
                      </button>
                    </div>
                  </header>

                  <div className="journal-table">
                    <div className="journal-header">
                      <span>{t.account}</span>
                      <span>{t.description}</span>
                      <span>{t.debit}</span>
                      <span>{t.credit}</span>
                    </div>
                    {group.rows.map((entry) => (
                      <div className="journal-row" key={entry.id}>
                        <span>{entry.accountNumber} {entry.accountName}</span>
                        <span>{entry.description}</span>
                        <span>{entry.debit} SEK</span>
                        <span>{entry.credit} SEK</span>
                      </div>
                    ))}
                  </div>
                </article>
              ))}
            </div>
            )}
            </>
          )}
        </section>}

        {activeView === "accounts" && <section className="orders-section">
          <div className="section-heading">
            <h2>{t.chartOfAccounts}</h2>
            <button type="button" className="secondary-button" onClick={() => loadAccounts()}>
              {t.refresh}
            </button>
          </div>

          <p className="settings-hint">
            {settings?.companyType === "LIMITED_COMPANY"
              ? (language === "sv"
                ? "Visar gemensamma konton och konton som passar aktiebolag."
                : "Showing shared accounts and accounts suitable for limited companies.")
              : (language === "sv"
                ? "Visar gemensamma konton och konton som passar enskild firma."
                : "Showing shared accounts and accounts suitable for sole traders.")}
          </p>

          {accounts.length === 0 ? (
            <p className="empty-state">{language === "sv" ? "Inga konton laddade." : "No accounts loaded."}</p>
          ) : (
            <div className="account-table">
              <div className="account-row account-header">
                <span>{t.accountNumber}</span>
                <span>{t.accountName}</span>
                <span>{t.appliesTo}</span>
              </div>
              {accounts.map((account) => (
                <div className="account-row" key={account.number}>
                  <strong>{account.number}</strong>
                  <span>{account.name}</span>
                  <span>{accountCompanyTypeLabel(account.companyType, language)}</span>
                </div>
              ))}
            </div>
          )}
        </section>}

        {activeView === "reports" && <section className="orders-section">
          <div className="section-heading">
            <h2>{language === "sv" ? "Boksluts- och kontrollcenter" : "Closing and control center"}</h2>
            <div className="button-row">
              <span className={closeChecklistWarnings === 0 ? "status success-status" : "status warning-status"}>
                {closeReadinessScore}% {language === "sv" ? "klart" : "ready"}
              </span>
              <button type="button" className="secondary-button" onClick={downloadCloseChecklistCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="close-center-hero">
            <article>
              <span>{language === "sv" ? "Status" : "Status"}</span>
              <strong>
                {closeChecklistWarnings === 0
                  ? (language === "sv" ? "Ser redo ut" : "Looks ready")
                  : `${closeChecklistWarnings} ${language === "sv" ? "saker att kontrollera" : "items to check"}`}
              </strong>
              <p>
                {language === "sv"
                  ? "Kontrollcentret samlar bokforing, underlag, kundfordringar och moms pa ett stalle innan du exporterar eller later perioden vila."
                  : "The control center gathers bookkeeping, receipts, receivables and VAT in one place before you export or close a period."}
              </p>
            </article>
            <article>
              <span>{language === "sv" ? "Period" : "Period"}</span>
              <strong>{vatPeriodFrom || "-"} - {vatPeriodTo || "-"}</strong>
              <p>
                {accountingLockedThroughDate
                  ? (language === "sv"
                    ? `Bokforingen ar last till ${formatDateOnly(accountingLockedThroughDate)}.`
                    : `Bookkeeping is locked through ${formatDateOnly(accountingLockedThroughDate)}.`)
                  : (language === "sv"
                    ? "Ingen bokforingsperiod ar last annu."
                    : "No bookkeeping period is locked yet.")}
              </p>
            </article>
          </div>

          <div className="expense-summary-grid close-center-summary-grid">
            <article>
              <span>{language === "sv" ? "Resultat" : "Result"}</span>
              <strong>{profitNet} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Moms vald period" : "VAT selected period"}</span>
              <strong>{vatPeriodToPay} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Kundfordringar" : "Receivables"}</span>
              <strong>{totalOutstanding} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Saknade underlag" : "Missing receipts"}</span>
              <strong>{expensesMissingReceipt.length}</strong>
            </article>
          </div>

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Skatteplan" : "Tax plan"}</h2>
            <div className="button-row">
              <span className={taxPlanWarnings === 0 ? "status success-status" : "status warning-status"}>
                {taxPlanWarnings === 0
                  ? (language === "sv" ? "Inga varningar" : "No warnings")
                  : `${taxPlanWarnings} ${language === "sv" ? "varningar" : "warnings"}`}
              </span>
              <span className={taxPlanOverdueCount > 0 ? "status warning-status" : "status"}>
                {taxPlanOverdueCount} {language === "sv" ? "passerade" : "overdue"}
              </span>
              <button type="button" className="secondary-button" onClick={downloadTaxPlanCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="tax-plan-panel">
            <div className="tax-plan-intro">
              <strong>{language === "sv" ? "Planera moms, deklaration och arkivering" : "Plan VAT, declaration and archiving"}</strong>
              <p>
                {language === "sv"
                  ? "Detta ar en arbetsplan baserad pa vald period och din data i AliBooks. Datum ar preliminara och ska alltid kontrolleras hos Skatteverket/Mina sidor innan du skickar in eller betalar."
                  : "This is a working plan based on the selected period and your AliBooks data. Dates are preliminary and must always be verified with the tax authority before filing or paying."}
              </p>
              <small>
                {language === "sv"
                  ? `Vald period: ${archivePeriodLabel}. Kommande inom 30 dagar: ${taxPlanDueSoonCount}.`
                  : `Selected period: ${archivePeriodLabel}. Upcoming within 30 days: ${taxPlanDueSoonCount}.`}
              </small>
            </div>
            <div className="tax-plan-list">
              {taxPlanItems.map((item) => (
                <article className={`tax-plan-card ${item.displayStatus}`} key={item.key}>
                  <div>
                    <span>{item.displayStatus === "ok"
                      ? "OK"
                      : item.displayStatus === "critical"
                        ? (language === "sv" ? "Sen" : "Overdue")
                        : item.displayStatus === "warning"
                          ? (language === "sv" ? "Kontrollera" : "Check")
                          : "Info"}
                    </span>
                    <strong>{item.title}</strong>
                    <small>{item.detail}</small>
                  </div>
                  <div>
                    <strong>{item.deadline ? formatDateOnly(item.deadline) : "-"}</strong>
                    <small>
                      {item.daysUntil === null
                        ? "-"
                        : item.daysUntil < 0
                          ? `${Math.abs(item.daysUntil)} ${language === "sv" ? "dagar sedan" : "days ago"}`
                          : `${item.daysUntil} ${language === "sv" ? "dagar kvar" : "days left"}`}
                    </small>
                  </div>
                  <button type="button" className="secondary-button" onClick={item.action}>
                    {item.actionLabel}
                  </button>
                </article>
              ))}
            </div>
          </div>

          <div className={sieExportCanDownload ? "sie-export-panel ready" : "sie-export-panel warning"}>
            <div>
              <span>{language === "sv" ? "SIE-export" : "SIE export"}</span>
              <strong>{sieExportStatusText}</strong>
              <p>
                {language === "sv"
                  ? "Exportera filtrerade verifikationer och kontoplan som en preliminar SIE4-fil for test, import eller vidare kontroll hos redovisningskonsult."
                  : "Export filtered vouchers and chart of accounts as a preliminary SIE4 file for testing, import or further review with an accountant."}
              </p>
              <small>
                {language === "sv"
                  ? "Tips: valj period i Bokforing eller Momsrapport innan export om du bara vill exportera en viss period."
                  : "Tip: choose a period in Bookkeeping or VAT report before exporting if you only want a selected period."}
              </small>
            </div>
            <div className="sie-export-stats">
              <article>
                <span>{language === "sv" ? "Period" : "Period"}</span>
                <strong>{sieExportPeriodLabel}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Verifikationer" : "Vouchers"}</span>
                <strong>{filteredJournalGroups.length}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Konton" : "Accounts"}</span>
                <strong>{sieExportAccountCount}</strong>
              </article>
              <article className={filteredJournalDifference === 0 ? "ok" : "warning"}>
                <span>{language === "sv" ? "Differens" : "Difference"}</span>
                <strong>{filteredJournalDifference} SEK</strong>
              </article>
            </div>
            <div className="button-row">
              <button type="button" className="secondary-button" onClick={() => setActiveView("bookkeeping")}>
                {language === "sv" ? "Justera urval" : "Adjust selection"}
              </button>
              <button type="button" className="primary-small-button" onClick={downloadSieExport} disabled={!sieExportCanDownload}>
                {language === "sv" ? "Ladda ner SIE" : "Download SIE"}
              </button>
            </div>
          </div>

          <div className="close-checklist-grid">
            {closeChecklistItems.map((item) => (
              <button
                type="button"
                className={`close-check-card ${item.status}`}
                key={item.key}
                onClick={item.action}
              >
                <span>{item.status === "ok" ? "OK" : item.status === "warning" ? (language === "sv" ? "Kontrollera" : "Check") : "Info"}</span>
                <strong>{item.title}</strong>
                <small>{item.value}</small>
              </button>
            ))}
          </div>

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Datahalsa" : "Data quality"}</h2>
            <div className="button-row">
              <span className={dataQualityCriticalCount === 0 && dataQualityWarningCount === 0 ? "status success-status" : "status warning-status"}>
                {dataQualityScore}% {language === "sv" ? "halsa" : "health"}
              </span>
              <button type="button" className="secondary-button" onClick={downloadDataQualityCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="data-quality-panel">
            <div className="data-quality-summary">
              <article className={dataQualityCriticalCount > 0 ? "critical" : ""}>
                <span>{language === "sv" ? "Kritiska" : "Critical"}</span>
                <strong>{dataQualityCriticalCount}</strong>
              </article>
              <article className={dataQualityWarningCount > 0 ? "warning" : ""}>
                <span>{language === "sv" ? "Varningar" : "Warnings"}</span>
                <strong>{dataQualityWarningCount}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Info" : "Info"}</span>
                <strong>{dataQualityInfoCount}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Totalt" : "Total"}</span>
                <strong>{dataQualityIssues.length}</strong>
              </article>
            </div>

            <div className="invoice-filter-panel data-quality-filter-panel">
              <label>
                {language === "sv" ? "Sok i datahalsa" : "Search data quality"}
                <input
                  value={dataQualitySearch}
                  onChange={(event) => setDataQualitySearch(event.target.value)}
                  placeholder={language === "sv" ? "Kund, faktura, personnummer, verifikat..." : "Customer, invoice, personal number, voucher..."}
                />
              </label>
              <div className="payment-filter-row">
                {[
                  ["all", language === "sv" ? "Alla" : "All"],
                  ["critical", language === "sv" ? "Kritiska" : "Critical"],
                  ["warning", language === "sv" ? "Varningar" : "Warnings"],
                  ["info", "Info"],
                  ["customers", language === "sv" ? "Kunder" : "Customers"],
                  ["invoices", language === "sv" ? "Fakturor" : "Invoices"],
                  ["bookkeeping", language === "sv" ? "Bokforing" : "Bookkeeping"],
                  ["receipts", language === "sv" ? "Underlag" : "Receipts"]
                ].map(([key, label]) => (
                  <button
                    type="button"
                    className={dataQualityFilter === key ? "filter-button active" : "filter-button"}
                    key={key}
                    onClick={() => setDataQualityFilter(key)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            {filteredDataQualityIssues.length === 0 ? (
              <p className="empty-state">{language === "sv" ? "Inga problem matchar filtret." : "No issues match the filter."}</p>
            ) : (
              <div className="data-quality-list">
                {filteredDataQualityIssues.slice(0, 80).map((issue) => (
                  <button type="button" className={`data-quality-row ${issue.severity}`} key={issue.id} onClick={issue.action}>
                    <span>
                      <strong>{issue.severity === "critical"
                        ? (language === "sv" ? "Kritisk" : "Critical")
                        : issue.severity === "warning"
                          ? (language === "sv" ? "Varning" : "Warning")
                          : "Info"}
                      </strong>
                      <small>{issue.areaLabel}</small>
                    </span>
                    <span>
                      <strong>{issue.title}</strong>
                      <small>{issue.detail}</small>
                    </span>
                    <span>{issue.reference || "-"}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Arkivpaket for perioden" : "Archive package for the period"}</h2>
            <div className="button-row">
              <span className={archivePackageWarnings === 0 ? "status success-status" : "status warning-status"}>
                {archivePackageReadyCount}/{archivePackageItems.length} {language === "sv" ? "redo" : "ready"}
              </span>
              <button type="button" className="secondary-button" onClick={downloadArchiveManifestCsv}>
                {language === "sv" ? "Exportera manifest" : "Export manifest"}
              </button>
            </div>
          </div>

          <div className="archive-package-panel">
            <div className="archive-package-intro">
              <strong>{language === "sv" ? "Spara detta tillsammans" : "Store this together"}</strong>
              <p>
                {language === "sv"
                  ? "Anvand arkivpaketet nar du vill samla bokforingsunderlag for en period: fakturor, kvitton, verifikationer, betalningar, moms och rapporter. Manifestet blir din innehallsforteckning."
                  : "Use the archive package when you want to gather bookkeeping records for a period: invoices, receipts, vouchers, payments, VAT and reports. The manifest becomes your table of contents."}
              </p>
              <small>
                {language === "sv"
                  ? "Tips: exportera manifestet sist, efter att du har kontrollerat varningar."
                  : "Tip: export the manifest last, after checking warnings."}
              </small>
            </div>

            <div className="archive-package-grid">
              {archivePackageItems.map((item) => (
                <article className={`archive-package-card ${item.status}`} key={item.key}>
                  <header>
                    <span>{item.status === "ok" ? "OK" : item.status === "warning" ? (language === "sv" ? "Kontrollera" : "Check") : "Info"}</span>
                    <strong>{item.count}</strong>
                  </header>
                  <h3>{item.title}</h3>
                  <p>{item.description}</p>
                  <button type="button" className="secondary-button" onClick={item.action}>
                    {item.actionLabel}
                  </button>
                </article>
              ))}
            </div>
          </div>

          <div className={selectedPeriodIsLocked ? "period-close-panel locked" : "period-close-panel"}>
            <div>
              <span>{language === "sv" ? "Periodavslut" : "Period close"}</span>
              <strong>
                {selectedPeriodIsLocked
                  ? (language === "sv" ? "Vald period ar last" : "Selected period is locked")
                  : (language === "sv" ? "Redo att lasa nar du har kontrollerat" : "Ready to lock after review")}
              </strong>
              <p>
                {language === "sv"
                  ? `Period: ${archivePeriodLabel}. Nar du laser perioden stoppar AliBooks nya andringar pa eller fore ${formatDateOnly(vatPeriodTo)}. Gor senare rattelser i en ny period.`
                  : `Period: ${archivePeriodLabel}. When you lock the period, AliBooks blocks new changes on or before ${formatDateOnly(vatPeriodTo)}. Make later corrections in a new period.`}
              </p>
              <small>
                {closeChecklistWarnings === 0 && archivePackageWarnings === 0
                  ? (language === "sv" ? "Inga varningar i kontrollcentret." : "No warnings in the control center.")
                  : `${closeChecklistWarnings + archivePackageWarnings} ${language === "sv" ? "varningar att kontrollera innan lasning" : "warnings to check before locking"}`}
              </small>
            </div>
            <div className="period-close-actions">
              <span className={selectedPeriodIsLocked ? "status success-status" : "status warning-status"}>
                {selectedPeriodIsLocked
                  ? `${language === "sv" ? "Last till" : "Locked through"} ${formatDateOnly(accountingLockedThroughDate)}`
                  : (language === "sv" ? "Inte last" : "Not locked")}
              </span>
              <button
                type="button"
                className="primary-action-button"
                onClick={closeSelectedAccountingPeriod}
                disabled={!selectedPeriodCanBeLocked}
              >
                {selectedPeriodIsLocked
                  ? (language === "sv" ? "Period redan last" : "Period already locked")
                  : (language === "sv" ? "Las vald period" : "Lock selected period")}
              </button>
              <button type="button" className="secondary-button" onClick={() => setActiveView("settings")}>
                {language === "sv" ? "Oppna installningar" : "Open settings"}
              </button>
              {periodCloseMessage && <p className="message success">{periodCloseMessage}</p>}
            </div>
          </div>

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Handelselogg / revisionsspar" : "Event log / audit trail"}</h2>
            <div className="button-row">
              <span className="status">{filteredAuditTrailRows.length} {language === "sv" ? "handelser" : "events"}</span>
              <button type="button" className="secondary-button" onClick={downloadAuditTrailCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="audit-trail-panel">
            <div className="audit-trail-summary">
              <article>
                <span>{language === "sv" ? "Fakturor" : "Invoices"}</span>
                <strong>{auditTrailTypeCounts.invoice || 0}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Betalningar" : "Payments"}</span>
                <strong>{auditTrailTypeCounts.payment || 0}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Kostnader" : "Expenses"}</span>
                <strong>{auditTrailTypeCounts.expense || 0}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Verifikat" : "Vouchers"}</span>
                <strong>{auditTrailTypeCounts.voucher || 0}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Bank" : "Bank"}</span>
                <strong>{auditTrailTypeCounts.bank || 0}</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Stripe" : "Stripe"}</span>
                <strong>{auditTrailTypeCounts.stripe || 0}</strong>
              </article>
            </div>

            <div className="invoice-filter-panel audit-trail-filter-panel">
              <label>
                {language === "sv" ? "Sok i handelselogg" : "Search event log"}
                <input
                  value={auditTrailSearch}
                  onChange={(event) => setAuditTrailSearch(event.target.value)}
                  placeholder={language === "sv" ? "Fakturanummer, kund, belopp, status..." : "Invoice number, customer, amount, status..."}
                />
              </label>
              <div className="payment-filter-row">
                {activityFilterKeys.map((type) => (
                  <button
                    type="button"
                    className={auditTrailFilter === type ? "filter-button active" : "filter-button"}
                    key={type}
                    onClick={() => setAuditTrailFilter(type)}
                  >
                    {activityFilterLabel(type)}
                  </button>
                ))}
              </div>
            </div>

            {filteredAuditTrailRows.length === 0 ? (
              <p className="empty-state">{language === "sv" ? "Inga handelser matchar filtret." : "No events match the filter."}</p>
            ) : (
              <div className="audit-trail-list">
                {filteredAuditTrailRows.slice(0, 80).map((row) => (
                  <button type="button" className={`audit-trail-row ${row.type}`} key={row.id} onClick={row.action}>
                    <span>
                      <strong>{formatDateOnly(row.date) || "-"}</strong>
                      <small>{row.typeLabel}</small>
                    </span>
                    <span>
                      <strong>{row.title}</strong>
                      <small>{row.reference || "-"}</small>
                    </span>
                    <span>{row.party || "-"}</span>
                    <strong>{row.amount ? `${row.amount} SEK` : "-"}</strong>
                    <span className="status">{row.status || "-"}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="section-heading report-subheading">
            <h2>{t.profitAndLoss}</h2>
            <div className="button-row">
              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  loadProfitAndLoss();
                  loadBalanceReport();
                }}
              >
                {t.refresh}
              </button>
              <button type="button" className="secondary-button" onClick={downloadProfitAndLossCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          {profitAndLoss ? (
            <div className="report-grid">
              <article className="report-card">
                <h3>{t.revenueLines}</h3>
                {profitAndLoss.revenue.length === 0 ? (
                  <p className="muted-line">{language === "sv" ? "Inga intakter annu." : "No revenue yet."}</p>
                ) : (
                  profitAndLoss.revenue.map((line) => (
                    <p key={line.accountNumber}>
                      <span>{line.accountNumber} {line.accountName}</span>
                      <strong>{line.amount} SEK</strong>
                    </p>
                  ))
                )}
                <footer>{t.total}: {profitAndLoss.totalRevenue} SEK</footer>
              </article>

              <article className="report-card">
                <h3>{t.expenseLines}</h3>
                {profitAndLoss.expenses.length === 0 ? (
                  <p className="muted-line">{language === "sv" ? "Inga kostnader annu." : "No expenses yet."}</p>
                ) : (
                  profitAndLoss.expenses.map((line) => (
                    <p key={line.accountNumber}>
                      <span>{line.accountNumber} {line.accountName}</span>
                      <strong>{line.amount} SEK</strong>
                    </p>
                  ))
                )}
                <footer>{t.total}: {profitAndLoss.totalExpenses} SEK</footer>
              </article>

              <article className="report-card result-card">
                <h3>{t.result}</h3>
                <strong>{profitAndLoss.result} SEK</strong>
              </article>
            </div>
          ) : (
            <p className="empty-state">{language === "sv" ? "Ingen rapport laddad." : "No report loaded."}</p>
          )}

          <div className="section-heading report-subheading">
            <h2>{t.balanceReport}</h2>
            <button type="button" className="secondary-button" onClick={downloadBalanceReportCsv}>
              {t.exportCsv}
            </button>
          </div>

          {balanceReport ? (
            <div className="report-grid">
              <article className="report-card">
                <h3>{t.assetLines}</h3>
                {balanceReport.assets.length === 0 ? (
                  <p className="muted-line">{language === "sv" ? "Inga tillgangar annu." : "No assets yet."}</p>
                ) : (
                  balanceReport.assets.map((line) => (
                    <p key={line.accountNumber}>
                      <span>{line.accountNumber} {line.accountName}</span>
                      <strong>{line.amount} SEK</strong>
                    </p>
                  ))
                )}
                <footer>{t.total}: {balanceReport.totalAssets} SEK</footer>
              </article>

              <article className="report-card">
                <h3>{t.liabilitiesAndEquity}</h3>
                {balanceReport.liabilitiesAndEquity.length === 0 ? (
                  <p className="muted-line">{language === "sv" ? "Inga skulder eller eget kapital annu." : "No liabilities or equity yet."}</p>
                ) : (
                  balanceReport.liabilitiesAndEquity.map((line) => (
                    <p key={line.accountNumber}>
                      <span>{line.accountNumber} {line.accountName}</span>
                      <strong>{line.amount} SEK</strong>
                    </p>
                  ))
                )}
                <footer>{t.total}: {balanceReport.totalLiabilitiesAndEquity} SEK</footer>
              </article>

              <article className="report-card result-card">
                <h3>{t.difference}</h3>
                <strong>{balanceReport.difference} SEK</strong>
              </article>
            </div>
          ) : (
            <p className="empty-state">{language === "sv" ? "Ingen balansrapport laddad." : "No balance report loaded."}</p>
          )}

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Manadsavslut" : "Monthly close"}</h2>
            <div className="button-row">
              <span className={monthlyCloseWarnings === 0 ? "status success-status" : "status warning-status"}>
                {monthlyCloseReadiness}% {language === "sv" ? "klart" : "ready"}
              </span>
              <button type="button" className="secondary-button" onClick={downloadMonthlyCloseCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="monthly-close-panel">
            <div className="monthly-close-hero">
              <div>
                <label>
                  {language === "sv" ? "Valj manad" : "Choose month"}
                  <input
                    type="month"
                    value={monthCloseSelectedMonth}
                    onChange={(event) => setMonthCloseSelectedMonth(event.target.value)}
                  />
                </label>
                <strong>{monthlyCloseStatusText}</strong>
                <p>
                  {language === "sv"
                    ? `Period ${monthlyCloseRange.from} - ${monthlyCloseRange.to}. Anvand detta som en snabb manadsstangning innan du exporterar rapporter eller laser period.`
                    : `Period ${monthlyCloseRange.from} - ${monthlyCloseRange.to}. Use this as a quick monthly close before exporting reports or locking a period.`}
                </p>
              </div>
              <div className="monthly-close-score">
                <span>{language === "sv" ? "Klarhet" : "Readiness"}</span>
                <strong>{monthlyCloseReadiness}%</strong>
                <small>{monthlyCloseWarnings} {language === "sv" ? "varningar" : "warnings"}</small>
              </div>
            </div>

            <div className="expense-summary-grid monthly-close-summary-grid">
              <article>
                <span>{language === "sv" ? "Intakter" : "Revenue"}</span>
                <strong>{monthlyCloseRow.revenue} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Kostnader" : "Expenses"}</span>
                <strong>{monthlyCloseRow.expenses} SEK</strong>
              </article>
              <article className={monthlyCloseRow.result >= 0 ? "balanced-summary" : "unbalanced-summary"}>
                <span>{language === "sv" ? "Resultat" : "Result"}</span>
                <strong>{monthlyCloseRow.result} SEK</strong>
              </article>
              <article>
                <span>{language === "sv" ? "Moms" : "VAT"}</span>
                <strong>{monthlyCloseRow.vatToPay} SEK</strong>
              </article>
              <article className={monthlyCloseRow.cashChange >= 0 ? "balanced-summary" : "unbalanced-summary"}>
                <span>{language === "sv" ? "Kassa" : "Cash"}</span>
                <strong>{monthlyCloseRow.cashChange} SEK</strong>
              </article>
            </div>

            <div className="monthly-close-checklist">
              {monthlyCloseChecklist.map((item) => (
                <button type="button" className={`monthly-close-card ${item.status}`} key={item.key} onClick={item.action}>
                  <span>{item.status === "ok" ? "OK" : item.status === "warning" ? (language === "sv" ? "Kontrollera" : "Check") : "Info"}</span>
                  <strong>{item.title}</strong>
                  <small>{item.detail}</small>
                  <em>{item.actionLabel}</em>
                </button>
              ))}
            </div>
          </div>

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Manadsrapport" : "Monthly report"}</h2>
            <div className="button-row">
              <span className="status">{monthlyReportRows.length} {language === "sv" ? "manader" : "months"}</span>
              <button type="button" className="secondary-button" onClick={downloadMonthlyReportCsv} disabled={monthlyReportRows.length === 0}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="expense-summary-grid monthly-report-summary-grid">
            <article>
              <span>{language === "sv" ? "Intakter" : "Revenue"}</span>
              <strong>{monthlyReportRevenueTotal} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Kostnader" : "Expenses"}</span>
              <strong>{monthlyReportExpenseTotal} SEK</strong>
            </article>
            <article className={monthlyReportResultTotal >= 0 ? "balanced-summary" : "unbalanced-summary"}>
              <span>{language === "sv" ? "Resultat" : "Result"}</span>
              <strong>{monthlyReportResultTotal} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Moms att betala" : "VAT to pay"}</span>
              <strong>{monthlyReportVatToPayTotal} SEK</strong>
            </article>
            <article className={monthlyReportCashChangeTotal >= 0 ? "balanced-summary" : "unbalanced-summary"}>
              <span>{language === "sv" ? "Kassaforandring" : "Cash change"}</span>
              <strong>{monthlyReportCashChangeTotal} SEK</strong>
            </article>
          </div>

          {monthlyReportRows.length === 0 ? (
            <p className="empty-state">{language === "sv" ? "Ingen manadsdata finns annu." : "No monthly data yet."}</p>
          ) : (
            <div className="account-table monthly-report-table">
              <div className="account-row monthly-report-header">
                <span>{language === "sv" ? "Manad" : "Month"}</span>
                <span>{language === "sv" ? "Intakter" : "Revenue"}</span>
                <span>{language === "sv" ? "Kostnader" : "Expenses"}</span>
                <span>{language === "sv" ? "Resultat" : "Result"}</span>
                <span>{language === "sv" ? "Moms" : "VAT"}</span>
                <span>{language === "sv" ? "Kassa" : "Cash"}</span>
                <span>{language === "sv" ? "Rader" : "Rows"}</span>
              </div>
              {monthlyReportRows.map((row) => {
                const monthRange = monthRangeFromKey(row.monthKey);

                return (
                  <button
                    type="button"
                    className={row.result >= 0 ? "account-row monthly-report-row" : "account-row monthly-report-row negative"}
                    key={row.monthKey}
                    onClick={() => {
                      setActiveView("bookkeeping");
                      setBookkeepingDateFrom(monthRange.from);
                      setBookkeepingDateTo(monthRange.to);
                      setBookkeepingSearch("");
                    }}
                    title={language === "sv" ? "Visa bokforing for manaden" : "Show bookkeeping for this month"}
                  >
                    <strong>{formatMonthLabel(row.monthKey, language)}</strong>
                    <span>{row.revenue} SEK</span>
                    <span>{row.expenses} SEK</span>
                    <strong className={row.result >= 0 ? "balanced-text" : "unbalanced-text"}>{row.result} SEK</strong>
                    <span>{row.vatToPay} SEK</span>
                    <span>{row.cashChange} SEK</span>
                    <span>{row.voucherCount}</span>
                  </button>
                );
              })}
            </div>
          )}

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Forfallorapport" : "Aging report"}</h2>
            <div className="button-row">
              <span className="status">{agingInvoices.length} {language === "sv" ? "oppna fakturor" : "open invoices"}</span>
              <button type="button" className="secondary-button" onClick={downloadAgingReportCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="aging-report-grid">
            {agingBuckets.map((bucket) => {
              const bucketTotal = bucket.items.reduce((sum, item) => sum + invoiceRemainingAmount(item), 0);

              return (
                <article className="report-card aging-card" key={bucket.key}>
                  <header>
                    <h3>{bucket.title}</h3>
                    <strong>{bucketTotal} SEK</strong>
                  </header>

                  {bucket.items.length === 0 ? (
                    <p className="muted-line">{language === "sv" ? "Inga fakturor i denna grupp." : "No invoices in this group."}</p>
                  ) : (
                    <div className="aging-list">
                      {bucket.items.map((item) => {
                        const dueStatus = invoiceDueStatus(item, language);

                        return (
                          <button
                            type="button"
                            className="aging-row"
                            key={item.id}
                            onClick={() => {
                              setActiveView("invoices");
                              setInvoiceSearch(invoiceNumber(item));
                            }}
                          >
                            <span>
                              <strong>{invoiceNumber(item)}</strong>
                              <small>{item.customerName || item.customer?.name || "-"}</small>
                            </span>
                            <span>
                              <strong>{invoiceRemainingAmount(item)} SEK</strong>
                              <small>{language === "sv" ? "Forfallodatum" : "Due"}: {formatDateOnly(item.dueDate)}</small>
                            </span>
                            <span className={`due-status ${dueStatus.className}`}>{dueStatus.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </article>
              );
            })}
          </div>

          <div className="section-heading report-subheading">
            <h2>{language === "sv" ? "Kundreskontra" : "Customer receivables"}</h2>
            <div className="button-row">
              <span className="status">{customerLedgerOpenCustomers} {language === "sv" ? "kunder med utestaende saldo" : "customers with outstanding balance"}</span>
              <button type="button" className="secondary-button" onClick={downloadReceivablesReportCsv}>
                {t.exportCsv}
              </button>
            </div>
          </div>

          <div className="expense-summary-grid customer-ledger-summary-grid">
            <article>
              <span>{language === "sv" ? "Kvar att betala" : "Outstanding"}</span>
              <strong>{customerLedgerOutstandingTotal} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Forfallet belopp" : "Overdue amount"}</span>
              <strong>{customerLedgerOverdueTotal} SEK</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Kunder med skuld" : "Customers owing"}</span>
              <strong>{customerLedgerOpenCustomers}</strong>
            </article>
            <article>
              <span>{language === "sv" ? "Forfallna kunder" : "Overdue customers"}</span>
              <strong>{customerLedgerOverdueCustomers}</strong>
            </article>
          </div>

          <div className="invoice-filter-panel customer-ledger-filter-panel">
            <label>
              {language === "sv" ? "Sok kundreskontra" : "Search receivables"}
              <input
                value={customerLedgerSearch}
                onChange={(event) => setCustomerLedgerSearch(event.target.value)}
                placeholder={language === "sv" ? "Namn, e-post, personnummer eller faktura" : "Name, email, personal number or invoice"}
              />
            </label>
            <button type="button" className="secondary-button" onClick={() => setCustomerLedgerSearch("")}>
              {language === "sv" ? "Rensa sokning" : "Clear search"}
            </button>
          </div>

          {filteredCustomerLedgerRows.length === 0 ? (
            <p className="empty-state">{language === "sv" ? "Ingen kundreskontra att visa annu." : "No customer receivables to show yet."}</p>
          ) : (
            <div className="account-table customer-ledger-table">
              <div className="account-row customer-ledger-header">
                <span>{language === "sv" ? "Kund" : "Customer"}</span>
                <span>{language === "sv" ? "Fakturor" : "Invoices"}</span>
                <span>{language === "sv" ? "Fakturerat" : "Invoiced"}</span>
                <span>{language === "sv" ? "Betalt" : "Paid"}</span>
                <span>{language === "sv" ? "Kvar" : "Outstanding"}</span>
                <span>{language === "sv" ? "Forfallet" : "Overdue"}</span>
                <span>{language === "sv" ? "Senaste" : "Latest"}</span>
              </div>
              {filteredCustomerLedgerRows.map((row) => (
                <button
                  type="button"
                  className={row.overdueTotal > 0 ? "account-row customer-ledger-row overdue" : "account-row customer-ledger-row"}
                  key={row.customerId || row.name}
                  onClick={() => {
                    setActiveView("invoices");
                    setInvoiceSearch(row.name || row.latestInvoiceNumber || "");
                  }}
                >
                  <span>
                    <strong>{row.name || "-"}</strong>
                    <small>{row.email || "-"}</small>
                    <small>{row.personalNumber || "-"}</small>
                  </span>
                  <span>{row.invoiceCount} / {row.openCount} {language === "sv" ? "oppna" : "open"}</span>
                  <span>{row.invoicedTotal} SEK</span>
                  <span>{row.paidTotal} SEK</span>
                  <strong>{row.outstandingTotal} SEK</strong>
                  <strong className={row.overdueTotal > 0 ? "overdue-text" : ""}>{row.overdueTotal} SEK</strong>
                  <span>
                    <strong>{row.latestInvoiceNumber || "-"}</strong>
                    <small>{formatDateOnly(row.latestInvoiceDate) || "-"}</small>
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>}

        {activeView === "settings" && (
          <section className="orders-section">
            <h2>{t.settings}</h2>
            {settings ? (
              <form className="form settings-form" onSubmit={handleSaveSettings}>
                <div className="settings-panel system-status-panel">
                  <div className="section-heading">
                    <strong>{language === "sv" ? "Systemstatus" : "System status"}</strong>
                    <button type="button" className="secondary-button" onClick={loadSystemStatus}>
                      {language === "sv" ? "Uppdatera" : "Refresh"}
                    </button>
                  </div>
                  {systemStatus?.error && <p className="message warning">{systemStatus.error}</p>}
                  <div className="system-status-grid">
                    {renderSystemStatusItem(
                      language === "sv" ? "Backend" : "Backend",
                      Boolean(systemStatus?.backend?.ok),
                      language === "sv" ? "API-servern svarar." : "API server is responding."
                    )}
                    {renderSystemStatusItem(
                      language === "sv" ? "Databas" : "Database",
                      Boolean(systemStatus?.database?.ok),
                      language === "sv" ? "PostgreSQL/RDS-anslutning." : "PostgreSQL/RDS connection."
                    )}
                    {renderSystemStatusItem(
                      language === "sv" ? "JWT-sakerhet" : "JWT security",
                      Boolean(systemStatus?.security?.jwtStrong),
                      systemStatus?.security?.jwtConfigured
                        ? (language === "sv" ? "JWT_SECRET finns men bor vara minst 32 tecken." : "JWT_SECRET exists but should be at least 32 characters.")
                        : (language === "sv" ? "Byt fran standard JWT_SECRET fore publik demo." : "Change the default JWT_SECRET before public demo.")
                    )}
                    {renderSystemStatusItem(
                      "Stripe",
                      Boolean(systemStatus?.stripe?.configured),
                      systemStatus?.stripe?.webhookConfigured
                        ? (language === "sv" ? "Nyckel och webhook finns." : "Key and webhook are set.")
                        : (language === "sv" ? "Lagg STRIPE_SECRET_KEY och webhook-secret senare." : "Add STRIPE_SECRET_KEY and webhook secret later.")
                    )}
                    {renderSystemStatusItem(
                      language === "sv" ? "AI-assistent" : "AI assistant",
                      Boolean(systemStatus?.ai?.configured),
                      systemStatus?.ai?.configured
                        ? `Provider: ${aiProviderLabel(systemStatus?.ai?.provider)}`
                        : (language === "sv" ? "Lagg GEMINI_API_KEY for riktig AI." : "Add GEMINI_API_KEY for real AI.")
                    )}
                    {renderSystemStatusItem(
                      language === "sv" ? "E-post/SMTP" : "Email/SMTP",
                      Boolean(systemStatus?.email?.configured),
                      systemStatus?.email?.configured
                        ? (language === "sv" ? "SMTP host och anvandare finns." : "SMTP host and username are set.")
                        : (language === "sv" ? "Lagg SMTP i IntelliJ Run Configuration for riktiga mail." : "Add SMTP in IntelliJ Run Configuration for real email.")
                    )}
                    {renderSystemStatusItem(
                      language === "sv" ? "Automatiska paminnelser" : "Automatic reminders",
                      Boolean(systemStatus?.automation?.invoiceRemindersConfigured),
                      systemStatus?.automation?.invoiceRemindersConfigured
                        ? (language === "sv"
                          ? `Schema: ${systemStatus?.automation?.invoiceRemindersCron || "-"} (${systemStatus?.automation?.timeZone || "Europe/Stockholm"})`
                          : `Schedule: ${systemStatus?.automation?.invoiceRemindersCron || "-"} (${systemStatus?.automation?.timeZone || "Europe/Stockholm"})`)
                        : (language === "sv" ? "Inget schema hittades." : "No schedule found.")
                    )}
                  </div>
                  {systemStatus && (!systemStatus?.security?.jwtStrong || !systemStatus?.stripe?.configured || !systemStatus?.stripe?.webhookConfigured || !systemStatus?.ai?.configured || !systemStatus?.email?.configured) && (
                    <div className="config-guide">
                      <div className="config-guide-header">
                        <strong>{language === "sv" ? "Konfigurationsguide" : "Configuration guide"}</strong>
                        <button type="button" className="secondary-button" onClick={copyMissingConfigValues}>
                          {language === "sv" ? "Kopiera alla" : "Copy all"}
                        </button>
                      </div>
                      <p>
                        {language === "sv"
                          ? "Lagg dessa som Environment variables i IntelliJ Run Configuration. Skriv aldrig riktiga nycklar direkt i koden."
                          : "Add these as Environment variables in IntelliJ Run Configuration. Never write real secrets directly in code."}
                      </p>
                      <p className="settings-hint">
                        {language === "sv"
                          ? "Efter att du har andrat miljo variabler: starta om CloudShopApplication."
                          : "After changing environment variables: restart CloudShopApplication."}
                      </p>
                      {!systemStatus?.security?.jwtStrong && (
                        <div className="config-guide-row">
                          <code>{jwtSecretConfigLine()}</code>
                          <button type="button" className="secondary-button" onClick={generateJwtSecret}>
                            {language === "sv" ? "Generera" : "Generate"}
                          </button>
                          <button type="button" className="secondary-button" onClick={() => copyConfigValue(jwtSecretConfigLine())}>
                            {language === "sv" ? "Kopiera" : "Copy"}
                          </button>
                        </div>
                      )}
                      {!systemStatus?.stripe?.configured && (
                        <div className="config-guide-row">
                          <code>STRIPE_SECRET_KEY=sk_test_...</code>
                          <button type="button" className="secondary-button" onClick={() => copyConfigValue("STRIPE_SECRET_KEY=sk_test_...")}>
                            {language === "sv" ? "Kopiera" : "Copy"}
                          </button>
                        </div>
                      )}
                      {!systemStatus?.stripe?.webhookConfigured && (
                        <div className="config-guide-row">
                          <code>STRIPE_WEBHOOK_SECRET=whsec_...</code>
                          <button type="button" className="secondary-button" onClick={() => copyConfigValue("STRIPE_WEBHOOK_SECRET=whsec_...")}>
                            {language === "sv" ? "Kopiera" : "Copy"}
                          </button>
                        </div>
                      )}
                      {!systemStatus?.ai?.configured && (
                        <>
                          <div className="config-guide-row">
                            <code>GEMINI_API_KEY=AIza...</code>
                            <button type="button" className="secondary-button" onClick={() => copyConfigValue("GEMINI_API_KEY=AIza...")}>
                              {language === "sv" ? "Kopiera" : "Copy"}
                            </button>
                          </div>
                          <div className="config-guide-row">
                            <code>GEMINI_MODEL=gemini-3.5-flash</code>
                            <button type="button" className="secondary-button" onClick={() => copyConfigValue("GEMINI_MODEL=gemini-3.5-flash")}>
                              {language === "sv" ? "Kopiera" : "Copy"}
                            </button>
                          </div>
                          <div className="config-guide-row">
                            <code>GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta</code>
                            <button type="button" className="secondary-button" onClick={() => copyConfigValue("GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta")}>
                              {language === "sv" ? "Kopiera" : "Copy"}
                            </button>
                          </div>
                        </>
                      )}
                      {!systemStatus?.email?.configured && (
                        <>
                          <div className="config-guide-row">
                            <code>SPRING_MAIL_HOST=smtp.gmail.com</code>
                            <button type="button" className="secondary-button" onClick={() => copyConfigValue("SPRING_MAIL_HOST=smtp.gmail.com")}>
                              {language === "sv" ? "Kopiera" : "Copy"}
                            </button>
                          </div>
                          <div className="config-guide-row">
                            <code>SPRING_MAIL_PORT=587</code>
                            <button type="button" className="secondary-button" onClick={() => copyConfigValue("SPRING_MAIL_PORT=587")}>
                              {language === "sv" ? "Kopiera" : "Copy"}
                            </button>
                          </div>
                          <div className="config-guide-row">
                            <code>SPRING_MAIL_USERNAME=din-email@gmail.com</code>
                            <button type="button" className="secondary-button" onClick={() => copyConfigValue("SPRING_MAIL_USERNAME=din-email@gmail.com")}>
                              {language === "sv" ? "Kopiera" : "Copy"}
                            </button>
                          </div>
                          <div className="config-guide-row">
                            <code>SPRING_MAIL_PASSWORD=app-losenord-fran-google</code>
                            <button type="button" className="secondary-button" onClick={() => copyConfigValue("SPRING_MAIL_PASSWORD=app-losenord-fran-google")}>
                              {language === "sv" ? "Kopiera" : "Copy"}
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                  )}
                </div>

                <label>
                  {t.company}
                  <input
                    value={settings.companyName || ""}
                    onChange={(event) => setSettings({ ...settings, companyName: event.target.value })}
                  />
                </label>

                <label>
                  {t.companyType}
                  <select
                    value={settings.companyType || "SOLE_TRADER"}
                    onChange={(event) => setSettings({ ...settings, companyType: event.target.value })}
                  >
                    <option value="SOLE_TRADER">{t.soleTrader}</option>
                    <option value="LIMITED_COMPANY">{t.limitedCompany}</option>
                  </select>
                </label>
                <p className="settings-hint">
                  {settings.companyType === "LIMITED_COMPANY"
                    ? (language === "sv"
                      ? "Aktiebolag valt. Nasta steg blir AB-konton som 2081, 2091, 2099 och 2510."
                      : "Limited company selected. Next step is AB accounts such as 2081, 2091, 2099 and 2510.")
                    : (language === "sv"
                      ? "Enskild firma valt. Detta passar din nuvarande bokforing."
                      : "Sole trader selected. This matches your current bookkeeping.")}
                </p>

                <label>
                  {t.contactEmail}
                  <input
                    type="email"
                    value={settings.contactEmail || ""}
                    onChange={(event) => setSettings({ ...settings, contactEmail: event.target.value })}
                  />
                </label>
                <div className="settings-panel">
                  <strong>{language === "sv" ? "E-posttest" : "Email test"}</strong>
                  <p className="settings-hint">
                    {language === "sv"
                      ? "Skickar ett testmail till kontaktadressen ovan med samma SMTP-installningar som fakturapaminnelser."
                      : "Sends a test email to the contact address above using the same SMTP settings as invoice reminders."}
                  </p>
                  <button type="button" className="secondary-button" onClick={sendSettingsTestEmail}>
                    {language === "sv" ? "Skicka testmail" : "Send test email"}
                  </button>
                </div>

                <div className="form-row">
                  <label>
                    PlusGiro
                    <input
                      value={settings.plusGiro || ""}
                      onChange={(event) => setSettings({ ...settings, plusGiro: event.target.value })}
                    />
                  </label>

                  <label>
                    OCR
                    <input
                      value={settings.defaultOcr || ""}
                      onChange={(event) => setSettings({ ...settings, defaultOcr: event.target.value })}
                    />
                  </label>
                </div>

                <label>
                  {t.paymentRecipient}
                  <input
                    value={settings.paymentRecipient || ""}
                    onChange={(event) => setSettings({ ...settings, paymentRecipient: event.target.value })}
                  />
                </label>

                <label>
                  {t.vatPercent}
                  <input
                    type="number"
                    value={settings.vatPercent || 25}
                    onChange={(event) => setSettings({ ...settings, vatPercent: Number(event.target.value) })}
                  />
                </label>

                <label>
                  {t.paymentTermsDays}
                  <select
                    value={settings.paymentTermsDays || 30}
                    onChange={(event) => setSettings({ ...settings, paymentTermsDays: Number(event.target.value) })}
                  >
                    <option value={5}>5</option>
                    <option value={10}>10</option>
                    <option value={15}>15</option>
                    <option value={30}>30</option>
                    <option value={60}>60</option>
                  </select>
                </label>

                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={Boolean(settings.ftaxApproved)}
                    onChange={(event) => setSettings({ ...settings, ftaxApproved: event.target.checked })}
                  />
                  {t.approvedForFtax}
                </label>

                <div className="settings-panel">
                  <strong>{language === "sv" ? "Periodlasning" : "Period locking"}</strong>
                  <label>
                    {language === "sv" ? "Las bokforing till och med" : "Lock bookkeeping through"}
                    <input
                      type="date"
                      value={settings.accountingLockedThroughDate || ""}
                      onChange={(event) => setSettings({
                        ...settings,
                        accountingLockedThroughDate: event.target.value || null
                      })}
                    />
                  </label>
                  <p className="settings-hint">
                    {language === "sv"
                      ? "Nar ett datum ar valt kan inga manuella verifikationer skapas pa eller fore detta datum. Lamna tomt om du inte vill lasa nagon period."
                      : "When a date is selected, manual vouchers cannot be created on or before that date. Leave empty if you do not want to lock a period."}
                  </p>
                </div>

                <div className="settings-panel">
                  <strong>{language === "sv" ? "Automatiska fakturapaminnelser" : "Automatic invoice reminders"}</strong>
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={settings.automaticInvoiceRemindersEnabled !== false}
                      onChange={(event) => setSettings({ ...settings, automaticInvoiceRemindersEnabled: event.target.checked })}
                    />
                    {language === "sv" ? "Skicka automatiskt via e-post" : "Send automatically by email"}
                  </label>

                  <label>
                    {language === "sv" ? "Skicka antal dagar fore forfallodatum" : "Send days before due date"}
                    <select
                      value={settings.invoiceReminderDaysBeforeDue || 5}
                      onChange={(event) => setSettings({ ...settings, invoiceReminderDaysBeforeDue: Number(event.target.value) })}
                    >
                      <option value={3}>3</option>
                      <option value={5}>5</option>
                      <option value={7}>7</option>
                      <option value={10}>10</option>
                    </select>
                  </label>

                  <p className="settings-hint">
                    {language === "sv"
                      ? "Backend kontrollerar fakturor varje dag kl. 09:00 nar appen kors."
                      : "Backend checks invoices every day at 09:00 while the app is running."}
                  </p>

                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={settings.overdueInvoiceRemindersEnabled !== false}
                      onChange={(event) => setSettings({ ...settings, overdueInvoiceRemindersEnabled: event.target.checked })}
                    />
                    {language === "sv" ? "Skicka forfallen paminnelse automatiskt" : "Send overdue reminder automatically"}
                  </label>

                  <label>
                    {language === "sv" ? "Skicka antal dagar efter forfallodatum" : "Send days after due date"}
                    <select
                      value={settings.overdueInvoiceReminderDaysAfterDue || 3}
                      onChange={(event) => setSettings({ ...settings, overdueInvoiceReminderDaysAfterDue: Number(event.target.value) })}
                    >
                      <option value={1}>1</option>
                      <option value={3}>3</option>
                      <option value={5}>5</option>
                      <option value={7}>7</option>
                    </select>
                  </label>
                </div>

                <div className="settings-panel">
                  <strong>{language === "sv" ? "Mall for fakturamejl" : "Invoice email template"}</strong>
                  <label>
                    {language === "sv" ? "E-posttext" : "Email text"}
                    <textarea
                      rows={10}
                      value={settings.invoiceEmailTemplate || defaultInvoiceEmailTemplate()}
                      onChange={(event) => setSettings({ ...settings, invoiceEmailTemplate: event.target.value })}
                    />
                  </label>
                  <p className="settings-hint">
                    {language === "sv"
                      ? "Denna text anvands nar du klickar Skicka faktura. PDF-fakturan bifogas automatiskt. Du kan anvanda: {kundnamn}, {fakturanummer}, {forfallodatum}, {belopp}, {plusgiro}, {ocr}, {betalningsmottagare}, {foretag}, {kontaktEpost}."
                      : "This text is used when you click Send invoice. The PDF invoice is attached automatically. You can use: {kundnamn}, {fakturanummer}, {forfallodatum}, {belopp}, {plusgiro}, {ocr}, {betalningsmottagare}, {foretag}, {kontaktEpost}."}
                  </p>
                  <div className="template-preview">
                    <strong>{language === "sv" ? "Forhandsgranskning" : "Preview"}</strong>
                    <span>
                      {language === "sv"
                        ? `Exempel baserat pa ${invoiceNumber(reminderPreviewInvoice)}`
                        : `Example based on ${invoiceNumber(reminderPreviewInvoice)}`}
                    </span>
                    <pre>{renderInvoiceEmailText(reminderPreviewInvoice, settings)}</pre>
                  </div>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => setSettings({ ...settings, invoiceEmailTemplate: defaultInvoiceEmailTemplate() })}
                  >
                    {language === "sv" ? "Aterstall standardmall" : "Reset default template"}
                  </button>
                </div>

                <div className="settings-panel">
                  <strong>{language === "sv" ? "Mall for fakturapaminnelse" : "Invoice reminder template"}</strong>
                  <label>
                    {language === "sv" ? "E-posttext" : "Email text"}
                    <textarea
                      rows={10}
                      value={settings.invoiceReminderTemplate || defaultInvoiceReminderTemplate()}
                      onChange={(event) => setSettings({ ...settings, invoiceReminderTemplate: event.target.value })}
                    />
                  </label>
                  <p className="settings-hint">
                    {language === "sv"
                      ? "Du kan anvanda: {kundnamn}, {fakturanummer}, {forfallodatum}, {belopp}, {plusgiro}, {ocr}, {betalningsmottagare}, {foretag}, {kontaktEpost}."
                      : "You can use: {kundnamn}, {fakturanummer}, {forfallodatum}, {belopp}, {plusgiro}, {ocr}, {betalningsmottagare}, {foretag}, {kontaktEpost}."}
                  </p>
                  <div className="template-preview">
                    <strong>{language === "sv" ? "Forhandsgranskning" : "Preview"}</strong>
                    <span>
                      {language === "sv"
                        ? `Exempel baserat pa ${invoiceNumber(reminderPreviewInvoice)}`
                        : `Example based on ${invoiceNumber(reminderPreviewInvoice)}`}
                    </span>
                    <pre>{renderInvoiceReminderText(reminderPreviewInvoice, settings)}</pre>
                  </div>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => setSettings({ ...settings, invoiceReminderTemplate: defaultInvoiceReminderTemplate() })}
                  >
                    {language === "sv" ? "Aterstall standardmall" : "Reset default template"}
                  </button>
                </div>

                <div className="settings-panel">
                  <strong>{language === "sv" ? "Mall for forfallen faktura" : "Overdue invoice template"}</strong>
                  <label>
                    {language === "sv" ? "E-posttext" : "Email text"}
                    <textarea
                      rows={10}
                      value={settings.overdueInvoiceReminderTemplate || defaultOverdueInvoiceReminderTemplate()}
                      onChange={(event) => setSettings({ ...settings, overdueInvoiceReminderTemplate: event.target.value })}
                    />
                  </label>
                  <p className="settings-hint">
                    {language === "sv"
                      ? "Denna mall anvands efter forfallodatum om fakturan fortfarande ar obetald."
                      : "This template is used after the due date if the invoice is still unpaid."}
                  </p>
                  <div className="template-preview">
                    <strong>{language === "sv" ? "Forhandsgranskning" : "Preview"}</strong>
                    <span>
                      {language === "sv"
                        ? `Exempel baserat pa ${invoiceNumber(reminderPreviewInvoice)}`
                        : `Example based on ${invoiceNumber(reminderPreviewInvoice)}`}
                    </span>
                    <pre>{renderInvoiceReminderText(
                      reminderPreviewInvoice,
                      settings,
                      settings.overdueInvoiceReminderTemplate || defaultOverdueInvoiceReminderTemplate()
                    )}</pre>
                  </div>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => setSettings({ ...settings, overdueInvoiceReminderTemplate: defaultOverdueInvoiceReminderTemplate() })}
                  >
                    {language === "sv" ? "Aterstall standardmall" : "Reset default template"}
                  </button>
                </div>

                <div className="settings-panel backup-panel">
                  <div>
                    <strong>{language === "sv" ? "Sakerhetskopia" : "Data backup"}</strong>
                    <p className="settings-hint">
                      {language === "sv"
                        ? "Ladda ner en JSON-backup med kunder, fakturor, kostnader, bokforing, rapportstatus och revisionsspar. Filen innehaller personuppgifter, spara den sakert."
                        : "Download a JSON backup with customers, invoices, expenses, bookkeeping, report status and audit trail. The file contains personal data, store it securely."}
                    </p>
                  </div>
                  <div className="backup-summary-grid">
                    <article>
                      <span>{language === "sv" ? "Kunder" : "Customers"}</span>
                      <strong>{customers.length}</strong>
                    </article>
                    <article>
                      <span>{language === "sv" ? "Fakturor" : "Invoices"}</span>
                      <strong>{invoices.length}</strong>
                    </article>
                    <article>
                      <span>{language === "sv" ? "Kostnader" : "Expenses"}</span>
                      <strong>{expenses.length}</strong>
                    </article>
                    <article>
                      <span>{language === "sv" ? "Handelser" : "Events"}</span>
                      <strong>{auditTrailRows.length}</strong>
                    </article>
                  </div>
                  <button type="button" className="secondary-button" onClick={downloadDataBackupJson}>
                    {language === "sv" ? "Ladda ner JSON-backup" : "Download JSON backup"}
                  </button>
                  <div className="backup-verify-box">
                    <label>
                      {language === "sv" ? "Kontrollera backupfil" : "Verify backup file"}
                      <input type="file" accept="application/json,.json" onChange={validateBackupFile} />
                    </label>
                    <p className="settings-hint">
                      {language === "sv"
                        ? "Detta laser bara filen och visar en kontroll. Ingen data skrivs till databasen."
                        : "This only reads the file and shows a validation result. No data is written to the database."}
                    </p>
                  </div>
                  {backupValidation && (
                    <div className={backupValidation.ok ? "backup-validation success" : "backup-validation warning"}>
                      <div className="backup-validation-header">
                        <strong>{backupValidation.ok
                          ? (language === "sv" ? "Backupfilen ser giltig ut" : "Backup file looks valid")
                          : (language === "sv" ? "Backupfilen behover kontrolleras" : "Backup file needs review")}
                        </strong>
                        <span>{backupValidation.fileName}</span>
                      </div>
                      <div className="backup-summary-grid">
                        <article>
                          <span>{language === "sv" ? "Version" : "Version"}</span>
                          <strong>{backupValidation.version}</strong>
                        </article>
                        <article>
                          <span>{language === "sv" ? "Period" : "Period"}</span>
                          <strong>{backupValidation.periodLabel}</strong>
                        </article>
                        <article>
                          <span>{language === "sv" ? "Exporterad" : "Exported"}</span>
                          <strong>{formatDateOnly(backupValidation.exportedAt)}</strong>
                        </article>
                        <article>
                          <span>{language === "sv" ? "Persondata" : "Personal data"}</span>
                          <strong>{backupValidation.containsPersonalData ? (language === "sv" ? "Ja" : "Yes") : (language === "sv" ? "Nej" : "No")}</strong>
                        </article>
                      </div>
                      <div className="backup-summary-grid">
                        <article>
                          <span>{language === "sv" ? "Kunder" : "Customers"}</span>
                          <strong>{backupValidation.counts.customers}</strong>
                        </article>
                        <article>
                          <span>{language === "sv" ? "Fakturor" : "Invoices"}</span>
                          <strong>{backupValidation.counts.invoices}</strong>
                        </article>
                        <article>
                          <span>{language === "sv" ? "Bokforingsrader" : "Journal rows"}</span>
                          <strong>{backupValidation.counts.journalEntries}</strong>
                        </article>
                        <article>
                          <span>{language === "sv" ? "Handelser" : "Events"}</span>
                          <strong>{backupValidation.counts.auditEvents}</strong>
                        </article>
                      </div>
                      {backupValidation.issues.length > 0 && (
                        <ul className="backup-issues">
                          {backupValidation.issues.map((issue) => (
                            <li key={issue}>{issue}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                </div>

                <button type="submit">{t.saveSettings}</button>
                {settingsMessage && <p className="message success">{settingsMessage}</p>}

                <div className="danger-zone">
                  <strong>{language === "sv" ? "Utveckling / test" : "Development / test"}</strong>
                  <p>
                    {language === "sv"
                      ? "Rensa demo- och testdata nar du vill borja om."
                      : "Clear demo and test data when you want to start over."}
                  </p>
                  <button type="button" className="danger-button" onClick={clearTestData}>
                    {t.clearTestData}
                  </button>
                  <button type="button" className="secondary-button" onClick={clearLocalPreferences}>
                    {language === "sv" ? "Aterstall lokala val" : "Reset local preferences"}
                  </button>
                </div>
              </form>
            ) : (
              <p className="empty-state">{language === "sv" ? "Logga in for att se installningar." : "Log in to see settings."}</p>
            )}
          </section>
        )}
      </section>
      <div className={aiAssistantOpen ? "floating-ai floating-ai-open" : "floating-ai"}>
        {aiAssistantOpen && (
          <section className="floating-ai-panel" aria-label={language === "sv" ? "AI-assistent" : "AI assistant"}>
            <header>
              <div>
                <strong>{language === "sv" ? "AI-assistent" : "AI assistant"}</strong>
                <span>{language === "sv" ? "Fraga om AliBooks" : "Ask about AliBooks"}</span>
              </div>
              <button type="button" className="floating-ai-close" onClick={() => setAiAssistantOpen(false)}>
                x
              </button>
            </header>

            <div className="floating-ai-quick">
              {aiContextQuestions().map((question) => (
                <button type="button" key={question} onClick={() => askAiAssistant(question)}>
                  {question}
                </button>
              ))}
            </div>

            <div className="floating-ai-messages">
              {aiAssistantMessages.length === 0 && !aiAssistantLoading ? (
                <p className="floating-ai-empty">
                  {language === "sv"
                    ? `Hej Ali, jag kan hjalpa dig med sidan ${viewTitle()} eller andra delar av AliBooks.`
                    : `Hi Ali, I can help with the ${viewTitle()} page or other parts of AliBooks.`}
                </p>
              ) : (
                aiAssistantMessages.map((message, index) => (
                  <article className={`ai-message ai-message-${message.role}`} key={`floating-${message.role}-${index}`}>
                    <strong>{message.role === "user" ? (language === "sv" ? "Du" : "You") : "AliBooks"}</strong>
                    {message.role === "assistant" && (
                      <span className={`ai-provider-badge ai-provider-${message.provider || "local"}`}>
                        {aiProviderLabel(message.provider)}
                      </span>
                    )}
                    <p>{message.text}</p>
                    {message.role === "assistant" && (
                      <div className="ai-message-actions">
                        <button
                          type="button"
                          className="ai-message-action"
                          onClick={() => copyAiAssistantMessage(message, `floating-${index}`)}
                        >
                          {copiedAiMessageId === `floating-${index}`
                            ? (language === "sv" ? "Kopierad" : "Copied")
                            : (language === "sv" ? "Kopiera" : "Copy")}
                        </button>
                        {message.targetView && (
                          <button
                            type="button"
                            className="ai-message-action"
                            onClick={() => {
                              setActiveView(message.targetView);
                              setAiAssistantOpen(false);
                            }}
                          >
                            {language === "sv" ? `Ga till ${aiTargetLabel(message.targetView)}` : `Go to ${aiTargetLabel(message.targetView)}`}
                          </button>
                        )}
                      </div>
                    )}
                  </article>
                ))
              )}
              {aiAssistantLoading && (
                <div className="floating-ai-loading">
                  <AiLoader size={62} text="AI" />
                  <span>{language === "sv" ? "Tanker..." : "Thinking..."}</span>
                </div>
              )}
            </div>

            <form
              className="floating-ai-form"
              onSubmit={(event) => {
                event.preventDefault();
                askAiAssistant();
              }}
            >
              <textarea
                value={aiAssistantQuestion}
                onChange={(event) => setAiAssistantQuestion(event.target.value)}
                placeholder={language === "sv" ? "Fraga assistenten..." : "Ask the assistant..."}
                rows="3"
              />
              <div className="button-row floating-ai-form-actions">
                <button type="submit" className="primary-small-button">
                  {language === "sv" ? "Skicka" : "Send"}
                </button>
                {aiAssistantMessages.length > 0 && (
                  <button type="button" className="secondary-button" onClick={() => setAiAssistantMessages([])}>
                    {language === "sv" ? "Rensa" : "Clear"}
                  </button>
                )}
              </div>
            </form>
          </section>
        )}

        <button
          type="button"
          className="floating-ai-button"
          onClick={() => setAiAssistantOpen((current) => !current)}
          aria-label={language === "sv" ? "Oppna AI-assistent" : "Open AI assistant"}
        >
          <AiLoader size={68} text="AI" />
        </button>
      </div>
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);
