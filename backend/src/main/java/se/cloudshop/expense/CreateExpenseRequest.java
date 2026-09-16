package se.cloudshop.expense;

import java.time.LocalDate;

public record CreateExpenseRequest(
    LocalDate expenseDate,
    String description,
    int netAmount,
    int vatAmount,
    String category,
    String paidFrom,
    se.cloudshop.bank.BankImportRow bankRow
) {
  public CreateExpenseRequest(LocalDate expenseDate, String description, int netAmount, int vatAmount, String category, String paidFrom) {
    this(expenseDate, description, netAmount, vatAmount, category, paidFrom, null);
  }
}
