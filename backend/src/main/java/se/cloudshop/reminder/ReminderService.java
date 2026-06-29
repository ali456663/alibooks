package se.cloudshop.reminder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import se.cloudshop.accounting.JournalEntry;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.accounting.VatReport;
import se.cloudshop.expense.ExpenseRepository;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;

@Service
public class ReminderService {

  private final OrderRepository orderRepository;
  private final ExpenseRepository expenseRepository;
  private final AccountingService accountingService;

  public ReminderService(
      OrderRepository orderRepository,
      ExpenseRepository expenseRepository,
      AccountingService accountingService
  ) {
    this.orderRepository = orderRepository;
    this.expenseRepository = expenseRepository;
    this.accountingService = accountingService;
  }

  public List<Reminder> createReminders() {
    List<Reminder> reminders = new ArrayList<>();
    List<Order> invoices = orderRepository.findAll();
    long unpaidInvoices = invoices.stream()
        .filter(invoice -> !"PAID".equals(invoice.getStatus()))
        .count();
    long overdueInvoices = invoices.stream()
        .filter(invoice -> !"PAID".equals(invoice.getStatus()))
        .filter(invoice -> invoice.getDueDate() != null)
        .filter(invoice -> invoice.getDueDate().isBefore(LocalDate.now()))
        .count();
    long invoicesDueSoon = invoices.stream()
        .filter(invoice -> !"PAID".equals(invoice.getStatus()))
        .filter(invoice -> invoice.getDueDate() != null)
        .filter(invoice -> !invoice.getDueDate().isBefore(LocalDate.now()))
        .filter(invoice -> ChronoUnit.DAYS.between(LocalDate.now(), invoice.getDueDate()) <= 5)
        .count();
    VatReport vatReport = accountingService.createVatReport();

    if (unpaidInvoices > 0) {
      reminders.add(new Reminder(
          "INVOICES",
          "Obetalda fakturor / Unpaid invoices",
          unpaidInvoices + " faktura/fakturor ar inte markerade som betalda.",
          "warning"
      ));
    }

    if (overdueInvoices > 0) {
      reminders.add(new Reminder(
          "OVERDUE",
          "Forfallna fakturor / Overdue invoices",
          overdueInvoices + " faktura/fakturor har passerat forfallodatum och ar inte betalda.",
          "important"
      ));
    }

    if (invoicesDueSoon > 0) {
      reminders.add(new Reminder(
          "INVOICE_REMINDER",
          "Fakturapaminnelser / Invoice reminders",
          invoicesDueSoon + " faktura/fakturor forfaller inom 5 dagar. Skicka en vanlig paminnelse innan forfallodatum.",
          "warning"
      ));
    }

    if (vatReport.vatToPay() > 0) {
      reminders.add(new Reminder(
          "VAT",
          "Moms att betala / VAT to pay",
          "Nuvarande momsrapport visar " + vatReport.vatToPay() + " SEK att betala.",
          "important"
      ));
    }

    if (expenseRepository.count() == 0) {
      reminders.add(new Reminder(
          "EXPENSES",
          "Inga kostnader registrerade / No expenses registered",
          "Du har inga kostnader registrerade annu. Kontrollera om kvitton eller inkop ska bokforas.",
          "info"
      ));
    }

    if (hasNoBookkeepingThisMonth()) {
      reminders.add(new Reminder(
          "MONTHLY_BOOKKEEPING",
          "Bokforing for manaden / Monthly bookkeeping",
          "Jag hittar inga bokforingsrader for denna manad. Kontrollera om fakturor eller kostnader saknas.",
          "info"
      ));
    }

    LocalDate annualVatDeadline = LocalDate.of(LocalDate.now().getYear() + 1, 5, 12);
    long daysToVatDeadline = ChronoUnit.DAYS.between(LocalDate.now(), annualVatDeadline);
    reminders.add(new Reminder(
        "VAT_DEADLINE",
        "Momsdatum / VAT deadline",
        "Demo: arsvis moms for enskild firma brukar kunna ha deadline 12 maj aret efter beskattningsaret. Nasta demo-datum: "
            + annualVatDeadline
            + " (om "
            + daysToVatDeadline
            + " dagar). Kontrollera alltid exakt datum hos Skatteverket.",
        daysToVatDeadline <= 30 ? "important" : "info"
    ));

    reminders.add(new Reminder(
        "DECLARATION",
        "Deklaration / Income declaration",
        "Kontrollera deklarationsdatum i Skatteverkets Mina sidor. AliBooks kan paminna dig, men exakta datum beror pa din situation.",
        "info"
    ));

    if (invoices.isEmpty()) {
      reminders.add(new Reminder(
          "START",
          "Skapa forsta fakturan / Create first invoice",
          "Lagg till kund och skapa en faktura for att starta fakturaflodet.",
          "info"
      ));
    }

    if (reminders.isEmpty()) {
      reminders.add(new Reminder(
          "OK",
          "Allt ser lugnt ut / Everything looks calm",
          "Inga akuta bokforingspaminelser just nu.",
          "success"
      ));
    }

    return reminders;
  }

  private boolean hasNoBookkeepingThisMonth() {
    LocalDate now = LocalDate.now();
    List<JournalEntry> entries = accountingService.findAllEntries();

    return entries.stream()
        .map(entry -> entry.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
        .noneMatch(date -> date.getYear() == now.getYear() && date.getMonth() == now.getMonth());
  }

  public AdvisorSummary createAdvisorSummary() {
    List<Order> invoices = orderRepository.findAll();
    long unpaidInvoices = invoices.stream()
        .filter(invoice -> !"PAID".equals(invoice.getStatus()))
        .count();
    long paidInvoices = invoices.stream()
        .filter(invoice -> "PAID".equals(invoice.getStatus()))
        .count();
    long overdueInvoices = invoices.stream()
        .filter(invoice -> !"PAID".equals(invoice.getStatus()))
        .filter(invoice -> invoice.getDueDate() != null)
        .filter(invoice -> invoice.getDueDate().isBefore(LocalDate.now()))
        .count();
    long expenses = expenseRepository.count();
    VatReport vatReport = accountingService.createVatReport();

    StringBuilder message = new StringBuilder();
    message.append("Du har ")
        .append(invoices.size())
        .append(" fakturor, varav ")
        .append(paidInvoices)
        .append(" ar betalda och ")
        .append(unpaidInvoices)
        .append(" ar obetalda. ");

    if (overdueInvoices > 0) {
      message.append(overdueInvoices)
          .append(" obetald faktura har passerat forfallodatum. ");
    }

    if (vatReport.vatToPay() > 0) {
      message.append("Momsrapporten visar ")
          .append(vatReport.vatToPay())
          .append(" SEK att betala. ");
    } else if (vatReport.vatToPay() < 0) {
      message.append("Momsrapporten visar ")
          .append(Math.abs(vatReport.vatToPay()))
          .append(" SEK att fa tillbaka. ");
    } else {
      message.append("Momsrapporten ar just nu neutral. ");
    }

    if (expenses == 0) {
      message.append("Jag ser inga registrerade kostnader annu, sa kontrollera om du har kvitton att lagga in.");
    } else {
      message.append("Du har registrerat ")
          .append(expenses)
          .append(" kostnad/kostnader.");
    }

    message.append(" Kom ihag att kontrollera exakta moms- och deklarationsdatum hos Skatteverket.");

    return new AdvisorSummary("AI Advisor / AI-radgivare", message.toString());
  }
}
