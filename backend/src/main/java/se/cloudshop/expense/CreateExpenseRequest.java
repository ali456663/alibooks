package se.cloudshop.expense;

import java.time.LocalDate;

public record CreateExpenseRequest(
    LocalDate expenseDate,
    String description,
    int netAmount,
    int vatAmount,
    String category,
    String paidFrom
) {
}

